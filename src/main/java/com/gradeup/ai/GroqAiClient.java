package com.gradeup.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gradeup.config.GradeUpProperties;
import com.gradeup.domain.Question;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The real AI engine: sends the handout to Groq (an online AI service) and reads its answer.
 *
 * HOW IT WORKS
 *   1. We write instructions (a "prompt") plus the handout text.
 *   2. We send them to Groq over the internet (an HTTP POST request).
 *   3. Groq replies with JSON, a text format for data that looks like:
 *        {"keyPoints":[{"text":"...","importance":5}]}
 *   4. We read the JSON and check every part of it carefully.
 *
 * SAFETY NET
 * If ANYTHING goes wrong — no internet, Groq is down, or the AI replies with something
 * that isn't valid JSON — we quietly use the OfflineAiClient instead.
 * A student's upload should never fail just because the AI had a bad day.
 */
public class GroqAiClient implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(GroqAiClient.class);

    private final GradeUpProperties.Ai settings;

    /** Spring's tool for sending HTTP requests. */
    private final RestClient restClient;

    /** Jackson's tool for reading JSON text. */
    private final ObjectMapper jsonReader = new ObjectMapper();

    /** The backup engine, used whenever Groq fails. */
    private final OfflineAiClient fallback = new OfflineAiClient();

    public GroqAiClient(GradeUpProperties.Ai settings) {
        this.settings = settings;

        // Every request we send goes to Groq's address and includes our API key.
        this.restClient = RestClient.builder()
                .baseUrl(settings.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + settings.getApiKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    // =====================================================================
    // KEY POINTS
    // =====================================================================

    @Override
    public List<AiKeyPoint> summarize(String handoutText, int maxPoints) {
        if (!isAvailable()) {
            return fallback.summarize(handoutText, maxPoints);
        }

        // Clean up the text and cut very long handouts down to size.
        String input = TextAnalysis.truncate(TextAnalysis.normalize(handoutText), settings.getMaxInputChars());
        if (input.isBlank()) {
            return List.of();
        }

        // The "system" message tells the AI what role to play.
        String systemMessage = """
                You are a study assistant for university students. You distil dense course
                handouts into short, plain-language key points that help a student pass exams.
                Reply with ONLY valid JSON, no markdown fences, no commentary.
                """;

        // The "user" message is the actual request. %d and %s are filled in by formatted().
        String userMessage = """
                Summarise the handout below into at most %d key points.

                Rules:
                - Each point must be a single, self-contained sentence a student can revise from.
                - Use plain language; avoid copying long verbatim passages.
                - importance is an integer 1-5, where 5 = absolutely must know.
                - Order the points from most to least important.

                Return JSON exactly in this shape:
                {"keyPoints":[{"text":"...","importance":5}]}

                HANDOUT:
                ---
                %s
                ---
                """.formatted(maxPoints, input);

        try {
            String reply = askGroq(systemMessage, userMessage);
            List<AiKeyPoint> points = readKeyPoints(reply, maxPoints);
            if (points.isEmpty()) {
                log.warn("Groq returned no usable key points; using offline summarizer");
                return fallback.summarize(handoutText, maxPoints);
            }
            return points;
        } catch (Exception ex) {
            log.warn("Groq summarization failed ({}); falling back to offline engine", ex.getMessage());
            return fallback.summarize(handoutText, maxPoints);
        }
    }

    // =====================================================================
    // QUESTIONS
    // =====================================================================

    @Override
    public List<AiQuestion> generateQuestions(String handoutText, int count) {
        if (!isAvailable()) {
            return fallback.generateQuestions(handoutText, count);
        }

        String input = TextAnalysis.truncate(TextAnalysis.normalize(handoutText), settings.getMaxInputChars());
        if (input.isBlank()) {
            return List.of();
        }

        String systemMessage = """
                You are an exam writer for university courses. You create fair multiple-choice
                questions that test real understanding, not trivia.
                Reply with ONLY valid JSON, no markdown fences, no commentary.
                """;

        String userMessage = """
                Create exactly %d multiple-choice questions from the handout below.

                Rules:
                - Exactly four options per question, each a plausible, distinct answer.
                - Vary the angle across questions: definitions, application, comparison, edge cases.
                - "answer" must be exactly one of: A, B, C, D.
                - Provide a one-sentence explanation of why the answer is correct.
                - difficulty must be one of EASY, MEDIUM, HARD.
                - Test understanding of the material, never the wording of a sentence.

                Return JSON exactly in this shape:
                {"questions":[{"prompt":"...","options":["...","...","...","..."],
                "answer":"A","explanation":"...","difficulty":"MEDIUM","angle":"Application"}]}

                HANDOUT:
                ---
                %s
                ---
                """.formatted(count, input);

        try {
            String reply = askGroq(systemMessage, userMessage);
            List<AiQuestion> questions = readQuestions(reply, count);
            if (questions.isEmpty()) {
                log.warn("Groq returned no usable questions; using offline generator");
                return fallback.generateQuestions(handoutText, count);
            }
            return questions;
        } catch (Exception ex) {
            log.warn("Groq question generation failed ({}); falling back to offline engine", ex.getMessage());
            return fallback.generateQuestions(handoutText, count);
        }
    }

    // =====================================================================
    // TALKING TO GROQ
    // =====================================================================

    /**
     * Sends the two messages to Groq and returns the text of its reply.
     * Throws an exception if the request fails or the reply is empty.
     */
    private String askGroq(String systemMessage, String userMessage) {
        // The request body. Map.of(...) builds a fixed key -> value map, which is turned into JSON.
        Map<String, Object> systemPart = Map.of("role", "system", "content", systemMessage);
        Map<String, Object> userPart = Map.of("role", "user", "content", userMessage);

        Map<String, Object> requestBody = Map.of(
                "model", settings.getModel(),
                "temperature", 0.4,                                   // lower = less random answers
                "response_format", Map.of("type", "json_object"),     // ask for JSON back
                "messages", List.of(systemPart, userPart)
        );

        String response = restClient.post()
                .uri("/chat/completions")
                .body(requestBody)
                .retrieve()
                .body(String.class);

        if (response == null || response.isBlank()) {
            throw new IllegalStateException("Empty response from Groq");
        }

        // Groq's reply looks like: {"choices":[{"message":{"content":"...the AI's answer..."}}]}
        // .path(...) walks into the JSON one step at a time.
        JsonNode root = parseJson(response);
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.asText().isBlank()) {
            throw new IllegalStateException("Groq response contained no message content");
        }
        return content.asText();
    }

    /**
     * Reads key points out of the AI's reply. Bad entries are skipped.
     * Expected shape: {"keyPoints":[{"text":"...","importance":5}]}
     */
    private List<AiKeyPoint> readKeyPoints(String reply, int maxPoints) {
        List<AiKeyPoint> points = new ArrayList<>();
        try {
            JsonNode list = parseJson(removeCodeFences(reply)).path("keyPoints");
            if (!list.isArray()) {
                return points;
            }

            for (JsonNode item : list) {
                String text = item.path("text").asText("").trim();
                if (text.isBlank()) {
                    continue;
                }
                int importance = item.path("importance").asInt(3);   // 3 if missing

                try {
                    points.add(new AiKeyPoint(text, importance));
                } catch (IllegalArgumentException ex) {
                    log.debug("Discarding invalid key point: {}", ex.getMessage());
                }

                if (points.size() >= maxPoints) {
                    break;
                }
            }
        } catch (Exception ex) {
            log.warn("Could not parse key points JSON: {}", ex.getMessage());
        }
        return points;
    }

    /**
     * Reads questions out of the AI's reply. Bad questions are skipped.
     * Expected shape: {"questions":[{"prompt":..., "options":[4 items], "answer":"A", ...}]}
     */
    private List<AiQuestion> readQuestions(String reply, int count) {
        List<AiQuestion> questions = new ArrayList<>();
        try {
            JsonNode list = parseJson(removeCodeFences(reply)).path("questions");
            if (!list.isArray()) {
                return questions;
            }

            for (JsonNode item : list) {
                String prompt = item.path("prompt").asText("").trim();
                if (prompt.isBlank()) {
                    continue;
                }

                JsonNode optionsJson = item.path("options");
                if (!optionsJson.isArray() || optionsJson.size() != 4) {
                    log.debug("Skipping question with {} options", optionsJson.size());
                    continue;
                }
                String[] options = new String[4];
                for (int i = 0; i < 4; i++) {
                    options[i] = optionsJson.get(i).asText("").trim();
                }

                String answer = item.path("answer").asText("").trim().toUpperCase();
                String explanation = item.path("explanation").asText("").trim();
                String angle = item.path("angle").asText("Recall").trim();
                Question.Difficulty difficulty = readDifficulty(item.path("difficulty").asText(""));

                try {
                    // AiQuestion checks the values itself and throws if something's wrong.
                    questions.add(new AiQuestion(prompt, options, answer, explanation, difficulty, angle));
                } catch (IllegalArgumentException ex) {
                    log.debug("Discarding invalid generated question: {}", ex.getMessage());
                }

                if (questions.size() >= count) {
                    break;
                }
            }
        } catch (Exception ex) {
            log.warn("Could not parse questions JSON: {}", ex.getMessage());
        }
        return questions;
    }

    /** "hard" -> HARD. Anything we don't recognise becomes MEDIUM. */
    private Question.Difficulty readDifficulty(String text) {
        try {
            return Question.Difficulty.valueOf(text.trim().toUpperCase());
        } catch (Exception ex) {
            return Question.Difficulty.MEDIUM;
        }
    }

    /**
     * Some AI models wrap their JSON in markdown code fences (```json ... ```) even when
     * told not to. This removes them so we can read the JSON inside.
     */
    private String removeCodeFences(String text) {
        String result = text.trim();
        if (result.startsWith("```")) {
            int endOfFirstLine = result.indexOf('\n');
            if (endOfFirstLine > 0) {
                result = result.substring(endOfFirstLine + 1);   // drop the "```json" line
            }
            int lastFence = result.lastIndexOf("```");
            if (lastFence >= 0) {
                result = result.substring(0, lastFence);         // drop the closing "```"
            }
        }
        return result.trim();
    }

    /** Turns JSON text into a JsonNode we can walk through. Throws if it isn't valid JSON. */
    private JsonNode parseJson(String json) {
        try {
            return jsonReader.readTree(json);
        } catch (Exception ex) {
            throw new IllegalStateException("Response was not valid JSON", ex);
        }
    }

    @Override
    public boolean isAvailable() {
        return settings.isConfigured();
    }

    @Override
    public String describe() {
        return "Groq · " + settings.getModel();
    }
}
