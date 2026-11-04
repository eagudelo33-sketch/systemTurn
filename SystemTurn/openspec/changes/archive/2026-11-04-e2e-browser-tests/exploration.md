# Exploration: e2e-browser-tests

A permanent, CI-ready browser E2E suite: drive a real Chromium through the client and advisor views, with pass/fail plus screenshots. No API or app behavior change.

> Note: the explorer ran on a branch based on a stale `main` and reported that `docs/app.md` and `openspec/specs/README.md` do not exist. They do (PR #5). Run instructions and docs references below use the current layout: `SystemTurn/docs/README.md` (map, status, roadmap), `SystemTurn/docs/app.md` (application), `SystemTurn/README.md` (Spanish quickstart).

## Current state

**Frontend** (`SystemTurn/frontend/frontend-react/react-client`): React 19, Vite 8 (`/api` proxy hardcoded to `http://localhost:8080`, so the backend port is fixed and CORS is irrelevant), Vitest 5 in `environment: 'node'` with one test file. `App.jsx` switches view by `?vista=asesor` with a `vista-switch` nav. `ClienteView` keeps the ticket only in React state. Both views use `role="alert"` and `aria-labelledby` regions → role-based locators work without app changes.

**Backend** (`backend/turnos-api`): Spring Boot 4.1.2, Java 17, `mvnw`/`mvnw.cmd`. `dev` profile = H2 file at `TURNOS_DB_PATH`. `DemoSeedRunner` is an `ApplicationRunner`, so the seed finishes before readiness is UP. Readiness `/actuator/health/readiness` excludes the DB, but Flyway and the seed run before it. Queue is FIFO by `horaSolicitud` (`LocalTime`, `America/Bogota`), then id. Errors `{error, mensaje}`; `CONFLICTO` (409) and `SERVICIO_NO_DISPONIBLE` (503) are mapped by `turnosService.js`.

No CI (`.github/`) exists yet.

## Affected areas

- `react-client/package.json` + `package-lock.json` (regenerate with `npx -y npm@11.21.0 install`)
- `react-client/playwright.config.js` (new), `react-client/e2e/*.spec.js` + helper (new)
- `react-client/vite.config.js` — `test.include: ['src/**/*.test.{js,jsx}']` (test block only)
- `react-client/eslint.config.js` — node-globals block for `e2e/**` and `playwright.config.js`; ignore `playwright-report`, `test-results`
- `react-client/.gitignore` — `playwright-report/`, `test-results/`, `blob-report/`
- `SystemTurn/README.md`, `SystemTurn/docs/app.md` — how to run the suite
- `openspec/specs/` — new capability spec; deltas on `concurrency-conflicts` and `daily-queue` if the deferred scenarios are adopted

## Findings

### 1. Tool

| Option | Pros | Cons |
|--------|------|------|
| **Playwright Test 1.64.1 (recommended)** | `webServer` array, `page.route`, HTML report, traces, screenshots, Chromium-only install | ~280 MB Chromium |
| Cypress | Mature | No documented multi-server startup; bundled Electron deprecated; separate binary |
| Vitest browser mode | Already on Vitest | Component-focused; no server orchestration |

- 1.64.1 is npm `latest`, published 2026-10-14 (≥ 2 weeks); exact pin (browsers are tied to the version). Betas/alphas excluded.
- Node 22/24/26 supported (local 26.13).
- Docs list Windows 11+; this machine is Windows 10 19045 → smoke run at apply.
- Chromium only, headless default. Local `npx playwright install chromium`; CI `--with-deps chromium` (optionally `--only-shell` for headless-only CI).

### 2. Layout

- `react-client/e2e/` with its own `playwright.config.js` (`testDir: './e2e'`).
- Scripts: `e2e`, `e2e:headed`, `e2e:ui`, `e2e:report`, `e2e:install`.
- Vitest's default include likely matches `e2e/*.spec.js` (assumed, unverified) → narrow `test.include`. Playwright's default match under `testDir` cannot pick up `src/**`. Alternative (`*.e2e.js` + `testMatch`) avoids the Vite edit but breaks the `.spec.js` convention.
- ESLint: scoped `globals.node` block for e2e files.

### 3. Booting the stack

| Option | Pros | Cons |
|--------|------|------|
| **`webServer` array: mvnw + `vite dev` (recommended)** | One command, matches README, fresh state | Backend 10–30 s (more on cold CI) |
| `vite preview` of a build | Tests the production bundle | Extra build; `preview.proxy` default unverified |
| Pre-started servers | Fast iteration | Dirty state, not deterministic |

- Backend entry: `cwd: '../../../backend/turnos-api'`, `mvnw.cmd`/`./mvnw` by `process.platform`, `env: { SPRING_PROFILES_ACTIVE: 'dev,demo' }` (avoids cmd/bash quoting), `url: http://localhost:8080/actuator/health/readiness`, timeout 180–240 s, stdout piped on CI only.
- Frontend entry: `npm run dev -- --port 5173 --strictPort`, `url: http://localhost:5173`.
- `reuseExistingServer: false` by default (a developer's live backend = dirty DB); explicit opt-in env (e.g. `E2E_REUSE=1`).
- Verified: array supported; readiness accepts 2xx/3xx/400–403; default timeout 60 s; `gracefulShutdown` ignored on Windows.

### 4. Isolation and time

- Fresh DB per boot: `SPRING_DATASOURCE_URL=jdbc:h2:mem:e2e;DB_CLOSE_DELAY=-1` in the backend env (env outranks `application-dev.yml`; `StartupGuard` accepts `jdbc:h2:` under `dev`). Fallback: `TURNOS_DB_PATH` to a gitignored folder wiped at config load.
- **Time-of-day dependence**: seeded pending tickets T-006 08:25, T-008 08:34, T-009 08:38, T-010 08:41, T-011 08:45. A new T-012 before 08:25 is position 1; after 08:45 position 6. `page.clock` only fakes the browser clock, not the JVM.
- Strategy: derive expected positions/next from the real `GET /api/v1/turnos/dashboard` via Playwright `request`; advisor tests read the displayed next and assert it becomes Atendido; pin `T-012` in exactly one test (first, fresh DB); search/filter assert on the fixed T-007.
- Rejected: test-only `Clock` bean via an `e2e` profile (changes app code).
- Edge: a run crossing midnight Bogota sees an empty "today" (≈1-minute window) — documented.
- Serial: `workers: 1`, `fullyParallel: false`, files ordered `01-`, `02-`.

### 5. Flows (~12 tests, each provable)

Real backend: (1) client requests → `T-012`, Pendiente, position matches API; (2) client cancel: Conservar keeps it, "Sí, cancelar" → Cancelado; (3) client stats cards; (4) advisor loads: "Conectado al servidor", next ticket card, ≥ 11 rows; (5) advisor "Llamar al siguiente": previous next → Atendido, next advances; (6) search T-007 → one row, non-match → empty message; (7) filter Cancelados → T-007 only; (8) view switch via nav and `?vista=asesor`.

Route-mocked (mock only one request): (9) client cancel gets 409 `CONFLICTO` → message + dashboard re-fetch, card stays Pendiente; (10) advisor next gets 409 → message, no "Reintentar", dashboard reloads; (11) stale ticket: dashboard mocked without it + "Actualizar estado" → "No encontramos ese turno."; (12, optional) dashboard 503 → message, "Reintentar" succeeds after un-routing.

Why: a real 409 race is not reproducible on demand; mocking one response proves the view, the real backend proves the proxy and contract. Mock drift is mitigated by `turnosService.test.js` and the real happy paths. Use `waitForRequest`/`waitForResponse` predicates, not counts (StrictMode may double effects).

### 6. Selectors

`getByRole` / `getByLabel` / `getByText` with Spanish copy (regions "Siguiente turno", "Tu turno en la fila"; buttons "Solicitar turno", "Llamar al siguiente"; label "Buscar turno"; select "Estado:"). No `data-testid` (would touch app code). Copy changes break tests — that is a spec signal.

### 7. Artifacts

`screenshot: 'only-on-failure'` + a helper saving named PNGs per key step via `testInfo.outputPath(...)` (attached to the report); `trace: 'on-first-retry'`, `retries` 1 on CI / 0 locally; `reporter: [['list'], ['html', { open: 'never' }]]` (default `on-failure` blocks a run); explicit `outputDir: test-results`, report `playwright-report`; gitignore both plus `blob-report/`. The assistant reads PNGs from `test-results/` with the Read tool.

### 8. The three deferred scenarios (USER DECISION)

Recommended: prove them now with tests 9–11 (behavior already exists) and re-home them as spec deltas on `concurrency-conflicts` and `daily-queue`, owned by this change. Leave the archived `deferred.md` frozen; `client-identity`'s proposal drops the obligation. Caveat: without persistence, "browser holds yesterday's ticket" is proved by refreshing against a dashboard that lacks the ticket — it becomes a true stale-storage scenario once `client-identity` adds persistence. Alternative: keep them with `client-identity` (component tests there).

### 9. CI notes (for infra-cicd; operational)

Linux job: `setup-java` 17 (Maven cache), `setup-node` (npm cache), `npm ci`, `npx playwright install --with-deps chromium` (don't cache browsers); `workers: 1`, `retries: 1`, `forbidOnly: !!process.env.CI`; backend webServer timeout 180–240 s; upload `playwright-report/` and `test-results/` with `if: ${{ !cancelled() }}`; `mvnw` must be mode 100755; lockfile from npm 11.21.0; no Docker needed (H2 `dev`). Docker image option `mcr.microsoft.com/playwright:v1.64.1-noble`.

### 10. Size

Config 60–80 · helpers 40–60 · specs 250–300 · package/ESLint/Vitest/gitignore 20–30 · lockfile 50–70 · docs + spec 60–100 → **~480–640** (single PR fits 800). Lessons from earlier changes: estimates ran 35–70% low; re-estimate from the scenario list in tasks.

## Recommendation

Playwright Test 1.64.1 (exact pin), Chromium only, `react-client/e2e` with its own config; `webServer` array (mvnw `dev,demo` with in-memory H2 via `SPRING_DATASOURCE_URL` + Vite dev); serial with API-derived expectations; real-backend happy paths + route-mocked errors; prove the 3 deferred scenarios now; role-based selectors; no app change.

## Risks

Time-of-day ordering (derive from API) · Windows 10 outside documented support (smoke run) · orphaned `java` on 8080 after teardown on Windows · fixed port 8080 · mock fidelity · `mvnw` executable bit for CI · npm 11.3.0 lockfile bug · midnight crossing · user WIP under `SystemTurn/infra/` must never be staged.

## Sources

[Playwright releases](https://github.com/microsoft/playwright/releases) · [npm dist-tags](https://registry.npmjs.org/@playwright/test) · [Intro / requirements](https://playwright.dev/docs/intro) · [webServer](https://playwright.dev/docs/test-webserver) · [CI](https://playwright.dev/docs/ci) · [Browsers](https://playwright.dev/docs/browsers) · [Configuration](https://playwright.dev/docs/test-configuration) · [Reporters](https://playwright.dev/docs/test-reporters) · [Test options](https://playwright.dev/docs/api/class-testoptions) · [Route](https://playwright.dev/docs/api/class-route) · [Mocking](https://playwright.dev/docs/mock) · [Locators](https://playwright.dev/docs/locators) · [Vitest browser mode](https://vitest.dev/guide/browser/) · [Cypress install](https://docs.cypress.io/app/get-started/install-cypress)
