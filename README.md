# AdaptiveEval — Adaptive Technical Interview Simulator & AI Resume Screener

A production-minded, full-stack AI technical interview and resume evaluation platform that evaluates not only whether candidate Java code passes test cases, but **why** a candidate succeeds or struggles across five core engineering competencies, alongside an evidence-based **100-Point AI Resume Screener**:

1. **Pattern Recognition (`PATTERN_RECOGNITION`)** — Selecting appropriate algorithmic paradigms and data structures.
2. **Implementation (`IMPLEMENTATION`)** — Translating logic into syntactically clean, edge-case-resilient Java code.
3. **Complexity Analysis (`COMPLEXITY_ANALYSIS`)** — Accurately deriving asymptotic time and auxiliary space complexity ($O(\cdot)$) and trade-offs.
4. **Debugging (`DEBUGGING`)** — Systematically isolating failing test cases, off-by-one errors, and state mutations.
5. **Communication & Explanation (`COMMUNICATION`)** — Articulating invariants, step-by-step walkthroughs, and assumptions clearly.

---

## Technology Stack

| Layer | Technologies |
| :--- | :--- |
| **Frontend (`frontend/`)** | React 18, TypeScript, Vite, Tailwind CSS (Semantic Dark/Light Theme Tokens), Monaco Editor (`@monaco-editor/react`), Recharts, React Router v6, Axios, Vitest + React Testing Library |
| **Backend (`backend/`)** | Java 21, Spring Boot 3.4.3, Spring Web, Spring Security (BCrypt + stateless JWT), Spring Data JPA (Hibernate), Apache PDFBox 2.0.32 & Apache POI 5.3.0 (PDF/DOCX extraction), Bean Validation, Flyway Migrations, SpringDoc OpenAPI, JUnit 5 + Mockito |
| **Database** | **MySQL 8.0** (relational persistence for users, theme preferences, skill profiles, interview sessions, questions, sample/hidden test cases, code attempts, assessments, diagnostics, messages, practice recommendations, and `resume_analyses`) |
| **Isolated Code Runner (`runner-service/`)** | Java 21, Spring Boot 3.4.3 microservice on port `8085` with AST/regex static security validation, per-test timeouts, JVM heap caps, process-tree termination, and Docker container isolation (`DOCKER_CONTAINER`) or restricted local process sandbox (`RESTRICTED_LOCAL_SANDBOX`) |

---

## Key Features & Enhancements

### 1. Centralized Live LLM Integration & Diagnostics
- **OpenAI-Compatible Provider Client (`AiProviderClient`)**: Supports Google Gemini (`https://generativelanguage.googleapis.com/v1beta/openai`), OpenAI (`https://api.openai.com/v1`), Groq (`https://api.groq.com/openai/v1`), and local Ollama (`http://localhost:11434/v1`).
- **Distinct Configuration vs. Connection Health States**:
  - `NOT_CONFIGURED`: Missing or placeholder `AI_API_KEY`.
  - `CONFIGURED_UNVERIFIED`: Credentials are present, awaiting verification.
  - `CONNECTED`: Verified live LLM response succeeded (`LIVE_LLM`).
  - `AUTH_ERROR`, `QUOTA_OR_BILLING_ERROR`, `MODEL_OR_ENDPOINT_ERROR`, `RATE_LIMITED`, `TIMEOUT`, `PROVIDER_ERROR`: Granular diagnostic error categories with actionable troubleshooting guidance.
- **Interactive Connection Test**: Click **Test AI Connection** on the **Profile & Settings** page (`POST /api/system/ai/test`) to perform a live provider verification without exposing secrets to the browser.

