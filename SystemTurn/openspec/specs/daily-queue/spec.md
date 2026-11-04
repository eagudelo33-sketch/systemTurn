# Daily Queue Specification

## Purpose
The queue, numbering, and dashboard are scoped to the current day.

## Requirements

### Requirement: Today's date by configured zone
"Today" MUST be derived from an injected clock in zone `turnos.timezone` (default `America/Bogota`).

#### Scenario: Midnight boundary
- GIVEN the clock reads 2026-10-30T04:30Z (23:30 on 2026-10-29 Bogota)
- WHEN a ticket is requested
- THEN it is dated 2026-10-29
- AND at 2026-10-30T05:00Z the next ticket is dated 2026-10-30

### Requirement: Queue and dashboard scoped to today
Queue, position, and dashboard MUST include only tickets whose `fecha` is today. PENDIENTE tickets from past days MUST remain PENDIENTE as history and MUST NOT change state.

#### Scenario: Yesterday excluded
- GIVEN 3 PENDIENTE tickets dated 2026-10-29 and 1 dated 2026-10-30
- WHEN the dashboard is read on 2026-10-30
- THEN only 1 pending ticket is counted
- AND the 2026-10-29 tickets remain PENDIENTE in storage

### Requirement: Daily numbering restart
Numbers MUST be max(today)+1, starting at 1 each day, formatted `T-NNN`.

#### Scenario: First ticket of day
- GIVEN the highest ticket on 2026-10-29 is 11
- WHEN the first ticket is requested on 2026-10-30
- THEN it is `T-001`

### Requirement: Past-day cancel rejected
Cancelling a ticket not dated today MUST fail with 400 `OPERACION_INVALIDA`.

#### Scenario: Cancel yesterday's ticket
- GIVEN ticket id 5 is PENDIENTE and dated 2026-10-29
- WHEN it is cancelled on 2026-10-30
- THEN the response is 400 `OPERACION_INVALIDA` and the state is unchanged


### Requirement: Stale ticket shows generic not-found
When the client queries a ticket that is absent from today's dashboard (for example a ticket dated on a past day), the client view MUST show the generic message "No encontramos ese turno." No special "expired" message MUST be shown. A ticket surviving a page refresh is not claimed here; true stored-ticket behavior belongs to `client-identity`.

#### Scenario: Browser holds a ticket missing from today's queue
- GIVEN the client view holds a pending ticket in the current page session
- AND the dashboard request is mocked to return today's queue without that ticket
- WHEN the user clicks "Actualizar estado"
- THEN an alert shows "No encontramos ese turno."
- AND no message mentioning an expired or past-day ticket is shown
- Proven by: Playwright test "Browser holds a ticket missing from today's queue" (mocked response)
