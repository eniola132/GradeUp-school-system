package com.gradeup.ai;

import com.gradeup.domain.Question;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The offline "AI" engine. It runs on this computer — no internet, no API key needed.
 *
 * It isn't a real AI. It uses simple rules (see TextAnalysis):
 *   - KEY POINTS: pick the sentences that use the handout's most common topic words.
 *   - QUESTIONS:  take an important sentence, blank out a key word, and ask which word
 *                 fills the gap. The wrong answers are other key words from the handout.
 *
 * It's used in two situations:
 *   1. No GROQ_API_KEY is set, so it's the only engine (see AiConfig).
 *   2. The real AI (GroqAiClient) fails, and uses this as a backup.
 *
 * It gives the same result every time for the same handout (nothing is random).
 */
public class OfflineAiClient implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(OfflineAiClient.class);

    /** The different kinds of question we take turns making, so practice isn't repetitive. */
    private enum Angle { DEFINITION, APPLICATION, COMPARISON, RECALL }

    // =====================================================================
    // KEY POINTS
    // =====================================================================

    @Override
    public List<AiKeyPoint> summarize(String handoutText, int maxPoints) {
        String text = TextAnalysis.normalize(handoutText);
        if (text.isBlank() || maxPoints <= 0) {
            return List.of();
        }

        // Get twice as many good sentences as we need, then keep the very best.
        List<String> candidates = TextAnalysis.topSentences(text, maxPoints * 2);

        if (candidates.isEmpty()) {
            return keyPointsFromKeywords(text, maxPoints);
        }

        Map<String, Integer> wordCounts = TextAnalysis.termFrequencies(TextAnalysis.contentWords(text));

        // Best sentences first, and only as many as we need.
        List<String> best = sortByScoreHighestFirst(candidates, wordCounts);
        if (best.size() > maxPoints) {
            best = best.subList(0, maxPoints);
        }

        List<AiKeyPoint> points = new ArrayList<>();
        for (int rank = 0; rank < best.size(); rank++) {
            String sentence = startWithCapital(best.get(rank));
            if (sentence.isBlank()) {
                continue;
            }
            int importance = importanceForRank(rank, best.size());
            points.add(new AiKeyPoint(sentence, importance));
        }

        log.debug("Offline summarizer produced {} key points", points.size());
        return points;
    }

    /**
     * Backup plan when the handout has no usable full sentences: make simple key points
     * from its most repeated words. If there's too little text even for that, return
     * nothing — an empty list is better than meaningless filler.
     */
    private List<AiKeyPoint> keyPointsFromKeywords(String text, int maxPoints) {
        List<String> keywords = TextAnalysis.topKeywords(text, maxPoints);

        // How many DIFFERENT meaningful words does the text have? (A Set ignores duplicates.)
        Set<String> differentWords = new HashSet<>(TextAnalysis.contentWords(text));

        if (keywords.isEmpty() || differentWords.size() < 8) {
            log.debug("Not enough usable content to summarize ({} distinct words)", differentWords.size());
            return List.of();
        }

        List<AiKeyPoint> points = new ArrayList<>();
        for (String keyword : keywords) {
            points.add(new AiKeyPoint(capitalize(keyword) + " is a recurring concept in this handout.", 3));
        }
        return points;
    }

    /**
     * How important a key point is, based on its rank (0 = best):
     *   top 20%   -> 5 "Must know"
     *   next 25%  -> 4 "Important"
     *   next 30%  -> 3 "Good to know"
     *   the rest  -> 2 "Background"
     * If there are only 1 or 2 points, they're all "Must know".
     */
    private int importanceForRank(int rank, int total) {
        if (total <= 2) {
            return 5;
        }
        double position = (double) rank / total;   // 0.0 = top of the list, close to 1.0 = bottom
        if (position < 0.2) {
            return 5;
        }
        if (position < 0.45) {
            return 4;
        }
        if (position < 0.75) {
            return 3;
        }
        return 2;
    }

    // =====================================================================
    // QUESTIONS
    // =====================================================================

    @Override
    public List<AiQuestion> generateQuestions(String handoutText, int count) {
        String text = TextAnalysis.normalize(handoutText);
        if (text.isBlank() || count <= 0) {
            return List.of();
        }

        List<String> sentences = TextAnalysis.sentences(text);
        if (sentences.size() < 2) {
            return List.of();
        }

        Map<String, Integer> wordCounts = TextAnalysis.termFrequencies(TextAnalysis.contentWords(text));
        List<String> keywords = TextAnalysis.topKeywords(text, Math.max(12, count * 2));

        // Good questions come from sentences full of important words, so use the best first.
        List<String> bestSentences = sortByScoreHighestFirst(sentences, wordCounts);
        int howManyToTry = Math.min(bestSentences.size(), count * 2);
        bestSentences = bestSentences.subList(0, howManyToTry);

        List<AiQuestion> questions = new ArrayList<>();
        Set<String> promptsUsed = new HashSet<>();
        Angle[] angles = Angle.values();

        int questionNumber = 0;
        for (String sentence : bestSentences) {
            if (questions.size() >= count) {
                break;   // we have enough
            }

            // Take turns: DEFINITION, APPLICATION, COMPARISON, RECALL, DEFINITION, ...
            Angle angle = angles[questionNumber % angles.length];
            questionNumber++;

            AiQuestion question = buildQuestion(sentence, angle, keywords, wordCounts, questionNumber);
            if (question == null) {
                continue;   // couldn't make a good question from this sentence
            }
            if (promptsUsed.contains(question.prompt())) {
                continue;   // we already have this exact question
            }
            promptsUsed.add(question.prompt());
            questions.add(question);
        }

        log.debug("Offline generator produced {} questions", questions.size());
        return questions;
    }

    /**
     * Makes one "fill in the blank" question from a sentence. Returns null if it can't.
     *
     * @param seed a number that changes for each question, used to vary which wrong
     *             answers are picked and where the right answer goes (A, B, C or D)
     */
    private AiQuestion buildQuestion(String sentence, Angle angle, List<String> keywords,
                                     Map<String, Integer> wordCounts, int seed) {
        List<String> wordsInSentence = TextAnalysis.contentWords(sentence);
        if (wordsInSentence.isEmpty()) {
            return null;
        }

        // 1. Choose the word to blank out (the right answer).
        String answerWord = chooseAnswerWord(wordsInSentence, keywords, wordCounts, seed);
        String answerDisplay = capitalize(answerWord);

        // 2. Choose three wrong answers from the other keywords.
        List<String> otherKeywords = new ArrayList<>();
        for (String keyword : keywords) {
            if (!keyword.equalsIgnoreCase(answerWord)) {
                otherKeywords.add(keyword);
            }
        }
        if (otherKeywords.size() < 3) {
            return null;   // not enough different words to make wrong answers
        }

        List<String> wrongAnswers = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            String wrong = otherKeywords.get((seed * 3 + i) % otherKeywords.size());
            // If we already picked this one, move along the list until we find a new one.
            while (wrongAnswers.contains(wrong)) {
                int nextPosition = (otherKeywords.indexOf(wrong) + 1) % otherKeywords.size();
                wrong = otherKeywords.get(nextPosition);
            }
            wrongAnswers.add(capitalize(wrong));
        }

        // 3. Put the four answers together and move them around, so the right answer
        //    isn't always "A". Collections.rotate shifts every item along by "seed % 4" places.
        List<String> options = new ArrayList<>();
        options.add(answerDisplay);
        options.addAll(wrongAnswers);
        Collections.rotate(options, seed % 4);

        int correctPosition = options.indexOf(answerDisplay);           // 0, 1, 2 or 3
        String correctLetter = String.valueOf("ABCD".charAt(correctPosition));

        // 4. Write the question text and explanation.
        String sentenceWithBlank = blankOutFirst(sentence, answerWord);
        String prompt = writePrompt(angle, sentence, sentenceWithBlank);
        String explanation = "The handout states: \"" + shorten(sentence) + "\" "
                + answerDisplay + " is the term this passage centres on.";

        String angleName = capitalize(angle.name().toLowerCase());     // e.g. "Definition"

        try {
            String[] optionArray = {options.get(0), options.get(1), options.get(2), options.get(3)};
            return new AiQuestion(prompt, optionArray, correctLetter, explanation, difficultyFor(angle), angleName);
        } catch (IllegalArgumentException ex) {
            log.debug("Skipping malformed generated question: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * Picks which word of the sentence to blank out.
     * We prefer one of the handout's keywords (the most common one in the sentence),
     * because a question about a main topic is more useful. If the sentence has no
     * keywords, we just pick one of its words using the seed.
     */
    private String chooseAnswerWord(List<String> wordsInSentence, List<String> keywords,
                                    Map<String, Integer> wordCounts, int seed) {
        String bestWord = null;
        int bestCount = -1;
        for (String word : wordsInSentence) {
            if (!keywords.contains(word)) {
                continue;
            }
            int count = wordCounts.getOrDefault(word, 0);
            if (count > bestCount) {   // ">" so that on a tie, the earlier word wins
                bestWord = word;
                bestCount = count;
            }
        }

        if (bestWord != null) {
            return bestWord;
        }
        return wordsInSentence.get(seed % wordsInSentence.size());
    }

    /** The question text, worded differently for each kind of question. */
    private String writePrompt(Angle angle, String sentence, String sentenceWithBlank) {
        if (angle == Angle.DEFINITION) {
            return "Which term correctly completes this statement?\n\n" + sentenceWithBlank;
        } else if (angle == Angle.RECALL) {
            return "According to the handout: " + shorten(sentence)
                    + "\n\nWhich concept from the list is this describing?";
        } else if (angle == Angle.APPLICATION) {
            return "A question applies this idea: which concept is central to the following?\n\n" + sentenceWithBlank;
        } else {   // COMPARISON
            return "Which concept is being contrasted or related in this statement?\n\n" + sentenceWithBlank;
        }
    }

    /** Definition and recall questions are easy, application is medium, comparison is hard. */
    private Question.Difficulty difficultyFor(Angle angle) {
        if (angle == Angle.DEFINITION || angle == Angle.RECALL) {
            return Question.Difficulty.EASY;
        } else if (angle == Angle.APPLICATION) {
            return Question.Difficulty.MEDIUM;
        } else {
            return Question.Difficulty.HARD;
        }
    }

    /**
     * Replaces only the FIRST time the word appears with "______".
     *
     * (If we blanked every time it appears, we'd get confusing questions like
     * "the average ______ complexity in a balanced ______ tree".)
     */
    private String blankOutFirst(String sentence, String word) {
        // (?i) = ignore case, \b = word boundary, so "tree" doesn't match inside "street".
        Pattern wholeWord = Pattern.compile("(?i)\\b" + Pattern.quote(word) + "\\b");
        Matcher matcher = wholeWord.matcher(sentence);
        if (!matcher.find()) {
            return sentence;
        }
        String before = sentence.substring(0, matcher.start());
        String after = sentence.substring(matcher.end());
        return before + "______" + after;
    }

    // =====================================================================
    // SMALL HELPERS
    // =====================================================================

    /** Returns a copy of the sentences, sorted so the highest-scoring sentence comes first. */
    private List<String> sortByScoreHighestFirst(List<String> sentences, Map<String, Integer> wordCounts) {
        List<String> sorted = new ArrayList<>(sentences);
        sorted.sort((a, b) -> Double.compare(
                TextAnalysis.scoreSentence(b, wordCounts),
                TextAnalysis.scoreSentence(a, wordCounts)));
        return sorted;
    }

    /** Cuts long text down to 240 characters, ending with "...". */
    private String shorten(String text) {
        if (text.length() <= 240) {
            return text;
        }
        return text.substring(0, 237).trim() + "...";
    }

    /** Trims the sentence and makes sure it starts with a capital letter. */
    private String startWithCapital(String sentence) {
        String trimmed = sentence.trim();
        if (!trimmed.isEmpty() && Character.isLowerCase(trimmed.charAt(0))) {
            return capitalize(trimmed);
        }
        return trimmed;
    }

    /** "stack" -> "Stack" */
    private static String capitalize(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    @Override
    public boolean isAvailable() {
        return true;   // always ready: it runs on this computer
    }

    @Override
    public String describe() {
        return "Offline study engine";
    }
}
