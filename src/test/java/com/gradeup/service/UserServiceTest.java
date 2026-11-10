package com.gradeup.service;

import com.gradeup.IntegrationTest;
import com.gradeup.domain.User;
import com.gradeup.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

@IntegrationTest
class UserServiceTest {

    @Autowired UserService userService;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private String unique(String prefix) {
        return prefix + System.nanoTime() + "@test.com";
    }

    @Test
    void registersNewUser() {
        String email = unique("new");
        User u = userService.register(email, "password123", "password123", "New Student");

        assertNotNull(u.getId());
        assertEquals(email, u.getEmail());
        assertEquals("New Student", u.getDisplayName());
        assertTrue(userRepository.existsByEmailIgnoreCase(email));
    }

    @Test
    void storesPasswordAsBcryptHashNeverPlaintext() {
        String email = unique("hash");
        User u = userService.register(email, "password123", "password123", "Hasher");

        assertNotEquals("password123", u.getPasswordHash(), "password must never be stored in plaintext");
        assertTrue(u.getPasswordHash().startsWith("$2"), "should be a BCrypt hash");
        assertTrue(passwordEncoder.matches("password123", u.getPasswordHash()));
        assertFalse(passwordEncoder.matches("wrongpassword", u.getPasswordHash()));
    }

    @Test
    void normalisesEmailToLowerCaseAndTrims() {
        User u = userService.register("  MiXeD" + System.nanoTime() + "@Test.COM  ",
                "password123", "password123", "  Spaced Name  ");
        assertEquals(u.getEmail(), u.getEmail().toLowerCase());
        assertEquals("Spaced Name", u.getDisplayName());
    }

    @Test
    void rejectsDuplicateEmailIgnoringCase() {
        String email = unique("dupe");
        userService.register(email, "password123", "password123", "First");

        SignupException ex = assertThrows(SignupException.class,
                () -> userService.register(email.toUpperCase(), "password123", "password123", "Second"));
        assertEquals("email", ex.getField());
    }

    @Test
    void rejectsShortPassword() {
        SignupException ex = assertThrows(SignupException.class,
                () -> userService.register(unique("short"), "abc", "abc", "Shorty"));
        assertEquals("password", ex.getField());
    }

    @Test
    void rejectsMismatchedPasswords() {
        SignupException ex = assertThrows(SignupException.class,
                () -> userService.register(unique("mismatch"), "password123", "password124", "Mismatch"));
        assertEquals("confirmPassword", ex.getField());
    }

    @Test
    void rejectsInvalidEmail() {
        for (String bad : new String[]{"notanemail", "a@b", "@test.com", "a b@test.com", ""}) {
            SignupException ex = assertThrows(SignupException.class,
                    () -> userService.register(bad, "password123", "password123", "Bad"),
                    "should reject: " + bad);
            assertEquals("email", ex.getField());
        }
    }

    @Test
    void rejectsBlankDisplayName() {
        SignupException ex = assertThrows(SignupException.class,
                () -> userService.register(unique("blank"), "password123", "password123", "   "));
        assertEquals("displayName", ex.getField());
    }

    @Test
    void derivesInitialsForAvatar() {
        assertEquals("AM", new User("a@b.com", "h", "Ama Mensah").getInitials());
        assertEquals("A", new User("a@b.com", "h", "Ama").getInitials());
        assertEquals("AM", new User("a@b.com", "h", "Ama Kofi Mensah").getInitials());
        assertEquals("?", new User("a@b.com", "h", "  ").getInitials());
    }
}
