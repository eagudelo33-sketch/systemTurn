# Gaps and Open Questions

Where the code differs from the plan (whiteboard + solution diagram), what is missing, and what the team must decide before writing specs.

Priority: **P1** = blocks a credible demo or causes wrong behavior · **P2** = important for quality · **P3** = nice to have.

## 1. Plan vs. reality

| Planned | Reality | Priority |
|---------|---------|----------|
| H2 database + Spring Data JPA, table `turnos` | ✅ **Resolved.** PostgreSQL is the production database (JPA + Flyway); H2 file only under the `dev` profile. Tickets survive restarts. | — |
| Client sees stats "in real time" | Stats load once when the page opens. Ticket state refreshes only with a manual button. Advisor view refreshes only after its own action. | P1 |
| Client sees waiting time (`tiempo de espera`) | Only the global average is shown. No estimate for *my* ticket (`esperaEstimadaMinutos` is always `null`). | P2 |
| CSV importer "validates the information" | ✅ **Partly resolved.** The CSV is now demo-only (`demo` profile), is skipped when today already has tickets, and its numbers are unique (T-001..T-011), so the duplicate-number problem is gone. Row validation of arbitrary uploaded files is not a feature yet (see question 6). | P3 |
| Postman automated tests | No Postman collection in the repo. | P2 |
| Advisor "reports" | Only the 4 counters + table. No reports. | P3 |

## 2. Missing behavior

| Gap | Why it matters | Priority |
|-----|----------------|----------|
| Client loses their ticket on page refresh | Ticket lives only in React state. Close the tab → you can't see or cancel it anymore. | P1 |
| No `GET /api/v1/turnos/{id}` | Client looks up its own ticket by downloading the **entire** dashboard (everyone's tickets). Wasteful, and exposes all data to any client. | P1 |
| No authentication / roles | Anyone who knows the URL can call `PATCH /siguiente` or cancel anyone's ticket by guessing ids. | P2 (P1 if deployed publicly) |
| ~~No concept of "day"~~ | ✅ **Resolved.** Tickets have a `fecha`; queue and metrics are scoped to today in `America/Bogota`; numbering restarts each day; past-day tickets cannot be cancelled. | — |
| Single advisor, single queue | No advisor id, no desk/window number ("go to desk 3"). | P3 |
| No pagination on dashboard | `turnos[]` returns full history forever. Fine for a day, not for months. | P3 |
| No component tests for the views | Frontend tests exist now (Vitest), but only for the service layer. `AsesorView` / `ClienteView` have no testing-library/jsdom tests. Planned together with the `client-identity` change. | P2 |
| ~~H2 file database = single instance~~ | ✅ **Resolved** by the `postgres-adapter` change: production uses PostgreSQL, so several stateless instances can share one database. Remaining deployment work is tracked in the operational notes below. | — |

## 3. Technical debt

| Item | Suggestion |
|------|-----------|
| API returns times as display text (`"08:25 a. m."`), and the frontend re-parses them with a regex | Return ISO times (`"08:25"` or full ISO datetime) and let the UI format. Display format belongs to the UI. |
| States returned as labels (`"Pendiente"`) and the frontend maps them back to codes | Return codes (`"PENDIENTE"`); UI maps to labels. |
| `turnosSimulation.js` and `isDemoMode`/`usandoSimulacion` are dead code | Delete, or move to a test fixture. |
| The "contrato v1.0.0" referenced in `Responses.java` is not in the repo | Add it (or an OpenAPI file) under `docs/`. It is the spec for the API. |
| `AsesorView.formatTime` tries `new Date("08:25 a. m.")`, which always fails and falls back to the raw string | Remove once API returns ISO times. |

## 4. Questions for the team

Answer these before the first proposal. Each answer becomes a requirement.

1. ~~**Persistence:** H2 or in-memory?~~ **Answered:** PostgreSQL in production (H2 file for local `dev`).
2. **Real time:** Is polling every N seconds enough, or do we need WebSockets / Server-Sent Events?
3. **Client identity:** How does a client get back to their ticket after a refresh? (Store id in `localStorage`? A link with the ticket id?)
4. **Wait estimate:** What formula? e.g. `position × average wait`? What if nothing has been attended yet?
5. ~~**Day boundary:** Does the queue reset every day? Does numbering restart at 1?~~ **Answered:** yes to both (per day, in `America/Bogota`).
6. **CSV:** ~~One-time seed or real feature?~~ **Answered:** demo-only seed. Still open: if an upload feature is wanted, what happens with invalid rows (skip, or reject the whole file)?
7. **Security:** Is this a classroom demo on localhost, or will it be deployed? That decides whether auth is P1.
8. **API format:** Can we change the contract (ISO times, state codes), or is v1 frozen because someone else consumes it?

## 5. Suggested first SDD changes

Small, independent, in this order:

| # | Change | Depends on question |
|---|--------|---------------------|
| 1 | `client-identity` — add `GET /turnos/{id}` and persist ticket id in the browser | 3 |
| 2 | `live-refresh` — periodic refresh for both views | 2 |
| 3 | ~~`h2-persistence` — JPA adapter behind `TurnoRepository`~~ Done (then `postgres-adapter`: PostgreSQL in production) (`persistence-daily-queue`, archived 2026-11-03). Its 3 deferred view-level scenarios are owned by the `client-identity` change, per `openspec/changes/archive/2026-11-03-persistence-daily-queue/deferred.md` | 1 |
| 4 | `csv-validation` — validate rows, report errors, don't crash | 6 |
| 5 | `wait-estimate` — per-ticket estimated wait for the client | 4 |

Each one is small enough to spec, build, and verify in one cycle.

**Status:** `postgres-adapter` is done and archived (2026-11-03, `openspec/changes/archive/2026-11-03-postgres-adapter/`). Its operational obligations are in section 6.

**Next change:** `infra-foundation`.

## 6. Operational notes (PostgreSQL)

These are not code gaps in this change. They are the checks and decisions that land with the infrastructure changes (`infra-app`, `infra-cicd`).

| Topic | What to do | Owner |
|-------|-----------|-------|
| ALB health check path | Use `/actuator/health/readiness`. It ignores the database on purpose, so an RDS failover does not pull every task. Requests that need the database answer `503 SERVICIO_NO_DISPONIBLE` meanwhile | `infra-app` |
| Pool size vs `max_connections` | `TURNOS_DB_POOL_SIZE` (default 10) x number of tasks must stay below the RDS `max_connections` | `infra-app` |
| `socketTimeout` | Hikari's 5 s timeout only covers acquiring a connection. Put `socketTimeout` in `SPRING_DATASOURCE_URL` so a running query cannot hang during a failover | `infra-app` |
| TLS | Set `sslmode` in the datasource URL value | `infra-app` |
| No `test` or `demo` in production | The H2 guard allows H2 under `test`, so it cannot detect a production task started with that profile. The ECS task definition must never set `test` or `demo` | `infra-app` |
| `requireDocker=true` in CI | Without it the PostgreSQL test suite is skipped when Docker is missing. CI must pass `-DrequireDocker=true` so the suite cannot be skipped silently | `infra-cicd` |
| Engine version | The tests pin `postgres:17.10`. Confirm the RDS major version (17 assumed) and keep the pinned tag on the same major | `infra-app` |
| Old H2 data | Not migrated (it was disposable). Developers must start the backend with `dev` profile or a PostgreSQL URL | — |
| Frontend 503 in the views | `turnosService.js` already turns the 503 into an error with the server message. Showing it in the views is deferred to `client-identity` | `client-identity` |
