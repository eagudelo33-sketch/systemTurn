# Runtime Operations Specification

## Purpose
The API runs as several stateless containers against PostgreSQL: configuration comes from the environment, unsafe database setups fail at startup, probes ignore the database, and shutdown is graceful.

## Requirements

### Requirement: Environment-driven datasource
The datasource MUST be configured through `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD`. The repository MUST NOT contain any password or production URL. `spring.datasource.url` MUST have no default in the base configuration.

#### Scenario: Missing URL fails startup
- GIVEN no active profile and no `SPRING_DATASOURCE_URL`
- WHEN the application context starts
- THEN startup fails with an error that names `spring.datasource.url`
- AND no embedded database is created

#### Scenario: Environment values are applied
- GIVEN `SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/turnos`, `SPRING_DATASOURCE_USERNAME=app`, `SPRING_DATASOURCE_PASSWORD=s3cret`
- WHEN the datasource properties are bound
- THEN the bound URL, username, and password equal those values

#### Scenario: No committed secrets
- GIVEN the files under `src/main/resources`
- WHEN they are scanned for `password` values
- THEN no non-empty literal password is found

### Requirement: Embedded database fail-fast
The application MUST start with an H2/embedded datasource URL only when profile `dev` or `test` is active. With any other profile set, including none, an H2/embedded URL MUST make startup fail with an error that names the offending URL scheme and the allowed profiles.

#### Scenario: H2 without dev or test is rejected
- GIVEN no active profile and `SPRING_DATASOURCE_URL=jdbc:h2:mem:x`
- WHEN the startup guard runs
- THEN startup fails with an error mentioning `h2` and the profiles `dev` and `test`

#### Scenario: H2 with `prod` is rejected
- GIVEN profile `prod` and `SPRING_DATASOURCE_URL=jdbc:h2:file:./data/turnos`
- WHEN the startup guard runs
- THEN startup fails

#### Scenario: H2 with dev is allowed
- GIVEN profile `dev` and `SPRING_DATASOURCE_URL=jdbc:h2:file:./data/turnos`
- WHEN the startup guard runs
- THEN startup succeeds

#### Scenario: H2 with test is allowed
- GIVEN profile `test` and an in-memory H2 URL
- WHEN the startup guard runs
- THEN startup succeeds

#### Scenario: PostgreSQL passes without dev or test
- GIVEN no active profile and `SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/turnos`
- WHEN the startup guard runs
- THEN the guard does not reject the configuration

### Requirement: Dev profile on H2
Profile `dev` MUST provide a zero-Docker local setup backed by an H2 file database, using the same Flyway `V1` migration with no vendor-specific migration folder.

#### Scenario: Dev starts empty on H2
- GIVEN profile `dev` and a new H2 file path
- WHEN the context starts
- THEN `V1` is applied, the Hibernate schema validation passes, and the queue is empty

### Requirement: Pool, migration retry, and probes configuration
The Hikari maximum pool size MUST default to 10 and be overridable by environment; the connection timeout MUST be at most 5000 ms. Flyway MUST be configured with connect retries greater than zero.

#### Scenario: Defaults
- GIVEN no overriding environment variables
- WHEN the configuration is bound
- THEN pool size is 10, connection timeout is at most 5000 ms, and Flyway connect retries is greater than 0

#### Scenario: Pool size override
- GIVEN `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=4`
- WHEN the configuration is bound
- THEN the pool size is 4

### Requirement: Health probes exclude the database
The application MUST expose only the `health` actuator endpoint over HTTP, with `/actuator/health/readiness` and `/actuator/health/liveness` enabled. Neither probe MUST include the database. While the database is unreachable, both probes MUST report status `UP`.

#### Scenario: Probes UP with a healthy database
- GIVEN the application is running with a reachable database
- WHEN `GET /actuator/health/readiness` and `GET /actuator/health/liveness` are called
- THEN both return 200 with status `UP`

#### Scenario: Probes stay UP with the database down
- GIVEN the application is running and the database connection fails on every attempt
- WHEN `GET /actuator/health/readiness` and `GET /actuator/health/liveness` are called
- THEN both return 200 with status `UP`

