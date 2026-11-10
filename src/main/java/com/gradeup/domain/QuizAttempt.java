package com.gradeup.domain;

import jakarta.persistence.*;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One finished quiz: who took it, for which course, their score, and every answer they gave.
 *
 * How things connect:
 *   QuizAttempt  --has many-->  AttemptAnswer  --points to-->  Question
 *
 * (See User.java for a short explanation of the database annotations.)
 */
@Entity
@Table(name = "quiz_attempts", indexes = @Index(name = "idx_attempt_user", columnList = "user_id"))
public class QuizAttempt {

    /** The lowest percentage that counts as a pass. */
    private static final int PASS_MARK = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(nullable = false)
    private Instant startedAt = Instant.now();

    private Instant completedAt;

    /** How many questions they got right. */
    @Column(nullable = false)
    private int score = 0;

    @Column(nullable = false)
    private int totalQuestions = 0;

    /** Whether the student chose to see right/wrong after each question. */
    @Column(nullable = false)
    private boolean instantFeedback = false;

    /** One entry per question in the quiz, in the order they were asked. */
    @OneToMany(mappedBy = "attempt", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<AttemptAnswer> answers = new ArrayList<>();

    /** Empty constructor that Hibernate needs. */
    protected QuizAttempt() { }

    public QuizAttempt(User user, Course course) {
        this.user = user;
        this.course = course;
    }

    /** The score as a percentage from 0 to 100. */
    @Transient
    public int getPercentage() {
        // Avoid dividing by zero if the quiz somehow had no questions.
        if (totalQuestions <= 0) {
            return 0;
        }
        float percentage = (score * 100f) / totalQuestions;
        return Math.round(percentage);
    }

    /** The letter grade shown on the results page. */
    @Transient
    public String getGradeLetter() {
        int percentage = getPercentage();
        if (percentage >= 90) return "A";
        if (percentage >= 80) return "B";
        if (percentage >= 70) return "C";
        if (percentage >= 60) return "D";
        if (percentage >= 50) return "E";
        return "F";
    }

    @Transient
    public boolean isPassed() {
        return getPercentage() >= PASS_MARK;
    }

    /** How long the quiz took, written like "45s" or "2m 14s". */
    @Transient
    public String getDurationLabel() {
        if (completedAt == null) {
            return "—";
        }

        long seconds = Duration.between(startedAt, completedAt).getSeconds();
        if (seconds < 0) {
            seconds = 0;
        }

        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        long secondsLeftOver = seconds % 60;   // % gives the remainder after dividing
        return minutes + "m " + secondsLeftOver + "s";
    }

    // ----- Getters and setters -----

    public Long getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public Course getCourse() { return course; }
    public void setCourse(Course course) { this.course = course; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
    public int getTotalQuestions() { return totalQuestions; }
    public void setTotalQuestions(int totalQuestions) { this.totalQuestions = totalQuestions; }
    public boolean isInstantFeedback() { return instantFeedback; }
    public void setInstantFeedback(boolean instantFeedback) { this.instantFeedback = instantFeedback; }
    public List<AttemptAnswer> getAnswers() { return answers; }
    public void setAnswers(List<AttemptAnswer> answers) { this.answers = answers; }
}
