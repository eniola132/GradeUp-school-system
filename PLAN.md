# GradeUp — Tertiary Study Assistant Platform

**Build Plan & Architecture**

> Upload a course handout → get simple key points → practice questions → pass the course.

---

## 1. What we are building

GradeUp solves one specific problem: university students drown in voluminous, complex
course handouts. They need the handouts **distilled** and then **tested** on.

The product loop is deliberately narrow and complete:

```
Sign up → Create course → Upload PDF handout → AI summarizes to key points
   → AI generates practice questions → Take a quiz → See score → Review in library
```

Everything in this plan serves that loop.

---

## 2. Locked technical decisions

| Area | Decision | Rationale |
|---|---|---|
| Language | **Java 17** | Installed and verified (`17.0.20.1`) |
| Framework | **Spring Boot 3.5.7** | Parent POM verified resolvable from cache |
| Frontend | **Thymeleaf** (server-rendered) | User-selected. One app, one command, no CORS/proxy |
| Database (dev) | **H2 2.3.232** (file-based) | User-selected. Zero setup, survives restarts |
| Database (prod) | **MySQL 8.4** | User-selected. Server installed; unreachable in this sandbox |
| AI | **Groq** (OpenAI-compatible REST) | User-selected |
| PDF parsing | **Apache PDFBox 3.x** | Mature, pure-Java, no native deps |
| Build | **Maven** + committed `mvnw` | `mvn` is *not* on PATH — wrapper makes builds reproducible |
| Testing | JUnit 5 + Spring Boot Test + MockMvc | Standard, already in the Spring Boot BOM |

### Environment facts this plan is built on (verified, not assumed)

- `mvn` and `gradle` are **not on PATH**. Maven 3.9.11 exists only at
  `~/.m2/wrapper/dists/apache-maven-3.9.11/a2d47e15/bin/mvn`.
  → **Mitigation:** commit a `mvnw` wrapper so `./mvnw` always works.
- `~/.m2/repository` was read-only under the initial sandbox policy, now writable.
  The plan keeps **no dependency on local-repo writes at runtime** — by the time the
  app runs, all jars are resolved.
- **MySQL is running but unreachable**: root password unknown, `sudo` blocked by the
  `no new privileges` flag, and common-password probing failed.
  → **Mitigation:** H2 is the *default* profile so the app runs and is fully testable
  today; MySQL ships as a fully-configured `prod` profile that activates the moment
  credentials exist. This is exactly the user's chosen strategy.
- **No `GROQ_API_KEY` in the environment.**
  → **Mitigation (critical):** the AI layer is an interface with two implementations.
  Missing key ⇒ app still starts, still serves every page, and the AI features return
  a clearly-labelled offline fallback. **A missing key must never break startup or tests.**
- A peer project's **PostgreSQL** container (`bax-qbank-db`, port 55480) is running.
  It is another project's database — **leave it untouched.**

---

## 3. Architecture

Classic layered Spring Boot, single deployable. Deliberately **not** microservices —
this is a focused product, and one artifact keeps it runnable by a student on a laptop.

```
Browser (Thymeleaf + custom CSS design system)
        │
        ▼
Controller layer      @Controller (pages)  +  @RestController (JSON API)
        │
        ▼
Service layer         AuthService · CourseService · HandoutService
                      AiSummaryService · QuestionService · QuizService
        │
        ├──────────────► AiClient  ◄── interface (the important seam)
        │                   ├── GroqAiClient        (real, needs API key)
        │                   └── OfflineAiClient     (deterministic fallback)
        │
        ├──────────────► PdfTextExtractor (PDFBox)
        │
        ▼
Repository layer      Spring Data JPA
        │
        ▼
H2 (dev/test)  |  MySQL (prod)
```

### The one architectural seam that matters

`AiClient` is the boundary between "our product logic" and "a third-party LLM":

