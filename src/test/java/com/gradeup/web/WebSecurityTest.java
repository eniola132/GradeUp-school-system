package com.gradeup.web;

import com.gradeup.IntegrationTest;
import com.gradeup.domain.Course;
import com.gradeup.domain.User;
import com.gradeup.service.CourseService;
import com.gradeup.service.UserService;
import com.gradeup.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@IntegrationTest
class WebSecurityTest {

    @Autowired WebApplicationContext context;
    @Autowired UserService userService;
    @Autowired CourseService courseService;
    @Autowired UserRepository userRepository;

    private MockMvc mockMvc;
    private User owner;
    private User other;
    private Course ownerCourse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        owner = userRepository.findByEmailIgnoreCase("webowner@test.com")
                .orElseGet(() -> userService.register("webowner@test.com", "password123", "password123", "Web Owner"));
        other = userRepository.findByEmailIgnoreCase("webother@test.com")
                .orElseGet(() -> userService.register("webother@test.com", "password123", "password123", "Web Other"));

        ownerCourse = courseService.create(owner, "WEB1", "Web Secured Course", null, "violet");
    }

    // ---------- Public pages ----------

    @Test
    void landingPageIsPublic() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("landing"));
    }

    @Test
    void loginAndSignupArePublic() throws Exception {
        mockMvc.perform(get("/login")).andExpect(status().isOk());
        mockMvc.perform(get("/signup")).andExpect(status().isOk());
    }

    // ---------- Protected pages redirect anonymous users ----------

    @Test
    void protectedPagesRedirectAnonymousUsersToLogin() throws Exception {
        for (String path : new String[]{"/dashboard", "/courses", "/quiz/start", "/quiz/history"}) {
            mockMvc.perform(get(path))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrlPattern("**/login"));
        }
    }

    @Test
    void protectedPagesRenderForAuthenticatedUsers() throws Exception {
        mockMvc.perform(get("/dashboard").with(user("webowner@test.com").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"));

        mockMvc.perform(get("/courses").with(user("webowner@test.com").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(view().name("courses/library"));

        mockMvc.perform(get("/courses/" + ownerCourse.getId())
                        .with(user("webowner@test.com").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(view().name("courses/detail"));
    }

    // ---------- Cross-user access is refused ----------

    @Test
    void anotherUserCannotViewSomeoneElsesCourse() throws Exception {
        mockMvc.perform(get("/courses/" + ownerCourse.getId())
                        .with(user("webother@test.com").roles("STUDENT")))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"));
    }

    @Test
    void anotherUserCannotViewSomeoneElsesHandoutOrResults() throws Exception {
        mockMvc.perform(get("/handouts/999999").with(user("webother@test.com").roles("STUDENT")))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/quiz/results/999999").with(user("webother@test.com").roles("STUDENT")))
                .andExpect(status().isNotFound());
    }

    @Test
    void anotherUserCannotDeleteSomeoneElsesCourse() throws Exception {
        mockMvc.perform(post("/courses/" + ownerCourse.getId() + "/delete")
                        .with(user("webother@test.com").roles("STUDENT"))
                        .with(csrf()))
                .andExpect(status().isNotFound());

        // The course must still exist for its real owner.
        mockMvc.perform(get("/courses/" + ownerCourse.getId())
                        .with(user("webowner@test.com").roles("STUDENT")))
                .andExpect(status().isOk());
    }

    // ---------- CSRF ----------

    @Test
    void stateChangingPostWithoutCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post("/courses")
                        .with(user("webowner@test.com").roles("STUDENT"))
                        .param("code", "NOCSRF")
                        .param("title", "Should Not Be Created"))
                .andExpect(status().isForbidden());
    }

    // ---------- Signup validation ----------

    @Test
    void signupRejectsMismatchedPasswords() throws Exception {
        mockMvc.perform(post("/signup").with(csrf())
                        .param("displayName", "Mismatch")
                        .param("email", "mismatch@test.com")
                        .param("password", "password123")
                        .param("confirmPassword", "different123"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/signup"))
                .andExpect(model().attributeExists("error"));
    }

    @Test
    void signupRejectsShortPasswordAndBadEmail() throws Exception {
        mockMvc.perform(post("/signup").with(csrf())
                        .param("displayName", "Shorty")
                        .param("email", "shorty@test.com")
                        .param("password", "abc")
                        .param("confirmPassword", "abc"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("errorField", "password"));

        mockMvc.perform(post("/signup").with(csrf())
                        .param("displayName", "Bad Email")
                        .param("email", "not-an-email")
                        .param("password", "password123")
                        .param("confirmPassword", "password123"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("errorField", "email"));
    }
}
