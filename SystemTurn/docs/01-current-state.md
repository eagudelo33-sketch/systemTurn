# Current State (as of 2026-11-02)

What the code does **today**, verified by reading the source. When this disagrees with the whiteboard or diagram, the code wins here; the differences are listed in [02-gaps-and-questions.md](02-gaps-and-questions.md).

## At a glance

| Area | Status |
|------|--------|
| Backend API (4 endpoints) | ✅ Working, tested |
| Advisor view | ✅ Working against real API |
| Client view | ✅ Working against real API (with limitations) |
| Database | ✅ H2 file database + Spring Data JPA + Flyway. Data survives restarts |
| Real-time updates | ❌ Not implemented — manual refresh |
| Frontend tests | ✅ Vitest, service level only (no component tests for the views) |
| Postman collection | ❌ Not in repo |

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

**Why this layering matters (hexagonal architecture).** The `domain` knows nothing about Spring, HTTP, or storage. `TurnoRepository` is an *interface* (a "port"); `JpaTurnoRepository` (in `infrastructure/persistence`) is one *implementation* (an "adapter"). That is how memory was replaced by H2 without rewriting the business rules: only a new adapter was added. Protect this boundary. The unit tests use an in-memory fake that lives in the test sources only (`testsupport/InMemoryTurnoRepository`).

The schema is owned by Flyway (`db/migration/V1__create_turno.sql`). Hibernate only validates it (`ddl-auto: validate`); it never creates or alters tables.

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

## Business rules (implemented)

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

The 409 message is exactly: `El turno cambió mientras se procesaba. Actualiza e intenta de nuevo.` The client should refresh and try again.

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
| `services/turnosService.js` | The only place that calls the API. Normalizes responses for the views. Maps `CONFLICTO` to a friendly, refreshable error. |
| `views/ClienteView.jsx` | Request ticket, see number/state/position, cancel (with confirm), manual refresh, stats. On a cancel conflict it re-fetches the ticket and keeps the message |
| `views/AsesorView.jsx` | Next ticket card, "call next" button, full table with search by number and filter by state, loading/error/empty states. On a conflict it refreshes the panel, then shows the message (no retry button) |
| `services/turnosSimulation.js` | Old offline mock. **Not used** anymore. |
| `utils/turnoFormat.js` | Formatting helpers (labels, `T-000`, minutes in `es-CO`) |

**Container/presentational note.** The service layer already isolates HTTP from the views — good. The views themselves still mix data fetching, state, and markup in one component; that is fine at this size but is a refactor candidate as features grow.

## Tests

| Suite | Tests | Covers |
|-------|-------|--------|
| `TurnoServiceTest` | 22 | FIFO, daily numbering, midnight boundary, yesterday excluded, retry exhaustion, cancel rules, stale write |
| `TurnoTest` | 7 | Domain rules: state machine, equality by id, past-day cancel |
| `JpaTurnoRepositoryTest` | 6 | Flyway schema + `validate`, time precision, duplicate number, version conflict |
| `SolicitarConcurrencyTest` | 2 | 10 threads requesting at once get numbers 1..10 without gaps or duplicates |
| `RestartSurvivalTest` | 1 | Tickets survive an application restart on the same file database |
| `DemoSeedRunnerTest` | 9 | Demo seed per day, idempotent restart, no seed in the default profile |
| `TurnoControllerTest` | 6 | HTTP status codes and JSON shape for the endpoints |
| `ConflictoHandlerTest` | 4 | 409 `CONFLICTO` body on solicitar / siguiente / cancelar |
| `InMemoryTurnoRepositoryTest` | 5 | The test fake behaves like the real port |
| `TurnosApiApplicationTests` | 1 | Spring context loads |
| **Backend total** | **63** | `./mvnw test` |
| Frontend `turnosService.test.js` | 8 | Vitest. Response normalization and `CONFLICTO` mapping. No component tests for the views |

The service tests use a fixed zoned `Clock`, so they are deterministic. Keep that pattern.

## Tech versions

Java 17 · Spring Boot 4.1.2 · H2 (file) · Spring Data JPA · Flyway · Maven wrapper · React 19 · Vite 8 · Vitest · ESLint 10

## Configuration

| Setting | Default | Meaning |
|---------|---------|---------|
| `TURNOS_DB_PATH` (env var) | `./data/turnos` | Path of the H2 database file (`jdbc:h2:file:${TURNOS_DB_PATH}`), without extension. The `data/` folder is git-ignored. Use forward slashes on Windows |
| `turnos.timezone` | `America/Bogota` | Zone that defines "today" for the queue |
| `turnos.seed-file` | `classpath:turnos_hoy.csv` | CSV loaded by the `demo` profile |
| Spring profile `demo` | off | Loads the sample queue dated today (see business rules) |
| `turnos.allowed-origins` | localhost `:4200`, `:5173` | CORS origins |

## How to run

```powershell
# Terminal 1
cd backend\turnos-api
.\mvnw.cmd spring-boot:run     # http://localhost:8080, empty queue
# or, with the sample queue (demo profile):
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo"

# custom database file (optional, set before starting)
$env:TURNOS_DB_PATH = "C:/data/turnos"

# Terminal 2
cd frontend\frontend-react\react-client
npm install
npm run dev                    # http://localhost:5173
npm test                       # Vitest (frontend)
```

Backend tests: `.\mvnw.cmd test` from `backend\turnos-api`.
