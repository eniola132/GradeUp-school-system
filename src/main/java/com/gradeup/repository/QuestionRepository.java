package com.gradeup.repository;

import com.gradeup.domain.Handout;
import com.gradeup.domain.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Reads and saves Question rows.
 * (See UserRepository for how repositories work, and CourseRepository for @Query.)
 */
public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findByHandoutOrderByIdAsc(Handout handout);

    /** Every question from any of the given handouts. "In" means "is one of these". */
    List<Question> findByHandoutIn(List<Handout> handouts);

    /**
     * Loads questions by their ids, but ONLY ones that belong to the given user.
     *
     * The ownership check is part of the query itself. So even if someone edits the
     * page and sends another student's question id, this returns nothing for it.
     */
    @Query("""
           select q from Question q
           join fetch q.handout h
           join h.course c
           where q.id in :ids and c.owner.id = :ownerId
           """)
    List<Question> findOwnedByIds(@Param("ids") List<Long> ids, @Param("ownerId") Long ownerId);

    void deleteByHandout(Handout handout);

    long countByHandout(Handout handout);
}
