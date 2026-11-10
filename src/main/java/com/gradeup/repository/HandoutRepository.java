package com.gradeup.repository;

import com.gradeup.domain.Course;
import com.gradeup.domain.Handout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Reads and saves Handout rows.
 * (See UserRepository for how repositories work, and CourseRepository for "join fetch".)
 */
public interface HandoutRepository extends JpaRepository<Handout, Long> {

    /** A course's handouts, newest first. */
    List<Handout> findByCourseOrderByUploadedAtDesc(Course course);

    /**
     * A course's handouts with their key points and questions loaded straight away,
     * because the course page shows how many of each every handout has.
     *
     * Note: loading two lists at once only works because keyPoints and questions are
     * Sets in Handout.java. With two Lists, Hibernate refuses ("MultipleBagFetchException").
     */
    @Query("""
           select distinct h from Handout h
           left join fetch h.keyPoints
           left join fetch h.questions
           where h.course = :course
           order by h.uploadedAt desc
           """)
    List<Handout> findByCourseWithDetails(@Param("course") Course course);

    /**
     * One handout with its course, the course's owner, key points and questions all loaded.
     * The handout page needs all of these, and we need the owner to check who it belongs to.
     */
    @Query("""
           select distinct h from Handout h
           join fetch h.course c
           join fetch c.owner
           left join fetch h.keyPoints
           left join fetch h.questions
           where h.id = :id
           """)
    Optional<Handout> findByIdWithDetails(@Param("id") Long id);

    /** One handout, but only if it's in the given course. */
    Optional<Handout> findByIdAndCourse(Long id, Course course);

    long countByCourse(Course course);
}
