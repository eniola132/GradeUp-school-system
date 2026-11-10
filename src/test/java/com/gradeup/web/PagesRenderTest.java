package com.gradeup.web;

import com.gradeup.IntegrationTest;
import com.gradeup.domain.*;
import com.gradeup.repository.QuestionRepository;
import com.gradeup.repository.UserRepository;
import com.gradeup.service.CourseService;
import com.gradeup.service.HandoutService;
import com.gradeup.service.QuizService;
import com.gradeup.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Opens every page of the app and checks it renders.
 * A typo in a Thymeleaf template only shows up when that page is drawn, so this
 * catches template mistakes that the other tests would miss.
 */
@IntegrationTest
class PagesRenderTest {

    private static final String EMAIL = "pages@test.com";

    @Autowired WebApplicationContext context;
    @Autowired UserService userService;
    @Autowired UserRepository userRepository;
    @Autowired CourseService courseService;
    @Autowired HandoutService handoutService;
    @Autowired QuestionRepository questionRepository;
    @Autowired QuizService quizService;

    private MockMvc mockMvc;
    private User owner;
    private Course course;
    private Handout handout;
    private QuizAttempt attempt;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        owner = userRepository.findByEmailIgnoreCase(EMAIL)
                .orElseGet(() -> userService.register(EMAIL, "password123", "password123", "Page Tester"));
        course = courseService.create(owner, "PG1", "Pages Course", "A course for page tests", "mint");
        handout = handoutService.attachForTesting(course, "pages.pdf");

        Question q = new Question(handout, "What is 2 + 2?", "4", "3", "5", "22", "A");
        q.setExplanation("Two plus two is four.");
        questionRepository.save(q);

        attempt = quizService.submit(owner, course.getId(), List.of(q), Map.of(q.getId(), "B"), false);
    }

    @Test
    void everyLoggedInPageRenders() throws Exception {
        String[] pages = {
                "/dashboard",
                "/courses",
                "/courses?q=pages",
                "/courses?q=nothing-matches-this",
                "/courses/new",
                "/courses/" + course.getId(),
                "/courses/" + course.getId() + "/edit",
                "/handouts/" + handout.getId(),
                "/handouts/" + handout.getId() + "?tab=questions",
                "/handouts/" + handout.getId() + "?tab=text",
                "/quiz/start",
                "/quiz/start?courseId=" + course.getId(),
                "/quiz/history",
                "/quiz/results/" + attempt.getId(),
        };
        for (String page : pages) {
            mockMvc.perform(get(page).with(user(EMAIL)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void publicAndErrorPagesRender() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk());
        mockMvc.perform(get("/login")).andExpect(status().isOk());
        mockMvc.perform(get("/signup")).andExpect(status().isOk());
        mockMvc.perform(get("/courses/999999").with(user(EMAIL)))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("That course doesn&#39;t exist.")));
    }

    @Test
    void navHighlightsTheCurrentSection() throws Exception {
        mockMvc.perform(get("/courses").with(user(EMAIL)))
                .andExpect(content().string(containsString("class=\"nav-link active\" href=\"/courses\"")));
        mockMvc.perform(get("/quiz/history").with(user(EMAIL)))
                .andExpect(content().string(containsString("class=\"nav-link active\" href=\"/quiz/history\"")));
    }

    @Test
    void loginPageConfirmsANewAccount() throws Exception {
        mockMvc.perform(get("/login").flashAttr("justRegistered", true))
                .andExpect(content().string(containsString("Account created. You can sign in now.")));
        mockMvc.perform(get("/login"))
                .andExpect(content().string(not(containsString("Account created."))));
    }

    @Test
    void developerCommentsAreNotSentToTheBrowser() throws Exception {
        mockMvc.perform(get("/dashboard").with(user(EMAIL)))
                .andExpect(content().string(not(containsString("THYMELEAF IN TWO MINUTES"))))
                .andExpect(content().string(not(containsString("<!--/*"))));
    }

    @Test
    void signupStillRedirectsToLogin() throws Exception {
        mockMvc.perform(post("/signup").with(csrf())
                        .param("displayName", "New Person")
                        .param("email", "newperson" + System.nanoTime() + "@test.com")
                        .param("password", "password123")
                        .param("confirmPassword", "password123"))
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attribute("justRegistered", true));
    }
}
