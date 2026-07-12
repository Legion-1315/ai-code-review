# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

AI-powered code review system. A developer submits a diff (manually or via a GitHub
`pull_request` webhook) → the backend reviews it with Claude → it persists inline findings,
a quality score, and a summary, which the React dashboard renders. Findings are categorized
(security, code quality, performance, best practice, test coverage) and severity-rated.

**Zero-credential design:** without an `ANTHROPIC_API_KEY` the engine falls back to a
deterministic heuristic reviewer (`MockReviewEngine`), so the whole app is demoable
end-to-end. Setting the key switches to real Claude (`claude-opus-4-8`). Any Claude failure
also transparently degrades to the heuristic engine — a review is *always* produced.

See [README.md](README.md) for the full product overview and [AGENTS.md](AGENTS.md) for agent conventions.

## Layout

Monorepo with two independent projects:
- `backend/` — Java 21, Spring Boot 3.4, Maven. JWT auth, Spring Data JPA, Anthropic Java SDK. Port `8080`.
- `frontend/` — React 18 + TypeScript, Vite, Tailwind, TanStack Query, React Router, Recharts. Port `5173`.

## Commands

Backend (run from `backend/`; no Maven wrapper, use system `mvn`):
```bash
mvn spring-boot:run          # start API on :8080 (H2 in-memory by default)
mvn test                     # unit tests + eval harness (10 tests)
mvn -Dtest=ClassName#method test   # run a single test
mvn package                  # build jar
SPRING_PROFILES_ACTIVE=postgres mvn spring-boot:run   # use PostgreSQL instead of H2
```

Frontend (run from `frontend/`; on Windows use `npm.cmd`):
```bash
npm install
npm run dev        # Vite dev server on :5173, proxies /api → :8080
npm run build      # tsc typecheck + vite build
```

Useful endpoints when backend is running: Swagger `http://localhost:8080/swagger-ui.html`,
H2 console `http://localhost:8080/h2-console` (JDBC `jdbc:h2:mem:codereview`).

## Architecture — the big picture

**Async, eventually-consistent review pipeline.** This is the core design and spans several files:

1. `POST /api/reviews` ([ReviewController](backend/src/main/java/com/codereview/web/ReviewController.java)) → [ReviewService.submit](backend/src/main/java/com/codereview/service/ReviewService.java) creates a `PENDING` review row and returns immediately. The actual review runs later, so the UI polls `GET /api/reviews/{id}` for completion.
2. **After-commit scheduling is load-bearing.** `ReviewService` registers a `TransactionSynchronization.afterCommit()` callback to fire the async worker — never call `ReviewProcessor.process` directly from within the creating transaction, or the worker thread may not see the committed row. Reuse the existing `createAndSchedule` path when adding any new review-creation entry point.
3. [ReviewProcessor.process](backend/src/main/java/com/codereview/service/ReviewProcessor.java) is `@Async("reviewExecutor")` (pool defined in [AsyncConfig](backend/src/main/java/com/codereview/config/AsyncConfig.java)). It flips status to `IN_PROGRESS`, calls the AI service, persists findings/score, and sets `COMPLETED` or `FAILED`.

**Anchor validation & evals** — the measured-quality layer:
- [FindingAnchorValidator](backend/src/main/java/com/codereview/service/ai/FindingAnchorValidator.java)
  runs on every result in `ReviewProcessor` *before* persisting: findings whose file/line
  don't exist in the diff's hunks are snapped (within ±3 lines) or demoted to file level;
  the demoted count is persisted as `Review.unanchoredFindings` (exposed in the detail DTO).
- [EvalHarnessTest](backend/src/test/java/com/codereview/evals/EvalHarnessTest.java) scores
  engines against the labeled dataset
  [cases.json](backend/src/test/resources/evals/cases.json) (12 diffs, 10 planted bugs
  split HEURISTIC/SEMANTIC, 2 clean controls). It asserts floors for the deterministic
  heuristic engine (heuristic-recall ≥ 0.8, clean-diff FPs = 0, precision ≥ 0.8) — this is
  a CI regression gate. With `ANTHROPIC_API_KEY` exported it also scores real Claude
  (report-only, no assertions). Report goes to `target/evals/report.json`; the committed
  copy at `backend/src/main/resources/evals/report.json` backs the public
  `GET /api/evals/report` and the frontend **Evals** page
  ([EvalsPage.tsx](frontend/src/pages/EvalsPage.tsx)). **To refresh the scoreboard:** run
  `mvn test`, then copy `target/evals/report.json` over the committed copy.
- When adding eval cases, keep labeled line numbers exact — the harness's
  `datasetAnchorsAreValid` test fails if a label doesn't exist in its diff. TEST_COVERAGE
  findings are excluded from scoring by design.

**Repo-context enrichment (webhook reviews)** — `service/context/`:
- [RepoContextService](backend/src/main/java/com/codereview/service/context/RepoContextService.java)
  fetches the diff's touched files at the PR head SHA (captured from the webhook payload
  into `PullRequest.headRef`) via raw.githubusercontent.com, then resolves one level of
  same-repo Java imports against a single `git/trees?recursive=1` listing. Budgeted
  (`ai.context.*` in application.yml: 48K chars total, 12K/file, 4 import files) and
  best-effort — failures degrade to an empty context, never a failed review. Optional
  `GITHUB_TOKEN` raises rate limits / enables private repos.
- **Prompt-caching layout** in [AiReviewServiceImpl](backend/src/main/java/com/codereview/service/ai/AiReviewServiceImpl.java):
  system prompt (cache breakpoint) → context block (cache breakpoint) → diff. The context
  block is the large stable prefix; the diff is the volatile suffix. Cache usage is logged
  from `response.usage()`. `Review.contextFiles` records how many files the model saw
  (rendered as a chip on the review detail page).