### 2. Complete Dark & Light Theme System
- **Semantic Theme Architecture**: Powered by CSS variables (`--bg-page`, `--bg-sidebar`, `--bg-card`, `--text-primary`, etc.) and `ThemeContext`.
- **Full Component & Editor Adaptation**: Switches immediately across the sidebar, cards, modals, Recharts axes/tooltips, and Monaco Editor (`vs-dark` ↔ `light`) without losing code or cursor position.
- **Persistent Preference**: Saved to `localStorage` (`adaptiveeval_theme_preference`) for immediate zero-flicker boot and synchronized to MySQL (`users.theme_preference` via `PATCH /api/users/me/theme`) for authenticated users.

### 3. AI-Powered Resume Screener & Persistent Screening History
- **PDF & DOCX Extraction**: Extracts text from `.pdf` (Apache PDFBox) and `.docx` (Apache POI) resumes up to 5 MB with magic-byte signature verification.
- **100-Point Evidence-Based Rubric**:
  - Required Technical Skills (`30 pts`)
  - Relevant Experience (`25 pts`)
  - Projects & Technical Accomplishments (`20 pts`)
  - Role Qualifications (`15 pts`)
  - Communication & Evidence of Impact (`10 pts`)
- **Truthfulness & Fairness Guardrails**: Never fabricates scores when the AI provider is unconfigured or fails; treats resume content as untrusted data (prompt-injection defense); ignores protected personal attributes.
- **Persistent Screening History**: Every screening attempt is stored in MySQL (`resume_analyses` table). Users can search, filter by status/score range, sort, reopen full saved reports (`GET /api/resume-analyses/{id}`) without re-calling the LLM, or delete records (`DELETE /api/resume-analyses/{id}`).

---

## Environment Variables & AI Configuration

Copy `.env.example` to `.env` in the project root (`.env` is automatically loaded by both `start-all.ps1` and Spring Boot's `optional:file:.env[.properties]` import):

```dotenv
# Database Configuration
DB_HOST=127.0.0.1
DB_PORT=3006
DB_NAME=interview_simulator
DB_USER=interview_app
DB_PASSWORD=interview_secret_2026

# Isolated Runner Microservice
RUNNER_SERVICE_URL=http://127.0.0.1:8085

# AI Provider Configuration (Gemini, OpenAI, Groq, or Ollama)
AI_PROVIDER=gemini
AI_API_KEY=your_real_api_key_here
AI_BASE_URL=https://generativelanguage.googleapis.com/v1beta/openai
AI_MODEL=gemini-2.5-flash
AI_TIMEOUT_MS=20000
```

---

## Starting Services Locally

### Option A: One-Command Launch (`start-all.ps1`)
From `d:\projects\interview-simulator`, run:
```powershell
.\start-all.ps1
```
This script loads `.env`, configures JDK 21 and Maven 3.9.9, and launches `runner-service` (`:8085`), `backend` (`:8080`), and `frontend` (`:5173`). To stop all services, run `.\stop-all.ps1`.

### Option B: Starting Each Service Individually
1. **Isolated Code Execution Service (`runner-service` on port `8085`)**:
   ```powershell
   cd d:\projects\interview-simulator\runner-service
   .\mvnw.cmd spring-boot:run
   ```
2. **Spring Boot Backend (`backend` on port `8080`)**:
   Flyway automatically applies `V1__init_schema.sql`, `V2__seed_questions.sql`, and `V3__theme_and_resume_analyses.sql`.
   ```powershell
   cd d:\projects\interview-simulator\backend
   .\mvnw.cmd spring-boot:run
   ```
3. **React + Vite Frontend (`frontend` on port `5173`)**:
   ```powershell
   cd d:\projects\interview-simulator\frontend
   npm run dev
   ```

---

## Running Automated Tests

1. **Backend Unit & Full-Lifecycle Integration Tests**:
   ```powershell
   cd d:\projects\interview-simulator\backend
   .\mvnw.cmd test
   ```
2. **Frontend Component & Route Tests (Vitest + React Testing Library)**:
   ```powershell
   cd d:\projects\interview-simulator\frontend
   npm test -- --run
   ```
3. **Frontend Production Build Verification**:
   ```powershell
   cd d:\projects\interview-simulator\frontend
   npm run build
   ```
