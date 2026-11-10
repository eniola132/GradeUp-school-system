package com.gradeup.domain;

import jakarta.persistence.*;

/**
 * The answer a student gave to ONE question during ONE quiz.
 * A quiz with 10 questions creates 10 of these.
 *
 * (See User.java for a short explanation of the database annotations.)
 */
@Entity
@Table(name = "attempt_answers", indexes = @Index(name = "idx_answer_attempt", columnList = "attempt_id"))
public class AttemptAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The quiz this answer belongs to. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false)
    private QuizAttempt attempt;

    /** The question that was answered. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    /** The letter they picked ("A" to "D"), or null if they skipped the question. */
    @Column(length = 1)
    private String selectedOption;

    @Column(nullable = false)
    private boolean correct;

    /** Empty constructor that Hibernate needs. */
    protected AttemptAnswer() { }

    /** Creates the answer and works out straight away whether it was right. */
    public AttemptAnswer(QuizAttempt attempt, Question question, String selectedOption) {
        this.attempt = attempt;
        this.question = question;
        this.selectedOption = selectedOption;
        this.correct = question.isCorrect(selectedOption);
    }

    /** True if the student didn't pick anything for this question. */
    @Transient
    public boolean isSkipped() {
        return selectedOption == null || selectedOption.isBlank();
    }

    // ----- Getters and setters -----

    public Long getId() { return id; }
    public QuizAttempt getAttempt() { return attempt; }
    public void setAttempt(QuizAttempt attempt) { this.attempt = attempt; }
    public Question getQuestion() { return question; }
    public void setQuestion(Question question) { this.question = question; }
    public String getSelectedOption() { return selectedOption; }
    public void setSelectedOption(String selectedOption) { this.selectedOption = selectedOption; }
    public boolean isCorrect() { return correct; }
    public void setCorrect(boolean correct) { this.correct = correct; }
}
