package com.gradeup.service;

import com.gradeup.domain.User;
import com.gradeup.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Answers the question "which student is using the app right now?".
 *
 * Every controller calls currentUser.get() at the start of each page. Having one shared
 * place for this means every page finds the user in exactly the same way.
 *
 * @Component tells Spring to create one of these and hand it to any class that asks for it.
 */
@Component
public class CurrentUser {

    private final UserRepository userRepository;

    /**
     * Spring calls this constructor and passes in the UserRepository for us.
     * (This is "dependency injection" — we never write "new UserRepository()" ourselves.)
     */
    public CurrentUser(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Returns the logged-in user. Throws an error if nobody is logged in. */
    public User get() {
        // Spring Security remembers who logged in during this visit. Ask it.
        Authentication login = SecurityContextHolder.getContext().getAuthentication();

        boolean nobodyLoggedIn = login == null
                || !login.isAuthenticated()
                || "anonymousUser".equals(login.getPrincipal());
        if (nobodyLoggedIn) {
            throw new IllegalStateException("No authenticated user in the security context");
        }

        // login.getName() is the email address they logged in with.
        Optional<User> user = userRepository.findByEmailIgnoreCase(login.getName());
        if (user.isEmpty()) {
            throw new UsernameNotFoundException("Authenticated user no longer exists");
        }
        return user.get();
    }
}
