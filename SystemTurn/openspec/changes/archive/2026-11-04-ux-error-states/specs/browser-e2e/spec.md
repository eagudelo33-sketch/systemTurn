# Delta for browser-e2e

No API change. Nothing under `backend/` changes; `TURNO_NO_ENCONTRADO` (see `daily-queue`) is client-side only. Visible Spanish copy below is a test contract. The Playwright suite goes from 13 to 18 tests (2 modified, 4 new, 1 accessibility).

## MODIFIED Requirements

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

## ADDED Requirements

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
