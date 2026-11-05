# SystemTurn — Start Here

SystemTurn is a **virtual queue** (fila virtual). A **client** taps a button and gets a ticket like `T-011`. An **advisor** sees the queue and presses "call next", which serves the oldest pending ticket. Everyone sees how many people are waiting, how many were served, and the average wait.

This page is the map. Read it first; it points you to the one document or spec you need.

## Feature map

| Feature | How it works | Exact rules (spec) | Code |
|---------|--------------|--------------------|------|
| Ticket queue: request, cancel, call next, daily reset | [app.md → Business rules](app.md#business-rules), [API](app.md#api-contract-v1) | [daily-queue](../openspec/specs/daily-queue/spec.md), [concurrency-conflicts](../openspec/specs/concurrency-conflicts/spec.md) | `backend/turnos-api/.../application`, `domain` |
| Data storage (PostgreSQL, H2 for dev) | [app.md → Architecture](app.md#architecture), [Domain model](app.md#domain-model) | [ticket-persistence](../openspec/specs/ticket-persistence/spec.md) | `.../infrastructure/persistence`, `db/migration` |
| Demo data | [app.md → Business rules](app.md#business-rules) | [demo-seed](../openspec/specs/demo-seed/spec.md) | `.../infrastructure/DemoSeedRunner.java` |
| Running in production: config, startup guards, health, 503, shutdown | [app.md → Configuration and operations](app.md#configuration-and-operations) | [runtime-operations](../openspec/specs/runtime-operations/spec.md) | `application.yml`, `StartupGuard.java` |
| Client and advisor screens | [app.md → Frontend](app.md#frontend) | [concurrency-conflicts](../openspec/specs/concurrency-conflicts/spec.md) (service mapping, conflict reactions), [browser-e2e](../openspec/specs/browser-e2e/spec.md) | `frontend/frontend-react/react-client/src` |
| AWS infrastructure (Terraform): what exists, cost, on/off switch | [infra.md](infra.md), [infra-onboarding.md](infra-onboarding.md) (runbook), [cicd.md](cicd.md) (GitHub Actions, OIDC, prod approval) | [infra-network](../openspec/specs/infra-network/spec.md), [infra-app](../openspec/specs/infra-app/spec.md), [infra-environments](../openspec/specs/infra-environments/spec.md), [infra-cost-guardrails](../openspec/specs/infra-cost-guardrails/spec.md), [infra-state-backend](../openspec/specs/infra-state-backend/spec.md), [infra-repo-conventions](../openspec/specs/infra-repo-conventions/spec.md) | `infra/` |

All specs, with requirement counts and the tests that prove them: [specs index](../openspec/specs/README.md). Why something changed: [change archive](../openspec/changes/archive/). How to run it in Spanish: [../README.md](../README.md).

## Status (2026-11-04)

| Area | Status |
|------|--------|
| Backend API (4 endpoints) | ✅ Working, 147 tests (17 against real PostgreSQL) |
| Advisor and client views | ✅ Working against the real API |
| Database | ✅ PostgreSQL in production, H2 file for local `dev`; data survives restarts |
| Operations | ✅ Env-driven config, startup guards, health probes that ignore the DB, fast `503`, graceful shutdown |
| Infrastructure | ✅ Terraform foundation, network module and app module (ECS, RDS, ALB, CloudFront), verified offline. `dev` applies the network only (~$0); the paid app layer is off behind `enable_app`. `prod` not wired yet. ⏳ First real `apply` is manual. CI: `.github/workflows/infra.yml` runs offline checks on every PR; AWS plan/apply stay off until you follow [cicd.md](cicd.md) |
| Frontend tests | ✅ Vitest (19: service layer + advisor panel states) + Playwright browser tests (18) covering both views, including conflict, stale-ticket, loading, empty-queue and refresh-failure states and a mocked advisor 503 (the real full-stack 503 is proved in the backend, `DatabaseOutageFullStackTest`). Both suites run in CI (`ci.yml`). ⚠️ No component tests for the views |
| Live updates | ❌ Manual refresh |
| Client keeps their ticket after a refresh | ❌ Ticket lives only in React state |
| Authentication | ❌ Anyone can call "next" or cancel any ticket |

## Roadmap

| # | Change | Status | What it delivers |
|---|--------|--------|------------------|
| 1 | `persistence-daily-queue` | ✅ Done | Database, daily queue, numbering restart, 409 conflicts, demo seed |
| 2 | `postgres-adapter` | ✅ Done | PostgreSQL in production, guards, health, 503, graceful shutdown |
| 3 | `infra-foundation` | ✅ Done | Terraform state backend, budget, env roots, conventions |
| 4 | `infra-network-app` | ✅ Done | VPC module (3 tiers, 2 AZs, `nat_mode`), app module (ECR, ECS Fargate, ALB, RDS PostgreSQL, S3 + CloudFront), wired into `dev` behind `enable_app`. `prod` wiring deferred |
| 5 | `e2e-browser-tests` | ✅ Done | Playwright suite: 13 browser tests over both views, real backend + mocked error responses |
| 6 | `ux-error-states` | ✅ Done | Honest error and stale states: the advisor never shows an unknown state as connected or empty; a ticket the server no longer has is cleared |
| 7 | `infra-cicd` | ✅ Done (PR #9–#11) | GitHub Actions with OIDC: credential-free infra checks, plan on same-repo PRs, manual prod approval, plus the app CI (`ci.yml`: backend with Docker, frontend, Playwright e2e) and a full-stack 503 test that needs no Docker ([cicd.md](cicd.md)) |
| 7b | `infra-prod-wiring` | Planned | Wire `prod` (`nat_mode = "per_az"`, deletion protection, final snapshot), first real apply, `/api/*`, SPA rewrite and ALB prefix-list tests |
| 8 | `client-identity` | Planned (can slot in earlier) | `GET /turnos/{id}`, ticket kept in the browser, ISO times and state codes, component tests |
| 9 | `wait-estimate` | Planned | Per-ticket wait estimate |
| 10 | `live-updates` | Planned | WebSocket push to both views |
| 11 | `advisor-auth` | Planned | Login for advisors (required before a public deploy) |

## Decisions already made

| Topic | Decision |
|-------|----------|
| Database | PostgreSQL in production; H2 file only under the `dev` profile (zero-Docker local dev) |
| Day boundary | The queue resets every day in `America/Bogota`; numbering restarts at `T-001` |
| CSV | Demo data only (`demo` profile, together with `dev`) |
| Deployment | The app will be deployed on AWS (`us-east-1`); auth is required before going public |
| API format | May change: move to ISO times and state codes (planned in `client-identity`) |
| Live updates | WebSockets |
| Wait estimate | Position × average time between consecutive attentions today; a configurable default per person when nobody has been served yet; capped at 120 minutes |
| Health checks | The database is excluded from readiness/liveness, so an RDS failover does not make ECS replace tasks; DB-dependent requests answer `503` |
| Infrastructure layout | One Terraform root per environment (no workspaces); `bootstrap/` separate; reusable `modules/network` and `modules/app`; account ID kept out of the repo |
| Dev cost | Paid layer is a switch (`enable_app`, default off), not a pause: an ALB bills while it exists and a stopped RDS restarts after 7 days. `nat_mode = "none"` in dev, `single` when the app is on, `per_az` for prod. See [infra.md](infra.md) |
| Frontend ↔ backend | Every API contract change ships with the matching frontend change |

## Open gaps

| Gap | Priority | Planned in |
|-----|----------|------------|
| Client loses their ticket on refresh; no `GET /turnos/{id}` (the client downloads the whole dashboard) | P1 | `client-identity` |
| No live updates (stats load once; views refresh only after their own action) | P1 | `live-updates` |
| No authentication or roles | P1 before a public deploy | `advisor-auth` |
| No per-ticket wait estimate | P2 | `wait-estimate` |
| No component tests for the views (the 3 view scenarios deferred from `persistence-daily-queue` are now proven by the browser tests) | P3 | — |
| API returns display text (`"08:25 a. m."`, `"Pendiente"`) that the frontend re-parses | P2 | `client-identity` |
| Client position is wrong after a re-fetch: `posicionEnDashboard` ignores the AM/PM suffix, so the order breaks across noon (found while writing the browser tests) | P2 | `client-identity` (fixed by ISO times) |
| Advisor greeting "Buenos días, asesor" is hardcoded regardless of the time of day (found in browser-test screenshots) | P3 | any change touching `AsesorView` |
| Dead code: `turnosSimulation.js` | P3 | any change touching the frontend |
| No Postman collection or OpenAPI file for the API | P3 | — |
| No pagination on the dashboard; single advisor, no desk number; no reports | P3 | — |
| AWS edge: ALB is HTTP only (no ACM), no WAF, no CloudFront-to-ALB secret header, no WebSocket path; `prod` not wired; nothing applied to a real account | P1 before a public deploy | `infra-prod-wiring`, `live-updates` |

## Obligations carried into upcoming changes

These come from the archive reports. Each one must be handled by the change that owns it.

| Change | Must do |
|--------|---------|
| `infra-prod-wiring` | Carried from `infra-network-app`: prove the region and `default_tags` on a real resource in `plan` (still proven only through outputs); bump the tflint AWS plugin to 0.49.0 → 0.50.0 (allowed from 2026-11-06; task 6.2 of `infra-cicd`, intentionally not done on 2026-11-05: verify the release date with `gh api` first); keep pool size × tasks < `max_connections` when raising `app_desired_count` (documented, not enforced); add tests for the `/api/*` behavior, SPA rewrite and ALB prefix-list rule; wire `prod` (`nat_mode = "per_az"`, deletion protection, final snapshot); confirm the first real assume-role and plan, and the Linux provider hashes ([cicd.md](cicd.md)). The `no_app_files_touched` check is already opt-in (done in `infra-cicd`) |
| `infra-cicd` (done) | Delivered: convention checks, `terraform test` failing on "0 passed", tflint and trivy in `infra.yml`, `no_app_files_touched` opt-in, `ci.yml` with `./mvnw test -DrequireDocker=true`, `npm run e2e` on Linux (report and traces uploaded) and a full-stack 503 test. Left for later: keep the PostgreSQL suite sequential (one shared container) if it grows |
| `client-identity` | Upgrade the stale-ticket browser test to a true stored-ticket test once persistence exists; fix the AM/PM position bug via ISO times; show the `503` message in the views |

## Docs, specs and archive

| | Docs (`docs/`) | Specs (`openspec/specs/`) | Archive (`openspec/changes/archive/`) |
|---|---|---|---|
| Answers | How it works, how to run it | What MUST be true, and which test proves it | Why it changed |
| Updated | With every change | By archive, merging the change's delta specs | Never (frozen history) |

## How we work: Spec-Driven Development

Every change follows `explore → propose → spec → design → tasks → apply → verify → archive`. The spec states *what* must be true as requirements with Given/When/Then scenarios; every scenario is proven by an automated test, and verify only passes when all of them are. Checks that need a real AWS account are listed as *Operational notes* in the spec instead.

Example: *"The system MUST serve pending tickets in order of request time."* **Given** ticket 7 at 08:34 and ticket 9 at 08:41, **when** the advisor calls next, **then** ticket 7 is served first.

## Team

| Person | Area |
|--------|------|
| David, Daniela | Frontend (React) |
| Kevin | Data |
| Edgar | Backend (Spring Boot) |
| Diover | Testing |
