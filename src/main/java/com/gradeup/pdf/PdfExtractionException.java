package com.gradeup.pdf;

/**
 * Thrown when we can't read text out of an uploaded PDF.
 *
 * Common reasons: the PDF is password-protected, the file is broken,
 * or it's a scanned document (just pictures of pages, with no real text inside).
 *
 * The message is written for students, so it can be shown on screen as-is.
 */
public class PdfExtractionException extends RuntimeException {

    public PdfExtractionException(String message) {
        super(message);
    }

    /** Same as above, but also keeps the original error ("cause") for the logs. */
    public PdfExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
