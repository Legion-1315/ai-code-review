# AGENTS.md

Project-wide instructions for AI coding agents working in this repository.

## Start Here
- Read [README.md](README.md) for the main product overview and run commands.
- Backend entrypoint: [backend/pom.xml](backend/pom.xml) and [CodeReviewApplication.java](backend/src/main/java/com/codereview/CodeReviewApplication.java).
- Frontend entrypoint: [frontend/package.json](frontend/package.json) and [frontend/src/App.tsx](frontend/src/App.tsx).

## Conventions
- Backend is a Spring Boot 3.4 app on port `8080` with JWT auth and H2 by default; PostgreSQL is profile-based.
- Frontend is a React 18 + Vite app on port `5173` and proxies `/api` to the backend in dev.
- The app is designed to work without external AI credentials; heuristic review mode is the default fallback.

## Run and Verify
- Backend: `cd backend && mvn spring-boot:run`
- Frontend on Windows: `cd frontend && npm.cmd install && npm.cmd run dev`
- Frontend on macOS/Linux: `cd frontend && npm install && npm run dev`

## Important Pitfalls
- Review submission is async and should be treated as eventually consistent; the UI polls for completion.
- Use the existing after-commit scheduling path in [ReviewService.java](backend/src/main/java/com/codereview/service/ReviewService.java) when adding review creation logic.
- Keep auth changes aligned with [frontend/src/api/client.ts](frontend/src/api/client.ts), which stores the bearer token in localStorage under `acr_token`.
- If you touch security or CORS, check [SecurityConfig.java](backend/src/main/java/com/codereview/config/SecurityConfig.java) and the frontend proxy in [frontend/vite.config.ts](frontend/vite.config.ts).

## When Editing
- Prefer minimal, local changes.
- Link to existing docs instead of copying them into this file.
- Add tests for new backend behavior when practical; the repo already includes test dependencies.