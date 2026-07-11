# AI-Powered Code Review System

[![CI](https://github.com/Legion-1315/ai-code-review/actions/workflows/ci.yml/badge.svg)](https://github.com/Legion-1315/ai-code-review/actions/workflows/ci.yml)

🔗 **Live demo:** https://ai-code-review-uzuo.onrender.com · **Source:** https://github.com/Legion-1315/ai-code-review

> ⏳ Hosted on Render's free tier, which sleeps after 15 min idle — the first
> request may take ~50 s to wake, then it's fast.

Automatically reviews pull requests with AI: a developer pushes code (or pastes a diff) →
the system analyzes it with Claude → it returns inline findings, a quality score, and a
summary. Findings are categorized (security, code quality, performance, best practice, test
coverage) and tracked over time on a dashboard.

> Built to run **out of the box with zero external credentials**. Without an `ANTHROPIC_API_KEY`
> the review engine falls back to a deterministic heuristic reviewer, so the whole app is
> demoable end-to-end. Set the key to switch to full Claude-powered review.

---

## Tech Stack

**Backend** — Java 21, Spring Boot 3.4, Spring Security (JWT), Spring Data JPA,
Anthropic Java SDK (Claude `claude-opus-4-8`), async processing, H2 (default) / PostgreSQL.

**Frontend** — React 18 + TypeScript, Vite, Tailwind CSS, TanStack Query, React Router,
Recharts, Axios.

---

## Architecture

```
                Submit diff / GitHub webhook (HMAC-verified)
                                │
                                ▼
   Spring Boot API  ──►  Review created (PENDING)  ──►  returns immediately
                                │  (after commit)
                                ▼  @Async thread pool
                       AiReviewService
                       ├─ ANTHROPIC_API_KEY set → Claude (adaptive thinking, JSON output)
                       └─ otherwise             → deterministic heuristic engine
                                │
                                ▼
                 Findings + score persisted (H2 / PostgreSQL)
                                │
                                ▼
        React dashboard polls and renders score, issues, diff viewer
```

Key design points worth discussing in an interview:
- **Async pipeline** — reviews run on a dedicated `ThreadPoolTaskExecutor`; the submitting
  request returns `202 Accepted` immediately and the UI polls for completion.
- **After-commit scheduling** — the async job is fired from a transaction-synchronization
  callback, guaranteeing the worker thread sees the committed row.
- **Webhook security** — GitHub deliveries are verified with HMAC-SHA256 over the raw body.
- **Structured AI output** — the model is prompted for strict JSON, parsed defensively
  (markdown-fence stripping, enum fallbacks).
- **Graceful degradation** — any Claude failure transparently falls back to the heuristic
  engine, so a review is always produced.

---

## Prerequisites

- Java 21+ and Maven 3.9+
- Node 18+ and npm
- (Optional) `ANTHROPIC_API_KEY` for real Claude reviews
- (Optional) PostgreSQL for the `postgres` profile

---

## Running

### 1. Backend (port 8080)

```bash
cd backend
mvn spring-boot:run
```

Optional configuration (environment variables):

| Variable | Purpose |
|---|---|
| `ANTHROPIC_API_KEY` | Enables real Claude review (otherwise heuristic mode) |
| `APP_JWT_SECRET` | JWT signing secret (≥ 32 bytes) — set in production |
| `GITHUB_WEBHOOK_SECRET` | HMAC secret for verifying GitHub webhooks |
| `SPRING_PROFILES_ACTIVE=postgres` | Use PostgreSQL instead of H2 |

Swagger UI: http://localhost:8080/swagger-ui.html
H2 console: http://localhost:8080/h2-console (JDBC URL `jdbc:h2:mem:codereview`)

### 2. Frontend (port 5173)

```bash
cd frontend
npm install
npm run dev
```

Open http://localhost:5173. The Vite dev server proxies `/api` to the backend on :8080.

### Quick demo

1. Register an account.
2. Go to **New Review**, click **Insert sample**, and **Run AI review**.
3. Watch the review move from *in progress* to *completed* with a score, categorized
   findings, and an annotated diff viewer.

---

## REST API

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/auth/register` | Create account, returns JWT |
| `POST` | `/api/auth/login` | Authenticate, returns JWT |
| `GET`  | `/api/dashboard/stats` | Aggregate stats + trends |
| `GET`  | `/api/reviews` | List reviews |
| `POST` | `/api/reviews` | Submit a diff for review |
| `GET`  | `/api/reviews/{id}` | Review detail (findings + diff) |
| `GET`/`POST`/`DELETE` | `/api/repositories` | Manage connected repositories |
| `POST` | `/api/webhooks/github` | GitHub `pull_request` webhook (HMAC-verified) |

All endpoints except `/api/auth/**` and `/api/webhooks/**` require a
`Authorization: Bearer <token>` header.

---

## GitHub Webhook Setup (optional)

1. Connect a repository in the **Repositories** page (full name, e.g. `octocat/hello-world`).
2. In the repo's GitHub settings, add a webhook:
   - Payload URL: `https://<your-host>/api/webhooks/github`
   - Content type: `application/json`
   - Secret: the value of `GITHUB_WEBHOOK_SECRET`
   - Events: *Pull requests*
3. Opening/updating a PR triggers an automatic review (the diff is fetched from the PR's
   public `diff_url`).

---

## Project Layout

```
ai-code-review/
├── backend/    Spring Boot API + AI review engine
├── frontend/   React + TypeScript dashboard
├── Dockerfile  Multi-stage: node build → maven build → JRE runtime (single container)
└── render.yaml Render Blueprint (free tier, docker runtime, /actuator/health check)
```

## Deployment (free, one Docker service)

The whole app ships as a single container: a multi-stage [`Dockerfile`](Dockerfile)
builds the React SPA, bundles it into Spring Boot's static resources, then runs one
JVM that serves both the UI and `/api` on the same origin — one URL, no CORS.

### Deploy to Render (free tier, no credit card)

[![Deploy to Render](https://render.com/images/deploy-to-render-button.svg)](https://render.com/deploy?repo=https://github.com/Legion-1315/ai-code-review)

1. Click the button above (or from the Render dashboard: **New + → Blueprint**),
   sign in with GitHub, authorize this repo.
2. Render reads [`render.yaml`](render.yaml) and provisions the `ai-code-review`
   web service from the [`Dockerfile`](Dockerfile). Click **Apply**.
3. Optionally set `ANTHROPIC_API_KEY` in **Environment** to enable real Claude reviews
   (without it the deterministic mock engine still produces a full review).
4. First build takes ~6–10 min. The app is then live at
   `https://ai-code-review-XXXX.onrender.com`.

Free-plan caveats:
- The service **sleeps after 15 min idle**; next visit cold-starts in ~50 s. This
  repo ships a scheduled GitHub Actions workflow
  ([`keep-warm.yml`](.github/workflows/keep-warm.yml)) that pings
  `/actuator/health` every ~10 minutes during business hours (≈ 9:30–19:20 IST),
  sized to share Render's 750 free instance-hours/month with the SentiSense app.
- H2 is in-memory, so all data (accounts, reviews) **resets on redeploy** — expected
  for a demo. For persistence, set `SPRING_PROFILES_ACTIVE=postgres` plus
  `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` and point at a managed Postgres.

### Run the container locally

```bash
docker build -t ai-code-review .
docker run -p 8080:8080 ai-code-review   # open http://localhost:8080
```
