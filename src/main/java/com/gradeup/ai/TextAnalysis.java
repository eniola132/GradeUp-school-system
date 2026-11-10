package com.gradeup.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simple text tools used by the offline AI engine (OfflineAiClient).
 *
 * THE BIG IDEA
 * Words that appear a lot in a handout are probably what the handout is about.
 * So a sentence full of those common words is probably an important sentence.
 *
 *   1. Split the handout into sentences.
 *   2. Count how often each meaningful word appears in the whole handout.
 *      ("Meaningful" means we skip filler words like "the", "and", "is" — called stopwords.)
 *   3. Give each sentence a score: add up the counts of its words.
 *   4. The highest-scoring sentences become the key points.
 *
 * No internet, no randomness: the same handout always gives the same result.
 *
 * "final class" + a private constructor means nobody can create a TextAnalysis object;
 * it's just a toolbox of static methods, used like TextAnalysis.sentences(text).
 */
final class TextAnalysis {

    /** Common English words that don't tell us what a text is about. We ignore these. */
    private static final Set<String> STOPWORDS = Set.of(
            "a", "about", "above", "after", "again", "against", "all", "am", "an", "and", "any",
            "are", "as", "at", "be", "because", "been", "before", "being", "below", "between",
            "both", "but", "by", "can", "cannot", "could", "did", "do", "does", "doing", "down",
            "during", "each", "few", "for", "from", "further", "had", "has", "have", "having",
            "he", "her", "here", "hers", "herself", "him", "himself", "his", "how", "i", "if",
            "in", "into", "is", "it", "its", "itself", "just", "me", "more", "most", "my",
            "myself", "no", "nor", "not", "now", "of", "off", "on", "once", "only", "or",
            "other", "our", "ours", "ourselves", "out", "over", "own", "same", "she", "should",
            "so", "some", "such", "than", "that", "the", "their", "theirs", "them", "themselves",
            "then", "there", "these", "they", "this", "those", "through", "to", "too", "under",
            "until", "up", "very", "was", "we", "were", "what", "when", "where", "which", "while",
            "who", "whom", "why", "will", "with", "would", "you", "your", "yours", "yourself",
            "yourselves", "also", "may", "many", "much", "one", "two", "use", "used", "using",
            "however", "therefore", "thus", "e.g", "i.e", "etc", "figure", "table", "chapter",
            "page", "example", "see", "given", "let", "note", "following", "within", "without",
            "upon", "among", "across", "since", "whether", "either", "neither", "must", "shall",
            "might", "make", "made", "like", "well", "even", "still", "yet", "per", "via"
    );

    // Regular expressions ("patterns") used to find things in text:

    /** Splits after ".", "!" or "?" followed by spaces — i.e. between sentences. */
    private static final Pattern SENTENCE_BREAK = Pattern.compile("(?<=[.!?])\\s+");

    /** A word: a letter followed by at least one more letter, hyphen or apostrophe. */
    private static final Pattern WORD = Pattern.compile("[A-Za-z][A-Za-z\\-']{1,}");

    /** One or more spaces, tabs or line breaks. */
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /** Private constructor: this class is only a toolbox, never an object. */
    private TextAnalysis() { }

