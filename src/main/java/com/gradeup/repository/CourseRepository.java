package com.gradeup.repository;

import com.gradeup.domain.Course;
import com.gradeup.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Reads and saves Course rows. (See UserRepository for how repositories work.)
 *
 * WHY SOME METHODS HAVE @Query AND "join fetch"
 * Normally the database loads a course's handouts LAZILY — only at the moment you ask for
 * them. But our web pages show counts (handouts, key points, questions) AFTER the database
 * connection has already closed. Asking for lazy data then causes a
 * "LazyInitializationException" crash.
 *
 * "left join fetch" tells the database: load the course AND its handouts, key points and
 * questions all at once, right now. The queries are written in JPQL, which looks like SQL
 * but uses class and field names (Course, c.owner) instead of table and column names.
 *
 * ":owner" and ":id" are placeholders. @Param("owner") fills ":owner" with the method's argument.
 * "distinct" stops the same course appearing several times in the results.
 */
public interface CourseRepository extends JpaRepository<Course, Long> {

    /** All of a user's courses, newest first, with handouts/key points/questions loaded. */
    @Query("""
           select distinct c from Course c
           left join fetch c.handouts h
           left join fetch h.keyPoints
           left join fetch h.questions
           where c.owner = :owner
           order by c.createdAt desc
           """)
    List<Course> findByOwnerWithDetails(@Param("owner") User owner);

    /**
     * One course, but ONLY if it belongs to this user, with everything loaded.
     * Checking the owner here means one student can never open another student's course.
     */
    @Query("""
           select distinct c from Course c
           left join fetch c.handouts h
           left join fetch h.keyPoints
           left join fetch h.questions
           where c.id = :id and c.owner = :owner
           """)
    Optional<Course> findByIdAndOwnerWithDetails(@Param("id") Long id, @Param("owner") User owner);

    List<Course> findByOwnerOrderByCreatedAtDesc(User owner);

    /** One course, but only if this user owns it (without loading the handouts). */
    Optional<Course> findByIdAndOwner(Long id, User owner);

    long countByOwner(User owner);

    /**
     * Search a user's courses by title or code, with everything loaded.
     * lower(...) makes the search ignore upper/lower case.
     * concat('%', :q, '%') means "contains q anywhere" (% matches any text).
     */
    @Query("""
           select distinct c from Course c
           left join fetch c.handouts h
           left join fetch h.keyPoints
           left join fetch h.questions
           where c.owner = :owner
             and (lower(c.title) like lower(concat('%', :q, '%'))
                  or lower(c.code) like lower(concat('%', :q, '%')))
           order by c.createdAt desc
           """)
    List<Course> searchByOwnerWithDetails(@Param("owner") User owner, @Param("q") String query);

    /** Same search as above, but without loading the handouts. */
    @Query("""
           select c from Course c
           where c.owner = :owner
             and (lower(c.title) like lower(concat('%', :q, '%'))
                  or lower(c.code) like lower(concat('%', :q, '%')))
           order by c.createdAt desc
           """)
    List<Course> searchByOwner(@Param("owner") User owner, @Param("q") String query);
}
