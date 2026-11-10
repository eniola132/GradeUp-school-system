package com.gradeup.domain;

import jakarta.persistence.*;

/**
 * One short, important idea taken from a handout,
 * e.g. "A queue is first-in, first-out (FIFO)."
 *
 * (See User.java for a short explanation of the database annotations.)
 */
@Entity
@Table(name = "key_points", indexes = @Index(name = "idx_keypoint_handout", columnList = "handout_id"))
public class KeyPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The handout this key point came from. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "handout_id", nullable = false)
    private Handout handout;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    /** How important it is: 1 = background ... 5 = must know. */
    @Column(nullable = false)
    private int importance = 3;

    /** Its position in the list (0 = first), so key points always show in the same order. */
    @Column(nullable = false)
    private int orderIndex;

    /** Empty constructor that Hibernate needs. */
    protected KeyPoint() { }

    public KeyPoint(Handout handout, String text, int importance, int orderIndex) {
        this.handout = handout;
        this.text = text;
        this.importance = importance;
        this.orderIndex = orderIndex;
    }

    /** The words shown on the coloured badge next to the key point. */
    @Transient
    public String getImportanceLabel() {
        if (importance == 5) {
            return "Must know";
        } else if (importance == 4) {
            return "Important";
        } else if (importance == 3) {
            return "Good to know";
        } else {
            return "Background";
        }
    }

    // ----- Getters and setters -----

    public Long getId() { return id; }
    public Handout getHandout() { return handout; }
    public void setHandout(Handout handout) { this.handout = handout; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public int getImportance() { return importance; }
    public void setImportance(int importance) { this.importance = importance; }
    public int getOrderIndex() { return orderIndex; }
    public void setOrderIndex(int orderIndex) { this.orderIndex = orderIndex; }
}
