package com.gradeup.repository;

import com.gradeup.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Reads and saves User rows in the database.
 *
 * HOW REPOSITORIES WORK (this applies to every file in this folder)
 * A repository is an interface — notice there's no code inside the methods!
 * Spring Data JPA writes the code for us, using two tricks:
 *
 *   1. "extends JpaRepository<User, Long>" gives us ready-made methods for free:
 *      save(user), findById(id), findAll(), delete(user), count() and more.
 *      (User is the entity type, Long is the type of its id.)
 *
 *   2. Spring reads the METHOD NAME and turns it into a database query. For example:
 *        findByEmailIgnoreCase(email)  ->  "find users whose email matches, ignoring case"
 *        existsByEmailIgnoreCase(email) ->  "is there at least one user with this email?"
 *
 * WHAT IS Optional?
 * Optional<User> means "maybe a User, maybe nothing". It's used instead of returning null,
 * so you're reminded to handle the "not found" case.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}
