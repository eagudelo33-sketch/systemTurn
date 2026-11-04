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
| AWS infrastructure (Terraform) | [infra-onboarding.md](infra-onboarding.md) | [infra-state-backend](../openspec/specs/infra-state-backend/spec.md), [infra-cost-guardrails](../openspec/specs/infra-cost-guardrails/spec.md), [infra-environments](../openspec/specs/infra-environments/spec.md), [infra-repo-conventions](../openspec/specs/infra-repo-conventions/spec.md) | `infra/` |

All specs, with requirement counts and the tests that prove them: [specs index](../openspec/specs/README.md). Why something changed: [change archive](../openspec/changes/archive/). How to run it in Spanish: [../README.md](../README.md).

## Status (2026-11-04)

| Area | Status |
|------|--------|
| Backend API (4 endpoints) | ✅ Working, 147 tests (17 against real PostgreSQL) |
| Advisor and client views | ✅ Working against the real API |
| Database | ✅ PostgreSQL in production, H2 file for local `dev`; data survives restarts |
| Operations | ✅ Env-driven config, startup guards, health probes that ignore the DB, fast `503`, graceful shutdown |
| Infrastructure | ✅ Terraform foundation (state bucket, budget, `dev`/`prod` roots), verified offline. ⏳ First real `apply` is manual |
| Frontend tests | ✅ Vitest (19: service layer + advisor panel states) + Playwright browser tests (18) covering both views, including conflict, stale-ticket, loading, empty-queue and refresh-failure states and a mocked advisor 503 (not a full-stack 503). ⚠️ No component tests for the views |
| Live updates | ❌ Manual refresh |
| Client keeps their ticket after a refresh | ❌ Ticket lives only in React state |
| Authentication | ❌ Anyone can call "next" or cancel any ticket |

## Roadmap

| # | Change | Status | What it delivers |
|---|--------|--------|------------------|
| 1 | `persistence-daily-queue` | ✅ Done | Database, daily queue, numbering restart, 409 conflicts, demo seed |
| 2 | `postgres-adapter` | ✅ Done | PostgreSQL in production, guards, health, 503, graceful shutdown |
| 3 | `infra-foundation` | ✅ Done | Terraform state backend, budget, env roots, conventions |
| 4 | `e2e-browser-tests` | ✅ Done | Playwright suite: 13 browser tests over both views, real backend + mocked error responses |
| 5 | `ux-error-states` | ✅ Done | Honest error and stale states: the advisor never shows an unknown state as connected or empty; a ticket the server no longer has is cleared |
| 6 | `infra-network` | ⏭️ Next | VPC module (3 tiers, 2 AZs, `nat_mode`), wired into `dev`/`prod` |
| 7 | `infra-app` | Planned | ECR, ECS Fargate, ALB, RDS PostgreSQL, S3 + CloudFront |
| 8 | `infra-cicd` | Planned | GitHub Actions with OIDC, plan on PR, manual prod approval |
| 9 | `client-identity` | Planned (can slot in earlier) | `GET /turnos/{id}`, ticket kept in the browser, ISO times and state codes, component tests |
| 10 | `wait-estimate` | Planned | Per-ticket wait estimate |
| 11 | `live-updates` | Planned | WebSocket push to both views |
| 12 | `advisor-auth` | Planned | Login for advisors (required before a public deploy) |

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
| Infrastructure layout | One Terraform root per environment (no workspaces); `bootstrap/` separate; account ID kept out of the repo |
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

## Obligations carried into upcoming changes

These come from the archive reports. Each one must be handled by the change that owns it.

| Change | Must do |
|--------|---------|
| `infra-network` | Prove the region and `default_tags` on a real resource in `plan` (today proven only through an output); replace the `env_roots_declare_no_resources` scenario; bump the tflint AWS plugin to 0.50.0 (allowed from 2026-11-06); remove or scope the `no_app_files_touched` convention check (a change-scoped rule from `infra-foundation` encoded as a permanent check: it fails every branch that touches `frontend/` or `backend/`) via a MODIFIED delta on `infra-repo-conventions` |
| `infra-app` | ALB health check `/actuator/health/readiness`; `socketTimeout` and `sslmode` in `SPRING_DATASOURCE_URL`; never run the `test` or `demo` profile in production; RDS PostgreSQL 17 (tests pin `postgres:17.10`); pool size × tasks < `max_connections`; ALB idle timeout long enough for WebSockets |
| `infra-cicd` | Run `./mvnw test -DrequireDocker=true`; a full-stack 503 test; keep the PostgreSQL suite sequential (one shared container); run `check-conventions.sh`, `terraform test` (fail on "0 passed"), tflint and trivy; never delete lock files before pre-commit; run `npm run e2e` on Linux (`npx playwright install --with-deps chromium`; upload `playwright-report/` and `test-results/`) |
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
