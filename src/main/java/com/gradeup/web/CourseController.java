package com.gradeup.web;

import com.gradeup.domain.Course;
import com.gradeup.domain.Handout;
import com.gradeup.domain.User;
import com.gradeup.service.CourseService;
import com.gradeup.service.CurrentUser;
import com.gradeup.service.HandoutService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * The course pages: the library, one course, the create/edit form, and uploading PDFs.
 *
 * @RequestMapping("/courses") means every address in this class starts with /courses.
 * So @GetMapping("/{id}") below handles /courses/7, /courses/12, and so on.
 *
 * (See AuthController for how controllers work.)
 */
@Controller
@RequestMapping("/courses")
public class CourseController {

    private final CurrentUser currentUser;
    private final CourseService courseService;
    private final HandoutService handoutService;

    public CourseController(CurrentUser currentUser, CourseService courseService,
                            HandoutService handoutService) {
        this.currentUser = currentUser;
        this.courseService = courseService;
        this.handoutService = handoutService;
    }

    /** /courses — the "My Courses" library, with an optional search (?q=...). */
    @GetMapping
    public String library(@RequestParam(required = false) String q, Model model) {
        User user = currentUser.get();
        List<Course> courses = courseService.searchFor(user, q);

        String searchText = q;
        if (searchText == null) {
            searchText = "";
        }

        model.addAttribute("user", user);
        model.addAttribute("courses", courses);
        model.addAttribute("query", searchText);
        model.addAttribute("totalCourses", courseService.countFor(user));
        return "courses/library";
    }

    /** /courses/new — the empty "New course" form. */
    @GetMapping("/new")
    public String newCourseForm(Model model) {
        model.addAttribute("user", currentUser.get());
        return "courses/form";
    }

    /** Runs when the "New course" form is sent. */
    @PostMapping
    public String create(@RequestParam String code,
                         @RequestParam String title,
                         @RequestParam(required = false) String description,
                         @RequestParam(required = false, defaultValue = "violet") String colorTag,
                         RedirectAttributes redirect) {
        User user = currentUser.get();
        Course course = courseService.create(user, code, title, description, colorTag);

        redirect.addFlashAttribute("toast", "Course created. Upload a handout to get started.");
        return "redirect:/courses/" + course.getId();
    }

    /** /courses/{id} — one course with its handouts. */
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        User user = currentUser.get();
        Course course = courseService.getOwned(id, user);
        List<Handout> handouts = handoutService.listFor(course);

        model.addAttribute("user", user);
        model.addAttribute("course", course);
        model.addAttribute("handouts", handouts);
        model.addAttribute("questionCount", course.getQuestionCount());
        model.addAttribute("keyPointCount", course.getKeyPointCount());
        return "courses/detail";
    }

    /** /courses/{id}/edit — the same form as "New course", filled in with this course. */
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        User user = currentUser.get();
        model.addAttribute("user", user);
        model.addAttribute("course", courseService.getOwned(id, user));
        return "courses/form";
    }

    /** Runs when the "Edit course" form is sent. */
    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @RequestParam String code,
                         @RequestParam String title,
                         @RequestParam(required = false) String description,
                         @RequestParam(required = false, defaultValue = "violet") String colorTag,
                         RedirectAttributes redirect) {
        User user = currentUser.get();
        courseService.update(id, user, code, title, description, colorTag);

        redirect.addFlashAttribute("toast", "Course updated.");
        return "redirect:/courses/" + id;
    }

    /** Runs when the "Delete course" button is pressed. */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        User user = currentUser.get();
        courseService.delete(id, user);

        redirect.addFlashAttribute("toast", "Course deleted.");
        return "redirect:/courses";
    }

    /** Runs when a PDF is uploaded into this course. */
    @PostMapping("/{id}/handouts")
    public String upload(@PathVariable Long id,
                         @RequestParam("file") MultipartFile file,
                         RedirectAttributes redirect) {
        User user = currentUser.get();
        try {
            Handout handout = handoutService.upload(user, id, file);

            // The file was saved, but we couldn't read any text from it.
            if (handout.getStatus() == Handout.Status.FAILED) {
                redirect.addFlashAttribute("error", handout.getFailureReason());
                return "redirect:/courses/" + id;
            }

            // Success: go to the new handout's page.
            int keyPoints = handout.getKeyPoints().size();
            int questions = handout.getQuestions().size();
            redirect.addFlashAttribute("toast",
                    "Uploaded. " + keyPoints + " key points and " + questions + " questions generated.");
            return "redirect:/handouts/" + handout.getId();

        } catch (IllegalArgumentException | IllegalStateException ex) {
            // The upload was rejected (not a PDF, too big, ...). Show why.
            redirect.addFlashAttribute("error", ex.getMessage());
            return "redirect:/courses/" + id;
        }
    }
}
