# Browser E2E Specification

## Purpose
A permanent Playwright suite proves the client and advisor views work end to end through a real browser, the Vite proxy, and the real backend. It changes no API or application behavior.

Scenario names below are the Playwright test titles (or the check name for file/config checks). Tests run headless Chromium only and locate elements by role plus visible text.

## Requirements

### Requirement: Single-command run
`npm run e2e` (from `react-client`) MUST boot the backend (profiles `dev,demo`, in-memory H2 selected through `SPRING_DATASOURCE_URL`) and the Vite dev server, run the suite in headless Chromium, and stop both servers afterwards. It MUST NOT require AWS credentials or Docker. It MUST NOT reuse already-running servers unless an explicit opt-in environment variable is set.

#### Scenario: run from a clean checkout
- GIVEN no backend or Vite server is running and Chromium is installed
- WHEN `npm run e2e` is executed
- THEN both servers are started by the command, all tests pass, and both servers are stopped

#### Scenario: no external prerequisites
- GIVEN `playwright.config.js` and the e2e npm scripts
- WHEN they are inspected
- THEN no AWS credential, Docker, or `docker compose` reference is present
- AND `reuseExistingServer` is false unless the opt-in variable is set

#### Scenario: Chromium only and headless
- GIVEN `playwright.config.js`
- WHEN it is inspected
- THEN exactly one project exists, it uses Chromium, and it runs headless

### Requirement: Fresh deterministic database per run
Each run MUST start from a fresh in-memory database with the deterministic demo seed, so the first ticket requested in a run is `T-012`. Tests MUST run serially (one worker).

#### Scenario: fresh seed yields T-012
- GIVEN a fresh run against the seeded in-memory database
- WHEN the client view requests a ticket as the first mutating test
- THEN the ticket shown is `T-012`

#### Scenario: serial execution
- GIVEN `playwright.config.js`
- WHEN it is inspected
- THEN `workers` is 1 and `fullyParallel` is not enabled

### Requirement: Time-of-day independent expectations
Position, next ticket, and counts asserted by tests MUST be derived from the live dashboard response read in the same test, never hard-coded from a clock or fixed seed ordering.

#### Scenario: position derived from the live dashboard
- GIVEN the client has requested a ticket on the real backend
- WHEN the test reads `/api/v1/turnos/dashboard` and the client view shows the queue row
- THEN the displayed position equals the ticket's 1-based place among PENDIENTE tickets in that response

### Requirement: Real-backend client flows
The suite MUST prove these client-view flows against the real backend, without mocked responses.

#### Scenario: client requests a ticket
- GIVEN the client view is open and no ticket is held
- WHEN the user clicks "Solicitar turno"
- THEN the status text "Turno T-012 solicitado." is announced
- AND the card shows "Pendiente" and the button reads "Ya tienes un turno"
- AND the queue row shows the position from the live dashboard

#### Scenario: client cancel keep
- GIVEN the client holds a pending ticket
- WHEN the user clicks "Cancelar turno" and then "Conservar"
- THEN the confirmation text "Si cancelas, el asesor no llamará este turno." disappears
- AND the ticket stays "Pendiente"

#### Scenario: client cancel confirm
- GIVEN the client holds a pending ticket
- WHEN the user clicks "Cancelar turno" and then "Sí, cancelar"
- THEN the text "Cancelaste este turno. El asesor no lo llamará." is shown
- AND the ticket status shows "Cancelado"

#### Scenario: client stats
- GIVEN the client view has loaded against the real backend
- WHEN the stats section is visible
- THEN "Personas en espera" equals the pending count in the live dashboard metrics
- AND "Turnos atendidos hoy" equals the attended count in those metrics
- AND the note "Cifras obtenidas del servicio." is shown

### Requirement: Real-backend advisor flows
The suite MUST prove these advisor-view flows against the real backend, without mocked responses.

#### Scenario: advisor loads the dashboard
- GIVEN the advisor view (`?vista=asesor`) is opened
- WHEN the load completes
- THEN the heading "Lista de turnos" is visible and the badge count equals the number of tickets in the live dashboard
- AND the label "Conectado al servidor" is shown

#### Scenario: advisor calls next
- GIVEN the dashboard has a next ticket
- WHEN the user clicks "Llamar al siguiente"
- THEN the table row of that ticket shows "Atendido"
- AND the next-ticket number changes to the new head of the queue from the live dashboard

#### Scenario: advisor searches by number
- GIVEN the advisor table is loaded
- WHEN the user types a ticket number into "Buscar por número"
- THEN only rows whose number contains the text are visible
- AND a search with no match shows "No hay turnos que coincidan con la búsqueda."

#### Scenario: advisor filters by status
- GIVEN the advisor table contains pending and attended tickets
- WHEN the user selects "Atendidos" in the "Estado:" filter
- THEN every visible row shows "Atendido"
- AND selecting "Pendientes" shows only rows with "Pendiente"

#### Scenario: view switch
- GIVEN the client view is open
- WHEN the user clicks the "Asesor" link in the "Cambiar de vista" navigation
- THEN the advisor view is shown and the URL contains `vista=asesor`
- AND clicking "Cliente" returns to the client view

