# Apply Progress: e2e-browser-tests

**Mode**: Strict TDD (spike ships no code; evidence only) | **Delivery**: exception-ok (`size:exception`) | **Batches**: Phase 1 (spike); Phases 2-3 (tooling, harness, 9 real-backend tests)

## Completed Tasks

- [x] 1.1 Install `@playwright/test@1.64.1` + Chromium; trivial test passes.
- [x] 1.2 webServer starts `mvnw.cmd spring-boot:run` with env merge, `dev,demo`, H2 mem URL, readiness wait.
- [x] 1.3 After run, no listener on :8080 / :5173; no java left; no `./data` file.

- [x] 2.1-2.4 Tooling (pin 1.64.1, 5 scripts, Vitest include, eslint block + ignores, .gitignore)
- [x] 3.1-3.6 Harness + 9 real-backend tests (9 passed)

- [x] 4.1-4.5 Four route-mocked tests + full gates (13 passed)

- [x] 5.1-5.5 Docs + config (app.md, docs/README.md, SystemTurn/README.md, config.yaml); config check, link check, src/backend diff empty.

## TDD Cycle Evidence

| Task | Test File | Layer | Safety Net | RED | GREEN | TRIANGULATE | REFACTOR |
|------|-----------|-------|------------|-----|-------|-------------|----------|
| 1.1-1.3 (spike) | throwaway `scratchpad/e2e-spike/e2e/spike.spec.js` (NOT in repo) | E2E | N/A (new, throwaway) | Written: run 1 failed at webServer launch (`spawn cmd.exe ENOENT`), run 2 failed `mvnw.cmd not recognized`; run 3 test 1 failed on a wrong assertion (`'ATENDIDO'`) | 2/2 passed, exit 0 (run 4) | Single: spike | None needed (no production code) |

| 2.1 RED | probe `e2e/red-probe.spec.js` (deleted) | Config | 11/11 vitest, lint clean | `npm test`: "No test suite found in file e2e/red-probe.spec.js" (1 failed file); lint: `'process' is not defined no-undef` | After 2.3: vitest 11 passed (1 file), lint exit 0 | Single | None |
| 2.2-2.4 | package.json, package-lock.json, vite.config.js, eslint.config.js, .gitignore | Config | N/A | covered by 2.1 | pin `"@playwright/test": "1.64.1"`, vitest 5.0.5 kept, 10 `libc` fields kept, `git check-ignore` matches both dirs, `playwright test --list` shows 9 tests, none under `src/` | Triangulation skipped: structural config | None |
| 3.1 | `e2e/01-cliente.spec.js` "client requests a ticket" | E2E | N/A (new) | no config: `apiRequestContext.get: Invalid URL` (no baseURL) | with config: 1 passed (40.5s) | Single | None |
| 3.3 | same test + `e2e/support/api.js`, `capturas.js` | E2E | 1/1 | helpers missing: `Cannot find module e2e/support/api.js` | 4 passed (40.9s) | Mutation run, see below | None |
| 3.4 | "client cancel keep", "client cancel confirm", "client stats" (+ position assertion in test 1) | E2E | 4/4 | Mutation RED (expected value changed): `Expected "7" Received "6"` (position), `toBeVisible` failed (keep), `Expected substring "Atendido" Received "...CanceladoCancelaste..."` (confirm), `Expected "8" Received "7"` (stats): 4 failed | 4 passed | Mutation proves each assertion can fail | None |
| 3.5 | "advisor loads the dashboard", "searches by number", "filters by status", "calls next", "view switch" | E2E | 4/4 | First run passed (characterization of existing behavior). Mutation RED: loads (`${total + 1} registros` not visible), search (`Expected: 2 Received: 1`), filter (`Expected "Cancelado" Received "...Atendido"`), calls next (`Expected "Cancelado" Received "T-006...Atendido"`), view switch (`Expected "step" Received "page"`): 5 failed | 9 passed (43.3s) | Mutation as above | None |
| 4.1-4.4 | `e2e/03-respuestas-simuladas.spec.js` (4 tests) | E2E | 9/9 | helpers missing: `does not provide an export named 'CONFLICTO'` (No tests found); then 4 passed (45.2s) | 4 passed | Mutation run (one wrong expectation per test, reverted, diff clean): `Expected "Atendido" Received "...Pendiente..."`, `toHaveCount Expected 1 Received 0`, `Expected "Turno vencido" Received "No encontramos ese turno."`, alert `toBeVisible` failed: 4 failed | None |

