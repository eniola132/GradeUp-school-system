package com.gradeup.service;

import com.gradeup.domain.User;
import com.gradeup.repository.UserRepository;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Helps Spring Security log people in.
 *
 * When someone submits the login form, Spring Security calls loadUserByUsername(email).
 * We look the user up in OUR database and hand back their email, scrambled password and
 * role. Spring Security then checks the typed password against the scrambled one itself.
 */
@Service
public class GradeUpUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public GradeUpUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Optional<User> found = userRepository.findByEmailIgnoreCase(email);
        if (found.isEmpty()) {
            throw new UsernameNotFoundException("No account found for " + email);
        }
        User user = found.get();

        // Spring Security has its own "User" class, so we write its full name to avoid
        // mixing it up with our com.gradeup.domain.User.
        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPasswordHash(),
                AuthorityUtils.createAuthorityList("ROLE_STUDENT")   // every user is a student
        );
    }
}
