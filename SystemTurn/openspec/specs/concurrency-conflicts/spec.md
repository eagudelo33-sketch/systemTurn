# Concurrency Conflicts Specification

## Purpose
Concurrent writers never corrupt numbering or state; conflicts surface as 409 `CONFLICTO`.

## Requirements

### Requirement: Unique daily numbers
`(fecha, numero)` MUST be unique in storage. Number assignment MUST retry a bounded number of times on a duplicate.

#### Scenario: Concurrent solicitar
- GIVEN an empty queue on 2026-10-30
- WHEN 10 threads call solicitar simultaneously
- THEN exactly numbers 1..10 are assigned, each once

#### Scenario: Retry exhaustion
- GIVEN every retry hits a duplicate number
- WHEN solicitar is called
- THEN the response is 409 `CONFLICTO`

### Requirement: Optimistic locking
`atender` and `cancelar` MUST fail with 409 `CONFLICTO` when the ticket changed since it was read.

#### Scenario: Version conflict
- GIVEN two actors read ticket 6 at the same version
- WHEN one cancels it, then the other attends it
- THEN the second gets 409 `CONFLICTO` and the ticket stays CANCELADO

### Requirement: 409 contract
The error body MUST use the existing error shape with code `CONFLICTO`; other API v1 shapes MUST NOT change.

#### Scenario: Error body
- GIVEN a stale attend
- WHEN the API responds
- THEN status is 409 and the code is `CONFLICTO`

### Requirement: Frontend conflict handling
`turnosService.js` MUST map `CONFLICTO` to a friendly message flagged refreshable, so every view can react to it. The advisor view MUST reload the dashboard and show that message without a retry button. The client view MUST re-fetch the ticket and keep that message visible. The view-level scenarios are proven by Playwright tests that mock only the conflicting response (`browser-e2e`).

#### Scenario: Service mapping
- GIVEN the API returns 409 `CONFLICTO`
- WHEN the service handles it
- THEN it throws an error with a friendly message and a refreshable flag

#### Scenario: Cancel after a completed attend is not a conflict
- GIVEN ticket 6 was attended and that change is already saved
- WHEN the client cancels ticket 6
- THEN the response is 400 `OPERACION_INVALIDA`, not 409

#### Scenario: Advisor conflict reloads without retry
- GIVEN the advisor view shows a next ticket
- AND the "call next" request (`PATCH /api/v1/turnos/siguiente`) is mocked to return 409 `CONFLICTO`
- WHEN the user clicks "Llamar al siguiente"
- THEN an alert shows "El turno cambió mientras se procesaba. Actualiza e intenta de nuevo."
- AND the dashboard is reloaded from the backend (a further dashboard request is made and the table is shown)
- AND no "Reintentar" button is present
- Proven by: Playwright test "Advisor conflict reloads without retry" (mocked response)

#### Scenario: Client cancel conflict re-fetches
- GIVEN the client holds a pending ticket and has confirmed with "Sí, cancelar"
- AND the cancel request (`PATCH /api/v1/turnos/{id}/cancelar`) is mocked to return 409 `CONFLICTO`
- WHEN the response arrives
- THEN an alert shows "El turno cambió mientras se procesaba. Actualiza e intenta de nuevo."
- AND the ticket is re-fetched from the dashboard and its current state is shown
- AND the alert remains visible after the re-fetch
- Proven by: Playwright test "Client cancel conflict re-fetches" (mocked response)

### Requirement: Engine-independent duplicate detection
An insert violating the `(fecha, numero)` unique constraint MUST be translated to a duplicate-number outcome on every supported engine, comparing the constraint name case-insensitively (H2 reports `UK_TURNO_FECHA_NUMERO`, PostgreSQL `uk_turno_fecha_numero`). Other integrity violations MUST NOT be reported as duplicate numbers. Number assignment, the bounded retry, and 409 `CONFLICTO` behavior MUST be identical on H2 and PostgreSQL.

#### Scenario: Duplicate on H2
- GIVEN ticket number 1 exists for 2026-10-30 on H2
- WHEN another ticket with number 1 for 2026-10-30 is saved
- THEN `NumeroDuplicadoException` is raised

#### Scenario: Duplicate on PostgreSQL
- GIVEN ticket number 1 exists for 2026-10-30 on PostgreSQL
- WHEN another ticket with number 1 for 2026-10-30 is saved
- THEN `NumeroDuplicadoException` is raised

#### Scenario: Mixed-case constraint name
- GIVEN a unique violation whose constraint name is `Uk_Turno_Fecha_Numero`
- WHEN the repository translates it
- THEN it is a duplicate-number outcome

#### Scenario: Unrelated violation is not a duplicate
- GIVEN a not-null or check violation on a ticket insert
- WHEN the repository translates it
- THEN it is NOT a duplicate-number outcome

#### Scenario: Concurrent solicitar on PostgreSQL
- GIVEN an empty queue on 2026-10-30 in PostgreSQL
- WHEN 10 threads call solicitar simultaneously
- THEN exactly numbers 1..10 are assigned, each once

#### Scenario: Version conflict on PostgreSQL
- GIVEN two actors read ticket 6 at the same version on PostgreSQL
- WHEN one cancels it, then the other attends it
- THEN the second gets 409 `CONFLICTO` and the ticket stays CANCELADO

#### Scenario: Retry exhaustion unchanged
- GIVEN every retry hits a duplicate number
- WHEN solicitar is called
- THEN the response is 409 `CONFLICTO`
