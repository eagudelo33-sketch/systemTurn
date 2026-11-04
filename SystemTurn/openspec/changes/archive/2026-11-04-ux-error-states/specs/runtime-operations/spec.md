# Delta for runtime-operations

No API change. Only the scope note of one requirement changes; all its scenarios are kept unchanged.

## MODIFIED Requirements

### Requirement: Frontend 503 handling
`turnosService.js` MUST map an HTTP 503 response with `error` `SERVICIO_NO_DISPONIBLE` to a thrown error whose message is the body `mensaje`. The advisor view's behavior on a 503 is specified in `browser-e2e` ("Mocked advisor service-unavailable recovery", "Honest advisor load states"). Client-view 503 handling remains out of scope here (deferred to `client-identity`); `browser-e2e` only guards that a refresh failure keeps the held ticket.

#### Scenario: Service maps 503
- GIVEN fetch is mocked to return status 503 with body `{"error":"SERVICIO_NO_DISPONIBLE","mensaje":"Servicio no disponible, intenta de nuevo en unos minutos."}`
- WHEN a service call (for example `solicitarTurno`) runs
- THEN it rejects with an error whose message is `Servicio no disponible, intenta de nuevo en unos minutos.`

#### Scenario: 409 mapping unchanged
- GIVEN fetch is mocked to return 409 `CONFLICTO`
- WHEN a service call runs
- THEN it rejects with the friendly message and the refreshable flag, as before
