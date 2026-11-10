package com.gradeup.service;

import com.gradeup.config.GradeUpProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

/**
 * Saves uploaded PDF files to the disk (and deletes them again).
 *
 * Files are saved like this:   storage/handouts/<userId>/<random-name>.pdf
 *
 * SECURITY — because we're accepting files from strangers:
 *   1. We NEVER use the uploaded file's name to decide where to save it. Someone could
 *      name a file "../../important-file" to try to write outside our folder. Instead we
 *      make up a random name (a UUID, like "1b9d6bcd-bbfd-4b2d-9b5d-ab8dfbbd4bed.pdf").
 *   2. We double-check the final path is still inside our storage folder.
 *   3. We check the file really is a PDF by reading its first bytes — every real PDF
 *      starts with "%PDF". Renaming "virus.exe" to "notes.pdf" won't fool this check.
 */
@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    /** The first 4 bytes of every PDF file: the characters "%PDF". */
    private static final byte[] PDF_SIGNATURE = {0x25, 0x50, 0x44, 0x46};

    /** The folder all handouts are saved in (e.g. /home/you/GradeUp/storage/handouts). */
    private final Path storageRoot;

    private final long maxSizeBytes;

    public StorageService(GradeUpProperties properties) {
        // toAbsolutePath() turns "./storage/handouts" into a full path.
        // normalize() cleans up things like "a/./b" or "a/x/../b".
        this.storageRoot = Paths.get(properties.getStorage().getLocation()).toAbsolutePath().normalize();
        this.maxSizeBytes = properties.getUpload().getMaxSizeBytes();

        try {
            Files.createDirectories(storageRoot);   // create the folder if it doesn't exist yet
            log.info("Handout storage root: {}", storageRoot);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not create handout storage at " + storageRoot, ex);
        }
    }

    /**
     * The result of saving a file. (A "record" is a small class that just holds data.)
     *
     * @param relativePath     where we saved it, inside the storage folder, e.g. "4/1b9d....pdf"
     * @param originalFilename the cleaned-up name the student's file had
     * @param sizeBytes        how big the file is
     */
    public record Stored(String relativePath, String originalFilename, long sizeBytes) { }

    /**
     * Checks an uploaded file and saves it in the user's folder.
     *
     * @throws IllegalArgumentException if the file is empty, too big, or not a real PDF
     *                                  (the message is shown to the student)
     */
    public Stored storePdf(MultipartFile file, Long userId) {
        // --- Check 1: is there a file at all? ---
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Please choose a PDF file to upload.");
        }

        // --- Check 2: is it small enough? ---
        if (file.getSize() > maxSizeBytes) {
            long maxMegabytes = maxSizeBytes / (1024 * 1024);
            throw new IllegalArgumentException("That file is larger than the " + maxMegabytes + " MB limit.");
        }

        // --- Check 3: does the name end in .pdf? ---
        String originalName = cleanFilename(file.getOriginalFilename());
        if (!originalName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new IllegalArgumentException("Only PDF files are supported.");
        }

        // --- Check 4: does the browser say it's a PDF? ---
        // (Some browsers send the vague "application/octet-stream", so we allow that too.)
        String contentType = file.getContentType();
        if (contentType != null) {
            boolean looksLikePdf = contentType.toLowerCase(Locale.ROOT).contains("pdf");
            boolean isGenericType = contentType.equals("application/octet-stream");
            if (!looksLikePdf && !isGenericType) {
                throw new IllegalArgumentException("That doesn't look like a PDF file.");
            }
        }

        // --- Check 5: does the file really start with "%PDF"? ---
        // "try (...)" automatically closes the file stream when we're done with it.
        try (InputStream in = file.getInputStream()) {
            if (!startsWithPdfSignature(in)) {
                throw new IllegalArgumentException(
                        "That file isn't a valid PDF. Please upload the original handout PDF.");
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("The upload could not be read. Please try again.", ex);
        }

        // --- All checks passed: work out where to save it. ---
        String savedName = UUID.randomUUID() + ".pdf";    // a random, unguessable name
        Path userFolder = storageRoot.resolve(String.valueOf(userId)).normalize();
        Path target = userFolder.resolve(savedName).normalize();

        // Extra safety: make sure the path didn't somehow end up outside our folder.
        if (!target.startsWith(storageRoot)) {
            throw new IllegalStateException("Resolved storage path escaped the storage root");
        }

        // --- Save the file. ---
        try {
            Files.createDirectories(userFolder);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Could not save the uploaded file.", ex);
        }

        // Store the path relative to the storage folder, always with "/" (even on Windows).
        String relativePath = storageRoot.relativize(target).toString().replace('\\', '/');

        log.info("Stored handout for user {} ({} bytes)", userId, file.getSize());
        return new Stored(relativePath, originalName, file.getSize());
    }

    /**
     * Turns a saved relative path (like "4/abc.pdf") back into a full path on disk.
     * Refuses any path that would point outside the storage folder.
     */
    public Path resolve(String relativePath) {
        Path fullPath = storageRoot.resolve(relativePath).normalize();
        if (!fullPath.startsWith(storageRoot)) {
            throw new IllegalArgumentException("Invalid stored path");
        }
        return fullPath;
    }

    /** Deletes a saved file. If it's already gone, that's fine — nothing happens. */
    public void delete(String relativePath) {
        try {
            Files.deleteIfExists(resolve(relativePath));
        } catch (IOException ex) {
            log.warn("Could not delete stored handout {}: {}", relativePath, ex.getMessage());
        }
    }

    /** Reads the first 4 bytes of the file and checks they are "%PDF". */
    private boolean startsWithPdfSignature(InputStream in) throws IOException {
        byte[] firstBytes = new byte[4];
        int bytesRead = in.read(firstBytes);
        if (bytesRead < 4) {
            return false;   // the file is shorter than 4 bytes, so it can't be a PDF
        }
        for (int i = 0; i < 4; i++) {
            if (firstBytes[i] != PDF_SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Cleans up the name of the uploaded file, which we only use for DISPLAY.
     * Example: "C:\Users\ama\Desktop\notes.pdf" becomes "notes.pdf".
     */
    private String cleanFilename(String name) {
        if (name == null || name.isBlank()) {
            return "handout.pdf";
        }

        // Keep only the part after the last slash (Windows uses \, everything else uses /).
        String cleaned = name.replace('\\', '/');
        int lastSlash = cleaned.lastIndexOf('/');
        if (lastSlash >= 0) {
            cleaned = cleaned.substring(lastSlash + 1);
        }

        // Remove invisible "control characters" (like tabs and line breaks).
        cleaned = cleaned.replaceAll("[\\p{Cntrl}]", "").trim();
        if (cleaned.isBlank()) {
            return "handout.pdf";
        }

        // The database column fits at most 255 characters.
        if (cleaned.length() > 255) {
            cleaned = cleaned.substring(0, 255);
        }
        return cleaned;
    }
}
