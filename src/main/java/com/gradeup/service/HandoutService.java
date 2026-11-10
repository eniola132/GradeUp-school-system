package com.gradeup.service;

import com.gradeup.ai.AiClient;
import com.gradeup.ai.AiKeyPoint;
import com.gradeup.ai.AiQuestion;
import com.gradeup.ai.OfflineAiClient;
import com.gradeup.domain.Course;
import com.gradeup.domain.Handout;
import com.gradeup.domain.KeyPoint;
import com.gradeup.domain.Question;
import com.gradeup.domain.User;
import com.gradeup.pdf.PdfExtractionException;
import com.gradeup.pdf.PdfTextExtractor;
import com.gradeup.repository.HandoutRepository;
import com.gradeup.repository.KeyPointRepository;
import com.gradeup.repository.QuestionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Everything that happens to a handout, from upload onwards.
 *
 * THE UPLOAD STEPS
 *   1. Save the PDF to disk                 (StorageService)
 *   2. Read the text out of the PDF         (PdfTextExtractor)
 *   3. Turn the text into key points        (AiClient.summarize)
 *   4. Turn the text into practice questions (AiClient.generateQuestions)
 *
 * If step 3 or 4 fails, the upload still counts: the PDF and its text are already saved,
 * so the student can press "Regenerate" later.
 *
 * (See UserService for what @Service and @Transactional mean.)
 */
@Service
public class HandoutService {

    private static final Logger log = LoggerFactory.getLogger(HandoutService.class);

    /** How many key points and questions we make straight after an upload. */
    private static final int KEY_POINTS_AFTER_UPLOAD = 8;
    private static final int QUESTIONS_AFTER_UPLOAD = 8;

    private final HandoutRepository handoutRepository;
    private final KeyPointRepository keyPointRepository;
    private final QuestionRepository questionRepository;
    private final StorageService storageService;
    private final PdfTextExtractor pdfTextExtractor;
    private final AiClient aiClient;
    private final CourseService courseService;

    /** Spring passes all of these in for us when it creates the HandoutService. */
    public HandoutService(HandoutRepository handoutRepository,
                          KeyPointRepository keyPointRepository,
                          QuestionRepository questionRepository,
                          StorageService storageService,
                          PdfTextExtractor pdfTextExtractor,
                          AiClient aiClient,
                          CourseService courseService) {
        this.handoutRepository = handoutRepository;
        this.keyPointRepository = keyPointRepository;
        this.questionRepository = questionRepository;
        this.storageService = storageService;
        this.pdfTextExtractor = pdfTextExtractor;
        this.aiClient = aiClient;
        this.courseService = courseService;
    }

    /**
     * Uploads a PDF into a course and makes key points and questions from it.
     *
     * @return the saved handout. Check its status: FAILED means we couldn't read the PDF.
     */
    @Transactional
    public Handout upload(User owner, Long courseId, MultipartFile file) {
        // Make sure the course exists and belongs to this student.
        Course course = courseService.getOwned(courseId, owner);

        // Step 1: save the file to disk, and create the handout row in the database.
        StorageService.Stored stored = storageService.storePdf(file, owner.getId());
        Handout handout = new Handout(course, stored.originalFilename(), stored.relativePath(), stored.sizeBytes());
        handout = handoutRepository.save(handout);

        // Step 2: read the text out of the PDF.
        Path pdfOnDisk = storageService.resolve(stored.relativePath());
        try {
            PdfTextExtractor.Extracted extracted = pdfTextExtractor.extract(pdfOnDisk);
            handout.setExtractedText(extracted.text());
            handout.setPageCount(extracted.pageCount());
            handout.setStatus(Handout.Status.EXTRACTED);
        } catch (PdfExtractionException ex) {
            // We couldn't read it (scanned, password-protected, broken...). Remember why and stop.
            handout.setStatus(Handout.Status.FAILED);
            handout.setFailureReason(ex.getMessage());
            log.warn("Extraction failed for handout {}: {}", handout.getId(), ex.getMessage());
            return handoutRepository.save(handout);
        }
        handout = handoutRepository.save(handout);

        // Steps 3 and 4: make key points and questions.
        try {
            makeKeyPoints(handout, KEY_POINTS_AFTER_UPLOAD);
            addQuestions(handout, QUESTIONS_AFTER_UPLOAD);
            handout.setStatus(Handout.Status.SUMMARIZED);
        } catch (Exception ex) {
            // The upload itself worked, so we keep the handout and its text.
            log.error("Study material generation failed for handout {}", handout.getId(), ex);
        }

        return handoutRepository.save(handout);
    }

    /** All handouts in a course. */
    @Transactional(readOnly = true)
    public List<Handout> listFor(Course course) {
        return handoutRepository.findByCourseWithDetails(course);
    }

