# GradeUp

**Turn dense course handouts into simple key points — then practise until you pass.**

GradeUp is a study assistant for tertiary students. Upload a course handout as a PDF,
and GradeUp distils it into ranked key points and generates multiple-choice practice
questions from several angles. Then quiz yourself and track your progress.

---

## The problem it solves

University handouts are voluminous and complex. Re-reading a 60-page PDF is a slow way
to find the handful of ideas that actually matter. GradeUp does two things:

1. **Distils** the handout into short, ranked, plain-language key points.
2. **Tests** you on it with generated questions, so you find gaps before the exam does.

---

## Features

| Feature | What it does |
|---|---|
| **Login / Signup** | Email + BCrypt-hashed password. Every user's data is fully isolated. |
| **Upload PDF Handouts** | Drag-and-drop upload with validation. Text is extracted immediately. |
| **Summarize to Key Points** | Ranked key points with importance badges ("Must know" → "Background"). |
| **Generate Practice Questions** | Multiple-choice questions from varied angles (definition, application, comparison, recall), each with an explanation. |
| **Practice / Quiz Mode** | Configurable quizzes, optional instant feedback, scored results with a full per-question review, and retakes. |
| **My Courses Library** | Organise handouts by course, search your library, and revisit any question bank. |

---

## Tech stack

- **Java 17**
- **Spring Boot 3.5.7** — Web, Security, Data JPA, Validation, Thymeleaf
- **Thymeleaf** — server-rendered UI (one app, one command to run)
- **H2** — default dev/test database (zero setup)
- **MySQL 8** — production profile
- **Apache PDFBox 3** — PDF text extraction
- **Groq** (OpenAI-compatible API) — AI summarization & question generation, with a
  built-in offline engine as a fallback
- **Maven** via the committed `./mvnw` wrapper

---

## Quick start

No database, no API key, and no configuration are required.

```bash
./mvnw spring-boot:run
```

Then open **http://localhost:8081**, create an account, and upload a PDF handout.

> Port **8081** is the default because 8080 is often already taken. Override it with
> `SERVER_PORT=9000 ./mvnw spring-boot:run`.

### Everyday commands

```bash
./mvnw spring-boot:run                 # run the app (H2, offline AI engine)
./mvnw test                            # run the full test suite (51 tests, no network needed)
./mvnw clean package                   # build a runnable jar into target/
java -jar target/gradeup-1.0.0.jar     # run the built jar
```

---

## Enabling real AI (Groq)

GradeUp works fully without an API key using its built-in **offline study engine**
(deterministic extractive summarization). To use Groq instead, export a key:

```bash
export GROQ_API_KEY="gsk_your_key_here"
./mvnw spring-boot:run
```

Optionally override the model or endpoint:

```bash
export GROQ_MODEL="llama-3.1-8b-instant"
export GROQ_BASE_URL="https://api.groq.com/openai/v1"
```

Get a free key at [console.groq.com](https://console.groq.com).

**The API key is optional by design.** If it is missing, invalid, or Groq is
unreachable, GradeUp transparently falls back to the offline engine — an upload never
fails because of an AI outage. The dashboard shows which engine is active.

---

## Running against MySQL (production)

The `prod` profile switches the datasource to MySQL:

```bash
export DB_URL="jdbc:mysql://localhost:3306/gradeup?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
export DB_USER="gradeup"
export DB_PASSWORD="your-password"

./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

Credentials are read **only** from environment variables — nothing sensitive is committed.

---

## Configuration reference

| Variable | Default | Purpose |
|---|---|---|
| `SERVER_PORT` | `8081` | HTTP port |
| `GROQ_API_KEY` | *(empty)* | Enables the Groq AI backend |
| `GROQ_MODEL` | `llama-3.3-70b-versatile` | Model used for generation |
| `DB_URL` | local MySQL | Prod datasource URL |
| `DB_USER` / `DB_PASSWORD` | `gradeup` / *(empty)* | Prod credentials |

Non-secret settings live in `src/main/resources/application.yml`
(upload size limit, storage location, AI input budget, timeouts).

---

## Project structure

```
src/main/java/com/gradeup/
├── GradeUpApplication.java     entry point
├── config/                     security, AI wiring, typed properties
├── domain/                     JPA entities (User, Course, Handout, KeyPoint, Question, QuizAttempt…)
├── repository/                 Spring Data JPA repositories
├── service/                    business logic; enforces per-user ownership
├── ai/                         AiClient seam → GroqAiClient + OfflineAiClient
├── pdf/                        PDFBox text extraction
└── web/                        controllers + global exception handling

src/main/resources/
├── templates/                  Thymeleaf pages (landing, auth, dashboard, courses, handouts, quiz)
└── static/css/app.css          the whole design system (tokens, dark mode, components)
```

### The one architectural seam that matters

```java
public interface AiClient {
    List<AiKeyPoint> summarize(String handoutText, int maxPoints);
    List<AiQuestion> generateQuestions(String handoutText, int count);
    boolean isAvailable();
    String describe();
}
```

`AiClient` isolates the product from a third-party LLM. It means AI features are testable
offline, the app demos with no API key, and swapping providers is a one-class change.
`OfflineAiClient` is a **real** implementation (term-frequency sentence scoring plus
definition/cloze question templating), not a stub.

---

## Security notes

This app stores user uploads, so the following are deliberate:

- **Passwords** are BCrypt-hashed, never stored or logged in plaintext.
- **Ownership scoping** is enforced in the service and repository layers — every lookup is
  filtered by the authenticated user, and an unowned resource returns `404`, not `403`,
  so the existence of another user's data is never revealed.
- **Upload safety**: strict PDF magic-byte check, extension and size validation, and the
  client filename is *never* used as a path — files are stored under a generated UUID name
  and re-checked to be inside the storage root.
- **CSRF** protection is enabled on all form posts.
- **Files** are stored outside the web root and served only through ownership-checked routes.

---

## Testing

```bash
./mvnw test
```

51 tests, all passing, and the suite is **fully offline** — it needs no API key and no
network (`./mvnw -o test` passes too).

Coverage includes:

- **AI engine** — output validity, determinism, empty/garbage input, and a regression test
  ensuring a cloze question blanks only one word.
- **Quiz scoring** — all-correct, all-wrong, unanswered questions counted as skipped
  rather than dropped, invalid options, and case-insensitive answers.
- **Ownership isolation** — a second user cannot read, update, or delete another's course;
  cannot load another's questions; and cannot read another's quiz attempt.
- **Upload safety** — non-PDF content rejected despite a `.pdf` name, empty files,
  oversized files, and path-traversal filenames that must not escape the storage root.
- **Web layer** — anonymous redirects, public pages, CSRF rejection, and signup validation.

---

## Notes and limitations

- **Scanned PDFs aren't supported.** GradeUp needs a text layer; image-only handouts return
  a clear message explaining this rather than failing silently.
- **MySQL is configured but not provisioned here.** The `prod` profile is complete; it
  activates as soon as credentials exist.
- **Schema management** uses Hibernate `ddl-auto: update`. For a production deployment with
  strict change control, add Flyway or Liquibase migrations.
- **This is a study aid, not a substitute for the course.** Generated questions are only as
  good as the handout they came from.
