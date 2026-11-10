package com.gradeup.service;

/**
 * Thrown when something the user asked for doesn't exist (or isn't theirs).
 *
 * We use the same error for "doesn't exist" and "belongs to someone else" on purpose:
 * that way a user can't find out whether another student's course exists by guessing ids.
 *
 * GlobalExceptionHandler catches this and shows the friendly 404 page.
 *
 * It "extends RuntimeException", which means methods can throw it without having to
 * declare "throws NotFoundException" in their signature.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
