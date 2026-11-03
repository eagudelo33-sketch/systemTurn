# Design: PostgreSQL Adapter

## Technical Approach

PostgreSQL becomes the default datasource, configured only through `SPRING_DATASOURCE_*`. H2 stays on the runtime classpath for the `dev` profile, and an infrastructure startup guard rejects H2 outside `dev`/`test`. V1, the port, the API and the non-transactional `saveAndFlush` adapter stay as they are. Two things are new: when the DB is unreachable, the adapter raises a domain exception that becomes a fast 503, and duplicate detection now uses the constraint name. A Testcontainers suite reuses the existing H2 test classes by subclassing them. Note: proposal assumption 3 ("instance goes not-ready") is superseded by the binding answer: the DB is excluded from readiness and liveness.

## Architecture Decisions

| Decision | Choice | Rejected (tradeoff) |
|---|---|---|
| H2 packaging | Keep `runtime` scope. The guard makes a silent fallback impossible | Maven `-Pdev` profile adds H2 (prod jar has no H2, but dev needs two switches and Maven + Spring profiles can drift); test-only scope (breaks zero-Docker `dev`) |
| Guard mechanism | `infrastructure/StartupGuard`: a `@Configuration` with a **static** `BeanPostProcessor` bean. Its factory method checks the demo rule using `Environment`, which runs before any singleton is created. `postProcessBeforeInitialization` checks the **effective** URL of every `DataSource` (`HikariDataSource#getJdbcUrl`), and also rejects an `EmbeddedDatabase` instance. This runs before Hikari connects and before Flyway | `EnvironmentPostProcessor` (sees only properties, not Boot's generated embedded URL, needs `spring.factories`, and its Boot 4 package moved); `ApplicationContextInitializer` (same blind spot); `@PostConstruct` check (runs after Flyway has migrated an embedded DB) |
| Guard rules | (1) `demo` without `dev` fails. (2) An effective URL starting with `jdbc:h2:` while neither `dev` nor `test` is active fails. If `spring.datasource.url` is absent, the message says so (Boot fell back to embedded). Decisions live in a pure static method for unit tests | Checking only `spring.datasource.url` (misses the embedded fallback) |
| DB-unreachable mapping | The adapter wraps each port call. It translates `CannotCreateTransactionException` (connection acquire fails at tx begin) and `DataAccessResourceFailureException` (includes `CannotGetJdbcConnectionException`, and Hibernate `JDBCConnectionException` once translated) into domain `ServicioNoDisponibleException`. `ApiExceptionHandler` maps that to 503 | Handler catching Spring exceptions directly (web would depend on persistence exceptions, which breaks the domain-exception pattern); catching `DataAccessException` (would swallow real bugs) |
| Fast failure | Hikari `connection-timeout: 5s`. `solicitar` retries only `NumeroDuplicadoException`, so a 503 is never retried | Default 30s (requests hang through an RDS failover) |
| Duplicate detection | Walk the cause chain for `org.hibernate.exception.ConstraintViolationException`. Strip quotes from `getConstraintName()`, lowercase it, then accept it if it equals or ends with `uk_turno_fecha_numero` (covers schema-qualified names). If there is no name, fall back to the current uppercased message `contains` check. The `id == null` gate stays | Requiring `getKind()==UNIQUE` (unverified on H2); SQLState 23505 alone (cannot tell which unique constraint failed) |
| Health | Expose `health` only. Enable probes. Pin `readiness.include: readinessState` and `liveness.include: livenessState`. The ALB uses `/actuator/health/readiness`. `/actuator/health` still shows the db status for operators | DB in readiness (an RDS failover pulls every task, per user decision) |
| Postgres tests | A singleton `PostgreSQLContainer`, started lazily and reaped by Ryuk. Subclasses inject URL, user and password with `@DynamicPropertySource` | `@ServiceConnection` bean (Boot stops the container when the context closes, which breaks the two-context restart test); a container per class (slow) |
| Docker condition | `@EnabledIfDockerOrRequired` → `ExecutionCondition`: enabled if `DockerClientFactory.instance().isDockerAvailable()` or `-DrequireDocker=true`. When required, the test is enabled and fails on the real Docker error | `@Testcontainers(disabledWithoutDocker)` (only applies to `@Container` fields) |
| Image | `postgres:17.6` pinned (Debian, no musl locale surprises) | `latest`/`17` floating tags (non-reproducible) |
| `demo` tests | Three classes switch to `@ActiveProfiles({"dev","demo","test"})`. Because `test` is last, its in-memory URL overrides `application-dev.yml` | Allowing `demo` with `test` (a prod task started with `test` could seed real data) |

## Configuration

| File | Content |
|---|---|
| `application.yml` | No `datasource.url`. `hikari.maximum-pool-size: ${TURNOS_DB_POOL_SIZE:10}`, `connection-timeout: 5000`. `flyway.connect-retries: ${TURNOS_DB_CONNECT_RETRIES:10}`. `server.shutdown: graceful`, `spring.lifecycle.timeout-per-shutdown-phase: 25s`. Management exposure/probes/groups as above. Keep jpa/turnos keys |
| `application-dev.yml` (new) | `url: jdbc:h2:file:${TURNOS_DB_PATH:./data/turnos}` |
| `application-test.yml` | Unchanged (H2 mem, UUID) |

Env vars: `SPRING_DATASOURCE_URL` (required outside `dev`; TLS and `socketTimeout` go in the URL), `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `TURNOS_DB_POOL_SIZE`, `TURNOS_DB_CONNECT_RETRIES`, `TURNOS_DB_PATH` (dev only), `TURNOS_ALLOWED_ORIGINS`, `SPRING_PROFILES_ACTIVE`.

## Data Flow

    Controller → TurnoService → JpaTurnoRepository ─┬→ Spring Data (per-call tx) → Hikari(5s) → Postgres
                                                     └ conn failure → ServicioNoDisponibleException
    ApiExceptionHandler: 503 {"error":"SERVICIO_NO_DISPONIBLE","mensaje":"..."}
    ALB → /actuator/health/readiness (readinessState only)

## Interfaces / Contracts

503 body: `{"error":"SERVICIO_NO_DISPONIBLE","mensaje":"El servicio no está disponible en este momento. Intenta de nuevo en unos minutos."}`. Frontend `request()`: if `data?.error === 'SERVICIO_NO_DISPONIBLE'`, throw an Error with that message (body fallback) and `code` set. It is not `refreshable`.

## File Changes (est. changed lines)

| File | Action | Est. |
|---|---|---|
| `pom.xml` (postgresql runtime, flyway-database-postgresql, actuator, testcontainers-postgresql test, `requireDocker` property + surefire `systemPropertyVariables`) | Modify | 30 |
| `application.yml` / `application-dev.yml` | Modify/Create | 34 |
| `infrastructure/StartupGuard.java` | Create | 55 |
| `domain/ServicioNoDisponibleException.java` | Create | 8 |
| `persistence/JpaTurnoRepository.java` | Modify | 40 |
| `web/ApiExceptionHandler.java` | Modify | 6 |
| `StartupGuardTest` (pure rules + 2 boot starts) | Create | 80 |
| `JpaTurnoRepositoryTest` (name variants, fallback, 503 translation) | Modify | 65 |
| `ConflictoHandlerTest` (503 case), `HealthGroupsTest` (`HealthEndpointGroups` membership) | Modify/Create | 45 |
| `testsupport/PostgresSupport`, `EnabledIfDockerOrRequired` | Create | 60 |
| `*PostgresTest` subclasses (repository, concurrency, restart); base cleanup/arg hooks; demo/restart profile edits | Create/Modify | 71 |
| `turnosService.js` + `.test.js` | Modify | 23 |
| `README.md`, `docs/01-current-state.md`, `docs/02-gaps-and-questions.md` | Modify | 80 |
| **Total (excl. spec deltas ~100)** | | **~597** (~700 with deltas) |

## Testing Strategy

| Layer | What | Approach |
|---|---|---|
| Unit | Guard rules; detection on fabricated cause chains (UPPER, lower, quoted, schema-qualified, null name) | Plain JUnit |
| Integration (H2) | No URL / H2 without dev fails; dev starts; demo without dev fails; 503 shape; health groups exclude `db` | `SpringApplicationBuilder`, `@WebMvcTest` |
| Integration (Postgres) | Full repository contract, 10-thread `solicitar`, restart survival, Flyway+`validate` | Subclasses + `@BeforeEach delete from turno` |
| Frontend | `SERVICIO_NO_DISPONIBLE` message + code | Vitest `stubFetchResponse(503, …)` |

## Threat Matrix

N/A — no routing, shell, subprocess, VCS/PR automation, executable-file classification, or process-integration boundary.

## Migration / Rollout

No data migration (H2 data is disposable). Local developers must add `--spring.profiles.active=dev`. Rollback: revert the PR.

## Open Questions

- [ ] Confirm the RDS engine major (17 assumed); bump the pinned tag to the latest 17.x minor at apply.
- [ ] At apply, verify that `{"dev","demo","test"}` resolves to the test in-memory URL.
