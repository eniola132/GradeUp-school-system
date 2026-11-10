package com.gradeup.web;

import com.gradeup.domain.Course;
import com.gradeup.domain.Question;
import com.gradeup.domain.QuizAttempt;
import com.gradeup.domain.User;
import com.gradeup.service.CourseService;
import com.gradeup.service.CurrentUser;
import com.gradeup.service.QuizService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Practice quizzes: the setup page, taking the quiz, and the results.
 *
 * HOW A QUIZ FLOWS
 *   1. GET  /quiz/start        -> setup page (pick a course, how many questions, difficulty)
 *   2. POST /quiz/start        -> pick the questions, remember them, go to step 3
 *   3. GET  /quiz/take?index=0 -> show one question
 *   4. POST /quiz/submit       -> save the answer, then either show another question (back
 *                                 to step 3) or, if they pressed Finish, mark the quiz
 *   5. GET  /quiz/results/{id} -> show the score and every answer
 *
 * WHAT IS THE "SESSION"?
 * The web is forgetful: each page load is separate. The HttpSession is a small storage
 * box that Spring keeps for each visitor between page loads. We use it to remember the
 * quiz in progress: which questions, which course, and the answers so far.
 * We only store question IDs, and reload the questions from the database each time.
 *
 * (See AuthController for how controllers work.)
 */
@Controller
@RequestMapping("/quiz")
public class QuizController {

    // The names we store things under in the session.
    private static final String SESSION_QUESTIONS = "quiz.questions";        // List<Long> of question ids
    private static final String SESSION_COURSE = "quiz.courseId";            // Long
    private static final String SESSION_INSTANT = "quiz.instantFeedback";    // Boolean
    private static final String SESSION_ANSWERS = "quiz.answers";            // Map: question id -> letter

    /** The fewest and most questions a quiz can have. */
    private static final int MIN_QUESTIONS = 1;
    private static final int MAX_QUESTIONS = 30;

    private final CurrentUser currentUser;
    private final QuizService quizService;
    private final CourseService courseService;

    public QuizController(CurrentUser currentUser, QuizService quizService, CourseService courseService) {
        this.currentUser = currentUser;
        this.quizService = quizService;
        this.courseService = courseService;
    }

    /** Step 1: the quiz setup page. ?courseId=... picks a course in advance. */
    @GetMapping("/start")
    public String setup(@RequestParam(required = false) Long courseId, Model model) {
        User user = currentUser.get();
        List<Course> courses = courseService.listFor(user);

        model.addAttribute("user", user);
        model.addAttribute("courses", courses);
        model.addAttribute("selectedCourseId", courseId);
        model.addAttribute("difficulties", Question.Difficulty.values());

        if (courseId != null) {
            model.addAttribute("available", quizService.availableQuestionCount(user, courseId));
        }
        return "quiz/setup";
    }

    /** Step 2: the setup form was sent. Pick the questions and start the quiz. */
    @PostMapping("/start")
    public String start(@RequestParam Long courseId,
                        @RequestParam(defaultValue = "5") int count,
                        @RequestParam(required = false) String difficulty,
                        @RequestParam(defaultValue = "false") boolean instantFeedback,
                        HttpSession session,
                        RedirectAttributes redirect) {
        User user = currentUser.get();

        Question.Difficulty chosenDifficulty = readDifficulty(difficulty);
        int questionCount = Math.max(MIN_QUESTIONS, Math.min(count, MAX_QUESTIONS));
        List<Question> questions = quizService.buildQuiz(user, courseId, questionCount, chosenDifficulty);

        if (questions.isEmpty()) {
            redirect.addFlashAttribute("error", "This course has no practice questions yet. Upload a handout first.");
            return "redirect:/quiz/start?courseId=" + courseId;
        }

        // Remember the quiz in the session. We only keep the question ids.
        List<Long> questionIds = new ArrayList<>();
        for (Question question : questions) {
            questionIds.add(question.getId());
        }
        session.setAttribute(SESSION_QUESTIONS, questionIds);
        session.setAttribute(SESSION_COURSE, courseId);
        session.setAttribute(SESSION_INSTANT, instantFeedback);
        session.setAttribute(SESSION_ANSWERS, new HashMap<Long, String>());

        return "redirect:/quiz/take";
    }

