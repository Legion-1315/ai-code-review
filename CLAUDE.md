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
mvn test                     # run tests (no test sources exist yet)
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
