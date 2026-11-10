package com.gradeup.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A PDF handout that a student uploaded, plus the text we read out of it.
 *
 * How things connect:
 *   Course  --has many-->  Handout  --has many-->  KeyPoint
 *                                   --has many-->  Question
 *
 * (See User.java for a short explanation of the database annotations.)
 */
@Entity
@Table(name = "handouts", indexes = {
        @Index(name = "idx_handout_course", columnList = "course_id")
})
public class Handout {

    /**
     * The steps a handout goes through after upload.
     * An "enum" is a fixed list of allowed values — a status can only be one of these four.
     */
    public enum Status {
        UPLOADED,     // the file is saved, but we haven't read the text yet
        EXTRACTED,    // we've read the text out of the PDF
        SUMMARIZED,   // key points and questions have been made
        FAILED        // we couldn't read the PDF (e.g. it's a scanned image)
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    /** The file's name on the student's computer, e.g. "week3-notes.pdf". Only for display. */
    @Column(nullable = false, length = 260)
    private String originalFilename;

    /**
     * Where we saved the file, relative to the storage folder, e.g. "4/1b9d...e2.pdf".
     * This is a name WE made up, never the student's filename (see StorageService for why).
     */
    @Column(nullable = false, length = 400)
    private String storedPath;

    private long sizeBytes;

    private int pageCount;

    /** All the text from the PDF. @Lob + TEXT lets it be very long. */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String extractedText;

    /** EnumType.STRING saves the status as a word like "EXTRACTED" instead of a number. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.UPLOADED;

    /** If the status is FAILED, this says why (shown to the student). */
    @Column(length = 400)
    private String failureReason;

    @Column(nullable = false)
    private Instant uploadedAt = Instant.now();

    @OneToMany(mappedBy = "handout", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    private Set<KeyPoint> keyPoints = new LinkedHashSet<>();

    @OneToMany(mappedBy = "handout", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private Set<Question> questions = new LinkedHashSet<>();

    /** Empty constructor that Hibernate needs. */
    protected Handout() { }

    public Handout(Course course, String originalFilename, String storedPath, long sizeBytes) {
        this.course = course;
        this.originalFilename = originalFilename;
        this.storedPath = storedPath;
        this.sizeBytes = sizeBytes;
    }

    /** True if we managed to read some text from the PDF. */
    @Transient
    public boolean hasText() {
        return extractedText != null && !extractedText.isBlank();
    }

    /** The file size in a friendly form: "800 B", "45 KB" or "2.3 MB". */
    @Transient
    public String getDisplaySize() {
        long oneKilobyte = 1024;
        long oneMegabyte = 1024 * 1024;

        if (sizeBytes < oneKilobyte) {
            return sizeBytes + " B";
        }
        if (sizeBytes < oneMegabyte) {
            double kilobytes = sizeBytes / 1024.0;
            return String.format("%.0f KB", kilobytes);   // %.0f = no decimal places
        }
        double megabytes = sizeBytes / (1024.0 * 1024.0);
        return String.format("%.1f MB", megabytes);       // %.1f = one decimal place
    }

    // ----- Getters and setters -----

    public Long getId() { return id; }
    public Course getCourse() { return course; }
    public void setCourse(Course course) { this.course = course; }
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
    public String getStoredPath() { return storedPath; }
    public void setStoredPath(String storedPath) { this.storedPath = storedPath; }
    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
    public int getPageCount() { return pageCount; }
    public void setPageCount(int pageCount) { this.pageCount = pageCount; }
    public String getExtractedText() { return extractedText; }
    public void setExtractedText(String extractedText) { this.extractedText = extractedText; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public Instant getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(Instant uploadedAt) { this.uploadedAt = uploadedAt; }
    public Set<KeyPoint> getKeyPoints() { return keyPoints; }
    public void setKeyPoints(Set<KeyPoint> keyPoints) { this.keyPoints = keyPoints; }
    public Set<Question> getQuestions() { return questions; }
    public void setQuestions(Set<Question> questions) { this.questions = questions; }
}
