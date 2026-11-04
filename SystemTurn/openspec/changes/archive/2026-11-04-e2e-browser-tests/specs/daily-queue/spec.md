# Delta for Daily Queue

No API or application behavior changes.

## ADDED Requirements

### Requirement: Stale ticket shows generic not-found
When the client queries a ticket that is absent from today's dashboard (for example a ticket dated on a past day), the client view MUST show the generic message "No encontramos ese turno." No special "expired" message MUST be shown. This change does NOT claim a ticket survives a page refresh; true stored-ticket behavior belongs to `client-identity`.

#### Scenario: Browser holds a ticket missing from today's queue
- GIVEN the client view holds a pending ticket in the current page session
- AND the dashboard request is mocked to return today's queue without that ticket
- WHEN the user clicks "Actualizar estado"
- THEN an alert shows "No encontramos ese turno."
- AND no message mentioning an expired or past-day ticket is shown
- Proven by: Playwright test "Browser holds a ticket missing from today's queue" (mocked response)