Note on RED: tests 2-9 characterize behavior the app already has, so no honest natural RED exists. RED was produced by writing the test before its helpers (3.3) and by one mutation run per batch (assertion value changed, run fails on the expected assertion, mutation reverted, file diff vs backup empty).

### Work Unit Evidence (unit 1)

| Evidence | Value |
|---|---|
| Focused test command | `npx playwright test` in the spike dir: exit 0, `2 passed (50.9s)`, 1 worker, tests > 0 |
| Runtime harness | After the run: `netstat -ano \| findstr LISTENING \| findstr ":8080 :5173"` empty; `tasklist \| findstr -i java` empty; no `backend/turnos-api/data` |
| Rollback boundary | None: throwaway dir under the session scratchpad; no repo file touched |

## Spike Findings

Spike dir: `C:\Users\user\AppData\Local\Temp\claude\C--Code-ticket-app\8e5f526d-46b2-4077-b31a-ea0dd7f4f1fc\scratchpad\e2e-spike`

### Timings
- `npx -y npm@11.21.0 install -D -E @playwright/test@1.64.1`: 18 s (npm 11.3.0 not used).
- `npx playwright install chromium`: 274 s (chromium 1248 + headless shell 156.0.8078.4 + ffmpeg, ~120 MB headless shell; first download).
- Backend: `Started TurnosApiApplication in 20.9 s` (16.8 s in another run); whole `playwright test` 57 s wall (tests 50.9 s including backend boot and Vite).
- Test result: `2 passed`, exit 0.

### F1 (design correction): bare `mvnw.cmd` FAILS on this machine; use `.\mvnw.cmd`
- Tried without `cmd /c` first. With command `mvnw.cmd spring-boot:run` and cwd = backend dir:
  `'mvnw.cmd' is not recognized as an internal or external command` then `Process from config.webServer was not able to start. Exit code: 1`.
- Cause: `NoDefaultCurrentDirectoryInExePath=1` is set in this user's environment, so cmd.exe does not resolve a bare name from the current directory. Reproduced with plain `node -e "spawn('mvnw.cmd -v',{shell:true})"`.
- Fix verified: `'.\\mvnw.cmd spring-boot:run'` (JS string for `.\mvnw.cmd`) works with no `cmd /c` wrapper. Design's "no `cmd /c`" holds; only the command string changes from `mvnw.cmd` to `.\mvnw.cmd` on `win32`. `./mvnw` stays for non-Windows.
- Design file change needed in design.md decision table (Backend command) and the config sample.

