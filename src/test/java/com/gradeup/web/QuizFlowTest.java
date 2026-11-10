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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Walks through a quiz one question at a time, the way a student does in the browser. */
@IntegrationTest
class QuizFlowTest {

    private static final String EMAIL = "quizflow@test.com";

    @Autowired WebApplicationContext context;
    @Autowired UserService userService;
    @Autowired UserRepository userRepository;
    @Autowired CourseService courseService;
    @Autowired HandoutService handoutService;
    @Autowired QuestionRepository questionRepository;
    @Autowired QuizService quizService;

    private MockMvc mockMvc;
    private MockHttpSession session;
    private User owner;
    private Course course;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        session = new MockHttpSession();

        owner = userRepository.findByEmailIgnoreCase(EMAIL)
                .orElseGet(() -> userService.register(EMAIL, "password123", "password123", "Flow"));
        course = courseService.create(owner, "FL" + System.nanoTime() % 10000, "Flow Course", null, "violet");

        Handout handout = handoutService.attachForTesting(course, "flow.pdf");
        List<Question> built = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Question q = new Question(handout, "Flow prompt " + i, "A" + i, "B" + i, "C" + i, "D" + i, "A");
            q.setDifficulty(Question.Difficulty.MEDIUM);
            q.setExplanation("Because " + i);
            q.setSource(Question.Source.OFFLINE);
            built.add(q);
        }
        questionRepository.saveAll(built);
    }

    @Test
    void nextQuestionKeepsTheQuizGoingAndAnswersSurviveNavigation() throws Exception {
        List<Long> ids = startQuiz(false);

        // Answering Q1 and pressing "Next" must move on, not grade the quiz.
        mockMvc.perform(post("/quiz/submit").session(session).with(user(EMAIL)).with(csrf())
                        .param("index", "0").param("action", "next")
                        .param("answer." + ids.get(0), "A"))
                .andExpect(redirectedUrl("/quiz/take?index=1"));

        // Going back shows the earlier answer still selected.
        mockMvc.perform(post("/quiz/submit").session(session).with(user(EMAIL)).with(csrf())
                        .param("index", "1").param("goto", "0"))
                .andExpect(redirectedUrl("/quiz/take?index=0"));
        mockMvc.perform(get("/quiz/take").param("index", "0").session(session).with(user(EMAIL)))
                .andExpect(status().isOk())
                .andExpect(content().string(matchesPattern("(?s).*value=\"A\"\\s+checked=\"checked\".*")))
                .andExpect(content().string(containsString("1 of 3 answered")));

        // Skip Q2, answer Q3 and finish.
        mockMvc.perform(post("/quiz/submit").session(session).with(user(EMAIL)).with(csrf())
                        .param("index", "0").param("goto", "2"))
                .andExpect(redirectedUrl("/quiz/take?index=2"));
        String results = mockMvc.perform(post("/quiz/submit").session(session).with(user(EMAIL)).with(csrf())
                        .param("index", "2").param("action", "finish")
                        .param("answer." + ids.get(2), "A"))
                .andExpect(redirectedUrlPattern("/quiz/results/*"))
                .andReturn().getResponse().getRedirectedUrl();

        QuizAttempt attempt = quizService.getOwned(attemptId(results), owner);
        assertEquals(3, attempt.getTotalQuestions());
        assertEquals(2, attempt.getScore(), "Q1 and Q3 answered correctly, Q2 skipped");
    }

    @Test
    void instantFeedbackRevealsTheAnswerAndLocksIt() throws Exception {
        List<Long> ids = startQuiz(true);

        mockMvc.perform(get("/quiz/take").session(session).with(user(EMAIL)))
                .andExpect(content().string(containsString("Check answer")))
                .andExpect(content().string(not(containsString("Because"))));

        mockMvc.perform(post("/quiz/submit").session(session).with(user(EMAIL)).with(csrf())
                        .param("index", "0").param("action", "check")
                        .param("answer." + ids.get(0), "B"))
                .andExpect(redirectedUrl("/quiz/take?index=0"));

        mockMvc.perform(get("/quiz/take").param("index", "0").session(session).with(user(EMAIL)))
                .andExpect(content().string(containsString("Not quite — the answer is A.")))
                .andExpect(content().string(containsString("option-wrong")))
                .andExpect(content().string(containsString("Next question")));

        // A changed answer after feedback is ignored.
        String results = mockMvc.perform(post("/quiz/submit").session(session).with(user(EMAIL)).with(csrf())
                        .param("index", "0").param("action", "finish")
                        .param("answer." + ids.get(0), "A"))
                .andReturn().getResponse().getRedirectedUrl();

        QuizAttempt attempt = quizService.getOwned(attemptId(results), owner);
        assertEquals(0, attempt.getScore());
        assertTrue(attempt.isInstantFeedback());
    }

    @SuppressWarnings("unchecked")
    private List<Long> startQuiz(boolean instant) throws Exception {
        mockMvc.perform(post("/quiz/start").session(session).with(user(EMAIL)).with(csrf())
                        .param("courseId", String.valueOf(course.getId()))
                        .param("count", "3")
                        .param("instantFeedback", String.valueOf(instant)))
                .andExpect(redirectedUrl("/quiz/take"));
        List<Long> ids = (List<Long>) session.getAttribute("quiz.questions");
        assertNotNull(ids);
        assertEquals(3, ids.size());
        return ids;
    }

    private Long attemptId(String resultsUrl) {
        assertNotNull(resultsUrl);
        return Long.valueOf(resultsUrl.substring(resultsUrl.lastIndexOf('/') + 1));
    }
}
