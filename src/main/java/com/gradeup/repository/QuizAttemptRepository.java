package com.gradeup.repository;

import com.gradeup.domain.Course;
import com.gradeup.domain.QuizAttempt;
import com.gradeup.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Reads and saves QuizAttempt rows.
 * (See UserRepository for how repositories work, and CourseRepository for "join fetch".)
 */
public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {

    /**
     * One quiz result, only if it belongs to this user, with the course, every answer
     * and every answer's question loaded — the results page shows all of them.
     */
    @Query("""
           select distinct a from QuizAttempt a
           join fetch a.course
           left join fetch a.answers ans
           left join fetch ans.question
           where a.id = :id and a.user = :user
           """)
    Optional<QuizAttempt> findByIdAndUserWithDetails(@Param("id") Long id, @Param("user") User user);

    /** All of a user's quizzes, newest first, with the course loaded (for the History page). */
    @Query("""
           select distinct a from QuizAttempt a
           join fetch a.course
           where a.user = :user
           order by a.startedAt desc
           """)
    List<QuizAttempt> findByUserWithCourse(@Param("user") User user);

    /** Same as above; used for the "recent activity" list on the dashboard. */
    @Query("""
           select distinct a from QuizAttempt a
           join fetch a.course
           where a.user = :user
           order by a.startedAt desc
           """)
    List<QuizAttempt> findRecentByUserWithCourse(@Param("user") User user);

    List<QuizAttempt> findByUserOrderByStartedAtDesc(User user);

    /** "Top8" means: only return the first 8 results. */
    List<QuizAttempt> findTop8ByUserOrderByStartedAtDesc(User user);

    /** One quiz result, but only if this user took it. */
    Optional<QuizAttempt> findByIdAndUser(Long id, User user);

    List<QuizAttempt> findByUserAndCourseOrderByStartedAtDesc(User user, Course course);

    long countByUser(User user);
}