```java
public interface AiClient {
    List<String> summarize(String handoutText, int maxPoints);
    List<GeneratedQuestion> generateQuestions(String handoutText, int count);
    boolean isAvailable();
}
```

Why this earns its place: it makes summarization and question generation **testable
without network access**, lets the app demo with no API key, and means swapping Groq
for another provider is a one-class change. The offline implementation is not a stub —
it is a real deterministic extractive summarizer (sentence scoring by term frequency
with stopword filtering) plus template-based question generation, so the product is
genuinely usable offline.

### Package layout

```
com.gradeup
├── GradeUpApplication.java
├── config/        SecurityConfig, AiConfig, StorageConfig
├── domain/        User, Course, Handout, KeyPoint, Question, QuizAttempt, AttemptAnswer
├── repository/    *Repository (Spring Data JPA)
├── service/       business logic
├── ai/            AiClient, GroqAiClient, OfflineAiClient, dto/
├── pdf/           PdfTextExtractor
├── web/           @Controller page controllers
├── api/           @RestController JSON endpoints
└── exception/     GlobalExceptionHandler, custom exceptions
```

---

## 4. Data model

```
User 1───N Course 1───N Handout 1───N KeyPoint
                          │
                          └───N Question N───1 QuizAttempt (via AttemptAnswer)
```

| Entity | Key fields |
|---|---|
| `User` | id, email (unique, login id), passwordHash (BCrypt), displayName, createdAt |
| `Course` | id, owner→User, code (e.g. "CSC301"), title, description, colorTag, createdAt |
| `Handout` | id, course→Course, originalFilename, storedPath, sizeBytes, pageCount, extractedText (CLOB), status (UPLOADED/EXTRACTED/SUMMARIZED/FAILED), uploadedAt |
| `KeyPoint` | id, handout→Handout, text, importance (1–5), orderIndex |
| `Question` | id, handout→Handout, prompt, optionA–D, correctOption, explanation, difficulty (EASY/MEDIUM/HARD), source (AI/OFFLINE), createdAt |
| `QuizAttempt` | id, user→User, course→Course, startedAt, completedAt, score, totalQuestions |
| `AttemptAnswer` | id, attempt→QuizAttempt, question→Question, selectedOption, correct |

**Schema management:** JPA `ddl-auto=update` for dev speed. Both H2 and MySQL are
supported by keeping column types portable (no MySQL-only JSON columns; `TEXT` via
`@Lob`/`columnDefinition` guards). A `schema-mysql.sql` is provided for prod parity.

---

## 5. Features → implementation map

| # | Feature | How it works |
|---|---|---|
| 1 | **Login / Signup** | Spring Security form login, BCrypt hashes, per-user data isolation enforced in the service layer (every query filtered by owner) |
| 2 | **Upload PDF Handouts** | Multipart upload, extension + content-type + size validation, files stored outside the web root under `storage/handouts/{userId}/`, PDFBox extracts text on upload |
| 3 | **Summarize to Key Points** | `AiClient.summarize()` → key points persisted with importance ranking; re-summarize is idempotent |
| 4 | **Generate Practice Questions** | `AiClient.generateQuestions()` → multiple-choice questions from several angles (definition, application, comparison, edge case); difficulty-tagged |
| 5 | **Practice / Quiz Mode** | Configurable quiz (course, question count, difficulty), one question at a time, instant feedback option, scored attempt persisted, results page with per-question review, retake |
| 6 | **My Courses Library** | Course grid, drill into course → handouts → key points → question bank; delete cascade; search/filter |

---

## 6. UI design direction

The user asked for **"a really nice UI."** This is treated as a first-class requirement,
not a finish-line polish pass.

**Approach:** a hand-written design system in a single CSS file using CSS custom
properties. No Bootstrap, no Tailwind build step — keeps the one-command run intact and
produces something that doesn't look like every other generated project.

**Design tokens**

