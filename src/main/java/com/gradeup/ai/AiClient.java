package com.gradeup.ai;

import java.util.List;

/**
 * The "AI" part of GradeUp, described as an interface.
 *
 * WHAT IS AN INTERFACE?
 * An interface is a list of methods WITHOUT their code — a promise of what a class can do.
 * Any class that says "implements AiClient" must write all four methods below.
 *
 * WHY USE ONE HERE?
 * We have two different "AI engines":
 *   - GroqAiClient    : sends the handout to a real AI service on the internet (needs an API key).
 *   - OfflineAiClient : runs on your own computer with simple text rules (needs nothing).
 *
 * The rest of the app only ever talks to "an AiClient". It doesn't know or care which one
 * it got. AiConfig decides which engine to use when the app starts. This means:
 *   - the app works even with no API key or no internet,
 *   - tests don't need the internet,
 *   - switching to a different AI company later means writing just one new class.
 *
 * RULE FOR IMPLEMENTATIONS: never crash just because the AI service is down.
 * Return a fallback result instead, so a student's upload never fails because of the AI.
 */
public interface AiClient {

    /**
     * Turns handout text into a short list of key points.
     *
     * @param handoutText the text we pulled out of the PDF
     * @param maxPoints   the most key points to return
     * @return the key points, most important first (never null, but may be empty)
     */
    List<AiKeyPoint> summarize(String handoutText, int maxPoints);

    /**
     * Creates multiple-choice practice questions from the handout text.
     *
     * @param handoutText the text we pulled out of the PDF
     * @param count       how many questions we'd like
     * @return the questions (never null, but may be empty)
     */
    List<AiQuestion> generateQuestions(String handoutText, int count);

    /** True if this engine is ready to be used. */
    boolean isAvailable();

    /** A short name for this engine, shown on the dashboard (e.g. "Offline study engine"). */
    String describe();
}
