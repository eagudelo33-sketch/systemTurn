# Exploration: ux-error-states

Fix two UI bugs found in the `e2e-browser-tests` screenshots, with the browser tests updated first to expect correct behavior. No API or backend change.

## Root cause

Both views cannot tell **unknown** from **empty** or **present**:

- **Advisor (`AsesorView.jsx`)** — initial state `turns=[]`, `nextTurn=null` equals a real empty queue. The badge is `isDemoMode ? 'Modo demostración' : 'Conectado al servidor'` and `isDemoMode` is a constant `false`, so it never reflects the connection. Next-card text and the "{n} registros" count render on `error` and on the first `loading` frame. On load failure the old `turns`/`nextTurn` are kept.
  - **Extra hazard (not in the screenshots):** call-next succeeds, then the refresh fails → `status='error'` while the *already-called* ticket is still shown as next and "Llamar al siguiente" stays enabled (disabled only by `!nextTurn || isCalling || status==='loading'`).
  - Table: `ready` with zero visible rows shows "No hay turnos que coincidan con la búsqueda." even on a genuinely empty day; `error` shows no row.
- **Client (`ClienteView.jsx` + `turnosService.js`)** — `consultarTurno` throws `new Error('No encontramos ese turno.')` with no `code`; `ejecutar` sets the message but keeps `turno`, so the card stays Pendiente with a position, "Ya tienes un turno" and "Cancelar turno". The same path serves a 503/network failure on "Actualizar estado", and the code cannot distinguish *absent* (definitive) from *could not ask* (unknown).

## Options

**Advisor on failure**

| Option | Behavior | Pros | Cons |
|--------|----------|------|------|
| **A1 (recommended)** | Unknown = `turns === null`; clear on any failed load, keep data while a refresh is loading | One rule; removes the stale-next hazard; badge derives from "has data" | A transient failure empties the table |
| A2 | Keep last data, mark "desactualizado", disable call | Continuity | Timestamp/flag, more copy and tests; after a successful call-next the held data is known wrong |
| A3 | Copy-only fix | Smallest | Leaves the stale-next hazard and the enabled button |

**Client stale ticket**

| Option | Behavior | Pros | Cons |
|--------|----------|------|------|
| **B1 (recommended)** | Typed client-side error `TURNO_NO_ENCONTRADO`; view clears `turno` only for that code; card back to empty, "Solicitar turno" enabled, alert stays | Truthful and recoverable; matches `client-identity` (a stored-but-absent ticket would be dropped) | The lost ticket number is no longer visible |
| B2 | Keep the card with a synthetic "No encontrado" state | Shows which ticket was lost | New status across card, row, labels; `client-identity` would remove it |
| B3 | Hide only the position | Tiny | Still shows Pendiente, user stays stuck |

**Client 503:** the typed split keeps a 503 on "Actualizar estado" as "unknown → keep the ticket"; the view already shows the 503 message. Leave client 503 handling to `client-identity`, but add one guard e2e test (a 503 refresh keeps the ticket) so a regression that clears on any error cannot pass.

## Proposed UX and Spanish copy

Advisor:

| Element | Loading, no data | Error | Ready |
|---------|------------------|-------|-------|
| Badge | "Conectando con el servidor…" | "Servidor no disponible" | "Conectado al servidor" |
| Next card text | "Cargando el siguiente turno…" | "No se pudo consultar el siguiente turno." | existing |
| Count | "Cargando…" | "Registros no disponibles" | "{n} registros" |
| Table row | "Cargando turnos…" (existing) | "No se pudieron cargar los turnos." | existing; genuinely empty day: "Aún no hay turnos registrados hoy." |

Turn number "—" and "Llamar al siguiente" natively `disabled` in both non-ready states. Alert + "Reintentar" unchanged.

Client: alert stays exactly "No encontramos ese turno." (spec + `/vencid|expir|ayer/` guard depend on it); card falls back to "Aún no tienes un turno asignado."; button "Solicitar turno".

