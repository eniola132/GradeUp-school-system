package com.gradeup.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A course the student is taking, for example "CSC301 — Data Structures".
 *
 * How things connect:
 *   User  --has many-->  Course  --has many-->  Handout
 *
 * (See User.java for a short explanation of the database annotations.)
 */
@Entity
@Table(name = "courses", indexes = @Index(name = "idx_course_owner", columnList = "owner_id"))
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The student who owns this course.
     * @ManyToOne  -> many courses can belong to one user.
     * LAZY       -> don't load the user from the database until someone actually asks for it.
     * @JoinColumn -> the column "owner_id" holds the user's id.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    /** Short course code, e.g. "CSC301". Two students can both have a "CSC301". */
    @Column(nullable = false, length = 32)
    private String code;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(length = 600)
    private String description;

    /** The colour of the course card on screen: "violet", "mint", "amber", "rose" or "sky". */
    @Column(nullable = false, length = 20)
    private String colorTag = "violet";

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    /**
     * The handouts (PDFs) uploaded to this course, newest first.
     * It's a Set (no duplicates) and a LinkedHashSet keeps the order they were loaded in.
     */
    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("uploadedAt DESC")
    private Set<Handout> handouts = new LinkedHashSet<>();

    /** Empty constructor that Hibernate needs. */
    protected Course() { }

    public Course(User owner, String code, String title) {
        this.owner = owner;
        this.code = code;
        this.title = title;
    }

    /** How many practice questions this course has, adding up every handout. */
    @Transient
    public int getQuestionCount() {
        int total = 0;
        for (Handout handout : handouts) {
            total = total + handout.getQuestions().size();
        }
        return total;
    }

    /** How many key points this course has, adding up every handout. */
    @Transient
    public int getKeyPointCount() {
        int total = 0;
        for (Handout handout : handouts) {
            total = total + handout.getKeyPoints().size();
        }
        return total;
    }

    // ----- Getters and setters -----

    public Long getId() { return id; }
    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getColorTag() { return colorTag; }
    public void setColorTag(String colorTag) { this.colorTag = colorTag; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Set<Handout> getHandouts() { return handouts; }
    public void setHandouts(Set<Handout> handouts) { this.handouts = handouts; }
}
