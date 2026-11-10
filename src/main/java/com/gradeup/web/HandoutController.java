package com.gradeup.web;

import com.gradeup.ai.AiClient;
import com.gradeup.domain.Handout;
import com.gradeup.domain.User;
import com.gradeup.service.CurrentUser;
import com.gradeup.service.HandoutService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The handout page: its key points, its practice questions, and the original text.
 *
 * (See AuthController for how controllers work.)
 */
@Controller
@RequestMapping("/handouts")
public class HandoutController {

    /** The fewest and most key points/questions a student can ask for at once. */
    private static final int MIN_COUNT = 3;
    private static final int MAX_COUNT = 20;

    private final CurrentUser currentUser;
    private final HandoutService handoutService;
    private final AiClient aiClient;

    public HandoutController(CurrentUser currentUser, HandoutService handoutService, AiClient aiClient) {
        this.currentUser = currentUser;
        this.handoutService = handoutService;
        this.aiClient = aiClient;
    }

    /** /handouts/{id}?tab=keypoints|questions|text — the handout page. */
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id,
                         @RequestParam(required = false, defaultValue = "keypoints") String tab,
                         Model model) {
        User user = currentUser.get();
        Handout handout = handoutService.getOwned(id, user);

        model.addAttribute("user", user);
        model.addAttribute("handout", handout);
        model.addAttribute("course", handout.getCourse());
        model.addAttribute("tab", tab);
        model.addAttribute("aiDescription", aiClient.describe());
        return "handouts/detail";
    }

    /** "Regenerate key points" button: replaces the old key points with new ones. */
    @PostMapping("/{id}/summarize")
    public String summarize(@PathVariable Long id,
                            @RequestParam(required = false, defaultValue = "8") int count,
                            RedirectAttributes redirect) {
        User user = currentUser.get();
        try {
            Handout handout = handoutService.summarize(id, user, keepInRange(count));
            redirect.addFlashAttribute("toast", "Generated " + handout.getKeyPoints().size() + " key points.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/handouts/" + id + "?tab=keypoints";
    }

    /** "Generate more questions" button: adds new questions and keeps the old ones. */
    @PostMapping("/{id}/questions")
    public String generateQuestions(@PathVariable Long id,
                                    @RequestParam(required = false, defaultValue = "6") int count,
                                    RedirectAttributes redirect) {
        User user = currentUser.get();
        try {
            int questionsBefore = handoutService.getOwned(id, user).getQuestions().size();
            Handout handout = handoutService.generateQuestions(id, user, keepInRange(count));
            int added = handout.getQuestions().size() - questionsBefore;

            if (added > 0) {
                redirect.addFlashAttribute("toast", "Added " + added + " new practice questions.");
            } else {
                redirect.addFlashAttribute("toast", "No new questions could be generated from this handout.");
            }
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/handouts/" + id + "?tab=questions";
    }

    /** "Delete handout" button. Afterwards, go back to the course the handout was in. */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        User user = currentUser.get();
        Handout handout = handoutService.getOwned(id, user);
        Long courseId = handout.getCourse().getId();

        handoutService.delete(id, user);

        redirect.addFlashAttribute("toast", "Handout deleted.");
        return "redirect:/courses/" + courseId;
    }

    /** Makes sure a requested amount is between MIN_COUNT and MAX_COUNT. */
    private int keepInRange(int count) {
        if (count < MIN_COUNT) {
            return MIN_COUNT;
        }
        if (count > MAX_COUNT) {
            return MAX_COUNT;
        }
        return count;
    }
}
