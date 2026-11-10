package com.gradeup.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Reads the text out of a PDF file, using a library called PDFBox.
 *
 * If something goes wrong we throw a PdfExtractionException with a message a student can
 * understand. The problems students usually hit are:
 *   - the PDF has a password,
 *   - the file is broken or isn't really a PDF,
 *   - the PDF is a scan (just pictures of pages, so there's no text to read).
 */
@Component
public class PdfTextExtractor {

    private static final Logger log = LoggerFactory.getLogger(PdfTextExtractor.class);

    private static final String PASSWORD_MESSAGE =
            "This PDF is password-protected. Remove the password and upload it again.";

    /**
     * What we got out of the PDF. (A "record" is a small class that just holds data.)
     *
     * @param text      all the text in the PDF
     * @param pageCount how many pages it has
     */
    public record Extracted(String text, int pageCount) { }

    /** Opens the PDF at the given path and returns its text. */
    public Extracted extract(Path pdfPath) {
        // "try (...)" makes sure the PDF file is closed again when we're done, even after an error.
        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {

            if (document.isEncrypted()) {
                throw new PdfExtractionException(PASSWORD_MESSAGE);
            }

            int pageCount = document.getNumberOfPages();
            if (pageCount == 0) {
                throw new PdfExtractionException("This PDF has no pages.");
            }

            // Set up the PDFBox text reader.
            PDFTextStripper textReader = new PDFTextStripper();
            textReader.setSortByPosition(true);    // read text top-to-bottom, left-to-right
            textReader.setLineSeparator("\n");
            textReader.setParagraphEnd("\n\n");

            String rawText = textReader.getText(document);
            String text = cleanUp(rawText);

            if (text.isBlank()) {
                throw new PdfExtractionException(
                        "No text could be read from this PDF. It looks like a scanned or "
                        + "image-only document — GradeUp needs a text-based PDF.");
            }

            log.debug("Extracted {} characters from {} pages", text.length(), pageCount);
            return new Extracted(text, pageCount);

        } catch (InvalidPasswordException ex) {
            throw new PdfExtractionException(PASSWORD_MESSAGE, ex);
        } catch (PdfExtractionException ex) {
            throw ex;   // already has a friendly message, so pass it on unchanged
        } catch (IOException ex) {
            throw new PdfExtractionException(
                    "This file could not be read as a PDF. It may be corrupted or not a real PDF.", ex);
        }
    }

    /** Fixes the messy bits that text pulled out of a PDF usually has. */
    private String cleanUp(String raw) {
        if (raw == null) {
            return "";
        }

        String text = raw;

        // Replace "null" characters (invisible junk some PDFs contain) with spaces.
        text = text.replace('\u0000', ' ');

        // Use one kind of line break everywhere (Windows uses \r\n, old Macs used \r).
        text = text.replace("\r\n", "\n");
        text = text.replace("\r", "\n");

        // Join words that were split across two lines with a hyphen: "impor-\ntant" -> "important".
        text = text.replaceAll("(\\w)-\\n(\\w)", "$1$2");

        // Squash several spaces or tabs in a row into one space.
        text = text.replaceAll("[ \\t]{2,}", " ");

        // Squash 3 or more line breaks into 2 (keeps paragraphs, removes big gaps).
        text = text.replaceAll("\\n{3,}", "\n\n");

        return text.trim();
    }
}
