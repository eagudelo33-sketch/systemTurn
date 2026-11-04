# Delta for Concurrency Conflicts

No API or application behavior changes. The view reactions below are already implemented; this delta adds their spec promise and proof.

## MODIFIED Requirements

### Requirement: Frontend conflict handling
`turnosService.js` MUST map `CONFLICTO` to a friendly message flagged refreshable, so every view can react to it. The advisor view MUST reload the dashboard and show that message without a retry button. The client view MUST re-fetch the ticket and keep that message visible. The view-level scenarios are proven by Playwright tests that mock only the conflicting response (`browser-e2e`); the previous deferral note is removed.

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
