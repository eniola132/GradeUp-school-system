package com.gradeup.service;

import com.gradeup.IntegrationTest;
import com.gradeup.domain.Course;
import com.gradeup.domain.User;
import com.gradeup.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@IntegrationTest
class CourseServiceTest {

    @Autowired CourseService courseService;
    @Autowired UserService userService;
    @Autowired UserRepository userRepository;

    private User owner;
    private User other;

    @BeforeEach
    void setUp() {
        owner = userRepository.findByEmailIgnoreCase("owner@test.com")
                .orElseGet(() -> userService.register("owner@test.com", "password123", "password123", "Owner"));
        other = userRepository.findByEmailIgnoreCase("other@test.com")
                .orElseGet(() -> userService.register("other@test.com", "password123", "password123", "Other"));
    }

    @Test
    void createsAndListsOwnCourses() {
        Course c = courseService.create(owner, "csc301", "Data Structures", "Trees", "mint");
        assertEquals("CSC301", c.getCode(), "code should be upper-cased");
        assertEquals("mint", c.getColorTag());

        List<Course> mine = courseService.listFor(owner);
        assertTrue(mine.stream().anyMatch(x -> x.getId().equals(c.getId())));
    }

    @Test
    void rejectsBlankTitle() {
        assertThrows(IllegalArgumentException.class,
                () -> courseService.create(owner, "X1", "   ", null, "violet"));
    }

    @Test
    void fallsBackToDefaultColourForUnknownTag() {
        Course c = courseService.create(owner, "X2", "Some Course", null, "not-a-colour");
        assertEquals("violet", c.getColorTag());
    }

    @Test
    void refusesToLoadAnotherUsersCourse() {
        Course mine = courseService.create(owner, "SEC1", "Private Course", null, "violet");

        // The core isolation guarantee: another user must not resolve this id.
        assertThrows(NotFoundException.class, () -> courseService.getOwned(mine.getId(), other));
        assertEquals(mine.getId(), courseService.getOwned(mine.getId(), owner).getId());
    }

    @Test
    void refusesToDeleteAnotherUsersCourse() {
        Course mine = courseService.create(owner, "SEC2", "Also Private", null, "violet");
        assertThrows(NotFoundException.class, () -> courseService.delete(mine.getId(), other));
        // Still present for the real owner.
        assertDoesNotThrow(() -> courseService.getOwned(mine.getId(), owner));
    }

    @Test
    void refusesToUpdateAnotherUsersCourse() {
        Course mine = courseService.create(owner, "SEC3", "Mine", null, "violet");
        assertThrows(NotFoundException.class,
                () -> courseService.update(mine.getId(), other, "HACK", "Stolen", null, "rose"));
        assertEquals("Mine", courseService.getOwned(mine.getId(), owner).getTitle());
    }

    @Test
    void searchIsScopedToOwner() {
        courseService.create(owner, "ZZ9", "Unique Searchable Title", null, "sky");
        courseService.create(other, "ZZ9", "Other Persons Course", null, "sky");

        List<Course> results = courseService.searchFor(owner, "Unique Searchable");
        assertEquals(1, results.size());
        assertTrue(results.get(0).getTitle().contains("Unique Searchable"));
    }
}
