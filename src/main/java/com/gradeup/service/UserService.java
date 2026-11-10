package com.gradeup.service;

import com.gradeup.domain.User;
import com.gradeup.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

/**
 * Creating new student accounts (signing up).
 *
 * WHAT IS A "SERVICE"?
 * Controllers deal with web pages; repositories deal with the database.
 * Services sit in the middle and hold the actual RULES of the app
 * ("a password must be at least 8 characters", "emails must be unique", ...).
 *
 * @Service      - Spring creates one of these and shares it with any class that needs it.
 * @Transactional - everything in the method happens as one unit in the database:
 *                  if something fails halfway, all of its changes are undone.
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    /**
     * A simple check that an email looks like "something@something.something".
     * It's not perfect, but it catches typos like "ama@gmail" or "ama.gmail.com".
     */
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s.]+\\.[^@\\s]{2,}$");

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_NAME_LENGTH = 80;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Checks the signup form and creates the account.
     *
     * @throws SignupException if anything is wrong; it says which field and why
     */
    @Transactional
    public User register(String email, String rawPassword, String confirmPassword, String displayName) {
        // Tidy up what was typed: remove spaces at the ends, make the email lower case.
        String cleanEmail = "";
        if (email != null) {
            cleanEmail = email.trim().toLowerCase();
        }
        String cleanName = "";
        if (displayName != null) {
            cleanName = displayName.trim();
        }

        // Check each field. The first problem found is reported.
        if (cleanName.isBlank()) {
            throw new SignupException("displayName", "Please tell us your name.");
        }
        if (cleanName.length() > MAX_NAME_LENGTH) {
            throw new SignupException("displayName", "That name is too long.");
        }
        if (!EMAIL_PATTERN.matcher(cleanEmail).matches()) {
            throw new SignupException("email", "Please enter a valid email address.");
        }
        if (rawPassword == null || rawPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new SignupException("password",
                    "Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        if (!rawPassword.equals(confirmPassword)) {
            throw new SignupException("confirmPassword", "Those passwords don't match.");
        }
        if (userRepository.existsByEmailIgnoreCase(cleanEmail)) {
            throw new SignupException("email", "An account with that email already exists.");
        }

        // Everything is fine. Scramble the password and save the new user.
        String passwordHash = passwordEncoder.encode(rawPassword);
        User user = new User(cleanEmail, passwordHash, cleanName);
        User saved = userRepository.save(user);

        log.info("Registered new student account id={}", saved.getId());
        return saved;
    }
}
