package com.gradeup.service;

import com.gradeup.IntegrationTest;
import com.gradeup.domain.*;
import com.gradeup.repository.QuestionRepository;
import com.gradeup.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@IntegrationTest
class QuizServiceTest {

    @Autowired QuizService quizService;
    @Autowired CourseService courseService;
    @Autowired HandoutService handoutService;
    @Autowired UserService userService;
    @Autowired QuestionRepository questionRepository;
    @Autowired UserRepository userRepository;

    private User owner;
    private Course course;
    private List<Question> questions;

    @BeforeEach
    void setUp() {
        owner = userRepository.findByEmailIgnoreCase("quiz@test.com")
                .orElseGet(() -> userService.register("quiz@test.com", "password123", "password123", "Quizzer"));
        course = courseService.create(owner, "QZ" + System.nanoTime() % 10000, "Quiz Course", null, "violet");

        // Attach a handout through the service so the whole graph is persisted properly,
        // then build a controlled question bank directly on it. This keeps scoring tests
        // independent of whatever the generator happens to produce.
        Handout handout = handoutService.attachForTesting(course, "q.pdf");

        List<Question> built = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Question q = new Question(handout, "Prompt number " + i,
                    "A" + i, "B" + i, "C" + i, "D" + i, "A");
            q.setDifficulty(i % 2 == 0 ? Question.Difficulty.EASY : Question.Difficulty.HARD);
            q.setExplanation("Because " + i);
            q.setSource(Question.Source.OFFLINE);
            built.add(q);
        }
        questions = questionRepository.saveAll(built);
    }

    @Test
    void scoresAllCorrectAnswers() {
        Map<Long, String> answers = new HashMap<>();
        questions.forEach(q -> answers.put(q.getId(), "A"));

        QuizAttempt attempt = quizService.submit(owner, course.getId(), questions, answers, false);

        assertEquals(questions.size(), attempt.getTotalQuestions());
        assertEquals(questions.size(), attempt.getScore(), "every answer was correct");
        assertEquals(100, attempt.getPercentage());
        assertTrue(attempt.isPassed());
        assertEquals("A", attempt.getGradeLetter());
    }

    @Test
    void scoresAllWrongAnswers() {
        Map<Long, String> answers = new HashMap<>();
        questions.forEach(q -> answers.put(q.getId(), "B"));

        QuizAttempt attempt = quizService.submit(owner, course.getId(), questions, answers, false);

        assertEquals(0, attempt.getScore());
        assertEquals(0, attempt.getPercentage());
        assertFalse(attempt.isPassed());
        assertEquals("F", attempt.getGradeLetter());
    }

    @Test
    void countsMissingAnswersAsIncorrectRatherThanDroppingThem() {
        // Regression guard: an unanswered question must still appear in the attempt and
        // count against the total, never be silently discarded.
        Map<Long, String> answers = new HashMap<>();
        answers.put(questions.get(0).getId(), "A"); // only one answered

        QuizAttempt attempt = quizService.submit(owner, course.getId(), questions, answers, false);

        assertEquals(questions.size(), attempt.getTotalQuestions(),
                "total must include every question, answered or not");
        assertEquals(questions.size(), attempt.getAnswers().size(),
                "every question must produce an answer record");
        assertEquals(1, attempt.getScore());
        long skipped = attempt.getAnswers().stream().filter(AttemptAnswer::isSkipped).count();
        assertEquals(questions.size() - 1, skipped, "unanswered questions are recorded as skipped");
    }

    @Test
    void treatsInvalidOptionValuesAsSkipped() {
        Map<Long, String> answers = new HashMap<>();
        answers.put(questions.get(0).getId(), "Z");   // not a valid option
        answers.put(questions.get(1).getId(), "  ");  // blank

        QuizAttempt attempt = quizService.submit(owner, course.getId(), questions, answers, false);

        assertEquals(0, attempt.getScore());
        assertTrue(attempt.getAnswers().stream().allMatch(AttemptAnswer::isSkipped));
    }

    @Test
    void acceptLowerCaseAnswers() {
        Map<Long, String> answers = new HashMap<>();
        questions.forEach(q -> answers.put(q.getId(), "a"));

        QuizAttempt attempt = quizService.submit(owner, course.getId(), questions, answers, false);
        assertEquals(questions.size(), attempt.getScore());
    }

    @Test
    void loadByIdsReturnsExactlyTheRequestedQuestionsInOrder() {
        // Regression guard for the bug where rebuilding via buildQuiz() (which shuffles and
        // truncates) silently dropped most in-progress quiz questions.
        List<Long> ids = questions.stream().map(Question::getId).toList();

        List<Question> loaded = quizService.loadByIds(ids, owner);

        assertEquals(ids.size(), loaded.size(), "all requested questions must be returned");
        assertEquals(ids, loaded.stream().map(Question::getId).toList(), "order must be preserved");
    }

    @Test
    void loadByIdsRefusesAnotherUsersQuestions() {
        User intruder = userService.register(
                "intruder" + System.nanoTime() + "@test.com", "password123", "password123", "Intruder");

        List<Question> loaded = quizService.loadByIds(
                questions.stream().map(Question::getId).toList(), intruder);

        assertTrue(loaded.isEmpty(), "must not leak another user's questions");
    }

    @Test
    void refusesToBuildQuizForAnotherUsersCourse() {
        User intruder = userService.register(
                "quizintruder" + System.nanoTime() + "@test.com", "password123", "password123", "Intruder");
        assertThrows(NotFoundException.class,
                () -> quizService.buildQuiz(intruder, course.getId(), 5, null));
    }

    @Test
    void refusesToReadAnotherUsersAttempt() {
        Map<Long, String> answers = new HashMap<>();
        answers.put(questions.get(0).getId(), "A");
        QuizAttempt attempt = quizService.submit(owner, course.getId(), questions, answers, false);

        User intruder = userService.register(
                "attemptintruder" + System.nanoTime() + "@test.com", "password123", "password123", "Intruder");

        assertThrows(NotFoundException.class, () -> quizService.getOwned(attempt.getId(), intruder));
    }

    @Test
    void difficultyFilterFallsBackToFullPoolWhenTooNarrow() {
        // All questions are EASY or HARD; asking for MEDIUM would otherwise yield nothing.
        List<Question> quiz = quizService.buildQuiz(owner, course.getId(), 5, Question.Difficulty.MEDIUM);
        assertFalse(quiz.isEmpty(), "should fall back to the full pool rather than return an empty quiz");
    }
}
