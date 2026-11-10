package com.gradeup.web;

import com.gradeup.ai.AiClient;
import com.gradeup.ai.GroqAiClient;
import com.gradeup.domain.Course;
import com.gradeup.domain.Handout;
import com.gradeup.domain.QuizAttempt;
import com.gradeup.domain.User;
import com.gradeup.service.CourseService;
import com.gradeup.service.CurrentUser;
import com.gradeup.service.QuizService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * The student's home page (/dashboard): their numbers, recent quizzes, and what to do next.
 *
 * (See AuthController for how controllers work.)
 */
@Controller
public class DashboardController {

    private final CurrentUser currentUser;
    private final CourseService courseService;
    private final QuizService quizService;
    private final AiClient aiClient;

    public DashboardController(CurrentUser currentUser, CourseService courseService,
                               QuizService quizService, AiClient aiClient) {
        this.currentUser = currentUser;
        this.courseService = courseService;
        this.quizService = quizService;
        this.aiClient = aiClient;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        User user = currentUser.get();
        List<Course> courses = courseService.listFor(user);

        // Add up the handouts and questions across all courses.
        int handoutCount = 0;
        int questionCount = 0;
        for (Course course : courses) {
            handoutCount = handoutCount + course.getHandouts().size();
            questionCount = questionCount + course.getQuestionCount();
        }

        // The first handout of the newest course, for the "continue where you left off" card.
        // (null if the student hasn't uploaded anything yet.)
        Handout lastHandout = null;
        for (Course course : courses) {
            if (!course.getHandouts().isEmpty()) {
                lastHandout = course.getHandouts().iterator().next();
                break;
            }
        }

        List<QuizAttempt> recentAttempts = quizService.recentFor(user);
        boolean aiLive = aiClient instanceof GroqAiClient;   // true if the real AI service is in use

        // Hand everything to the dashboard.html page.
        model.addAttribute("user", user);
        model.addAttribute("courses", courses);
        model.addAttribute("courseCount", courses.size());
        model.addAttribute("handoutCount", handoutCount);
        model.addAttribute("questionCount", questionCount);
        model.addAttribute("recentAttempts", recentAttempts);
        model.addAttribute("averageScore", quizService.averageScore(user));
        model.addAttribute("aiDescription", aiClient.describe());
        model.addAttribute("aiLive", aiLive);
        model.addAttribute("lastHandout", lastHandout);

        return "dashboard";
    }
}