    /**
     * Step 3: show the quiz.
     *
     * @param index which question to show (0 = the first one)
     * @param all   true to show every question on one page instead
     */
    @GetMapping("/take")
    public String take(@RequestParam(defaultValue = "0") int index,
                       @RequestParam(defaultValue = "false") boolean all,
                       HttpSession session, Model model) {
        User user = currentUser.get();
        List<Question> questions = loadSessionQuestions(session);
        if (questions.isEmpty()) {
            return "redirect:/quiz/start";   // no quiz in progress
        }

        Long courseId = (Long) session.getAttribute(SESSION_COURSE);
        Course course = courseService.getOwned(courseId, user);
        boolean instantFeedback = isInstantFeedback(session);
        Map<Long, String> answers = sessionAnswers(session);

        model.addAttribute("user", user);
        model.addAttribute("course", course);
        model.addAttribute("questions", questions);
        model.addAttribute("total", questions.size());
        model.addAttribute("showAll", all);
        model.addAttribute("instantFeedback", instantFeedback);
        model.addAttribute("answers", answers);

        if (!all) {
            // Make sure the index is a real question number (not -1, not past the end).
            int lastIndex = questions.size() - 1;
            int safeIndex = Math.max(0, Math.min(index, lastIndex));

            Question question = questions.get(safeIndex);
            String selected = answers.get(question.getId());   // null if not answered yet

            // With instant feedback on, an answered question shows right/wrong and is locked.
            boolean checked = instantFeedback && selected != null;

            model.addAttribute("index", safeIndex);
            model.addAttribute("question", question);
            model.addAttribute("selected", selected);
            model.addAttribute("checked", checked);
        }
        return "quiz/take";
    }

    /**
     * Step 4: a button on the quiz page was pressed.
     *
     * Every button sends the form here, so the current answer is always saved first.
     * Then we look at WHICH button it was:
     *   - a "goto" button (Previous, or a question number) -> show that question
     *   - action=check  (Check answer)                     -> show the same question with feedback
     *   - action=next   (Next question)                    -> show the next question
     *   - anything else (Finish / Submit answers)          -> mark the quiz and show the results
     *
     * @param params every value in the form. Answers are named "answer.<questionId>",
     *               for example "answer.42" = "B".
     */
    @PostMapping("/submit")
    public String submit(@RequestParam Map<String, String> params,
                         @RequestParam(defaultValue = "0") int index,
                         @RequestParam(required = false) String action,
                         @RequestParam(name = "goto", required = false) Integer gotoIndex,
                         HttpSession session) {
        User user = currentUser.get();
        List<Question> questions = loadSessionQuestions(session);
        if (questions.isEmpty()) {
            return "redirect:/quiz/start";   // no quiz in progress
        }

        Long courseId = (Long) session.getAttribute(SESSION_COURSE);
        boolean instantFeedback = isInstantFeedback(session);

        // Save any answers from the form into the session.
        Map<Long, String> answers = sessionAnswers(session);
        for (Question question : questions) {
            String value = params.get("answer." + question.getId());
            if (value == null || value.isBlank()) {
                continue;   // no answer for this question in the form
            }
            // With instant feedback, once the student has seen the answer they can't change theirs.
            if (instantFeedback && answers.containsKey(question.getId())) {
                continue;
            }
            answers.put(question.getId(), value.trim().toUpperCase(Locale.ROOT));
        }
        session.setAttribute(SESSION_ANSWERS, answers);

        // Which button was pressed?
        if (gotoIndex != null) {
            return "redirect:/quiz/take?index=" + gotoIndex;
        }
        if ("check".equals(action)) {
            return "redirect:/quiz/take?index=" + index;
        }
        if ("next".equals(action)) {
            return "redirect:/quiz/take?index=" + (index + 1);
        }

        // Finish: mark the quiz, then forget it from the session.
        QuizAttempt attempt = quizService.submit(user, courseId, questions, answers, instantFeedback);

        session.removeAttribute(SESSION_QUESTIONS);
        session.removeAttribute(SESSION_COURSE);
        session.removeAttribute(SESSION_INSTANT);
        session.removeAttribute(SESSION_ANSWERS);

        return "redirect:/quiz/results/" + attempt.getId();
    }

