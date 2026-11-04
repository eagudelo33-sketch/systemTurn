# Apply Progress: ux-error-states (Phases 1-3)

Mode: Strict TDD. Branch feat/ux-error-states. Committed: 1d217c3 (fix + tests), 80e9529 (docs). Phase 4 done.

## TDD Cycle Evidence

| Task | Test file | Layer | Safety net | RED | GREEN | Triangulate | Refactor |
|------|-----------|-------|------------|-----|-------|-------------|----------|
| 1.1 | `src/services/turnosService.test.js` | Unit | 11/11 | Written; failed: `expected Error: No encontramos ese turno. to match object` (`code` undefined, `TURNO_NO_ENCONTRADO` not exported) | 19/19 | Paired with 1.2 (found / absent / 503) | None needed |
| 1.2 | `src/services/turnosService.test.js` | Unit | 11/11 | "resolves a present ticket" and "503 not converted" passed pre-fix (guards on unchanged behavior); the code export made the 503 assertion meaningful only after 2.1 | 19/19 | 3 cases, 3 code paths | None needed |
| 1.3 | `src/utils/panelAsesor.test.js` | Unit | N/A (new) | Written; failed: module `./panelAsesor.js` missing (suite failed to import) | 19/19 | 5 cases (cargando, error, listo, empty day, filter no match) | None needed |
| 1.4 | `e2e/03-respuestas-simuladas.spec.js` | E2E | 13/13 | Failed: `getByRole('status')` element(s) not found (badge had no role) | 18/18 | Pre-503 + post-Reintentar recovery in one test | None needed |
| 1.5 | same | E2E | 13/13 | Failed: card still `Tu turnoT-016Pendiente...Cancelar turno`, expected "Aún no tienes un turno asignado." | 18/18 | Single scenario | None needed |
| 1.6 | same | E2E | N/A (new) | Failed: `getByRole('status')` not found (loading badge) | 18/18 | Single scenario | None needed |
| 1.7 | same | E2E | N/A (new) | Failed: `getByRole('status')` not found. Note: the empty-day cell assertion was not reached pre-fix | 18/18 | Complements 1.3 empty-day unit case | None needed |
| 1.8 | same | E2E | N/A (new) | Failed: stale code `T-008` still in the region (expected count 0, received 1) | 18/18 | Single scenario | None needed |
| 1.9 | same | E2E | N/A (new) | Passed pre-fix (guard). Mutation: `setTurno(null)` on every error in `ClienteView.jsx` made it fail: `Expected substring: "Pendiente"`, `Received: "Tu turno—Sin turno..."`. Reverted, clean diff | 18/18 | Mutation proves it can fail | None needed |
| 1.10 | same | E2E | N/A (new) | Failed: `getByRole('status')` not found | 18/18 | Single scenario | None needed |
| 1.11 | all | Unit+E2E | - | Vitest 13 passed, 1 failed, 1 suite failed to load; Playwright 6 failed, 12 passed | - | - | - |
| 1.12 | `ClienteView.jsx` | Mutation | - | See 1.9; 1.10 failed unfixed (no `role=status`) | - | - | - |
| 2.1-2.5 | src files | - | - | - | Vitest 19/19, Playwright 18/18 | - | None needed |

## Test Summary

- New tests: Vitest 8 (3 consultarTurno + 5 estadoPanel), Playwright 5 (4 new + a11y; 2 modified).
- Layers: Unit 8, E2E 7 (5 new, 2 modified).
- Pure functions created: 1 (`estadoPanel`).

## Work Unit Evidence

| Evidence | Value |
|---|---|
| Focused test command | `npm test`: 2 files, 19 passed (baseline 11) |
| Runtime harness | `npm run e2e`: 18 passed (baseline 13), ports 8080/5173 free and no java after |
| Rollback boundary | `react-client/src` + `react-client/e2e` files listed below |

Also: `npm run lint` clean, `npm run build` ok.

## Tasks

Phases 1, 2, 3 complete (see tasks.md). Phase 4 (docs) pending.

## Files Changed

src: `services/turnosService.js`, `utils/panelAsesor.js` (new), `views/AsesorView.jsx`, `views/ClienteView.jsx`, `asesor.css` (79 changed lines).
tests: `services/turnosService.test.js`, `utils/panelAsesor.test.js` (new), `e2e/03-respuestas-simuladas.spec.js` (221 changed lines).

## Deviations

None. Unit test names for `estadoPanel` are descriptive (the spec has no scenario names for them). Visual review of the 503 and loading screens done: badge colors and copy match design.

## Open (design) items resolved

- Held routes in the loading test are aborted at the end of the test; teardown is clean.

## Phase 4 (orchestrator)

| Task | Evidence |
|------|----------|
| 4.1 docs/README.md | 2 UX gap rows removed; Dead code lists only `turnosSimulation.js`; status Vitest 19 / Playwright 18; roadmap: ux-error-states ✅, infra-network ⏭️ Next |
| 4.2 docs/app.md | AsesorView/ClienteView rows describe the new states; Vitest 19, e2e 18, `03` = 9 tests |
| 4.3 openspec counts | spec line 3 and design rows say 18 (done before apply) |
| Links | 60 relative links, 0 broken |