## Accessibility

Badge `role="status"`; exactly one `role="alert"` per view (tests use strict `getByRole('alert')`); native `disabled` on the call button; focus drop when the client card unmounts is acceptable (alert announces).

## Test impact (strict TDD: tests first, RED, then fix)

- **Vitest:** `consultarTurno` found → normalized; absent → rejects with code `TURNO_NO_ENCONTRADO` and the same message; 503 → unchanged. Optional pure helper for advisor panel state (4 tests).
- **Playwright 13 → 17:**
  - MODIFIED "advisor 503 shows Reintentar and recovers": also assert no "Conectado al servidor"/"No hay turnos pendientes"/"0 registros", "Servidor no disponible" visible, call button disabled, "No se pudieron cargar los turnos." cell; after recovery the badge is back.
  - MODIFIED "Browser holds a ticket missing from today's queue": alert unchanged; card no longer Pendiente, "Solicitar turno" enabled, "Cancelar turno"/"Actualizar estado" absent; optionally request a new ticket.
  - NEW advisor loading (promise-gated route; StrictMode → two GETs).
  - NEW advisor genuinely empty queue (mock 200 empty) — proves no over-correction.
  - NEW advisor refresh failure discards stale data (mock PATCH `siguiente` 200 `{}` — no real mutation — and dashboard 503).
  - NEW client keeps its ticket when refresh fails (real ticket, then dashboard 503).
- Gotcha: the error-state table has header + message rows → row-count assertions become cell-text assertions.

## Spec impact

- `browser-e2e`: MODIFIED "Mocked advisor service-unavailable recovery" (stays here; must not claim `runtime-operations`); ADDED honest advisor load states (loading, error, empty, refresh failure); ADDED client 503 guard.
- `daily-queue`: MODIFIED "Stale ticket shows generic not-found" (card cleared, "Solicitar turno" offered, alert unchanged; title unchanged).
- `runtime-operations`: optional note that advisor view-level 503 behavior lives in `browser-e2e`, client still deferred.
- `concurrency-conflicts`: unchanged.
- Docs: `docs/README.md` (remove two gaps, counts, roadmap), `docs/app.md` (Frontend, Browser E2E).

## Alignment

No API change. `TURNO_NO_ENCONTRADO` is a client-side code only.

## Affected areas

`src/views/AsesorView.jsx`, `src/views/ClienteView.jsx`, `src/services/turnosService.js` (code on the absent-ticket error; `isDemoMode` import leaves `AsesorView`), `src/asesor.css` (badge states), `src/services/turnosService.test.js`, `e2e/03-respuestas-simuladas.spec.js` (+ possibly a new spec), 3–4 specs, 2 docs.

## Out of scope

Hardcoded greeting (P3, not adjacent); AM/PM position bug (`client-identity`, needs ISO times); client 503 view handling (guard test only); stale ticket detected inside the cancel-conflict re-fetch (needs two mocks; `client-identity` replaces `consultarTurno`).

## Size

Production ~100 · tests ~235 (Vitest ~85, e2e ~150) · specs ~100 · docs ~30 → raw ~465; with the 1.35–1.7× history ≈ 630–790 (tight vs 800). Trim candidates (~100): optional helper + its tests, empty-queue contrast test, loading test. Must-have: both fixes + the 2 modified tests + refresh-failure test + client guard.

## Recommendation

A1 + B1, one PR, tests first (Vitest `consultarTurno`, then e2e; confirm RED), then service code, advisor state, client clear; specs and docs last.

## Risks

Size near 800 · mock tests depend on StrictMode double-loading (keep promise-gated) · Spanish copy is a test contract · A1 deliberately clears on any failed load (vs A2) · grep consumers of `isDemoMode` at design time.

## Decision for the user

Include the optional items (pure helper, empty-queue contrast test, loading test) or not, given the size risk.