    /**
     * Loads one handout, but ONLY if it's in a course owned by this user.
     * Otherwise the student sees the same "doesn't exist" page either way, so they
     * can't tell whether another student's handout exists.
     */
    @Transactional(readOnly = true)
    public Handout getOwned(Long handoutId, User owner) {
        Optional<Handout> found = handoutRepository.findByIdWithDetails(handoutId);
        if (found.isEmpty()) {
            throw new NotFoundException("That handout doesn't exist.");
        }

        Handout handout = found.get();
        Long ownerOfHandout = handout.getCourse().getOwner().getId();
        if (!ownerOfHandout.equals(owner.getId())) {
            throw new NotFoundException("That handout doesn't exist.");
        }
        return handout;
    }

    /** Makes a fresh set of key points for a handout, replacing the old ones. */
    @Transactional
    public Handout summarize(Long handoutId, User owner, int maxPoints) {
        Handout handout = getOwned(handoutId, owner);
        if (!handout.hasText()) {
            throw new IllegalStateException("This handout has no readable text to summarise.");
        }

        makeKeyPoints(handout, maxPoints);
        handout.setStatus(Handout.Status.SUMMARIZED);
        return handoutRepository.save(handout);
    }

    /** Makes extra practice questions for a handout. The existing questions are kept. */
    @Transactional
    public Handout generateQuestions(Long handoutId, User owner, int count) {
        Handout handout = getOwned(handoutId, owner);
        if (!handout.hasText()) {
            throw new IllegalStateException("This handout has no readable text to generate questions from.");
        }

        addQuestions(handout, count);
        return handoutRepository.save(handout);
    }

    /** Asks the AI for key points and puts them on the handout (removing any old ones). */
    private void makeKeyPoints(Handout handout, int maxPoints) {
        List<AiKeyPoint> points = aiClient.summarize(handout.getExtractedText(), maxPoints);

        // Remove the old key points.
        keyPointRepository.deleteByHandout(handout);
        handout.getKeyPoints().clear();

        // Add the new ones, numbering them 0, 1, 2... so they keep their order.
        int position = 0;
        for (AiKeyPoint point : points) {
            KeyPoint keyPoint = new KeyPoint(handout, point.text(), point.importance(), position);
            handout.getKeyPoints().add(keyPoint);
            position++;
        }

        log.info("Handout {}: {} key points via {}", handout.getId(), points.size(), aiClient.describe());
    }

    /** Asks the AI for questions and adds the ones we don't already have. */
    private void addQuestions(Handout handout, int count) {
        List<AiQuestion> generated = aiClient.generateQuestions(handout.getExtractedText(), count);
        if (generated.isEmpty()) {
            log.warn("Handout {}: no questions were generated", handout.getId());
            return;
        }

        // Collect the questions the handout already has, so we don't add the same one twice.
        List<String> existingPrompts = new ArrayList<>();
        for (Question existing : handout.getQuestions()) {
            existingPrompts.add(existing.getPrompt());
        }

        // Which engine wrote these questions? (Shown in the UI.)
        Question.Source source;
        if (aiClient instanceof OfflineAiClient) {
            source = Question.Source.OFFLINE;
        } else {
            source = Question.Source.AI;
        }

        List<Question> newQuestions = new ArrayList<>();
        for (AiQuestion generatedQuestion : generated) {
            if (existingPrompts.contains(generatedQuestion.prompt())) {
                continue;   // skip: we already have this question
            }

            Question question = new Question(handout, generatedQuestion.prompt(),
                    generatedQuestion.optionA(), generatedQuestion.optionB(),
                    generatedQuestion.optionC(), generatedQuestion.optionD(),
                    generatedQuestion.correctOption());
            question.setExplanation(generatedQuestion.explanation());
            question.setDifficulty(generatedQuestion.difficulty());
            question.setAngle(generatedQuestion.angle());
            question.setSource(source);
            newQuestions.add(question);
        }

        handout.getQuestions().addAll(newQuestions);
        log.info("Handout {}: {} new questions via {}", handout.getId(), newQuestions.size(), aiClient.describe());
    }

    /**
     * FOR TESTS ONLY: saves an empty handout without a real PDF or the AI.
     * Tests use it to build a handout with hand-written questions.
     */
    @Transactional
    public Handout attachForTesting(Course course, String filename) {
        Handout handout = new Handout(course, filename, "test/" + filename, 0L);
        handout.setStatus(Handout.Status.EXTRACTED);
        return handoutRepository.save(handout);
    }

    /** Deletes a handout from the database, and then its PDF from the disk. */
    @Transactional
    public void delete(Long handoutId, User owner) {
        Handout handout = getOwned(handoutId, owner);
        String storedPath = handout.getStoredPath();

        handoutRepository.delete(handout);
        // Only delete the file after the database delete worked.
        storageService.delete(storedPath);
    }

    /** How many handouts a course has. */
    @Transactional(readOnly = true)
    public long countFor(Course course) {
        return handoutRepository.countByCourse(course);
    }
}
