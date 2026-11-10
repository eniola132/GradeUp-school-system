package com.gradeup.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Login, logout, and "who is allowed to see which page".
 *
 * WHAT THIS FILE DOES
 * Spring Security checks EVERY request before it reaches a controller. This file tells it:
 *   - which pages anyone can see (the landing page, login, signup, CSS/JS files),
 *   - that every other page needs the user to be logged in,
 *   - how the login form and logout button work,
 *   - how passwords are scrambled (BCrypt).
 *
 * Because the rule is "everything needs login unless listed as public", a new page we
 * add later is automatically protected, even if we forget about security.
 *
 * ANNOTATIONS
 *   @Configuration     - this class sets things up for Spring.
 *   @EnableWebSecurity - switch on Spring Security for the website.
 *   @Bean              - "Spring, please keep the object this method returns and give it
 *                         to any class that needs one". For example, UserService asks for
 *                         a PasswordEncoder and receives the one created below.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** The tool used to scramble ("hash") passwords before saving them. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** All the security rules for the website. */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        // 1. Which pages are public and which need a login.
        http.authorizeHttpRequests(auth -> {
            auth.requestMatchers("/", "/login", "/signup", "/error").permitAll();
            auth.requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll();
            auth.requestMatchers("/h2-console/**").permitAll();   // database viewer for development
            auth.anyRequest().authenticated();                    // everything else: must be logged in
        });

        // 2. The login form.
        http.formLogin(form -> {
            form.loginPage("/login");                      // our own login page (auth/login.html)
            form.loginProcessingUrl("/login");             // where the form is sent
            form.usernameParameter("email");               // the form field holding the email
            form.passwordParameter("password");            // the form field holding the password
            form.defaultSuccessUrl("/dashboard", true);    // where to go after logging in
            form.failureUrl("/login?error");               // where to go if the password is wrong
            form.permitAll();
        });

        // 3. Logging out (the "Log out" button sends a POST to /logout).
        http.logout(logout -> {
            logout.logoutRequestMatcher(new AntPathRequestMatcher("/logout", "POST"));
            logout.logoutSuccessUrl("/login?logout");
            logout.invalidateHttpSession(true);            // forget everything about this visit
            logout.deleteCookies("JSESSIONID");            // remove the login cookie
            logout.permitAll();
        });

        // 4. CSRF protection: every form must include a secret token, so another website
        //    can't trick a logged-in student's browser into submitting our forms.
        //    Thymeleaf adds the token to our forms automatically.
        //    The H2 database console (development only) is left out because it can't send it.
        http.csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**"));

        // 5. Allow our own pages to be shown inside frames (the H2 console uses frames).
        http.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        return http.build();
    }
}
