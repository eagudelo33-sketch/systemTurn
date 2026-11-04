# Tasks: Browser E2E Test Suite

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~860 (530 authored + 269 spec + ~60 lockfile) |
| 400-line budget risk | High |
| Chained PRs recommended | No (size:exception accepted by user) |
| Suggested split | Single PR, reviewable commits per unit |
| Delivery strategy | exception-ok |
| Chain strategy | size-exception |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: size-exception
400-line budget risk: High

Staging rule: stage only `SystemTurn/frontend`, `SystemTurn/docs` (NOT `infra-onboarding.md`), `SystemTurn/README.md`, `SystemTurn/openspec/config.yaml`. NEVER root `.gitignore`, `SystemTurn/infra/`, `openspec/changes/`.

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|-----------|----------------------|-----------------|-------------------|
| 1 | Spike (no commit) | PR 1 | `npx playwright test` (trivial) | `netstat -ano \| findstr :8080` empty after run | none (throwaway) |
| 2 | Tooling | PR 1 | `npm test`; `npm run lint` | `npx playwright test --list` shows no `src/` | package*.json, vite/eslint config, .gitignore |
| 3 | Harness + real tests 1-9 | PR 1 | `npm run e2e` | fresh run, 9 pass | playwright.config.js, e2e/support, 01/02 specs |
| 4 | Mocked tests 10-13 | PR 1 | `npm run e2e` | 13 pass | 03 spec |
| 5 | Docs + config | PR 1 | `rg getByTestId e2e`; inspection | N/A (docs) | docs, READMEs, config.yaml |

## Phase 1: Spike (Windows 10, no commit)

- [x] 1.1 Install `@playwright/test@1.64.1` + Chromium; trivial test passes.
- [x] 1.2 Prove webServer starts `mvnw.cmd spring-boot:run` with `...process.env` merged (JAVA_HOME/PATH), `dev,demo`, H2 mem URL, readiness wait.
- [x] 1.3 After run, `netstat -ano | findstr :8080` shows no listener. If ANY step fails, STOP and report.

## Phase 2: Tooling (commit 1)

- [x] 2.1 RED: `npm test` collects an e2e spec; `npm run lint` flags `process` no-undef.
- [x] 2.2 `npx -y npm@11.21.0 install -D -E @playwright/test@1.64.1`; add 5 scripts. Check exact pin.
- [x] 2.3 `vite.config.js` `test.include: ['src/**/*.test.{js,jsx}']`; eslint ignores + e2e block.
- [x] 2.4 `react-client/.gitignore` three output paths; `git check-ignore` both.

## Phase 3: Harness + real-backend tests (commit 2)

- [x] 3.1 RED: write `e2e/01-cliente.spec.js` titled "client requests a ticket" with no config; see it fail (no webServer/baseURL), not "0 tests".
- [x] 3.2 GREEN: `playwright.config.js` per design (serial, one chromium project, reuse via `E2E_REUSE`).
- [x] 3.3 `e2e/support/api.js`: `leerDashboard`, AM/PM-aware `minutosDelDia`, `posicionEsperada`, predicates; `capturas.js`.
- [x] 3.4 Per test, RED then GREEN, titles = spec names: "client cancel keep", "client cancel confirm", "client stats".
- [x] 3.5 `e2e/02-asesor.spec.js` order: "advisor loads the dashboard", "advisor searches by number", "advisor filters by status", then "advisor calls next", then "view switch".
- [x] 3.6 Evidence: Playwright exit 0 AND list reporter shows 9 passed (guard against "0 tests ran"); screenshots in `test-results/`.

## Phase 4: Mocked tests (commit 3)

- [x] 4.1 RED then GREEN each in `e2e/03-respuestas-simuladas.spec.js`: "Client cancel conflict re-fetches" (409, flag, re-fetch).
- [x] 4.2 "Advisor conflict reloads without retry" (409, no `Reintentar`).
- [x] 4.3 "Browser holds a ticket missing from today's queue" (`route.fetch` filtered, `No encontramos ese turno.`).
- [x] 4.4 "advisor 503 shows Reintentar and recovers" (mock until `unroute`).
- [x] 4.5 Full `npm run e2e`: 13 passed; `rg getByTestId e2e` empty; `npm test`, `npm run lint`, `npm run build` green.

## Phase 5: Docs and config (commit 4)

- [x] 5.1 `docs/app.md`: Tests e2e row; How to run (install, run, report, ports, `E2E_REUSE=1`, cleanup).
- [x] 5.2 `docs/README.md`: view-tests gap row updated; NEW gap row for client position AM/PM bug owned by `client-identity`; `client-identity` obligation rewritten to "upgrade the stale-ticket test once persistence exists"; NEW P3 gap row: advisor greeting "Buenos días, asesor" is hardcoded regardless of time of day (found in e2e screenshots; owner: any change touching AsesorView); NEW P2 gap rows found in e2e screenshots, owner ux-error-states (next change, user decision): (a) advisor 503 still shows "Conectado al servidor", "No hay turnos pendientes" and "0 registros" (claims an empty queue when the state is unknown); (b) stale ticket shows "No encontramos ese turno." while the card still shows the ticket as Pendiente with a position; `infra-cicd` row; roadmap.
- [x] 5.3 `SystemTurn/README.md` Spanish e2e instructions + Playwright.
- [x] 5.4 `openspec/config.yaml` testing section per design; remove GAP text.
- [x] 5.5 Final check: diff on `frontend/src`/`backend` empty; staging rule honored.
