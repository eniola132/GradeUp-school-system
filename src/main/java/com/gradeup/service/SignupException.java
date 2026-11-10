package com.gradeup.service;

/**
 * Thrown when someone fills in the signup form incorrectly
 * (for example: email already used, or the two passwords don't match).
 *
 * Besides the message, it remembers WHICH form field was wrong, so the signup page
 * can highlight that exact field in red.
 */
public class SignupException extends RuntimeException {

    /** The name of the form field that has the problem, e.g. "email" or "password". */
    private final String field;

    public SignupException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
