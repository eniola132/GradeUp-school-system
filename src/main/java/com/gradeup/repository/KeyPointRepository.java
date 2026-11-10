package com.gradeup.repository;

import com.gradeup.domain.Handout;
import com.gradeup.domain.KeyPoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Reads and saves KeyPoint rows. (See UserRepository for how repositories work.)
 */
public interface KeyPointRepository extends JpaRepository<KeyPoint, Long> {

    /** A handout's key points in their display order. */
    List<KeyPoint> findByHandoutOrderByOrderIndexAsc(Handout handout);

    /** Deletes every key point of a handout (used before re-summarising it). */
    void deleteByHandout(Handout handout);

    long countByHandout(Handout handout);
}
