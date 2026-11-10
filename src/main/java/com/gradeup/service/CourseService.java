package com.gradeup.service;

import com.gradeup.domain.Course;
import com.gradeup.domain.User;
import com.gradeup.repository.CourseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Creating, reading, updating and deleting courses.
 *
 * IMPORTANT RULE: a student can only ever see or change THEIR OWN courses.
 * That's why almost every method takes the "owner" and checks it.
 *
 * (See UserService for what @Service and @Transactional mean.
 *  readOnly = true is a small hint to the database that we're only reading.)
 */
@Service
public class CourseService {

    /** The colours a course card can be. Anything else falls back to "violet". */
    private static final Set<String> ALLOWED_COLORS = Set.of("violet", "mint", "amber", "rose", "sky");

    private static final int MAX_CODE_LENGTH = 32;

    private final CourseRepository courseRepository;

    public CourseService(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    /** Creates a new course for this user. */
    @Transactional
    public Course create(User owner, String code, String title, String description, String colorTag) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Course title is required.");
        }

        // The course code: use "COURSE" if empty, make it upper case, and limit its length.
        String cleanCode;
        if (code == null || code.isBlank()) {
            cleanCode = "COURSE";
        } else {
            cleanCode = code.trim().toUpperCase();
        }
        if (cleanCode.length() > MAX_CODE_LENGTH) {
            cleanCode = cleanCode.substring(0, MAX_CODE_LENGTH);
        }

        Course course = new Course(owner, cleanCode, title.trim());
        course.setDescription(trimOrNull(description));

        if (ALLOWED_COLORS.contains(colorTag)) {
            course.setColorTag(colorTag);
        } else {
            course.setColorTag("violet");
        }

        return courseRepository.save(course);
    }

    /** All of this user's courses. */
    @Transactional(readOnly = true)
    public List<Course> listFor(User owner) {
        return courseRepository.findByOwnerWithDetails(owner);
    }

    /** This user's courses whose title or code contains the search text. */
    @Transactional(readOnly = true)
    public List<Course> searchFor(User owner, String query) {
        if (query == null || query.isBlank()) {
            return listFor(owner);   // nothing typed in the search box: show everything
        }
        return courseRepository.searchByOwnerWithDetails(owner, query.trim());
    }

    /**
     * Loads one course, but ONLY if it belongs to this user.
     * If it doesn't exist (or belongs to someone else) the student sees a 404 page.
     */
    @Transactional(readOnly = true)
    public Course getOwned(Long courseId, User owner) {
        Optional<Course> found = courseRepository.findByIdAndOwnerWithDetails(courseId, owner);
        if (found.isEmpty()) {
            throw new NotFoundException("That course doesn't exist.");
        }
        return found.get();
    }

    /** Saves changes from the "Edit course" form. */
    @Transactional
    public Course update(Long courseId, User owner, String code, String title,
                         String description, String colorTag) {
        Course course = getOwned(courseId, owner);   // also checks the owner

        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Course title is required.");
        }
        course.setTitle(title.trim());

        // Only change the code if a new one was typed in.
        if (code != null && !code.isBlank()) {
            course.setCode(code.trim().toUpperCase());
        }

        course.setDescription(trimOrNull(description));

        // Only change the colour if it's one we allow.
        if (ALLOWED_COLORS.contains(colorTag)) {
            course.setColorTag(colorTag);
        }

        return courseRepository.save(course);
    }

    /** Deletes a course. Its handouts, key points and questions are deleted with it. */
    @Transactional
    public void delete(Long courseId, User owner) {
        Course course = getOwned(courseId, owner);   // also checks the owner
        courseRepository.delete(course);
    }

    /** How many courses this user has. */
    @Transactional(readOnly = true)
    public long countFor(User owner) {
        return courseRepository.countByOwner(owner);
    }

    /** Removes spaces from both ends of the text, or returns null if there's no text. */
    private String trimOrNull(String text) {
        if (text == null) {
            return null;
        }
        return text.trim();
    }
}