### Requirement: Mocked advisor service-unavailable recovery
The suite MUST prove the advisor view's behavior on a 503 by mocking the dashboard response. While the dashboard cannot be loaded, the advisor view MUST NOT present unknown data as a real state: it MUST NOT show "Conectado al servidor", "No hay turnos pendientes" or "0 registros". This proof belongs to `browser-e2e` only and MUST NOT be claimed as satisfying `runtime-operations` or the `infra-cicd` full-stack 503 obligation.

#### Scenario: advisor 503 shows Reintentar and recovers
- GIVEN the dashboard request is mocked to return 503 `SERVICIO_NO_DISPONIBLE` until the mock is removed (React StrictMode loads the view twice in dev)
- WHEN the advisor view loads
- THEN an alert with the service message and a "Reintentar" button is shown
- AND the badge shows "Servidor no disponible"
- AND the text "Conectado al servidor", "No hay turnos pendientes" and "0 registros" are absent
- AND the next-ticket card shows "No se pudo consultar el siguiente turno."
- AND the count shows "Registros no disponibles"
- AND a table cell shows "No se pudieron cargar los turnos."
- AND the "Llamar al siguiente" button is disabled
- AND after the mock is removed, clicking "Reintentar" loads the table, hides the alert and shows the badge "Conectado al servidor" again

### Requirement: Mocked conflict and stale-ticket view scenarios
Scenarios that cannot be produced on demand against the real backend (409 races, a stale ticket) MUST be proven by mocking exactly the response involved while all other requests reach the real backend. The scenarios are specified in `concurrency-conflicts` ("Advisor conflict reloads without retry", "Client cancel conflict re-fetches") and `daily-queue` ("Browser holds a ticket missing from today's queue"); the Playwright test titles MUST equal those scenario names. The mocked scenarios defined in this capability ("Advisor loading state is not reported as connected", "Advisor empty queue is reported as empty", "Advisor refresh failure discards stale data", "Client keeps its ticket when refresh fails") follow the same title rule.

#### Scenario: Advisor conflict reloads without retry
- GIVEN the Playwright test with this title exists in the suite
- WHEN `npm run e2e` runs
- THEN it passes, proving the `concurrency-conflicts` scenario of the same name

#### Scenario: Client cancel conflict re-fetches
- GIVEN the Playwright test with this title exists in the suite
- WHEN `npm run e2e` runs
- THEN it passes, proving the `concurrency-conflicts` scenario of the same name

#### Scenario: Browser holds a ticket missing from today's queue
- GIVEN the Playwright test with this title exists in the suite
- WHEN `npm run e2e` runs
- THEN it passes, proving the `daily-queue` scenario of the same name

### Requirement: Visible Spanish copy is a contract
Tests MUST locate elements by role and visible text. Visible Spanish copy in the views and `turnosService.js` is therefore a contract: any change to that copy MUST update the affected tests in the same pull request. Tests MUST NOT use `data-testid`.

#### Scenario: role and text locators only
- GIVEN the files under `e2e/`
- WHEN they are searched for `data-testid` or `getByTestId`
- THEN no match exists

### Requirement: Evidence artifacts
The suite MUST capture named screenshots at key steps and on failure, a trace on the first retry, and an HTML report that never opens automatically. Reports and results MUST be git-ignored.

#### Scenario: screenshots written
- GIVEN a passing run
- WHEN it finishes
- THEN `test-results/` contains named screenshots for the key steps of the client and advisor flows

#### Scenario: failure and retry evidence
- GIVEN `playwright.config.js`
- WHEN it is inspected
- THEN screenshots are retained on failure, `trace` is `on-first-retry`, and the HTML reporter has `open: 'never'`

#### Scenario: outputs git-ignored
- GIVEN the repository `.gitignore`
- WHEN `git check-ignore` is run on `test-results/` and `playwright-report/` under `react-client`
- THEN both paths are ignored

### Requirement: Runner isolation
Vitest and Playwright MUST NOT collect each other's files. Vitest `test.include` MUST cover only `src/`; Playwright MUST collect only `e2e/`.

#### Scenario: Vitest ignores e2e
- GIVEN e2e spec files exist under `e2e/`
- WHEN `npm test` runs
- THEN no e2e spec file is collected and the run passes

#### Scenario: Playwright ignores unit tests
- GIVEN `src/services/turnosService.test.js` exists
- WHEN `npx playwright test --list` runs
- THEN no `src/` file is listed

### Requirement: Pinned toolchain and project config
Playwright Test MUST be pinned to the exact version `1.64.1`. The `testing` section of `openspec/config.yaml` MUST record the frontend unit runner (Vitest) and the e2e runner (Playwright).

#### Scenario: exact Playwright pin
- GIVEN `react-client/package.json`
- WHEN the `@playwright/test` entry is read
- THEN its version is exactly `1.64.1` with no range prefix

