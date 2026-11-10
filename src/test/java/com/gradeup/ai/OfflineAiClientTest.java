package com.gradeup.ai;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class OfflineAiClientTest {

    private final OfflineAiClient client = new OfflineAiClient();

    private static final String HANDOUT = """
            Binary search trees are a fundamental data structure in computer science.
            A binary search tree maintains the invariant that every node in the left subtree
            holds a key smaller than the parent node, while every node in the right subtree
            holds a key larger than the parent node.

            The average time complexity of search in a balanced binary search tree is
            logarithmic, specifically O(log n), because each comparison discards roughly half
            of the remaining candidate nodes.

            However, in the worst case a binary search tree can degenerate into a linked list
            when keys are inserted in sorted order. In that degenerate case search performance
            degrades to linear time, O(n), which defeats the purpose of the structure.

            Self-balancing variants such as AVL trees and red-black trees restore logarithmic
            guarantees by performing rotations after insertion and deletion operations.
            Tree rotation is a local restructuring operation that preserves the in-order
            sequence of keys while changing the shape of the tree.

            Deletion in a binary search tree has three distinct cases depending on how many
            children the target node possesses. A leaf node can simply be removed, a node with
            one child is replaced by that child, and a node with two children requires finding
            its in-order successor to maintain the search tree invariant.
            """;

    @Test
    void summarizesIntoBoundedKeyPoints() {
        List<AiKeyPoint> points = client.summarize(HANDOUT, 5);
        assertFalse(points.isEmpty(), "should produce key points");
        assertTrue(points.size() <= 5, "must respect maxPoints");
        points.forEach(p -> {
            assertNotNull(p.text());
            assertFalse(p.text().isBlank());
            assertTrue(p.importance() >= 1 && p.importance() <= 5);
        });
    }

    @Test
    void generatesValidMultipleChoiceQuestions() {
        List<AiQuestion> questions = client.generateQuestions(HANDOUT, 4);
        assertFalse(questions.isEmpty(), "should generate questions");
        assertTrue(questions.size() <= 4);
        for (AiQuestion q : questions) {
            assertEquals(4, q.options().length, "exactly four options");
            assertTrue(q.correctOption().matches("[A-D]"), "answer must be a valid letter");
            // options must be distinct
            assertEquals(4, java.util.Arrays.stream(q.options()).distinct().count(),
                    "options must be distinct");
            // prompt must be non-trivial
            assertTrue(q.prompt().length() > 25);
        }
    }

    @Test
    void handlesEmptyAndGarbageInput() {
        assertTrue(client.summarize("", 5).isEmpty());
        assertTrue(client.summarize(null, 5).isEmpty());
        assertTrue(client.generateQuestions("", 3).isEmpty());
        assertTrue(client.generateQuestions("hi", 3).isEmpty());
        assertTrue(client.summarize("word ".repeat(5), 3).isEmpty());
    }

    @Test
    void isAlwaysAvailableAndDescribesItself() {
        assertTrue(client.isAvailable());
        assertNotNull(client.describe());
    }

    @Test
    void isDeterministic() {
        List<AiKeyPoint> a = client.summarize(HANDOUT, 4);
        List<AiKeyPoint> b = client.summarize(HANDOUT, 4);
        assertEquals(a.stream().map(AiKeyPoint::text).toList(),
                     b.stream().map(AiKeyPoint::text).toList(),
                     "same input must produce same output");
    }

    @Test
    void blanksOnlyOneWordPerClozeQuestion() {
        // Regression: a repeated focus word used to produce two blanks in one prompt,
        // e.g. "the average ______ complexity in a balanced ______ tree".
        String repeated = """
                The average search complexity in a balanced search tree is logarithmic overall.
                A balanced search tree keeps search cost low because each comparison halves the
                remaining search space, and that property is what makes search efficient here.
                Rotation keeps a search tree balanced after every single insertion operation.
                """;
        List<AiQuestion> questions = client.generateQuestions(repeated, 5);
        assertFalse(questions.isEmpty(), "should generate questions from repeated-term text");
        for (AiQuestion q : questions) {
            long blanks = q.prompt().chars().filter(c -> c == '_').count() / 6;
            assertTrue(blanks <= 1,
                    "prompt must contain at most one cloze blank but had " + blanks + ": " + q.prompt());
        }
    }
}
