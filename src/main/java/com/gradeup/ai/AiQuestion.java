package com.gradeup.ai;

import com.gradeup.domain.Question;

/**
 * One multiple-choice question produced by the AI, before we save it to the database.
 *
 * This is a "record" — a small class that only holds data (see AiKeyPoint for a
 * longer explanation). You read the values with prompt(), options(), correctOption(), etc.
 *
 * @param prompt        the question text
 * @param options       exactly four answers, in the order A, B, C, D
 * @param correctOption the letter of the right answer: "A", "B", "C" or "D"
 * @param explanation   a sentence explaining why the answer is right
 * @param difficulty    EASY, MEDIUM or HARD
 * @param angle         what kind of question it is, e.g. "Definition" or "Application"
 */
public record AiQuestion(
        String prompt,
        String[] options,
        String correctOption,
        String explanation,
        Question.Difficulty difficulty,
        String angle
) {

    /** Runs whenever a new AiQuestion is created: checks the data and fills in defaults. */
    public AiQuestion {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Question prompt must not be blank");
        }

        if (options == null || options.length != 4) {
            throw new IllegalArgumentException("A question needs exactly four options");
        }
        for (String option : options) {
            if (option == null || option.isBlank()) {
                throw new IllegalArgumentException("Question options must not be blank");
            }
        }

        // The regular expression [A-Da-d] means "exactly one letter from A to D, upper or lower case".
        if (correctOption == null || !correctOption.matches("[A-Da-d]")) {
            throw new IllegalArgumentException("correctOption must be A, B, C or D");
        }
        correctOption = correctOption.toUpperCase();

        // Fill in sensible defaults for anything missing.
        if (difficulty == null) {
            difficulty = Question.Difficulty.MEDIUM;
        }
        if (angle == null || angle.isBlank()) {
            angle = "Recall";
        }
    }

    // Small helpers so other code can say q.optionA() instead of q.options()[0].
    public String optionA() { return options[0]; }
    public String optionB() { return options[1]; }
    public String optionC() { return options[2]; }
    public String optionD() { return options[3]; }
}
