package com.gradeup.ai;

/**
 * One key point produced by the AI, before we save it to the database.
 *
 * WHAT IS A "record"?
 * A record is a short way to write a class that only holds data. This one line:
 *
 *     public record AiKeyPoint(String text, int importance)
 *
 * automatically gives us a constructor, plus the methods text() and importance()
 * to read the values back. Records can't be changed after they're created.
 *
 * @param text       the key point itself, e.g. "A stack is last-in, first-out."
 * @param importance how important it is, from 1 (background) to 5 (must know)
 */
public record AiKeyPoint(String text, int importance) {

    /**
     * This block runs every time a new AiKeyPoint is created.
     * We use it to check the values and fix them up if needed.
     */
    public AiKeyPoint {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Key point text must not be blank");
        }

        // Keep importance between 1 and 5, even if the AI sends something like 9 or -2.
        if (importance < 1) {
            importance = 1;
        }
        if (importance > 5) {
            importance = 5;
        }
    }
}