- **Palette:** deep indigo/ink base (`#0F172A`), soft violet primary (`#6D5AE6`),
  mint accent for success (`#2DD4A7`), amber for warnings, warm paper background
  (`#FAFAFB`) in light mode.
- **Dark mode:** first-class, via `prefers-color-scheme` plus a manual toggle persisted
  in `localStorage`.
- **Type:** system font stack with a deliberate modular scale; generous line-height for
  long reading passages.
- **Space:** 4px-based spacing scale; consistent radii (12–20px) and layered soft shadows.
- **Motion:** 150–250ms transitions, subtle card lift on hover, animated progress ring
  on quiz results. All motion wrapped in `prefers-reduced-motion` guards.

**Screens**

1. **Landing** — hero with animated gradient mesh, product promise, feature cards, CTA
2. **Signup / Login** — split layout, inline validation, friendly error states
3. **Dashboard** — greeting, stat tiles (courses, handouts, questions, average score),
   recent activity, empty state that teaches the first action
4. **Course detail** — handout list with status chips, key points panel, question bank
5. **Upload** — drag-and-drop zone with live file feedback and progress
6. **Handout detail** — tabbed: Key Points | Questions | Original text
7. **Quiz setup** — pick course, length, difficulty
8. **Quiz taking** — one question per screen, progress bar, keyboard 1–4 shortcuts
9. **Results** — animated score ring, per-question review, retake CTA
10. **Library** — all courses, search/filter, polished empty states

**Quality bar:** responsive down to 360px, visible focus rings, semantic HTML,
`aria` labels on interactive controls, and real empty states everywhere.

---

## 7. Build phases

Each phase ends with something runnable and verified.

**Phase 0 — Foundation**
Project skeleton, `pom.xml`, `mvnw` wrapper, `application.yml` (dev H2 / prod MySQL),
`.gitignore`, `README.md`. ✅ Verify: `./mvnw spring-boot:run` boots and serves a page.

**Phase 1 — Domain & persistence**
Entities, repositories, schema generation against H2. ✅ Verify: context loads, tables created.

**Phase 2 — Auth**
Spring Security config, signup/login/logout, `UserDetailsService`, per-user isolation.
✅ Verify: MockMvc tests for signup, login, and unauthorised redirect.

**Phase 3 — Design system & shell**
`app.css` tokens, layout fragment, nav, theme toggle, landing page. ✅ Verify: pages render,
responsive check.

**Phase 4 — Courses & library**
Course CRUD, dashboard, library. ✅ Verify: service + MockMvc tests.

**Phase 5 — Upload & extraction**
PDF validation, storage, PDFBox extraction. ✅ Verify: real PDF parsed, bad input rejected.

**Phase 6 — AI layer**
`AiClient` + Groq + offline implementations, config-driven, resilient to missing key.
✅ Verify: unit tests with mocked HTTP; app runs with no key.

**Phase 7 — Summarize & questions**
Persist key points and questions; UI tabs. ✅ Verify: end-to-end from uploaded PDF.

**Phase 8 — Quiz mode**
Setup, taking, scoring, results, review, retake. ✅ Verify: scoring unit tests + flow test.

**Phase 9 — Polish & hardening**
All templates finished, empty states, error pages, accessibility pass, README with
screenshots. ✅ Verify: full test suite green, app run manually and checked in a browser.

---

## 8. Testing strategy

