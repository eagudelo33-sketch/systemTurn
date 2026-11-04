# Design: Browser E2E Test Suite

## Technical Approach

Playwright Test `1.64.1` runs in `react-client/` with its own `playwright.config.js`. A `webServer` array boots the real backend (`spring-boot:run`, `dev,demo`, in-memory H2) and Vite dev, so `npm run e2e` is a single command. The 13 tests run serially in one Chromium worker against one backend. 9 tests use only the real backend. 4 tests mock exactly one response with `page.route`. Every position, next ticket and count is derived from `GET /api/v1/turnos/dashboard` read in the same test. Only the first test pins `T-012`. No file under `src/` or `backend/` changes. Specs: `specs/browser-e2e`, plus deltas on `concurrency-conflicts` and `daily-queue`. Test titles equal the spec scenario names.

## Architecture Decisions

| Decision | Choice | Rejected (tradeoff) |
|---|---|---|
| Boot | `webServer` array: mvnw `spring-boot:run` + `npm run dev -- --port 5173 --strictPort` | `vite preview` (needs a build; preview proxy unverified); pre-started servers (dirty DB, not deterministic) |
| Fresh DB | env `SPRING_DATASOURCE_URL=jdbc:h2:mem:e2e;DB_CLOSE_DELAY=-1` | `TURNOS_DB_PATH` plus a wipe step (extra code, leftover files); test-only `Clock` bean (changes app code) |
| Backend command | `.\mvnw.cmd spring-boot:run` on `win32` (spike F1: bare `mvnw.cmd` fails when `NoDefaultCurrentDirectoryInExePath=1`), `./mvnw spring-boot:run` otherwise; no `cmd /c` | `cmd /c` wrapper (redundant: webServer `command` is a shell command); `package` + `java -jar` (two steps, jar-name glob) |
| `cwd` | absolute, `fileURLToPath(new URL('../../../backend/turnos-api', import.meta.url))` | relative string (resolution base is not documented) |
| Reuse | `reuseExistingServer: process.env.E2E_REUSE === '1'` | `!CI` (the Playwright default template silently reuses a developer's dirty backend) |
| Busy port | rely on Playwright's built-in "already used" failure plus Vite `--strictPort`; document cleanup | custom `globalSetup` port probe (duplicates built-in behavior) |
| Expectations | derived from the live dashboard; `T-012` pinned only in test 1, behind a fresh-DB precondition | fixed seed positions (depend on the time of day) |
| Mocks | `route.fulfill` for 409/503; `route.fetch()` + filter for the stale dashboard | full fake dashboard body (drifts from the real shape) |
| Selectors | `getByRole`/`getByLabel`/`getByText` with exact Spanish copy | `data-testid` (touches `src/`; user decision) |
| Runner isolation | Vitest `test.include: ['src/**/*.test.{js,jsx}']` | `*.e2e.js` + `testMatch` (breaks the `.spec.js` convention) |

Verified against code: `StartupGuard.datasourceViolation` returns empty whenever `dev` is active, so an H2 memory URL passes. Because a URL is configured, Boot builds a Hikari pool, not an `EmbeddedDatabase`. `demoViolation` passes because `dev` is present. `DemoSeedRunner` (`@Profile("demo")`, an `ApplicationRunner`) seeds 11 tickets dated today, then readiness becomes `ACCEPTING_TRAFFIC`. OS env outranks `application-dev.yml`.

## Data Flow

    Playwright ──spawn──> mvnw (8080, H2 mem) <──proxy /api── Vite (5173)
        │ page (Chromium) ──────────────────────────────────────┘
        │   page.route intercepts ONE request before the proxy (mocked tests)
        └─ request fixture (baseURL 5173) ── GET dashboard → expectations

## File Changes

| File | Action | Lines |
|---|---|---|
| `react-client/playwright.config.js` | Create | ~45 |
| `react-client/e2e/support/api.js` | Create: `leerDashboard(request)`, `minutosDelDia("08:25 a. m.")`, `posicionEsperada(dash,id)`, request predicates | ~40 |
| `react-client/e2e/support/capturas.js` | Create: `captura(page, nombre)` → `test.info().outputPath`, `attach` | ~12 |
| `e2e/01-cliente.spec.js` | Create: tests 1–4 | ~100 |
| `e2e/02-asesor.spec.js` | Create: tests 5–9 | ~120 |
| `e2e/03-respuestas-simuladas.spec.js` | Create: tests 10–13 | ~130 |
| `package.json` | 5 scripts + `"@playwright/test": "1.64.1"` | ~7 |
| `package-lock.json` | Regenerated (generated) | ~50–70 |
| `vite.config.js` | `test.include` only | +1 |
| `eslint.config.js` | ignores + e2e block | ~8 |
| `react-client/.gitignore` | `/playwright-report/`, `/test-results/`, `/blob-report/` | ~4 |
| `openspec/config.yaml` | `context` + `testing.frontend` + apply/verify commands | ~14 |
| `docs/app.md` | "Tests" row + "How to run" block | ~25 |
| `docs/README.md` | status row, gap row, `client-identity` + `infra-cicd` obligation rows, roadmap | ~10 |
| `SystemTurn/README.md` | Spanish "Validación" + Tecnologías | ~15 |
| `openspec/specs/README.md` | "How to run" row (capability rows are updated at archive) | ~2 |
| Specs (already written) | browser-e2e 215, deltas 54 | 269 |

**Forecast**: about 530 authored code, config and docs lines, plus 269 spec lines, is about 800 authored lines, with about 60 more lockfile lines. The specs came out at 269 lines against the 150 forecast. Under the proposal's counting rule (specs counted; proposal, exploration, design and tasks not counted), the change sits at the 800 limit. Given the 35–70% history of low estimates, it will likely go over. **The user's rule applies: ask before splitting.** The natural split: slice 1 = harness, tests 1–9, config and docs; slice 2 = tests 10–13 plus the two deltas.

## Interfaces / Contracts

```js
// playwright.config.js (shape)
const reuse = process.env.E2E_REUSE === '1'
export default defineConfig({
  testDir: './e2e', outputDir: './test-results', fullyParallel: false, workers: 1,
  forbidOnly: !!process.env.CI, retries: process.env.CI ? 1 : 0,
  timeout: 30_000, expect: { timeout: 10_000 },
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  use: { baseURL: 'http://localhost:5173', headless: true, trace: 'on-first-retry', screenshot: 'only-on-failure' },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: [
    { command: `${mvnw} spring-boot:run`, cwd: backendDir, timeout: 240_000, reuseExistingServer: reuse,
      url: 'http://localhost:8080/actuator/health/readiness',
      env: { SPRING_PROFILES_ACTIVE: 'dev,demo', SPRING_DATASOURCE_URL: 'jdbc:h2:mem:e2e;DB_CLOSE_DELAY=-1' },
      stdout: process.env.CI ? 'pipe' : 'ignore', stderr: 'pipe' },
    { command: 'npm run dev -- --port 5173 --strictPort', url: 'http://localhost:5173', timeout: 60_000, reuseExistingServer: reuse },
  ],
})
```

Scripts: `e2e` = `playwright test`, `e2e:headed` = `--headed`, `e2e:ui` = `--ui`, `e2e:report` = `playwright show-report`, `e2e:install` = `playwright install chromium`. Dependency and lockfile come from one command: `npx -y npm@11.21.0 install -D -E @playwright/test@1.64.1`.

ESLint: add `playwright-report`, `test-results` and `blob-report` to `globalIgnores`. Add a block `{ files: ['e2e/**/*.js', 'playwright.config.js'], languageOptions: { globals: globals.node }, rules: { 'react-hooks/rules-of-hooks': 'off' } }`. The hooks rule is off because Playwright's fixture `use` callback looks like React's `use`. Tests define no custom fixtures, so this is defensive only.

`minutosDelDia` must handle `a. m.`/`p. m.`, because `Hora.format` uses `hh:mm`. The app's own `posicionEnDashboard` ignores the AM/PM suffix (a latent app bug; out of scope). For that reason, tests assert positions only from the `POST` response render, never after a client-side re-fetch.

## Config and Docs

- `openspec/config.yaml`: in `context`, change "no test runner yet" to "Vitest (unit) + Playwright (e2e)". Set `testing.frontend.runner: "npm test"` (Vitest 5, `src/**/*.test.{js,jsx}`) and add `e2e: { runner: "npm run e2e", path: e2e/, install: "npm run e2e:install", tool: "Playwright Test 1.64.1, Chromium" }`. Remove the `GAP` comment and the "add a test runner first" apply guideline. Add the frontend commands to `apply.test_command` and `verify.test_command`.
- `docs/app.md`: in "Tests", add an e2e row (13 tests, Playwright) and reword the Vitest row's "No component tests for the views". In "How to run", add `npm run e2e:install` (once), `npm run e2e`, `npm run e2e:report`, ports 8080/5173 must be free, `E2E_REUSE=1`, and the port-cleanup command.
- `docs/README.md`: in Status, the "Frontend tests" row reads Vitest 11 + e2e 13. In Gaps, the view-tests row keeps "no component tests" and drops the 3 deferred scenarios. The `client-identity` obligation row becomes "upgrade the stale-ticket e2e test to a true stored-ticket scenario once persistence exists; show the 503 message in the client view". The `infra-cicd` obligation row adds "run `npm run e2e` (`--with-deps chromium`, `mvnw` mode 100755)". Add this change to the roadmap.
- `SystemTurn/README.md` (Spanish): in "Validación", add `npm run e2e:install` (una vez) and `npm run e2e`, plus the free-port note. In "Tecnologías", add Playwright.

## Tests (titles = spec scenario names)

Waits: arm `page.waitForResponse`/`waitForRequest` with method + URL predicates before the click, never response counts. StrictMode runs mount effects twice in dev, so one view load makes two dashboard GETs. Each test opens a fresh page, so React state starts empty. A screenshot is taken after each key step.

| # | File / title | Flow and assertions | Expectation source / mock |
|---|---|---|---|
| 1 | 01 "client requests a ticket" | Precondition: dashboard has 11 tickets (fresh DB; otherwise fails with "DB not fresh"). Click `Solicitar turno`. `getByText('Turno T-012 solicitado.')` is attached (sr-only, so not `toBeVisible`). `article` "Tu turno" contains `T-012` + `Pendiente`. Button now reads `Ya tienes un turno`. Region "Tu turno en la fila" first cell = expected position | `posicionEsperada(leerDashboard(), id)`, with `id` from the POST response |
| 2 | 01 "client cancel keep" | Request a ticket → `Cancelar turno` → text `Si cancelas, el asesor no llamará este turno.` → `Conservar` → text hidden, `Pendiente` | real |
| 3 | 01 "client cancel confirm" | Request → `Cancelar turno` → `Sí, cancelar` → `Cancelaste este turno. El asesor no lo llamará.`, `Cancelado` | real |
| 4 | 01 "client stats" | Region "Información de la fila": article "Personas en espera" = `metricas.pendientes`, "Turnos atendidos hoy" = `metricas.atendidos`; `Cifras obtenidas del servicio.` | response body of the page's own dashboard GET |
| 5 | 02 "advisor loads the dashboard" | `?vista=asesor`: `Conectado al servidor`, heading `Lista de turnos`, `${n} registros`, data rows = n | `turnos.length` |
| 6 | 02 "advisor calls next" | Read region "Siguiente turno" code → `Llamar al siguiente` → wait for PATCH `siguiente` 200, then for a later dashboard GET → that row shows `Atendido`; region shows new `siguienteTurno.codigo` (or `No hay turnos pendientes`) | dashboard read after the call |
| 7 | 02 "advisor searches by number" | `getByLabel('Buscar turno')` fill `T-007` → every row contains it; count matches the API; `T-999` → `No hay turnos que coincidan con la búsqueda.` | `codigo.includes` |
| 8 | 02 "advisor filters by status" | `getByRole('combobox', { name: /Estado/ })` select `Atendidos` → all rows `Atendido`; `Pendientes` → all rows `Pendiente`; counts match the API | dashboard by `estado` |
| 9 | 02 "view switch" | nav "Cambiar de vista" link `Asesor` → URL `vista=asesor`, heading `Buenos días, asesor`, `aria-current="page"`; `Cliente` → `¡Hola!` | none |
| 10 | 03 "Client cancel conflict re-fetches" | Request a real ticket; route `**/api/v1/turnos/*/cancelar` → 409 `{error:'CONFLICTO',mensaje:'El turno cambió mientras se procesaba. Actualiza e intenta de nuevo.'}`, which sets `servido=true`; arm `waitForRequest(dashboard GET && servido)`; confirm → alert with that text stays visible; card `Pendiente`; `Cancelar turno` visible again | re-fetch is real |
| 11 | 03 "Advisor conflict reloads without retry" | Route PATCH `**/api/v1/turnos/siguiente` → same 409 body; same flag pattern; alert text; `Reintentar` count 0; table rows > 0; next code unchanged and equal to API `siguienteTurno` | reload is real |
| 12 | 03 "Browser holds a ticket missing from today's queue" | Request a real ticket (id from POST); route dashboard: `route.fetch()` → remove `id` from `turnos` → `route.fulfill({ response, json })`; `Actualizar estado` → alert `No encontramos ese turno.`; no text matching `/vencid|expir|ayer/i` | real body, filtered |
| 13 | 03 "advisor 503 shows Reintentar and recovers" | Route dashboard → 503 `{error:'SERVICIO_NO_DISPONIBLE',mensaje:'Servicio no disponible, intenta de nuevo en unos minutos.'}` before `goto`; alert contains the message plus `Reintentar`; `page.unroute`; click → wait for a 200 dashboard response; alert hidden, rows shown | the mock persists until `unroute` (StrictMode double GET) |

Ordering and state: files run in alphabetical order when parallelism is off. Mutations: test 1 creates T-012; test 2 creates one pending; test 3 creates one cancelled; test 6 attends the head of the queue; tests 10 and 12 each create one pending. Tests 11 and 13 do not mutate. Order is safe because every expectation after test 1 is derived. With retries, a retried test 1 fails honestly on the fresh-DB precondition. `E2E_REUSE=1` on a dirty DB fails test 1 by design (documented).

## Testing Strategy

| Layer | What | Approach |
|---|---|---|
| Unit | `turnosService.test.js` (unchanged) | `npm test`. RED: an e2e spec is collected before `include` is narrowed |
| Lint | config + e2e | RED: `process` is `no-undef` before the e2e block |
| E2E | 13 scenarios | `npm run e2e`. RED per test: write it before its helper/route exists and watch it fail |
| Config checks | pin, workers, reporter, gitignore, no `data-testid` | inspection, `git check-ignore`, `rg getByTestId e2e` |

## Threat Matrix

| Boundary | Applicability |
|---|---|
| Documentation-like paths | N/A: no file classification |
| Git repository selection | N/A: no git automation |
| Commit state | N/A |
| Push state | N/A |
| PR commands | N/A |

Process note: the webServer commands are static strings with no user input. Windows teardown uses Playwright's process-tree kill. If a stray `java` remains: `Get-NetTCPConnection -LocalPort 8080 -State Listen | % { Stop-Process -Id $_.OwningProcess -Force }`.

## Migration / Rollout

No migration required. Revert the PR to roll back.

## Open Questions

- [ ] Over-budget forecast (about 800 plus underestimate history): ask the user before splitting (non-blocking for design).
- [ ] Spec wording "mocked to return 503 once": implementation mocks until `unroute` (StrictMode). Suggest the wording "until the mock is removed".
- [x] Smoke-verified on Windows 10 (spike): `.\mvnw.cmd` required; env merges with `process.env` automatically; normal and failing runs leave no process; a hard-killed runner orphans java/cmd/node — cleanup by killing the LISTENING PIDs on :8080 and :5173 (`Get-NetTCPConnection -State Listen`). Playwright empties `test-results/` at the start of every run.

## Sources

[webServer](https://playwright.dev/docs/test-webserver) · [TestConfig.webServer](https://playwright.dev/docs/api/class-testconfig#test-config-web-server) · [Test order](https://playwright.dev/docs/test-parallel#control-test-order) · [Retries](https://playwright.dev/docs/test-retries) · [Reporters](https://playwright.dev/docs/test-reporters#html-reporter) · [Route](https://playwright.dev/docs/api/class-route) · [Mock / modify responses](https://playwright.dev/docs/mock#modify-api-responses) · [Locators](https://playwright.dev/docs/locators) · [Assertions](https://playwright.dev/docs/test-assertions) · [Browsers](https://playwright.dev/docs/browsers) · [Vitest include](https://vitest.dev/config/#include) · [React StrictMode](https://react.dev/reference/react/StrictMode) · [Spring Boot externalized config](https://docs.spring.io/spring-boot/reference/features/external-config.html)
