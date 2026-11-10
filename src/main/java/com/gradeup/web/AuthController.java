package com.gradeup.web;

import com.gradeup.service.CurrentUser;
import com.gradeup.service.NotFoundException;
import com.gradeup.service.SignupException;
import com.gradeup.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The landing page, the login page, and signing up.
 *
 * HOW CONTROLLERS WORK (this applies to every file in this "web" folder)
 * A controller decides what happens when someone visits a web address.
 *
 *   @Controller        - this class handles web pages.
 *   @GetMapping("/x")  - run this method when the browser OPENS the page /x.
 *   @PostMapping("/x") - run this method when a FORM is sent to /x.
 *   @RequestParam      - read a value from the form or the address (?name=value).
 *   @PathVariable      - read a value from inside the address, like the 7 in /courses/7.
 *   Model              - a bag of values we hand to the HTML page (Thymeleaf template).
 *
 * WHAT THE METHODS RETURN
 *   "auth/login"           - show the page src/main/resources/templates/auth/login.html
 *   "redirect:/dashboard"  - tell the browser to go to /dashboard instead.
 *
 * RedirectAttributes.addFlashAttribute(...) passes a message (like "Course created")
 * to the NEXT page only, after a redirect.
 */
@Controller
public class AuthController {

    private final UserService userService;
    private final CurrentUser currentUser;

    public AuthController(UserService userService, CurrentUser currentUser) {
        this.userService = userService;
        this.currentUser = currentUser;
    }

    /** The home page. Logged-in students skip it and go straight to their dashboard. */
    @GetMapping("/")
    public String landing() {
        try {
            currentUser.get();              // throws an error if nobody is logged in
            return "redirect:/dashboard";
        } catch (IllegalStateException | NotFoundException ex) {
            return "landing";               // not logged in: show the landing page
        }
    }

    /** Shows the login page. (Spring Security handles the actual login — see SecurityConfig.) */
    @GetMapping("/login")
    public String login() {
        return "auth/login";
    }

    /** Shows the empty signup form. */
    @GetMapping("/signup")
    public String signupForm() {
        return "auth/signup";
    }

    /** Runs when the signup form is sent. */
    @PostMapping("/signup")
    public String signup(@RequestParam String displayName,
                         @RequestParam String email,
                         @RequestParam String password,
                         @RequestParam String confirmPassword,
                         Model model,
                         RedirectAttributes redirect) {
        try {
            userService.register(email, password, confirmPassword, displayName);
        } catch (SignupException ex) {
            // Something was wrong. Show the form again with the error, and keep what the
            // student typed so they don't have to type it all again. (Never the password.)
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("errorField", ex.getField());
            model.addAttribute("displayName", displayName);
            model.addAttribute("email", email);
            return "auth/signup";
        }

        // Account created: send them to the login page with a "welcome" message.
        redirect.addFlashAttribute("justRegistered", true);
        return "redirect:/login";
    }
}
