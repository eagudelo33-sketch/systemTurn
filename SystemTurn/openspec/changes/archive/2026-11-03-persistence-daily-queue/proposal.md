# Proposal: Persistence and Daily Queue

## Intent

Restarting the backend loses every ticket (in-memory list), and there is no concept of "day": numbers never reset and yesterday's tickets pollute today's queue (docs/02 gaps: P1 persistence, P2 day boundary; questions 1, 5, 6). Make tickets durable and scope the queue to today.

## Scope

### In Scope
- H2 file DB + Spring Data JPA behind the `TurnoRepository` port; Flyway `V1`, `ddl-auto=validate`, `open-in-view=false`, path from `TURNOS_DB_PATH`, `data/` gitignored.
- `Turno` gains `LocalDate fecha`, version, `equals` by id; times stored with second precision.
- Queue, numbering, dashboard scoped to today (`turnos.timezone`, default `America/Bogota`); numbering restarts at 1 daily; past days are history; cancelling a past-day ticket → 400 `OPERACION_INVALIDA`.
- Numbering: `UNIQUE(fecha, numero)` + max+1 + bounded retry (drop `synchronized`).
- `atender`/`cancelar` races: `@Version` → **409 `CONFLICTO`** (also on retry exhaustion).
- CSV demo-only: `DemoSeedRunner` under `demo` profile, idempotent, dated today, renumbered (next ticket `T-012`).
- Frontend handling of `CONFLICTO` (see below).

### Out of Scope
H2 console, auth, client identity / `GET /turnos/{id}`, wait estimate, websockets, API v1 format change (ISO times, state codes), cleanup job.

## Capabilities

### New Capabilities
- `ticket-persistence`: durable storage, schema migration, DB configuration.
- `daily-queue`: today scoping, daily numbering, time zone, past-day rules.
- `concurrency-conflicts`: unique numbering with retry, optimistic locking, 409 `CONFLICTO` contract and frontend handling.
- `demo-seed`: profile-gated idempotent CSV seed.

### Modified Capabilities
None (no specs exist yet).

## Approach

Hexagonal mapped adapter: `TurnoJpaEntity` + mapper + `JpaTurnoRepository` in infrastructure; domain free of `jakarta.persistence`. Port evolves to `findByFecha`, `findById`, `maxNumero(fecha)`, `guardar`. Service uses only an injected `Clock.system(zone)` and persists after every mutation. `InMemoryTurnoRepository` becomes a copy-returning test fake.

## Frontend Alignment

- `turnosService.js`: map `CONFLICTO` to a friendly message flagged as refreshable.
- `AsesorView`: on conflict, show message and reload dashboard.
- `ClienteView`: on conflict during cancel, show message and re-query the ticket.
- `400 OPERACION_INVALIDA` already shows the API message; no change.
- Known behavior: a ticket from yesterday held in the browser is no longer found after midnight (accepted until client-identity change).
- **Recommendation**: add minimal Vitest (node env, no jsdom/Testing Library) and TDD the service mapping; verify view refresh manually. ~+60–80 authored lines (lockfile excluded as generated).

## Affected Areas

| Area | Impact |
|------|--------|
| `backend/turnos-api/.../domain` | Modified |
| `.../application/TurnoService` | Modified |
| `.../infrastructure` (JPA, seed, config) | New/Modified |
| `.../web` error handler | Modified (409) |
| `src/main/resources` (yml, migration, CSV) | New/Modified |
| `pom.xml`, `.gitignore` | Modified |
| `react-client/src/services`, `src/views`, `package.json`, `vite.config.js` | Modified |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Budget overrun (~660–830 total) | Med | If tasks forecast >800, move Vitest setup to a preceding change |
| Hibernate 7 enum validation on H2 | Med | `@JdbcTypeCode(VARCHAR)`; `@DataJpaTest` early |
| Test config shadowing / real clock | Med | `application-test.yml`, fixed `@Primary` Clock |
| H2 file single-instance, no HA | Low | Port keeps Postgres swap cheap |

## Rollback Plan

Single PR revert. DB file is new and isolated (`data/`, gitignored); delete it. No external consumers of new schema; API v1 shapes unchanged.

## Dependencies

`spring-boot-starter-data-jpa`, `spring-boot-starter-flyway`, `h2`, `vitest` (dev).

## Success Criteria

- [ ] Tickets survive restart.
- [ ] First ticket of a new day is `T-001`; yesterday excluded from queue/dashboard.
- [ ] Concurrent `solicitar` yields unique consecutive numbers; stale `atender`/`cancelar` → 409 `CONFLICTO`.
- [ ] Frontend shows friendly conflict message and refreshes; Vitest test passes.
- [ ] Demo profile seeds once; default profile starts empty.
- [ ] `mvnw test`, `npm run lint`, `npm run build` green; API v1 responses unchanged.
