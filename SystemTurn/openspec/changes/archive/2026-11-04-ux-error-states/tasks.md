# Tasks: Honest UI Error States (ux-error-states)

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~465 raw (incl. a11y test), ~630-790 with history; review budget 800 |
| 400-line budget risk | Medium (exceeds 400, under the 800 project budget) |
| Chained PRs recommended | No |
| Suggested split | Single PR (stacked on feat/e2e-browser-tests, PR #7) |
| Delivery strategy | single-pr |
| Chain strategy | stacked-to-main |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: stacked-to-main
400-line budget risk: Medium

Staging: only `SystemTurn/frontend/frontend-react/react-client` and `SystemTurn/docs`. Never `SystemTurn/infra/` or `openspec/changes/`. Paths below are relative to `react-client` unless noted.

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|-----------|----------------------|-----------------|-------------------|
| 1 | RED tests | PR 1 (commit 1) | `npm test` | `npm run e2e` | tests only |
| 2 | Impl to GREEN | PR 1 (commit 2) | `npm test` | `npm run e2e` -> 18 passed | src/ files |
| 3 | Docs | PR 1 (commit 3) | N/A (docs) | N/A | docs/ |

## Phase 1: RED tests (commit 1)

- [x] 1.1 `src/services/turnosService.test.js`: absent ticket rejects `{message:'No encontramos ese turno.', code:'TURNO_NO_ENCONTRADO'}` (spec "consultarTurno rejects with the not-found code"). RED.
- [x] 1.2 Same file: "consultarTurno resolves a present ticket"; "503 is not converted to the not-found code" (message = `mensaje`, code differs).
- [x] 1.3 Create `src/utils/panelAsesor.test.js`: 5 cases (cargando, error, listo with data, empty day, filter no match). RED: module missing.
- [x] 1.4 `e2e/03-respuestas-simuladas.spec.js` MOD "advisor 503 shows Reintentar and recovers": status badge, three copy strings, absent old strings, disabled button, post-Reintentar recovery.
- [x] 1.5 MOD "Browser holds a ticket missing from today's queue": no "Pendiente", empty-card copy, Solicitar enabled, Cancelar/Actualizar count 0.
- [x] 1.6 NEW "Advisor loading state is not reported as connected" (held routes, `waitForRequest` first).
- [x] 1.7 NEW "Advisor empty queue is reported as empty" (`turnos:[]`, `siguienteTurno:null`).
- [x] 1.8 NEW "Advisor refresh failure discards stale data" (PATCH 200 `{}`, dashboard 503, no real mutation).
- [x] 1.9 NEW "Client keeps its ticket when refresh fails" (real ticket, dashboard 503, strict alert).
- [x] 1.10 NEW "status, single alert and native disabled": mocked 503 advisor; `getByRole('status')`, strict `getByRole('alert')`, `toBeDisabled()`.
- [x] 1.11 Run `npm test` and `npm run e2e`; record RED reasons (1.9 and 1.10 may pass pre-fix).
- [x] 1.12 Mutation proof: temporarily clear the ticket on every error in `ClienteView.jsx`; confirm 1.9 fails; revert. Likewise confirm 1.10 fails (no `role=status`) on the unfixed code, or mutate the badge role.

## Phase 2: Implementation (commit 2)

- [x] 2.1 `src/services/turnosService.js`: export `TURNO_NO_ENCONTRADO`; set `code` on the absent-ticket error; delete the `isDemoMode` export.
- [x] 2.2 Create `src/utils/panelAsesor.js` with `estadoPanel` per the design table.
- [x] 2.3 `src/views/AsesorView.jsx`: `turns` null = unknown; clear `turns`/`nextTurn` in the `aplicarResultado` failure branch; use `estadoPanel` for badge (`role="status"` + class), card, count, cell; drop the `isDemoMode` import and the appended loading row.
- [x] 2.4 `src/views/ClienteView.jsx`: in the `ejecutar` catch, on `TURNO_NO_ENCONTRADO` only, `setTurno(null)` and `setConfirmarCancelacion(false)`.
- [x] 2.5 `src/asesor.css`: rename `.data-mode.demo` to `.cargando`; add `.data-mode.error` (`#fff0f0`/`#b34e4e`).

## Phase 3: GREEN verification

- [x] 3.1 `npm test`: all Vitest pass (19 expected).
- [x] 3.2 `npm run e2e`: confirm "18 passed" by count, not exit code.
- [x] 3.3 `npm run lint` and `npm run build` clean.
- [x] 3.4 Review error-state screenshots from `captura`.

## Phase 4: Docs (commit 3)

- [x] 4.1 `SystemTurn/docs/README.md`: remove the 2 UX gap rows; Dead-code lists only `turnosSimulation.js`; Vitest 19, Playwright 18; roadmap `ux-error-states` done, next `infra-network`.
- [x] 4.2 `SystemTurn/docs/app.md`: Frontend states (AsesorView, ClienteView clear); test counts; Browser E2E list, 03 = 9 tests, total 18.
- [x] 4.3 Unstaged: update openspec counts 17 to 18 in `specs/browser-e2e/spec.md` line 3 and `design.md`; spec line 90 and Open Question 1 are resolved.

## Spec-to-test map

| Scenario | Test |
|---|---|
| advisor 503 shows Reintentar and recovers | e2e MOD (1.4) |
| Browser holds a ticket missing... | e2e MOD (1.5) |
| Advisor loading / empty / refresh failure | e2e (1.6-1.8) |
| Client keeps its ticket when refresh fails | e2e (1.9) |
| status, single alert and native disabled | e2e (1.10) |
| 3 consultarTurno scenarios | Vitest (1.1-1.2) |
| Service maps 503 / 409 unchanged | existing Vitest, unchanged |
