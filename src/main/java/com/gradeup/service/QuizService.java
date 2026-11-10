package com.gradeup.service;

import com.gradeup.domain.AttemptAnswer;
import com.gradeup.domain.Course;
import com.gradeup.domain.Handout;
import com.gradeup.domain.Question;
import com.gradeup.domain.QuizAttempt;
import com.gradeup.domain.User;
import com.gradeup.repository.HandoutRepository;
import com.gradeup.repository.QuestionRepository;
import com.gradeup.repository.QuizAttemptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Quizzes: picking the questions, marking the answers, and the student's quiz history.
 *
 * (See UserService for what @Service and @Transactional mean.)
 */
@Service
public class QuizService {

    private static final Logger log = LoggerFactory.getLogger(QuizService.class);

    private final QuizAttemptRepository attemptRepository;
    private final QuestionRepository questionRepository;
    private final HandoutRepository handoutRepository;
    private final CourseService courseService;

    public QuizService(QuizAttemptRepository attemptRepository,
                       QuestionRepository questionRepository,
                       HandoutRepository handoutRepository,
                       CourseService courseService) {
        this.attemptRepository = attemptRepository;
        this.questionRepository = questionRepository;
        this.handoutRepository = handoutRepository;
        this.courseService = courseService;
    }

    /**
     * Picks random questions from a course for a new quiz.
     *
     * @param count      how many questions the student asked for
     * @param difficulty only use questions of this difficulty, or null for a mix
     * @return the questions (fewer than count if the course doesn't have enough)
     */
    @Transactional(readOnly = true)
    public List<Question> buildQuiz(User user, Long courseId, int count, Question.Difficulty difficulty) {
        Course course = courseService.getOwned(courseId, user);   // also checks the owner

        List<Handout> handouts = handoutRepository.findByCourseOrderByUploadedAtDesc(course);
        if (handouts.isEmpty()) {
            return List.of();
        }

        // Every question from every handout in the course.
        List<Question> allQuestions = questionRepository.findByHandoutIn(handouts);

        // If a difficulty was chosen, keep only those questions...
        List<Question> pool = allQuestions;
        if (difficulty != null) {
            List<Question> matching = new ArrayList<>();
            for (Question question : allQuestions) {
                if (question.getDifficulty() == difficulty) {
                    matching.add(question);
                }
            }
            // ...but if there are none, use all of them rather than giving an empty quiz.
            if (!matching.isEmpty()) {
                pool = matching;
            }
        }

        if (pool.isEmpty()) {
            return List.of();
        }

        // Shuffle a copy of the list and take the first "count" questions.
        List<Question> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled);
        int howMany = Math.min(count, shuffled.size());
        return shuffled.subList(0, howMany);
    }

    /**
     * Loads specific questions by id, in the same order as the ids.
     * Only returns questions that belong to this user.
     *
     * Used while a quiz is in progress: we only remember the question ids between pages,
     * and reload the questions each time. (We must NOT call buildQuiz() for this, because
     * it shuffles and picks new questions.)
     */
    @Transactional(readOnly = true)
    public List<Question> loadByIds(List<Long> ids, User owner) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        // The database gives the questions back in any order, so we put them in a map
        // (id -> question) and then rebuild the list in the order of the ids.
        List<Question> found = questionRepository.findOwnedByIds(ids, owner.getId());
        Map<Long, Question> questionsById = new HashMap<>();
        for (Question question : found) {
            questionsById.put(question.getId(), question);
        }

        List<Question> inOrder = new ArrayList<>();
        for (Long id : ids) {
            Question question = questionsById.get(id);
            if (question != null) {   // null if the question was deleted during the quiz
                inOrder.add(question);
            }
        }
        return inOrder;
    }

    /** How many questions a course has in total (shown on the quiz setup page). */
    @Transactional(readOnly = true)
    public int availableQuestionCount(User user, Long courseId) {
        Course course = courseService.getOwned(courseId, user);
        List<Handout> handouts = handoutRepository.findByCourseOrderByUploadedAtDesc(course);
        if (handouts.isEmpty()) {
            return 0;
        }
        return questionRepository.findByHandoutIn(handouts).size();
    }

    /**
     * Marks a finished quiz and saves the result.
     *
     * @param questions the questions that were in the quiz
     * @param answers   question id -> the letter the student picked ("A" to "D").
     *                  A question with no entry counts as skipped (and wrong).
     * @return the saved result
     */
    @Transactional
    public QuizAttempt submit(User user, Long courseId, List<Question> questions,
                              Map<Long, String> answers, boolean instantFeedback) {
        Course course = courseService.getOwned(courseId, user);

        QuizAttempt attempt = new QuizAttempt(user, course);
        attempt.setInstantFeedback(instantFeedback);
        attempt.setTotalQuestions(questions.size());

        int score = 0;
        for (Question question : questions) {
            String picked = cleanAnswer(answers.get(question.getId()));

            // AttemptAnswer works out by itself whether the answer is right.
            AttemptAnswer answer = new AttemptAnswer(attempt, question, picked);
            if (answer.isCorrect()) {
                score++;
            }
            attempt.getAnswers().add(answer);
        }

        attempt.setScore(score);
        attempt.setCompletedAt(Instant.now());

        QuizAttempt saved = attemptRepository.save(attempt);
        log.info("Quiz attempt {} scored {}/{} for user {}", saved.getId(), score, questions.size(), user.getId());
        return saved;
    }

    /**
     * Makes an answer safe to save: "b " becomes "B".
     * Anything that isn't A, B, C or D becomes null, which means "skipped".
     */
    private String cleanAnswer(String raw) {
        if (raw == null) {
            return null;
        }
        String letter = raw.trim().toUpperCase(Locale.ROOT);
        if (letter.equals("A") || letter.equals("B") || letter.equals("C") || letter.equals("D")) {
            return letter;
        }
        return null;
    }

    /** Loads one quiz result, but only if this user took it. */
    @Transactional(readOnly = true)
    public QuizAttempt getOwned(Long attemptId, User user) {
        Optional<QuizAttempt> found = attemptRepository.findByIdAndUserWithDetails(attemptId, user);
        if (found.isEmpty()) {
            throw new NotFoundException("That quiz result doesn't exist.");
        }
        return found.get();
    }

    /** All of a user's quiz results, newest first. */
    @Transactional(readOnly = true)
    public List<QuizAttempt> historyFor(User user) {
        return attemptRepository.findByUserWithCourse(user);
    }

    /** Recent quiz results, for the dashboard. */
    @Transactional(readOnly = true)
    public List<QuizAttempt> recentFor(User user) {
        return attemptRepository.findRecentByUserWithCourse(user);
    }

    /** How many quizzes this user has taken. */
    @Transactional(readOnly = true)
    public long countFor(User user) {
        return attemptRepository.countByUser(user);
    }

    /** The user's average quiz score, as a whole-number percentage (0 if no quizzes yet). */
    @Transactional(readOnly = true)
    public int averageScore(User user) {
        List<QuizAttempt> attempts = attemptRepository.findByUserOrderByStartedAtDesc(user);
        if (attempts.isEmpty()) {
            return 0;
        }

        int total = 0;
        for (QuizAttempt attempt : attempts) {
            total = total + attempt.getPercentage();
        }
        double average = (double) total / attempts.size();
        return Math.round((float) average);
    }
}