#### Scenario: Only health is exposed
- GIVEN the application is running
- WHEN `GET /actuator/env` and `GET /actuator/beans` are called
- THEN each returns 404

### Requirement: Database outage returns 503
When a request needs the database and it is unreachable (connection acquisition fails or times out), the API MUST fail fast and respond HTTP 503 with body `{"error":"SERVICIO_NO_DISPONIBLE","mensaje":"<friendly Spanish message>"}`, using the existing error shape. The response MUST NOT expose driver or SQL details. The 409 `CONFLICTO` and 400 `OPERACION_INVALIDA` behaviors MUST NOT change.

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

### Requirement: Frontend 503 handling
`turnosService.js` MUST map an HTTP 503 response with `error` `SERVICIO_NO_DISPONIBLE` to a thrown error whose message is the body `mensaje`. View-level behavior is out of scope (deferred to `client-identity`).

#### Scenario: Service maps 503
- GIVEN fetch is mocked to return status 503 with body `{"error":"SERVICIO_NO_DISPONIBLE","mensaje":"Servicio no disponible, intenta de nuevo en unos minutos."}`
- WHEN a service call (for example `solicitarTurno`) runs
- THEN it rejects with an error whose message is `Servicio no disponible, intenta de nuevo en unos minutos.`

#### Scenario: 409 mapping unchanged
- GIVEN fetch is mocked to return 409 `CONFLICTO`
- WHEN a service call runs
- THEN it rejects with the friendly message and the refreshable flag, as before

### Requirement: Graceful shutdown
The server MUST use graceful shutdown with a per-phase timeout of 25 seconds, so in-flight requests finish before the ECS 30 s stop timeout.

#### Scenario: Configuration
- GIVEN the bound configuration
- WHEN shutdown properties are read
- THEN `server.shutdown` is `graceful` and `spring.lifecycle.timeout-per-shutdown-phase` is 25 seconds

#### Scenario: In-flight request completes
- GIVEN a request that takes 1 second is in flight
- WHEN the context is closed
- THEN the request completes with its normal response and no connection reset

### Requirement: API v1 contract unchanged
Routes, payloads, and status codes of API v1 MUST NOT change, other than the added 503 outcome.

#### Scenario: Existing controller tests
- GIVEN the existing controller test suite, unmodified
- WHEN it runs
- THEN all tests pass

### Requirement: PostgreSQL test suite gating
The build MUST include a PostgreSQL test suite using Testcontainers. Without Docker, it MUST be skipped when property `requireDocker` is not `true`, and MUST FAIL when `requireDocker=true`. The default H2 suite MUST NOT need Docker.

#### Scenario: Skipped without Docker
- GIVEN Docker is unavailable and `requireDocker` is unset
- WHEN the Docker-availability condition is evaluated
- THEN the Postgres suite is reported as skipped

#### Scenario: Fails when Docker is required
- GIVEN Docker is unavailable and `requireDocker=true`
- WHEN the Docker-availability condition is evaluated
- THEN it raises a failure whose message mentions `requireDocker`

#### Scenario: Runs with Docker
- GIVEN Docker is available
- WHEN the Postgres suite runs
- THEN Flyway `V1` applies, schema validation passes, and the suite executes against a PostgreSQL container

## Operational notes (not scenarios; manual or out of this change)
- ALB target health check path: `/actuator/health/readiness`.
- Pool size times task count must stay below RDS `max_connections`.
- TLS (`sslmode`) is set in the datasource URL value.
- Enabling `requireDocker=true` in CI lands with `infra-cicd`.
- Production MUST NOT run with the `test` (or `demo`) profile: the H2 guard allows H2 under `test`, so it cannot catch that case. Enforced by the ECS task definition in `infra-app`.
- The RDS datasource URL SHOULD set `socketTimeout` so in-flight queries cannot hang during a failover (Hikari's connection timeout only covers acquisition). Set in `infra-app`.
