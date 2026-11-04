# Delta for daily-queue

No API change. `TURNO_NO_ENCONTRADO` is a client-side code created by `turnosService.js`; the backend never sends it.

## MODIFIED Requirements

### Requirement: Stale ticket shows generic not-found
When the client queries a ticket that is absent from today's dashboard (for example a ticket dated on a past day), the client view MUST show the generic message "No encontramos ese turno." No special "expired" message MUST be shown. Because the server has definitively reported the ticket absent, the view MUST also clear the held ticket: the card returns to its empty state and the user MUST be able to request a new ticket. A ticket surviving a page refresh is not claimed here; true stored-ticket behavior belongs to `client-identity`.

#### Scenario: Browser holds a ticket missing from today's queue
- GIVEN the client view holds a pending ticket in the current page session
- AND the dashboard request is mocked to return today's queue without that ticket
- WHEN the user clicks "Actualizar estado"
- THEN an alert shows exactly "No encontramos ese turno."
- AND no message mentioning an expired, yesterday or past-day ticket is shown
- AND the card shows "Aún no tienes un turno asignado." and no "Pendiente"
- AND the "Solicitar turno" button is enabled
- AND "Cancelar turno" and "Actualizar estado" are absent
- Proven by: Playwright test "Browser holds a ticket missing from today's queue" (mocked response)

## ADDED Requirements

### Requirement: Typed not-found error in the client service
`consultarTurno` MUST reject with an error whose `code` is `TURNO_NO_ENCONTRADO` and whose message is "No encontramos ese turno." when the ticket is absent from today's dashboard. A 503 `SERVICIO_NO_DISPONIBLE`, a network failure or any other error MUST NOT be converted to that code. The view MUST clear the held ticket only for that code.

#### Scenario: consultarTurno rejects with the not-found code
- GIVEN fetch is mocked to return a dashboard that does not contain the queried ticket
- WHEN `consultarTurno` runs
- THEN it rejects with `code` `TURNO_NO_ENCONTRADO` and message "No encontramos ese turno."
- Proven by: Vitest in `src/services/turnosService.test.js`

#### Scenario: consultarTurno resolves a present ticket
- GIVEN fetch is mocked to return a dashboard containing the queried ticket
- WHEN `consultarTurno` runs
- THEN it resolves with the normalized ticket and does not reject
- Proven by: Vitest in `src/services/turnosService.test.js`

#### Scenario: 503 is not converted to the not-found code
- GIVEN fetch is mocked to return 503 `SERVICIO_NO_DISPONIBLE` with a `mensaje`
- WHEN `consultarTurno` runs
- THEN it rejects with that `mensaje` and its `code` is not `TURNO_NO_ENCONTRADO`
- Proven by: Vitest in `src/services/turnosService.test.js`
