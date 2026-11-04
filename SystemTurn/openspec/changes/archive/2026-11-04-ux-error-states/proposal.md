# Proposal: Honest UI Error States (ux-error-states)

## Intent

The advisor view cannot tell unknown data apart from an empty or present queue. It shows "Conectado al servidor", "No hay turnos pendientes" and "0 registros" while the server is down, and after call-next plus a failed refresh it keeps showing the already-called ticket with "Llamar al siguiente" still enabled. The client view keeps a ticket the server reports as absent, so the user cannot request a new one. Both are open UX gaps in `docs/README.md`.

## Scope

### In Scope
- Advisor (A1): an explicit unknown state; clear data on any failed dashboard load, and keep it only while a refresh is in flight. The badge shows the real state ("Conectando con el servidor…" / "Servidor no disponible" / "Conectado al servidor"). On error, the next card, the count and the table show the unavailable copy from the exploration. "Llamar al siguiente" is natively `disabled` when not ready. An empty day shows "Aún no hay turnos registrados hoy." (and keeps "No hay turnos pendientes" + "0 registros").
- Client (B1): `consultarTurno` rejects with the client-side code `TURNO_NO_ENCONTRADO` (message unchanged). `ClienteView` clears the ticket only for that code. A 503 or network failure keeps the last-known ticket.
- Accessibility: badge `role="status"`, exactly one `role="alert"` per view, native `disabled`.
- Tests first (strict TDD): Vitest for the typed error, plus a pure advisor panel-state helper if design keeps it. Playwright goes from 13 to 18 (2 modified, 4 new, 1 accessibility).
- Spec deltas and doc updates (see below).

### Out of Scope
- Hardcoded greeting (P3), the AM/PM position bug (`client-identity`)
- Client 503 handling in the view beyond the guard test
- A stale ticket detected inside the cancel-conflict re-fetch
- Any API or backend change

## Capabilities

### New Capabilities
None

### Modified Capabilities
- `browser-e2e`:
  - MODIFIED "Mocked advisor service-unavailable recovery"
  - ADDED honest advisor load states (loading, error, empty, refresh failure)
  - ADDED client 503 guard
- `daily-queue`: MODIFIED "Stale ticket shows generic not-found". The card is cleared, "Solicitar turno" is offered and the alert is unchanged. The title stays the same.
- `runtime-operations` (optional): a note on "Frontend 503 handling" that the advisor's view-level behavior now lives in `browser-e2e`, while the client's stays deferred.

## Approach

1. Write the tests first and confirm RED: Vitest for `consultarTurno`, then the e2e tests.
2. Add the error code in the service.
3. Model the advisor state, using `turns === null` as unknown.
4. Clear the ticket in the client view only for that code.
5. Update specs and docs last.

## Frontend/Backend Alignment

No API change. `TURNO_NO_ENCONTRADO` exists only on the client side and is never sent by the backend.

## Affected Areas

Frontend root: `frontend/frontend-react/react-client/`.

| Area | Impact | Description |
|------|--------|-------------|
| `src/views/AsesorView.jsx`, `src/asesor.css` | Modified | Panel states, badge, disabled call |
| `src/views/ClienteView.jsx` | Modified | Clear ticket on the not-found code |
| `src/services/turnosService.js` (+ test) | Modified | Typed not-found error |
| `e2e/*.spec.js` | Modified/New | 2 modified, 4 new tests |
| `openspec/specs/*` deltas, `docs/README.md`, `docs/app.md` | Modified | Remove 2 gaps, update counts, roadmap: ux-error-states done, next infra-network |

## Size

Raw ~465 lines (production ~100, tests ~235, specs ~100, docs ~30). Adjusted by history: ~630–790, against a budget of 800. If the tasks phase estimates more than 800, ask the user before splitting.

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Size near 800 | Med | Re-estimate at tasks; ask before splitting |
| StrictMode double GET makes mocks flaky | Med | Promise-gated routes |
| Spanish copy is a test contract | Low | Copy fixed in the spec |
| A transient failure empties the advisor table | Low | Accepted (A1); "Reintentar" recovers |

## Rollback Plan

Revert the single PR. There is no data, API or infra change.

## Dependencies

- Stacked on `feat/e2e-browser-tests` (PR #7)

## Success Criteria

- [x] All 18 Playwright tests and all Vitest tests pass; the new tests were RED before the fix.
- [ ] The advisor never shows "Conectado al servidor" or a stale next ticket after a failed load.
- [ ] A stale client ticket clears, and "Solicitar turno" works again.
- [ ] A 503 on refresh keeps the client ticket.