#### Scenario: openspec config records runners
- GIVEN `openspec/config.yaml`
- WHEN its `testing.frontend` section is read
- THEN the unit runner is Vitest and an e2e entry names Playwright with the `npm run e2e` command
- AND the "no test runner" gap text is gone

### Requirement: No application behavior change
This change MUST NOT alter API v1 contracts or application behavior. Changes under `src/` and `backend/` MUST be absent.

#### Scenario: app sources untouched
- GIVEN the change's diff against `main`
- WHEN it is filtered to `react-client/src/` and `backend/`
- THEN it is empty

#### Scenario: existing gates stay green
- GIVEN the change is applied
- WHEN `npm test` and `npm run lint` run
- THEN both pass

### Requirement: Honest advisor load states
The advisor view MUST distinguish unknown data from empty or present data. While no data is known (first load in flight, or any failed load) it MUST clear the previous queue data; data MAY be kept only while a refresh is in flight. The "Llamar al siguiente" button MUST be natively `disabled` whenever the view is not ready, and the next-ticket number MUST show "—". Visible copy per state:

| Element | Loading, no data | Error | Ready |
|---------|------------------|-------|-------|
| Badge | "Conectando con el servidor…" | "Servidor no disponible" | "Conectado al servidor" |
| Next card text | "Cargando el siguiente turno…" | "No se pudo consultar el siguiente turno." | existing copy |
| Count | "Cargando…" | "Registros no disponibles" | "{n} registros" |
| Table cell | "Cargando turnos…" | "No se pudieron cargar los turnos." | existing copy; empty day: "Aún no hay turnos registrados hoy." |

#### Scenario: Advisor loading state is not reported as connected
- GIVEN the dashboard route is held by a promise-gated mock (React StrictMode issues two GETs; both stay pending)
- WHEN the advisor view is opened
- THEN the badge shows "Conectando con el servidor…" and "Conectado al servidor" is absent
- AND the next card shows "Cargando el siguiente turno…", the count shows "Cargando…" and a table cell shows "Cargando turnos…"
- AND "No hay turnos pendientes" and "0 registros" are absent
- AND "Llamar al siguiente" is disabled

#### Scenario: Advisor empty queue is reported as empty
- GIVEN the dashboard request is mocked to return 200 with no tickets
- WHEN the advisor view loads
- THEN the badge shows "Conectado al servidor"
- AND "No hay turnos pendientes" and "0 registros" are shown
- AND a table cell shows "Aún no hay turnos registrados hoy."
- AND no alert is shown and "Llamar al siguiente" is disabled

#### Scenario: Advisor refresh failure discards stale data
- GIVEN the advisor view has loaded against the real backend with a next ticket
- AND `PATCH` to the call-next endpoint is mocked to return 200 `{}` (no real mutation)
- AND the dashboard request is mocked to return 503 `SERVICIO_NO_DISPONIBLE`
- WHEN the user clicks "Llamar al siguiente"
- THEN an alert is shown
- AND the previous next-ticket number is no longer shown
- AND "Llamar al siguiente" is disabled
- AND the badge shows "Servidor no disponible"

### Requirement: Client keeps its ticket when refresh fails
A failure to reach the server (503 or network error) while refreshing the client's ticket MUST NOT clear the held ticket, because the ticket's absence is unknown. This guards the `TURNO_NO_ENCONTRADO` rule in `daily-queue`; client-view 503 handling beyond this guard stays deferred to `client-identity`.

#### Scenario: Client keeps its ticket when refresh fails
- GIVEN the client holds a real pending ticket from the real backend
- AND the dashboard request is mocked to return 503 `SERVICIO_NO_DISPONIBLE`
- WHEN the user clicks "Actualizar estado"
- THEN an alert shows the 503 `mensaje`
- AND the card still shows "Pendiente"
- AND the "Actualizar estado" button is visible

### Requirement: Accessible state semantics
The advisor connection badge MUST have `role="status"`, each view MUST show at most one element with `role="alert"` at a time, and the call-next button MUST use the native `disabled` attribute (not `aria-disabled` or a class).

#### Scenario: status, single alert and native disabled
- GIVEN the Playwright tests of "Honest advisor load states" and "advisor 503 shows Reintentar and recovers"
- WHEN they run
- THEN they locate the badge with `getByRole('status')`, use strict `getByRole('alert')` (a second alert makes them fail) and assert `toBeDisabled()` on "Llamar al siguiente"
- AND "Client keeps its ticket when refresh fails" also uses strict `getByRole('alert')`

## Operational notes (not spec scenarios)

Not provable by an automated check in this change; documented only.

- Windows 10 is outside Playwright's documented support matrix; a manual smoke run is recorded in the PR.
- CI on Linux is owned by `infra-cicd`; the e2e workflow is out of scope here.
- A stale `java` process holding port 8080 (Windows) must be killed by hand; the run fails fast on a busy port.
- `mvnw` needs its executable bit on Linux CI.
- A run that crosses midnight in `America/Bogota` may see a new day's queue.
- npm 11.3.0 has a lockfile bug; regenerate with a newer npm if it appears.
