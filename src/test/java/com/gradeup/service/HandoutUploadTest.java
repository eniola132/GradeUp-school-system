package com.gradeup.service;

import com.gradeup.IntegrationTest;
import com.gradeup.config.GradeUpProperties;
import com.gradeup.domain.Course;
import com.gradeup.domain.Handout;
import com.gradeup.domain.User;
import com.gradeup.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@IntegrationTest
class HandoutUploadTest {

    @Autowired HandoutService handoutService;
    @Autowired CourseService courseService;
    @Autowired UserService userService;
    @Autowired StorageService storageService;
    @Autowired UserRepository userRepository;
    @Autowired GradeUpProperties properties;

    private User owner;
    private User other;
    private Course course;

    @BeforeEach
    void setUp() {
        owner = userRepository.findByEmailIgnoreCase("uploader@test.com")
                .orElseGet(() -> userService.register("uploader@test.com", "password123", "password123", "Uploader"));
        other = userRepository.findByEmailIgnoreCase("uploadother@test.com")
                .orElseGet(() -> userService.register("uploadother@test.com", "password123", "password123", "Other"));
        course = courseService.create(owner, "UP1", "Upload Course", null, "sky");
    }

    /** Minimal but genuinely valid PDF containing extractable text. */
    private static byte[] validPdf(String text) {
        String content = "BT /F1 12 Tf 50 760 Td 16 TL\n(" + text + ") Tj T*\nET";
        byte[] stream = content.getBytes(StandardCharsets.ISO_8859_1);
        StringBuilder sb = new StringBuilder();
        sb.append("%PDF-1.4\n");
        int[] offsets = new int[5];
        String[] objs = {
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                        + "/Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
                null
        };
        for (int i = 0; i < objs.length; i++) {
            offsets[i] = sb.length();
            sb.append(i + 1).append(" 0 obj\n");
            if (i == 4) {
                sb.append("<< /Length ").append(stream.length).append(" >>\nstream\n");
                sb.append(new String(stream, StandardCharsets.ISO_8859_1));
                sb.append("\nendstream");
            } else {
                sb.append(objs[i]);
            }
            sb.append("\nendobj\n");
        }
        int xref = sb.length();
        sb.append("xref\n0 6\n0000000000 65535 f \n");
        for (int off : offsets) sb.append(String.format("%010d 00000 n \n", off));
        sb.append("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        return sb.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    private MockMultipartFile pdf(String filename, String text) {
        return new MockMultipartFile("file", filename, "application/pdf", validPdf(text));
    }

    @Test
    void uploadsValidPdfAndExtractsText() {
        String sentence = "Binary search trees keep keys ordered so that lookup stays logarithmic "
                + "even as the number of stored elements grows very large over time.";

        Handout h = handoutService.upload(owner, course.getId(), pdf("lecture.pdf", sentence));

        assertNotEquals(Handout.Status.FAILED, h.getStatus(), "valid PDF should not fail");
        assertTrue(h.hasText(), "text should be extracted");
        assertTrue(h.getExtractedText().contains("Binary search trees"));
        assertEquals("lecture.pdf", h.getOriginalFilename());
        assertTrue(h.getPageCount() >= 1);
    }

    @Test
    void rejectsNonPdfContentEvenWithPdfExtension() {
        MockMultipartFile fake = new MockMultipartFile("file", "evil.pdf",
                "application/pdf", "this is definitely not a pdf".getBytes());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> handoutService.upload(owner, course.getId(), fake));
        assertTrue(ex.getMessage().toLowerCase().contains("valid pdf"));
    }

    @Test
    void rejectsEmptyFile() {
        MockMultipartFile empty = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);
        assertThrows(IllegalArgumentException.class,
                () -> handoutService.upload(owner, course.getId(), empty));
    }

    @Test
    void rejectsNonPdfExtension() {
        MockMultipartFile txt = new MockMultipartFile("file", "notes.txt",
                "text/plain", validPdf("hello").clone());
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> handoutService.upload(owner, course.getId(), txt));
        assertTrue(ex.getMessage().toLowerCase().contains("pdf"));
    }

    @Test
    void pathTraversalFilenameCannotEscapeStorageRoot() {
        // The stored path must never contain the traversal sequence, because the client
        // filename is never used as a path component.
        Handout h = handoutService.upload(owner, course.getId(),
                pdf("../../../../etc/passwd.pdf", "Some readable handout content for extraction purposes here."));

        assertFalse(h.getStoredPath().contains(".."), "stored path must not contain traversal");
        assertFalse(h.getStoredPath().contains("passwd"), "stored path must not use the client filename");
        assertTrue(h.getStoredPath().endsWith(".pdf"));
    }

    @Test
    void refusesToUploadIntoAnotherUsersCourse() {
        assertThrows(NotFoundException.class,
                () -> handoutService.upload(other, course.getId(),
                        pdf("sneaky.pdf", "Trying to write into a course I do not own at all.")));
    }

    @Test
    void refusesToReadAnotherUsersHandout() {
        Handout h = handoutService.upload(owner, course.getId(),
                pdf("private.pdf", "Private handout content that belongs only to the owner account."));

        assertThrows(NotFoundException.class, () -> handoutService.getOwned(h.getId(), other));
    }

    @Test
    void storageResolveRejectsEscapingPaths() {
        assertThrows(IllegalArgumentException.class, () -> storageService.resolve("../../../etc/passwd"));
    }

    @Test
    void rejectsFileLargerThanConfiguredLimit() {
        // Simulate an oversized upload without allocating 25MB of real data.
        long limit = properties.getUpload().getMaxSizeBytes();
        byte[] header = validPdf("small");
        MockMultipartFile big = new MockMultipartFile("file", "big.pdf", "application/pdf", header) {
            @Override public long getSize() { return limit + 1; }
        };

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> handoutService.upload(owner, course.getId(), big));
        assertTrue(ex.getMessage().toLowerCase().contains("larger"));
    }
}
