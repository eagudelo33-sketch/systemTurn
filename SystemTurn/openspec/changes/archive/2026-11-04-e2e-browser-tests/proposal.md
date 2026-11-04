# Proposal: Browser E2E Test Suite

## Intent

Nothing proves today that the client and advisor views work end to end through a real browser, proxy, and backend. Three view scenarios (advisor conflict reload, client cancel conflict re-fetch, stale ticket "not found") have been deferred since `persistence-daily-queue` with no proof. This change adds a permanent, CI-ready Playwright suite that gives pass/fail plus screenshots, and proves those three scenarios now.

## Scope

### In Scope
- Playwright Test `1.64.1` (exact pin), Chromium only, headless, in `react-client/e2e/` with its own `playwright.config.js`.
- `npm run e2e` boots the backend (`mvnw`, `dev,demo`, in-memory H2 via `SPRING_DATASOURCE_URL`) and Vite dev through a `webServer` array. `reuseExistingServer: false`; reuse only through an opt-in env variable.
- Serial run (`workers: 1`). Expectations come from the live dashboard API, so results do not depend on the time of day.
- Real-backend tests: client request (`T-012` on a fresh DB, position from the API), cancel (keep vs. confirm), client stats, advisor load, call next, search, filter, view switch.
- Route-mocked tests: advisor 409 reload, client cancel 409 re-fetch, stale ticket generic not-found, advisor 503 + "Reintentar".
- Role/text selectors, named screenshots at key steps and on failure, trace on first retry, HTML report with `open: 'never'`, report/results gitignored.
- Docs: run instructions in `SystemTurn/README.md` and `docs/app.md`. Update the roadmap, gaps, and `client-identity` obligation in `docs/README.md`, plus the `openspec/specs/README.md` tables.

### Out of Scope
- CI workflow (`infra-cicd` owns it; recorded as Operational notes).
- Firefox/WebKit, visual regression, `data-testid`, a test-only clock.
- The client-view 503 message (still owned by `client-identity`).
- Editing the archived `deferred.md`, which stays frozen.

## Capabilities

### New Capabilities
- `browser-e2e`: the suite itself. One command, fresh DB per run, serial, artifacts, no credentials and no Docker. Every scenario is proven by running the suite or by a config/file check.

### Modified Capabilities
- `concurrency-conflicts`: "Frontend conflict handling" gains the advisor-reload and client-re-fetch scenarios. The deferral note is removed.
- `daily-queue`: adds the stale-ticket generic not-found scenario. A note says it becomes a true stored-ticket test when `client-identity` adds persistence.

## Frontend/Backend Alignment

- No API, backend, or app behavior change. No edits under `src/` or `backend/`.
- In `vite.config.js`, only the Vitest `test.include` block changes. ESLint gains an `e2e` block.

## Approach

This follows the exploration recommendation. The real backend proves the proxy and the API contract. Mocking a single response proves the view reactions that cannot be reproduced on demand (409 races, a stale ticket, 503).

## Affected Areas

| Area | Impact |
|------|--------|
| `react-client/e2e/`, `playwright.config.js` | New |
| `react-client/package.json`, `package-lock.json`, `vite.config.js`, `eslint.config.js`, `.gitignore` | Modified |
| `SystemTurn/README.md`, `docs/app.md`, `docs/README.md`, `openspec/specs/README.md` | Modified |

## Size Forecast

| Item | Lines |
|---|---|
| Real-backend specs (~9 tests) | ~200 |
| Mocked specs (4 tests) | ~125 |
| Helpers + config | ~130 |
| package/Vite/ESLint/gitignore | ~25 |
| Docs | ~50 |
| Specs (new + 2 deltas) | ~150 |
| **Authored total** | **~680** (+~50 generated lockfile) |

This fits 800 only narrowly, and past estimates ran 35–70% low, so budget risk is High. `sdd-tasks` must define a split point: harness + real paths in the first slice, mocked scenarios + deltas in the second.

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Windows 10 is outside Playwright's documented support | Med | Smoke run in unit 1 |
| Orphaned `java` on `:8080` (Windows); fixed port | Med | Document the cleanup; fail fast on a busy port |
| Mocks drift from the real API | Low | `turnosService.test.js` + real happy paths |
| npm 11.3.0 lockfile bug | Med | `npx -y npm@11.21.0 install` |
| Run crosses midnight in Bogota | Low | Documented |
| `mvnw` executable bit in CI | Med | Operational note |
| Staging user WIP (`SystemTurn/infra/`, `.gitignore`, `docs/infra-onboarding.md`) | Med | Stage explicit paths only |

## Rollback Plan

Revert the PR. Only test, config, and docs files change, so app behavior is unaffected. The spec deltas revert with it, and `client-identity` takes back the obligation.

## Dependencies

- Chromium download (`npm run e2e:install`), JDK 17, Node 22+.

## Success Criteria

- [ ] `npm run e2e` passes from a clean checkout with no servers running.
- [ ] Screenshots are written to `test-results/` and the HTML report is generated.
- [ ] `npm test` (Vitest) and `npm run lint` stay green.
- [ ] `git diff` shows no changes under `src/` or `backend/`.