### F2: env merge works
- `JAVA_HOME` (`C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot\`) and PATH reach `mvnw.cmd`/Maven: backend booted; log shows `profiles are active: "dev", "demo"`. Playwright merges `process.env` with the webServer `env`, so no manual spread is needed.
- `SPRING_DATASOURCE_URL` honored: `HikariPool-1 - Added connection conn0: url=jdbc:h2:mem:e2e user=SA`. No `data/` directory or H2 file created under `backend/turnos-api` (listing after run: `mvnw, mvnw.cmd, pom.xml, src, target`).
- Readiness URL `http://localhost:8080/actuator/health/readiness` works; Playwright waited for it before starting tests.

### F3: absolute `cwd` via `fileURLToPath(new URL(...))`
- A hand-written backslash path string breaks if backslashes get eaten (first attempt: the spike config was written through a shell heredoc that stripped them, giving `spawn C:\Windows\system32\cmd.exe ENOENT`, which really means "cwd does not exist"). `fileURLToPath(new URL('file:///C:/.../turnos-api'))` yields a correct path with spaces (`ticket app`). The design's `fileURLToPath` choice is validated. Gotcha: the ENOENT error names `cmd.exe`, not the bad cwd.

### F4: Vite proxy and UI
- `request.get('/api/v1/turnos/dashboard')` through baseURL :5173 returned 200 (proxy works); direct `http://localhost:8080/api/v1/turnos/dashboard` returned 200.
- `page.goto('/')`: `getByRole('button', { name: 'Solicitar turno' })` visible.
- Screenshot: `...\scratchpad\e2e-spike\test-results\spike-proxy-UI-via-vite-chromium\cliente.png` (client view, 5 waiting, 12,2 min, 5 attended).
- Vite ran from the repo's `react-client` with its existing `node_modules`; no repo file changed.

### F5: demo seed data
- Dashboard keys: `metricas`, `siguienteTurno`, `turnos`. 11 tickets. `estado` values are display labels: `Atendido`, `Pendiente`, `Cancelado` (NOT upper case).
- `metricas`: `{"pendientes":5,"atendidos":5,"cancelados":1,"esperaPromedioMinutos":12.2}`. Attended tickets exist (5), so the "filters by status" test has data for `Atendidos`.
- `siguienteTurno`: `{"id":6,"codigo":"T-006","numero":6,"horaSolicitud":"08:25 a. m.","estado":"Pendiente"}`. `horaSolicitud` uses `a. m.`/`p. m.` as the design states.

### F6: process cleanup
- Normal runs (one passing run, one run with a failing test): after exit, no LISTENING socket on :8080 or :5173, no `java.exe`, no vite node/cmd process. `netstat -ano | findstr :8080` still shows `TIME_WAIT` rows right after the run. These are not listeners. **Task 1.3 / docs checks must filter `LISTENING`** (`netstat -ano | findstr LISTENING | findstr :8080`), or use `Get-NetTCPConnection -State Listen`.
- Abnormal termination probe: hard-killed only the Playwright runner (`Stop-Process -Force`, no tree) while both servers were up. Orphans left: `java` 24188 (Maven launcher), `java` 24068 (Spring app, LISTENING :8080), `cmd` 22692, `node` 6760 (Vite, LISTENING :5173), parents already dead. This is the hard-kill case only, not normal operation or a failed test.
- Mitigation verified: the design's command works and is sufficient:
  `Get-NetTCPConnection -LocalPort 8080 -State Listen | % { Stop-Process -Id $_.OwningProcess -Force }` and the same for 5173. After it, the Maven launcher `java` and the `cmd` exited on their own; ports free, no java. Document both ports in the cleanup command (design lists only 8080).
- Not tested: Ctrl-C (SIGINT) on an interactive terminal run. Playwright handles it with its process-tree kill, but this spike did not exercise it.

## Work Unit Evidence (units 2 and 3)

| Evidence | Value |
|---|---|
| Focused test command | `npm run e2e` (final): exit 0, `9 passed (43.3s)`, 1 worker. `npm test`: 1 file, 11 passed. `npm run lint`: exit 0. `npm run build`: built |
| Runtime harness | Real backend (dev,demo, H2 mem) + Vite booted by Playwright. After the run: no LISTENING on :8080/:5173, no java.exe |
| Rollback boundary | react-client: package.json, package-lock.json, playwright.config.js, e2e/, vite.config.js, eslint.config.js, .gitignore |

Changed lines (staged, `git diff --cached --numstat`): authored +286/-2 (.gitignore 5, 01 spec 79, 02 spec 91, api.js 40, capturas.js 8, eslint 7/-1, package.json 7/-1, playwright.config.js 48, vite.config.js 1); lockfile +46.

## Verdict

Spike PASSES with one design correction (F1). Design stays valid: webServer array, in-memory H2, `dev,demo`, readiness wait, Vite proxy, no `cmd /c`. No STOP condition hit.

## Deviations from Design (phases 2-3)

4. `captura` takes no extra helper; client tests share a local `solicitarTurno(page)` in the spec file (not in design); `esLlamadaAlSiguiente` predicate added to api.js.
5. Advisor "calls next" additionally asserts the region code before and after, and that the head changed.

## Deviations from Design (phase 1)

1. Windows command is `.\mvnw.cmd spring-boot:run`, not `mvnw.cmd spring-boot:run` (F1).
2. Cleanup docs should cover port 5173 as well as 8080, and use `Listen`/`LISTENING` filtering (F6).
3. Dashboard `estado` is mixed case (`Atendido`); test predicates must use these labels (F5).

## Files Changed (repo)

| File | Action | What Was Done |
|------|--------|---------------|
| `SystemTurn/openspec/changes/e2e-browser-tests/apply-progress.md` | Created | This record |
| `SystemTurn/openspec/changes/e2e-browser-tests/tasks.md` | Modified | Phases 1-3 tasks marked `[x]` |
| `react-client/package.json`, `package-lock.json` | Modified | `@playwright/test` 1.64.1 exact + 5 scripts |
| `react-client/playwright.config.js` | Created | webServer array, serial, chromium |
| `react-client/e2e/01-cliente.spec.js`, `02-asesor.spec.js` | Created | 9 real-backend tests |
| `react-client/e2e/support/api.js`, `capturas.js` | Created | dashboard client, AM/PM parser, predicates, screenshots |
| `react-client/vite.config.js`, `eslint.config.js`, `.gitignore` | Modified | include, e2e block + ignores, output paths |

No `src/`, backend, docs or infra file changed.

## Workload / PR Boundary

- Mode: size:exception (single PR)
- Current work unit: 1 (spike, no commit)
- Boundary: starts at branch `feat/e2e-browser-tests`; ends with evidence only
- Estimated review budget impact: none (openspec change folder is not staged)

## Status

23/23 tasks complete (Phases 1-5). Ready for sdd-verify.

## Phase 4 Evidence

- `npm run e2e`: exit 0, `13 passed (50.0s)`; `npm test` 11 passed; `npm run lint` exit 0; `npm run build` built; `rg getByTestId e2e` empty.
- Ports 8080/5173 not LISTENING, no java after the run. `test-results/` left in place.
- Changed lines (staged): 03 spec +110, api.js +10 (CONFLICTO, SERVICIO_NO_DISPONIBLE constants).
- Deviation: mocks use `waitForRequest` with a flag set in the route handler, so the advisor/client alert is asserted only after the real re-fetch was issued.

## Phase 5 Evidence

| Task | Check | RED | GREEN |
|------|-------|-----|-------|
| 5.4 | scratchpad `cfgcheck.py` (spec scenario "openspec config records runners") | `FAIL e2e playwright + npm run e2e`, `FAIL no 'no test runner' gap` | 3/3 PASS, exit 0; YAML parses |
| 5.5 | scratchpad `linkcheck.py` over docs/app.md, docs/README.md, README.md | n/a | 45 relative links + anchors, 0 bad |
| 5.5 | `git diff main --stat -- react-client/src backend` | n/a | empty |

Staged (numstat +/-): SystemTurn/README.md 13/1, docs/README.md 17/11, docs/app.md 34/1, openspec/config.yaml 11/6. Not committed.

Deviations: roadmap renumbered (`e2e-browser-tests` = 4, `ux-error-states` = 5, later rows +2). Config check false-positived on the unrelated word "gaps" in a rules line; checker narrowed to `# GAP`. docs/app.md links to the change's spec under `openspec/changes/`; it must be repointed to `openspec/specs/browser-e2e/` when archived.