- Manual reviews (`prNumber == null`) skip context — there is no repo to fetch from.

**AI engine selection & resilience** — `service/ai/`:
- [AiReviewServiceImpl](backend/src/main/java/com/codereview/service/ai/AiReviewServiceImpl.java) picks real Claude vs. [MockReviewEngine](backend/src/main/java/com/codereview/service/ai/MockReviewEngine.java) based on whether `ai.api-key` is set, and falls back to the mock on any Claude error.
- [ReviewPromptFactory](backend/src/main/java/com/codereview/service/ai/ReviewPromptFactory.java) builds the prompt (strict-JSON instructions); [ReviewJsonParser](backend/src/main/java/com/codereview/service/ai/ReviewJsonParser.java) parses defensively (markdown-fence stripping, enum fallbacks). `AiReviewResult.usedRealAi()` records which engine ran.

**GitHub webhook intake** — `service/github/`: [WebhookController](backend/src/main/java/com/codereview/web/WebhookController.java) → signature checked by [GitHubSignatureVerifier](backend/src/main/java/com/codereview/service/github/GitHubSignatureVerifier.java) (HMAC-SHA256 over the raw body), diff fetched by [GitHubDiffClient](backend/src/main/java/com/codereview/service/github/GitHubDiffClient.java) from the PR's public `diff_url`, then handed to `ReviewService.submitForRepository`. A blank `GITHUB_WEBHOOK_SECRET` disables signature checks (dev only).

**Auth & ownership** — JWT in [security/](backend/src/main/java/com/codereview/security/), config in [SecurityConfig](backend/src/main/java/com/codereview/config/SecurityConfig.java). All endpoints except `/api/auth/**` and `/api/webhooks/**` require `Authorization: Bearer <token>`. Reviews/repositories are scoped per-owner — service queries use `...ForOwner(...)` methods; preserve that scoping on any new read/write path. The frontend stores the token in `localStorage` under `acr_token` ([client.ts](frontend/src/api/client.ts)); keep auth changes in sync with it.

**Frontend data flow** — [api/client.ts](frontend/src/api/client.ts) (axios + token), [api/hooks.ts](frontend/src/api/hooks.ts) (TanStack Query hooks, including polling for in-progress reviews), [api/types.ts](frontend/src/api/types.ts). Pages in [src/pages/](frontend/src/pages/) mirror the REST surface (Dashboard, Reviews, ReviewDetail, NewReview, Repositories, Login, Register).

## Configuration

All in [application.yml](backend/src/main/resources/application.yml), overridable via env vars:
`ANTHROPIC_API_KEY` (real Claude), `AI_MODEL` (default `claude-opus-4-8`), `APP_JWT_SECRET`
(≥32 bytes, has an insecure dev default), `GITHUB_WEBHOOK_SECRET`, `APP_CORS_ORIGINS`,
`SPRING_PROFILES_ACTIVE=postgres` (+ `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`). If you touch CORS
or security, check both `SecurityConfig` and the frontend proxy in [vite.config.ts](frontend/vite.config.ts).

## Conventions

- Prefer minimal, local changes. The repo includes backend test dependencies but no tests yet; add tests for new backend behavior where practical.
- Treat review submission as asynchronous/eventually-consistent everywhere it surfaces.

## Deployment

Single-container design: multi-stage [`Dockerfile`](Dockerfile) builds the React SPA
(node:20-alpine) → copies `dist/` into Spring Boot's `resources/static/` →
`mvn package` on `maven:3.9-eclipse-temurin-21` → runs on `eclipse-temurin:21-jre`.
Spring serves the SPA at `/` and the API at `/api/**` on the **same origin**, so
production needs no CORS. [`SpaController`](backend/src/main/java/com/codereview/web/SpaController.java)
forwards non-API, non-asset GETs to `index.html` so React Router handles them; the
matching security rules in [`SecurityConfig`](backend/src/main/java/com/codereview/config/SecurityConfig.java)
permit any non-`/api/**` path. `server.port=${PORT:8080}` honors the host's injected port.

- **Repo:** github.com/Legion-1315/ai-code-review (public).
- **Live:** https://ai-code-review-uzuo.onrender.com (deployed & verified 2026-07-09 —
  SPA, `/actuator/health`, React-Router `/login`, and `/api/auth/register` all return 200).
- **Host:** Render free tier via [`render.yaml`](render.yaml) Blueprint (`runtime: docker`,
  `plan: free`, health check `/actuator/health`). `APP_JWT_SECRET` auto-generated per deploy;
  `ANTHROPIC_API_KEY` optional (mock engine used when absent).
- **Free-tier caveats:** sleeps after 15 min idle (~50 s cold start). Mitigated by
  [`keep-warm.yml`](.github/workflows/keep-warm.yml) — scheduled GitHub Actions ping
  of `/actuator/health` every ~10 min, 4–13 UTC (≈ 9:30–19:20 IST). Window budgeted
  against the workspace's 750 free instance-hours/month shared with SentiSense
  (~285 h vs ~430 h). GitHub pauses cron workflows after 60 days without commits.
  H2 in-memory so all data resets on redeploy.
- **CI:** [`.github/workflows/ci.yml`](.github/workflows/ci.yml) — backend `mvn verify` +
  frontend `npm ci && npm run build`.
- **`.dockerignore`** excludes `target/`, `node_modules/`, `dist/`, `.git/`, IDE files.