    /** Step 5: the results page, with every question reviewed. */
    @GetMapping("/results/{id}")
    public String results(@PathVariable Long id, Model model) {
        User user = currentUser.get();
        QuizAttempt attempt = quizService.getOwned(id, user);

        model.addAttribute("user", user);
        model.addAttribute("attempt", attempt);
        model.addAttribute("course", attempt.getCourse());
        return "quiz/results";
    }

    /** /quiz/history — all past quizzes. */
    @GetMapping("/history")
    public String history(Model model) {
        User user = currentUser.get();
        model.addAttribute("user", user);
        model.addAttribute("attempts", quizService.historyFor(user));
        return "quiz/history";
    }

    // ------------------------------------------------------------------
    // Small helper methods
    // ------------------------------------------------------------------

    /**
     * Turns the difficulty from the form ("EASY", "HARD", "MIXED"...) into a Difficulty.
     * Returns null for "MIXED", for nothing, or for anything we don't recognise —
     * null means "use questions of every difficulty".
     */
    private Question.Difficulty readDifficulty(String difficulty) {
        if (difficulty == null || difficulty.isBlank()) {
            return null;
        }
        if (difficulty.equalsIgnoreCase("MIXED")) {
            return null;
        }
        try {
            return Question.Difficulty.valueOf(difficulty.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;   // unknown value, e.g. "SUPERHARD"
        }
    }

    /** True if the student turned on "Instant feedback" for this quiz. */
    private boolean isInstantFeedback(HttpSession session) {
        return Boolean.TRUE.equals(session.getAttribute(SESSION_INSTANT));
    }

    /**
     * The answers given so far in this quiz (question id -> letter).
     * We copy them into a new map, keeping only entries of the right type,
     * in case the session holds something unexpected.
     */
    private Map<Long, String> sessionAnswers(HttpSession session) {
        Map<Long, String> answers = new HashMap<>();

        Object stored = session.getAttribute(SESSION_ANSWERS);
        if (stored instanceof Map) {
            Map<?, ?> storedMap = (Map<?, ?>) stored;
            for (Map.Entry<?, ?> entry : storedMap.entrySet()) {
                if (entry.getKey() instanceof Long && entry.getValue() instanceof String) {
                    answers.put((Long) entry.getKey(), (String) entry.getValue());
                }
            }
        }
        return answers;
    }

    /**
     * Reloads the quiz's questions from the database, using the ids saved in the session.
     * Returns an empty list if there's no quiz in progress.
     */
    private List<Question> loadSessionQuestions(HttpSession session) {
        Object storedIds = session.getAttribute(SESSION_QUESTIONS);
        Long courseId = (Long) session.getAttribute(SESSION_COURSE);
        if (!(storedIds instanceof List) || courseId == null) {
            return List.of();
        }

        List<?> idList = (List<?>) storedIds;
        if (idList.isEmpty()) {
            return List.of();
        }

        List<Long> questionIds = new ArrayList<>();
        for (Object id : idList) {
            if (id instanceof Long) {
                questionIds.add((Long) id);
            }
        }

        // loadByIds only returns this user's questions, in the same order as the ids.
        return quizService.loadByIds(questionIds, currentUser.get());
    }
}
