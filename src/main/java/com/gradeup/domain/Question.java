package com.gradeup.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * A multiple-choice practice question made from a handout.
 * Every question has exactly four answers, labelled A, B, C and D.
 *
 * (See User.java for a short explanation of the database annotations.)
 */
@Entity
@Table(name = "questions", indexes = @Index(name = "idx_question_handout", columnList = "handout_id"))
public class Question {

    /** How hard the question is. */
    public enum Difficulty { EASY, MEDIUM, HARD }

    /** Who wrote the question: the real AI service, or the offline engine on this computer. */
    public enum Source { AI, OFFLINE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The handout this question was made from. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "handout_id", nullable = false)
    private Handout handout;

    /** The question itself. */
    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String prompt;

    // The four possible answers.
    @Column(nullable = false, length = 500) private String optionA;
    @Column(nullable = false, length = 500) private String optionB;
    @Column(nullable = false, length = 500) private String optionC;
    @Column(nullable = false, length = 500) private String optionD;

    /** The letter of the right answer: "A", "B", "C" or "D". */
    @Column(nullable = false, length = 1)
    private String correctOption;

    /** Why the right answer is right. Shown after the quiz. */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String explanation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Difficulty difficulty = Difficulty.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Source source = Source.OFFLINE;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    /** What kind of question it is, e.g. "Definition", "Application" or "Comparison". */
    @Column(length = 60)
    private String angle;

    /** Empty constructor that Hibernate needs. */
    protected Question() { }

    public Question(Handout handout, String prompt, String optionA, String optionB,
                    String optionC, String optionD, String correctOption) {
        this.handout = handout;
        this.prompt = prompt;
        this.optionA = optionA;
        this.optionB = optionB;
        this.optionC = optionC;
        this.optionD = optionD;
        this.correctOption = correctOption;
    }

    /**
     * Gives back the answer text for a letter.
     * Example: optionText("B") returns whatever optionB is. Unknown letters return null.
     */
    @Transient
    public String optionText(String letter) {
        if (letter == null) {
            return null;
        }

        String upper = letter.toUpperCase();
        if (upper.equals("A")) {
            return optionA;
        } else if (upper.equals("B")) {
            return optionB;
        } else if (upper.equals("C")) {
            return optionC;
        } else if (upper.equals("D")) {
            return optionD;
        } else {
            return null;
        }
    }

    /** True if the given letter is the right answer. "b" and "B" both count. */
    @Transient
    public boolean isCorrect(String letter) {
        if (letter == null) {
            return false;
        }
        return letter.equalsIgnoreCase(correctOption);
    }

    // ----- Getters and setters -----

    public Long getId() { return id; }
    public Handout getHandout() { return handout; }
    public void setHandout(Handout handout) { this.handout = handout; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public String getOptionA() { return optionA; }
    public void setOptionA(String optionA) { this.optionA = optionA; }
    public String getOptionB() { return optionB; }
    public void setOptionB(String optionB) { this.optionB = optionB; }
    public String getOptionC() { return optionC; }
    public void setOptionC(String optionC) { this.optionC = optionC; }
    public String getOptionD() { return optionD; }
    public void setOptionD(String optionD) { this.optionD = optionD; }
    public String getCorrectOption() { return correctOption; }
    public void setCorrectOption(String correctOption) { this.correctOption = correctOption; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
    public Difficulty getDifficulty() { return difficulty; }
    public void setDifficulty(Difficulty difficulty) { this.difficulty = difficulty; }
    public Source getSource() { return source; }
    public void setSource(Source source) { this.source = source; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public String getAngle() { return angle; }
    public void setAngle(String angle) { this.angle = angle; }
}
