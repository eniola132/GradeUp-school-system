package com.gradeup.web;

import com.gradeup.service.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Turns errors into friendly pages instead of scary error screens.
 *
 * @ControllerAdvice means: "these rules apply to ALL controllers".
 * Each @ExceptionHandler method catches one kind of error that a controller didn't catch
 * itself, and decides what the student sees instead.
 *
 * The full technical details are written to the log for us (the developers) —
 * students only ever see a short, friendly message.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Something doesn't exist (or isn't theirs): show the 404 page. */
    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ModelAndView handleNotFound(NotFoundException ex) {
        ModelAndView page = new ModelAndView("error/404");
        page.addObject("message", ex.getMessage());
        return page;
    }

    /** The uploaded file was bigger than the limit in application.yml. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleTooLarge(RedirectAttributes redirect, HttpServletRequest request) {
        redirect.addFlashAttribute("error", "That file is too large. The limit is 25 MB per handout.");
        return "redirect:" + previousPageOrCourses(request);
    }

    /** Bad input (e.g. an empty course title): go back to the previous page and show why. */
    @ExceptionHandler(IllegalArgumentException.class)
    public String handleBadInput(IllegalArgumentException ex, RedirectAttributes redirect,
                                 HttpServletRequest request) {
        redirect.addFlashAttribute("error", ex.getMessage());
        return "redirect:" + previousPageOrCourses(request);
    }

    /** Anything else we didn't expect: log it and show the "something went wrong" page. */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ModelAndView handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return new ModelAndView("error/500");
    }

    /**
     * The page the student came from. Browsers send this in the "Referer" header.
     * If it's missing, we use the courses page.
     */
    private String previousPageOrCourses(HttpServletRequest request) {
        String previousPage = request.getHeader("Referer");
        if (previousPage != null) {
            return previousPage;
        }
        return "/courses";
    }
}
