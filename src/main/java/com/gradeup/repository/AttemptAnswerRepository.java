package com.gradeup.repository;

import com.gradeup.domain.AttemptAnswer;
import com.gradeup.domain.QuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Reads and saves AttemptAnswer rows. (See UserRepository for how repositories work.)
 */
public interface AttemptAnswerRepository extends JpaRepository<AttemptAnswer, Long> {

    /** All answers from one quiz, in the order they were saved. */
    List<AttemptAnswer> findByAttemptOrderByIdAsc(QuizAttempt attempt);
}
