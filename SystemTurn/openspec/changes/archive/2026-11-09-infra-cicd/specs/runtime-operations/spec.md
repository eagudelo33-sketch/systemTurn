# Delta for runtime-operations

Verification: JUnit in `backend/turnos-api` under plain `./mvnw test` (no Docker, no `-DrequireDocker`).

## MODIFIED Requirements

### Requirement: Database outage returns 503
When a request needs the database and it is unreachable (connection acquisition fails or times out), the API MUST fail fast and respond HTTP 503 with body `{"error":"SERVICIO_NO_DISPONIBLE","mensaje":"<friendly Spanish message>"}`, using the existing error shape. The response MUST NOT expose driver or SQL details. The 409 `CONFLICTO` and 400 `OPERACION_INVALIDA` behaviors MUST NOT change. This MUST also hold end to end: a real HTTP request traversing controller, service, JPA adapter and the Hikari pool against an unreachable database MUST yield the 503 contract, and that proof MUST run without Docker.
(Previously: the requirement covered only a repository-level failure; the full-stack path was unspecified.)

#### Scenario: Solicitar with the database down
- GIVEN the repository throws a connection-acquisition failure (`CannotGetJdbcConnectionException`)
- WHEN `POST /api/v1/turnos` is called
- THEN the status is 503
- AND the body `error` is `SERVICIO_NO_DISPONIBLE` and `mensaje` is a non-empty Spanish text

#### Scenario: Queue read with the database down
- GIVEN the same failure
- WHEN the queue endpoint is called
- THEN the status is 503 with `error` `SERVICIO_NO_DISPONIBLE`

#### Scenario: No internal details leak
- GIVEN the failure message contains `jdbc:postgresql://db:5432/turnos`
- WHEN the API responds
- THEN the body does not contain that URL or the word `jdbc`

#### Scenario: Fast failure
- GIVEN the database host does not accept connections
- WHEN a DB-dependent request is made
- THEN the 503 is returned within the Hikari connection timeout plus 2 seconds

#### Scenario: Full-stack outage returns 503 without Docker
- GIVEN the application started on a random port with the real JPA adapter and Hikari pointed at an unreachable local port, with a short connection timeout
- WHEN a real HTTP `POST /api/v1/turnos` and a queue `GET` are sent
- THEN each response is 503 with `error` `SERVICIO_NO_DISPONIBLE` and a non-empty Spanish `mensaje`
- AND the body contains neither `jdbc` nor the unreachable host/port
- AND the test needs no Docker and passes under plain `./mvnw test`