    /** Removes invisible junk characters and extra spaces/blank lines from the text. */
    static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw;
        text = text.replace('\u0000', ' ');                          // "null" characters -> space
        text = text.replaceAll("[\\p{Cntrl}&&[^\n\t]]", " ");        // other control characters -> space
        text = text.replaceAll("[ \\t]+", " ");                      // many spaces/tabs -> one space
        text = text.replaceAll("\\n{3,}", "\n\n");                   // many blank lines -> one blank line
        return text.trim();
    }

    /** Splits the text into sentences, skipping ones that are too short or messy to be useful. */
    static List<String> sentences(String text) {
        String cleanText = normalize(text);
        if (cleanText.isBlank()) {
            return List.of();
        }

        List<String> result = new ArrayList<>();

        // First split into paragraphs (separated by a blank line), then into sentences.
        String[] paragraphs = cleanText.split("\\n{2,}");
        for (String paragraph : paragraphs) {
            String[] pieces = SENTENCE_BREAK.split(paragraph);
            for (String piece : pieces) {
                // Join lines inside the sentence and squash extra spaces.
                String sentence = WHITESPACE.matcher(piece.replace('\n', ' ')).replaceAll(" ").trim();
                if (isUsableSentence(sentence)) {
                    result.add(sentence);
                }
            }
        }
        return result;
    }

    /**
     * Decides whether a sentence is worth using. We skip:
     *   - very short or very long sentences,
     *   - "sentences" that are mostly numbers or symbols (page numbers, tables of contents),
     *   - sentences with fewer than 7 words.
     */
    private static boolean isUsableSentence(String sentence) {
        if (sentence.length() < 40 || sentence.length() > 420) {
            return false;
        }

        int letterCount = 0;
        for (int i = 0; i < sentence.length(); i++) {
            if (Character.isLetter(sentence.charAt(i))) {
                letterCount++;
            }
        }
        // At least 55% of the characters must be letters.
        if (letterCount < sentence.length() * 0.55) {
            return false;
        }

        int wordCount = sentence.split("\\s+").length;
        return wordCount >= 7;
    }

    /**
     * The meaningful words in a text: lower case, at least 3 letters, and not a stopword.
     * Example: "The stack is a LIFO structure" -> ["stack", "lifo", "structure"]
     */
    static List<String> contentWords(String text) {
        List<String> words = new ArrayList<>();

        Matcher matcher = WORD.matcher(text.toLowerCase());
        while (matcher.find()) {
            String word = matcher.group();
            if (word.length() >= 3 && !STOPWORDS.contains(word)) {
                words.add(word);
            }
        }
        return words;
    }

    /**
     * Counts how many times each word appears.
     * Example: ["stack", "queue", "stack"] -> {stack=2, queue=1}
     */
    static Map<String, Integer> termFrequencies(List<String> words) {
        Map<String, Integer> counts = new HashMap<>();
        for (String word : words) {
            int countSoFar = counts.getOrDefault(word, 0);
            counts.put(word, countSoFar + 1);
        }
        return counts;
    }

    /**
     * Gives a sentence a score: higher means "more about the main topics of the handout".
     *
     * We add up how often each of its words appears in the whole handout, then divide by
     * the square root of the number of words — otherwise long sentences would always win
     * just because they have more words.
     */
    static double scoreSentence(String sentence, Map<String, Integer> wordCounts) {
        List<String> words = contentWords(sentence);
        if (words.isEmpty()) {
            return 0;
        }

        double total = 0;
        for (String word : words) {
            total = total + wordCounts.getOrDefault(word, 0);
        }
        return total / Math.sqrt(words.size());
    }

    /**
     * Picks the best sentences, and gives them back in the order they appear in the handout
     * (so they still read naturally).
     *
     * @param limit the most sentences to return
     */
    static List<String> topSentences(String text, int limit) {
        List<String> allSentences = sentences(text);
        if (allSentences.isEmpty()) {
            return List.of();
        }
        if (allSentences.size() <= limit) {
            return allSentences;   // not more than we need, so keep them all
        }

        Map<String, Integer> wordCounts = termFrequencies(contentWords(text));

        // Score every sentence. scores[i] is the score of allSentences.get(i).
        double[] scores = new double[allSentences.size()];
        for (int i = 0; i < allSentences.size(); i++) {
            scores[i] = scoreSentence(allSentences.get(i), wordCounts);
        }

        // Make a list of positions 0, 1, 2, ... and sort it so the best-scoring come first.
        List<Integer> positions = new ArrayList<>();
        for (int i = 0; i < allSentences.size(); i++) {
            positions.add(i);
        }
        positions.sort((a, b) -> Double.compare(scores[b], scores[a]));   // highest score first

        // Keep the best ones, then sort those back into document order (0, 1, 2, ...).
        List<Integer> bestPositions = new ArrayList<>(positions.subList(0, limit));
        bestPositions.sort(null);   // null = normal smallest-to-largest order

        List<String> result = new ArrayList<>();
        for (int position : bestPositions) {
            result.add(allSentences.get(position));
        }
        return result;
    }

    /**
     * The most common meaningful words in the text — a rough list of its main topics.
     * Words that only appear once are left out.
     * If two words appear equally often, they're listed alphabetically.
     */
    static List<String> topKeywords(String text, int limit) {
        Map<String, Integer> wordCounts = termFrequencies(contentWords(text));

        // Keep only words that appear more than once.
        List<String> repeatedWords = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : wordCounts.entrySet()) {
            if (entry.getValue() > 1) {
                repeatedWords.add(entry.getKey());
            }
        }

        // Sort: most common first; ties in alphabetical order.
        repeatedWords.sort((a, b) -> {
            int byCount = Integer.compare(wordCounts.get(b), wordCounts.get(a));
            if (byCount != 0) {
                return byCount;
            }
            return a.compareTo(b);
        });

        if (repeatedWords.size() > limit) {
            return new ArrayList<>(repeatedWords.subList(0, limit));
        }
        return repeatedWords;
    }

    /**
     * Shortens the text to at most maxChars characters. If possible, it cuts at the end of
     * a sentence (if that isn't too far back) so we don't send half a sentence to the AI.
     */
    static String truncate(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxChars) {
            return text;
        }

        String cut = text.substring(0, maxChars);

        // Find the last place a sentence ended (". " or ".\n" or "! ").
        int lastSentenceEnd = Math.max(cut.lastIndexOf(". "),
                              Math.max(cut.lastIndexOf(".\n"), cut.lastIndexOf("! ")));

        // Only cut there if we'd still keep at least 60% of the allowed text.
        if (lastSentenceEnd > maxChars * 0.6) {
            return cut.substring(0, lastSentenceEnd + 1);
        }
        return cut;
    }
}