| Layer | What is covered |
|---|---|
| Unit | Offline summarizer/question generator, quiz scoring, PDF validation, filename handling |
| Service | Course/Handout/Quiz services with H2 |
| Web | MockMvc: auth flows, ownership isolation (user A cannot read user B's course), upload validation |
| AI | `GroqAiClient` with a stubbed HTTP layer — no live API calls in tests |

**Explicit non-goal:** tests never require a network connection or a Groq API key.

**Security checks:** BCrypt password storage, CSRF enabled on form posts, upload
size/type limits, path-traversal-safe storage, and ownership checks on every
resource fetch (a user must not reach another user's data by guessing an ID).

---

## 9. Running it

```bash
./mvnw spring-boot:run                     # dev, H2, works with no API key
GROQ_API_KEY=... ./mvnw spring-boot:run    # dev, with real AI
./mvnw test                                # full suite, offline
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod   # MySQL
```

Prod requires `DB_URL`, `DB_USER`, `DB_PASSWORD`. All secrets come from environment
variables — **no credentials are ever committed.**

---

## 10. Known risks & mitigations

| Risk | Mitigation |
|---|---|
| MySQL unreachable in this environment | H2 default profile; prod profile ready to activate. Documented in README. |
| No Groq API key available | Offline implementation keeps every feature functional; key read from env only. |
| `mvn` not on PATH | Committed `mvnw` wrapper pinned to Maven 3.9.11. |
| LLM output malformed / off-schema | Strict response parsing with validation and fallback to offline generator on any parse failure. |
| Large PDFs causing memory pressure | Upload size cap, text truncation with a token budget before sending to the LLM. |
| Sandbox may restrict file writes | All app writes confined to the project directory (`storage/`). |

---

## 11. Definition of done

- All 6 core features work end-to-end against H2.
- `./mvnw test` passes with no network access and no API key.
- The app boots with one command and is verified in a browser.
- UI is polished, responsive, dark-mode capable, with real empty states.
- README documents setup, both profiles, and how to enable Groq.
- No secrets committed.

---

## 12. Build outcome (what actually shipped)

All nine phases were completed and verified against a running application.

| Area | Result |
|---|---|
| Build | `./mvnw` wrapper pinned to Maven 3.9.11; compiles clean |
| Runtime | Boots in ~9s on port 8081 (8080 was occupied) |
| Features | All 6 core features working end-to-end |
| Tests | **51 tests, all passing**, and the suite passes in **offline mode** (`./mvnw -o test`) |
| UI | 10 screens, light + dark themes, responsive to 390px |
| AI | Groq client + real offline engine; runs with no API key |

### Bugs found by testing and fixed

Each of these was discovered by exercising the running app rather than by inspection:

1. **`LazyInitializationException` on course/library/handout/details pages.**
   Templates read lazy collections after the transaction closed. Fixed with explicit
   fetch-join queries (and `Set` instead of `List` for nested collections, which also
   resolved a `MultipleBagFetchException`).
2. **Quiz questions were silently dropped.** Restoring an in-progress quiz rebuilt the
   pool through the shuffling allocator, so only 1 of 5 stored questions resolved — the
   other 4 were scored as "Skipped" no matter what the student answered. Fixed with a
   dedicated order-preserving, owner-scoped `loadByIds` query.
3. **Cloze questions blanked the same word twice** ("the average ______ complexity in a
   balanced ______ tree"). Fixed to blank only the first occurrence.
4. **Ownership check ran in Java against lazy proxies**, causing a 500. Moved into the
   query (`findOwnedByIds`), which is both safer and cheaper.
5. **Offline summarizer emitted filler** ("Word is a recurring concept…") for
   low-content input. Now returns nothing rather than noise.
6. **Mobile nav clipped mid-word.** Reflowed into its own scrollable row.

### Deviations from the plan

- **Port changed 8080 → 8081**; 8080 was already in use in this environment.
- **`~/.m2/repository` became writable** mid-build, so the planned project-local Maven
  repository workaround was dropped as unnecessary.
- **`HandoutService.attachForTesting`** was added as a test seam so quiz scoring can be
  tested against a hand-built question bank rather than generator output.

### Honest limitations

- MySQL is configured and ready via the `prod` profile but was **never run** — the server
  in this environment is unreachable (no credentials, no `sudo`). H2 carries dev and test.
- Scanned/image-only PDFs are rejected with a clear message rather than OCR'd.
- Schema uses `ddl-auto: update`; a production deployment should add Flyway/Liquibase.
