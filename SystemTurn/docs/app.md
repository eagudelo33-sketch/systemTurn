# SystemTurn Application

How the ticket-queue application works **today**: architecture, domain, business rules, API, frontend, configuration and tests. Verified against the source on 2026-11-04. For status, roadmap and open gaps see [README.md](README.md); for the binding rules see the specs linked in each section.

| Section | Spec |
|---------|------|
| [Architecture](#architecture), [Domain model](#domain-model) | [ticket-persistence](../openspec/specs/ticket-persistence/spec.md) |
| [Business rules](#business-rules) | [daily-queue](../openspec/specs/daily-queue/spec.md), [concurrency-conflicts](../openspec/specs/concurrency-conflicts/spec.md), [demo-seed](../openspec/specs/demo-seed/spec.md) |
| [API contract](#api-contract-v1), [Frontend](#frontend) | [concurrency-conflicts](../openspec/specs/concurrency-conflicts/spec.md), [runtime-operations](../openspec/specs/runtime-operations/spec.md) |
| [Configuration and operations](#configuration-and-operations) | [runtime-operations](../openspec/specs/runtime-operations/spec.md) |

## Architecture

```text
Browser (React + Vite, :5173)
   │  fetch /api/...   (Vite dev proxy forwards /api → :8080)
   ▼
Spring Boot API (:8080)
   web/            → HTTP: controller, response shapes, error mapping
   application/    → use cases: TurnoService, Dashboard
   domain/         → business rules: Turno, EstadoTurno, exceptions, TurnoRepository (port)
   infrastructure/ → adapters: persistence/ (JPA + Flyway), DemoSeedRunner, CsvSeedLoader, Spring config
```

**Why this layering matters (hexagonal architecture).** The `domain` knows nothing about Spring, HTTP, or storage. `TurnoRepository` is an *interface* (a "port"); `JpaTurnoRepository` (in `infrastructure/persistence`) is one *implementation* (an "adapter"). That is how memory was replaced by H2, and later H2 by PostgreSQL, without rewriting the business rules: only the adapter and its configuration changed. Protect this boundary. The unit tests use an in-memory fake that lives in the test sources only (`testsupport/InMemoryTurnoRepository`).

The schema is owned by Flyway (`db/migration/V1__create_turno.sql`), one file shared by H2 and PostgreSQL (no vendor-specific folders). Hibernate only validates it (`ddl-auto: validate`); it never creates or alters tables.

## Domain model

### `Turno` (ticket)

| Field | Type | Meaning |
|-------|------|---------|
| `id` | Long | Internal, unique, assigned by the database on first save (`null` while unsaved). Used in URLs. |
| `fecha` | LocalDate | The day the ticket belongs to (queue day, in `turnos.timezone`). |
| `numero` | int | Visible number, unique **per day** (`fecha` + `numero` is a unique constraint). Shown as `codigo` = `T-%03d` (e.g. `T-011`). |
| `horaSolicitud` | LocalTime | When it was requested (second precision). |
| `horaAtencion` | LocalTime / null | When it was served (second precision). |
| `estado` | enum | `PENDIENTE`, `ATENDIDO`, `CANCELADO` |
| `version` | Long | Opaque to the domain. The repository uses it to detect stale writes (optimistic locking). |

Equality is by `id` (identity while unsaved), so a ticket reloaded from the database is the same ticket.

> Why separate `id` and `numero`? A visible number is not a safe identifier: it restarts every day.

### State machine

```text
            atender()
PENDIENTE ───────────▶ ATENDIDO
    │
    │ cancelar()
    ▼
CANCELADO
```

Only `PENDIENTE` can change. Any other transition throws `OperacionInvalidaException` → HTTP 400. A pending ticket from a **past day** cannot be cancelled either (same exception, HTTP 400).

## Business rules

| Rule | Where |
|------|-------|
| "Today" = the date of the injected `Clock` in the zone `turnos.timezone` (default `America/Bogota`). Queue, dashboard, metrics and `siguiente` only see today's tickets. Older days stay in the database but are invisible | `TurnoService`, `TurnosConfig` |
| Queue = today's pending tickets sorted by `horaSolicitud`, ties broken by `id` (FIFO) | `TurnoService.FIFO` |
| New ticket number = highest number **of today** + 1. The first ticket of a day is `T-001`: numbering restarts every day | `TurnoService.solicitar` |
| Two simultaneous requests may compute the same number. The unique constraint rejects the loser (`NumeroDuplicadoException`) and the service recomputes and retries, up to **10 attempts** (`maxIntentos`). If all fail → `ConflictoException` → HTTP 409 | `TurnoService.solicitar` |
| Updates (attend, cancel) use optimistic locking on `version`. If the ticket changed since it was read, the write fails with `ConflictoException` → HTTP 409 | `JpaTurnoRepository` |
| Cancelling a pending ticket from a past day is rejected → HTTP 400 `OPERACION_INVALIDA` | `Turno.cancelar` |
| Request/attend time = server clock, truncated to seconds | `Turno` |
| Average wait = mean of (`horaAtencion − horaSolicitud`) over **attended** tickets of today only, in minutes, 1 decimal | `TurnoService.esperaPromedioMin` |
| Concurrency is resolved by the database (unique constraint + version), not by `synchronized` locks | `JpaTurnoRepository` |
| A duplicate number is recognised by the **constraint name**, compared case-insensitively (H2 reports `UK_TURNO_FECHA_NUMERO`, PostgreSQL `uk_turno_fecha_numero`). Other integrity violations are not reported as duplicates | `JpaTurnoRepository` |
| If the database cannot be reached (connection acquisition fails or times out), the adapter raises `ServicioNoDisponibleException` → HTTP 503. It is never retried | `JpaTurnoRepository`, `ApiExceptionHandler` |
| The CSV is **demo-only**: it is loaded only under the `demo` Spring profile, dated today, and skipped if today already has tickets (no duplicates on restart) | `DemoSeedRunner`, `CsvSeedLoader` |

With the demo seed: pending 5, attended 5, cancelled 1, average wait 12.2 min. Numbers run `T-001` to `T-011`, so the next requested ticket is `T-012`. Without the `demo` profile the database starts empty.

## API contract (v1)

Base path: `/api/v1/turnos`. Times are formatted as text like `"08:25 a. m."`. States are labels: `"Pendiente"`, `"Atendido"`, `"Cancelado"`.

| Method & path | Used by | Success | Errors |
|---------------|---------|---------|--------|
| `POST /` | Client | 201 + ticket + `posicionFila` | 409 `CONFLICTO` |
| `PATCH /{id}/cancelar` | Client | 200 + ticket + `mensaje` | 404 `TURNO_NO_ENCONTRADO`, 400 `OPERACION_INVALIDA`, 409 `CONFLICTO` |
| `GET /dashboard` | Both | 200 + `metricas`, `siguienteTurno`, `turnos[]` | — |
| `PATCH /siguiente` | Advisor | 200 + served ticket | 404 `FILA_VACIA`, 409 `CONFLICTO` |

Error body: `{ "error": "FILA_VACIA", "mensaje": "No hay personas esperando en la fila." }`

| Status | `error` | When |
|--------|---------|------|
| 404 | `FILA_VACIA` / `TURNO_NO_ENCONTRADO` | Empty queue / unknown id |
| 400 | `OPERACION_INVALIDA` | Ticket is not pending, or it belongs to a past day |
| 409 | `CONFLICTO` | Concurrent change: the ticket changed while being processed, or no free number after 10 attempts |
| 503 | `SERVICIO_NO_DISPONIBLE` | The database is unreachable. Fails within the Hikari connection timeout (5 s). The message is Spanish and never contains driver or SQL details |

The 409 message is exactly: `El turno cambió mientras se procesaba. Actualiza e intenta de nuevo.` The client should refresh and try again.

The 503 message is exactly: `El servicio no está disponible en este momento. Intenta de nuevo en unos minutos.` Refreshing does not help; the service must come back first.

Dashboard example (shape):

```json
{
  "metricas": { "pendientes": 5, "atendidos": 5, "cancelados": 1, "esperaPromedioMinutos": 12.2 },
  "siguienteTurno": { "id": 6, "codigo": "T-006", "numero": 6, "horaSolicitud": "08:25 a. m.", "estado": "Pendiente" },
  "turnos": [ { "id": 1, "codigo": "T-001", "numero": 1, "horaSolicitud": "08:02 a. m.", "horaAtencion": "08:05 a. m.", "estado": "Atendido" } ]
}
```

In `turnos[]`, unattended tickets show `horaAtencion: "-"`.

## Frontend

| Piece | What it does |
|-------|--------------|
| `App.jsx` | Switches views by URL: `/` = client, `/?vista=asesor` = advisor |
| `services/turnosService.js` | The only place that calls the API. Normalizes responses for the views. Maps `CONFLICTO` to a friendly, refreshable error and `SERVICIO_NO_DISPONIBLE` (503) to an error carrying the server message (a Spanish fallback if the body has none) and `code`, not refreshable. |
| `views/ClienteView.jsx` | Request ticket, see number/state/position, cancel (with confirm), manual refresh, stats. On a cancel conflict it re-fetches the ticket and keeps the message |
| `views/AsesorView.jsx` | Next ticket card, "call next" button, full table with search by number and filter by state, loading/error/empty states. On a conflict it refreshes the panel, then shows the message (no retry button) |
| `services/turnosSimulation.js` | Old offline mock. **Not used** anymore. |
| `utils/turnoFormat.js` | Formatting helpers (labels, `T-000`, minutes in `es-CO`) |

**Container/presentational note.** The service layer already isolates HTTP from the views — good. The views themselves still mix data fetching, state, and markup in one component; that is fine at this size but is a refactor candidate as features grow.

## Tests

Backend: **147 tests**, `./mvnw test`. The 17 PostgreSQL tests need Docker (see below); everything else runs without it.

| Suite | Tests | Covers |
|-------|-------|--------|
| `TurnoServiceTest` | 22 | FIFO, daily numbering, midnight boundary, yesterday excluded, retry exhaustion, cancel rules, stale write |
| `TurnoTest` | 7 | Domain rules: state machine, equality by id, past-day cancel |
| `JpaTurnoRepositoryTest` | 8 | H2: Flyway schema + `validate`, time precision, duplicate number, unrelated violations, version conflict |
| `JpaTurnoRepositoryTranslationTest` | 20 | Duplicate detection on fabricated exception chains (upper, lower, mixed, quoted, schema-qualified, no name); DB-unreachable translation to `ServicioNoDisponibleException` |
| `DatabaseDownFailFastTest` | 2 | A closed port makes each port call fail within the Hikari timeout + 2 s |
| `SolicitarConcurrencyTest` | 2 | 10 threads requesting at once get numbers 1..10 without gaps or duplicates (H2) |
| `RestartSurvivalTest` | 1 | Tickets survive an application restart on the same H2 file |
| `RetryExhaustionTest` | 2 | Every retry hits a duplicate number: the response is 409 `CONFLICTO` |
| `StartupGuardTest` | 17 | Guard rules (H2 outside `dev`/`test`, `demo` without `dev`, missing URL) as pure rules and as real boots |
| `ConfigBindingTest` | 9 | Env-driven datasource, defaults, pool override, no committed secrets, `V1` untouched, `dev` starts empty |
| `HealthGroupsTest` | 4 | Probes `UP` with a healthy and with an unreachable database; only `health` is exposed |
| `GracefulShutdownTest` | 2 | Graceful shutdown configuration; an in-flight request completes while the context closes |
| `DemoSeedRunnerTest` | 9 | Demo seed per day, idempotent restart, no seed in the default profile |
| `DemoSeedAtomicityTest` | 2 | The demo seed is all-or-nothing |
| `TurnoControllerTest` | 6 | HTTP status codes and JSON shape for the endpoints |
| `ConflictoHandlerTest` | 6 | 409 `CONFLICTO` body on solicitar / siguiente / cancelar; 503 body without driver details; other shapes unchanged |
| `InMemoryTurnoRepositoryTest` | 5 | The test fake behaves like the real port |
| `EnabledIfDockerOrRequiredTest` | 5 | The Docker gate: skip without Docker, fail when `requireDocker=true` |
| `TurnosApiApplicationTests` | 1 | Spring context loads |
| `JpaTurnoRepositoryPostgresTest` | 12 | **PostgreSQL.** The whole repository contract, lowercase constraint name, Flyway `V1` + validation, Bogota time with the JVM in UTC |
| `SolicitarConcurrencyPostgresTest` | 3 | **PostgreSQL.** 10 threads get numbers 1..10 exactly once |
| `RestartSurvivalPostgresTest` | 2 | **PostgreSQL.** Two contexts on one container: tickets 1-11 and their states survive |
| **Backend total** | **147** | `./mvnw test` |
| Frontend `turnosService.test.js` | 11 | Vitest. Response normalization, `CONFLICTO` and `SERVICIO_NO_DISPONIBLE` mapping. No component tests for the views |
| Frontend `e2e/*.spec.js` | 13 | Playwright (Chromium). Both views in a real browser: 9 against the real backend, 4 with mocked responses. See [Browser E2E](#browser-e2e-playwright) |

### Browser E2E (Playwright)

13 tests in `frontend/frontend-react/react-client/e2e/`, run serially in headless Chromium. `npm run e2e` boots the backend (profiles `dev,demo`, in-memory H2) and the Vite dev server, runs the suite, and stops both. No Docker or AWS credentials are needed. Rules: [browser-e2e](../openspec/specs/browser-e2e/spec.md).

| File | Tests | Backend | Covers |
|------|-------|---------|--------|
| `01-cliente.spec.js` | 4 | Real | Request a ticket (`T-012` on a fresh seed, position), cancel and keep, cancel and confirm, stats |
| `02-asesor.spec.js` | 5 | Real | Dashboard load, search by number, filter by status, call next, switch between views |
| `03-respuestas-simuladas.spec.js` | 4 | Mocked | Cancel conflict (409) re-fetches the ticket, advisor conflict reloads without `Reintentar`, a ticket missing from today's queue shows "No encontramos ese turno.", advisor 503 shows `Reintentar` and recovers |

- **Expectations come from the live dashboard.** Position, next ticket and counts are read from `GET /api/v1/turnos/dashboard` in the same test (`e2e/support/api.js`), never hard-coded from the clock or the seed order. Times like `08:25 a. m.` are parsed AM/PM-aware.
- **Mocked tests** use Playwright route interception for the cases the real backend cannot produce on demand (409, 503, a stale ticket).
- **Evidence:** screenshots of the key steps go to `test-results/` (emptied at the start of each run); the HTML report goes to `playwright-report/`. Both folders are git-ignored.

### PostgreSQL test suite and `requireDocker`

The `*PostgresTest` classes subclass the H2 test classes and run them against one shared `postgres:17.10` container (Testcontainers). Docker is detected by `DockerOrRequiredCondition`:

| Docker | `-DrequireDocker` | Result |
|--------|-------------------|--------|
| Available | any | The PostgreSQL tests run |
| Missing | unset or `false` | They are **skipped**; the H2 suite is unaffected |
| Missing | `true` | They **fail**, with a message mentioning `requireDocker` (so a CI run cannot go green by skipping) |

Use `./mvnw test -DrequireDocker=true` when you must be sure the PostgreSQL suite ran. Turning it on in CI is part of the `infra-cicd` change.

The service tests use a fixed zoned `Clock`, so they are deterministic. Keep that pattern.

## Tech versions

Java 17 · Spring Boot 4.1.2 · PostgreSQL 17 (prod) · H2 (file, `dev` only) · Spring Data JPA · Flyway · Actuator · Testcontainers · Maven wrapper · React 19 · Vite 8 · Vitest · Playwright 1.64.1 · ESLint 10 · Terraform 1.16 (`~> 1.11`) · AWS provider 6.69.1 · tflint · trivy · pre-commit

## Configuration and operations

The datasource has **no default URL**: outside the `dev` profile the app needs `SPRING_DATASOURCE_URL`. Nothing secret is committed.

| Setting | Default | Meaning |
|---------|---------|---------|
| `SPRING_DATASOURCE_URL` (env var) | none (required outside `dev`) | PostgreSQL JDBC URL. TLS (`sslmode`) and `socketTimeout` belong in this URL |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` (env vars) | none | Database credentials |
| `TURNOS_DB_POOL_SIZE` (env var) | `10` | Hikari maximum pool size. Pool size x task count must stay below the database `max_connections` |
| `spring.datasource.hikari.connection-timeout` | `5000` ms | Why a DB outage returns 503 within about 5 s |
| `TURNOS_DB_CONNECT_RETRIES` (env var) | `10` | Flyway connect retries at startup |
| `TURNOS_DB_PATH` (env var) | `./data/turnos` | H2 file path (`jdbc:h2:file:${TURNOS_DB_PATH}`), **`dev` profile only**, without extension. The `data/` folder is git-ignored. Use forward slashes on Windows |
| `turnos.timezone` | `America/Bogota` | Zone that defines "today" for the queue |
| `turnos.seed-file` | `classpath:turnos_hoy.csv` | CSV loaded by the `demo` profile |
| `TURNOS_ALLOWED_ORIGINS` (`turnos.allowed-origins`) | localhost `:4200`, `:5173`, `127.0.0.1:5173` | CORS origins |
| `SPRING_PROFILES_ACTIVE` | none | Active profiles |
| `server.shutdown` / `spring.lifecycle.timeout-per-shutdown-phase` | `graceful` / `25s` | In-flight requests finish before the ECS 30 s stop timeout |

### Profiles

| Profile | Database | Notes |
|---------|----------|-------|
| none | PostgreSQL from `SPRING_DATASOURCE_*` | Production. Fails to start if the URL is missing or points to H2 |
| `dev` | H2 file at `TURNOS_DB_PATH` | Zero-Docker local setup, same `V1` migration |
| `demo` | (inherits) | Seeds 11 sample tickets for today. Only allowed together with `dev` |
| `test` | In-memory H2 (unique per context) | Used by the automated tests. Never set it in production |

### Startup guards

`StartupGuard` rejects unsafe setups before Hikari connects or Flyway migrates: no `spring.datasource.url` outside `dev`/`test` (Boot would silently use an embedded database), a `jdbc:h2:` URL outside `dev`/`test`, and `demo` without `dev`. It cannot catch a production task started with `test`; the ECS task definition must prevent that.

### Health endpoints

Only `health` is exposed over HTTP (`/actuator/env`, `/actuator/beans` return 404).

| Path | Includes the database? | Use |
|------|------------------------|-----|
| `/actuator/health/readiness` | No (`readinessState` only) | ALB target health check |
| `/actuator/health/liveness` | No (`livenessState` only) | Liveness probe |
| `/actuator/health` | Yes, as an aggregate status (no details) | Operators. It turns `DOWN` (503) when the database is unreachable |

With the database down the instance stays in rotation: requests that need the database answer `503 SERVICIO_NO_DISPONIBLE` instead of the task being pulled out (an RDS failover must not remove every task).

Infrastructure (Terraform, AWS) is documented in [infra-onboarding.md](infra-onboarding.md).

## How to run

```powershell
# Terminal 1 (local development on H2, no Docker)
cd backend\turnos-api
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"        # http://localhost:8080, empty queue
# or, with the sample queue:
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev,demo"

# custom H2 file (optional, set before starting)
$env:TURNOS_DB_PATH = "C:/data/turnos"

# against PostgreSQL (no profile)
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5432/turnos"
$env:SPRING_DATASOURCE_USERNAME = "turnos"
$env:SPRING_DATASOURCE_PASSWORD = "<password>"
.\mvnw.cmd spring-boot:run

# Terminal 2
cd frontend\frontend-react\react-client
npm install
npm run dev                    # http://localhost:5173
npm test                       # Vitest (frontend)
```

Browser E2E (Playwright), from `frontend\frontend-react\react-client`. It starts its own backend and Vite, so **ports 8080 and 5173 must be free** (stop the terminals above first):

```powershell
npm run e2e:install            # first time only: downloads Chromium
npm run e2e                    # headless, 13 tests
npm run e2e:headed             # visible browser
npm run e2e:ui                 # Playwright UI mode
npm run e2e:report             # open the last HTML report (playwright-report/)
```

`$env:E2E_REUSE = "1"` reuses servers that are already running instead of failing on busy ports. Caveat: the tests assume the fresh demo seed (first new ticket is `T-012`), so a backend that already served requests, or one without `demo`, makes them fail. Use it only against a freshly started `dev,demo` backend.

If a run is hard-killed (the runner process is stopped without cleanup), the servers can stay alive. Free the ports:

```powershell
Get-NetTCPConnection -State Listen -LocalPort 8080,5173 | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }
```

Backend tests: `.\mvnw.cmd test` from `backend\turnos-api` (add `"-DrequireDocker=true"` to fail instead of skipping when Docker is missing).
