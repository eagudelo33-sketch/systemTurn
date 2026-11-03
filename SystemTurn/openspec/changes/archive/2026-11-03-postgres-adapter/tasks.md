# Tasks: PostgreSQL Adapter

Paths: BE = `SystemTurn/backend/turnos-api` (`src/main/java/com/systemturn/turnos` = MAIN, `src/test/java/com/systemturn/turnos` = TEST); FE = `SystemTurn/frontend/frontend-react/react-client`.

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~1,100 (range 950-1,300), tests included; no lockfile changes (backend Maven, no new npm deps). Excludes openspec artifacts (~100 spec-delta lines plus proposal/design/tasks) |
| 400-line budget risk | High (also over the 800 review_budget_lines) |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 = units 1-3 (~520), PR 2 = units 4-8 (~615); or one PR with size:exception |
| Delivery strategy | single-pr |
| Chain strategy | pending |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: pending
400-line budget risk: High

Honest note: design estimated ~597; the previous change undershot by ~50% once tests were counted, so this forecast is bottom-up per unit and exceeds 800. Orchestrator MUST stop and ask (size:exception vs chain) before apply.
Staging: this change CREATES new source files (StartupGuard, ServicioNoDisponibleException, application-dev.yml, testsupport classes, *PostgresTest, HealthGroupsTest, ConfigBindingTest). Run `git add` on them before tracker settlement/commit.

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|-----------|----------------------|-----------------|-------------------|
| 1 (~180) | Deps, profile/config restructure, env datasource | PR 1 | `./mvnw test -Dtest=ConfigBindingTest` | `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` starts on H2 | pom.xml, application*.yml, ConfigBindingTest |
| 2 (~215) | Startup guards (H2, demo) | PR 1 | `./mvnw test -Dtest=StartupGuardTest` | Boot with no profile and `jdbc:h2:mem:x` fails | StartupGuard + tests + 3 demo test profile edits |
| 3 (~125) | Hardened duplicate detection | PR 1 | `./mvnw test -Dtest=JpaTurnoRepositoryTest` | N/A: covered by H2 repository contract | JpaTurnoRepository detection method |
| 4 (~155) | DB unreachable to 503 | PR 2 | `./mvnw test -Dtest=ConflictoHandlerTest,JpaTurnoRepositoryTest` | Run with unreachable PG URL, `curl` POST /api/v1/turnos returns 503 | exception, adapter wrapping, handler |
| 5 (~125) | Probes and graceful shutdown | PR 2 | `./mvnw test -Dtest=HealthGroupsTest,GracefulShutdownTest` | `curl /actuator/health/readiness` returns UP | actuator + shutdown yml and tests |
| 6 (~205) | Testcontainers Postgres suite | PR 2 | `./mvnw test -Dtest=*PostgresTest -DrequireDocker=true` | Docker Desktop running | testsupport, subclasses, pom test deps |
| 7 (~30) | Frontend 503 mapping | PR 2 | `npm test -- turnosService` | N/A: unit-level fetch mock is the contract | turnosService.js + test |
| 8 (~100) | Docs and env var table | PR 2 | N/A (docs) | Link/readback check | README, docs/01, docs/02 |

## Unit 1: Dependencies and config (build stays green)

- [x] 1.1 RED `TEST/ConfigBindingTest`: scenarios "Environment values are applied", "Defaults", "Pool size override", "No committed secrets" (scan `src/main/resources`), "V1 file untouched" (checksum + no vendor folder), "Dev starts empty on H2", "Empty database". Fails first.
- [x] 1.2 `BE/pom.xml`: add postgresql (runtime), flyway-database-postgresql, spring-boot-starter-actuator.
- [x] 1.3 `application.yml`: remove datasource url, add Hikari pool/timeout 5000, Flyway connect-retries; create `application-dev.yml` (H2 file `TURNOS_DB_PATH`). Keep `src/test/resources/application-test.yml` unchanged.
- [x] 1.4 Run full `./mvnw test`; fix existing tests that relied on default H2 (add `test` profile where missing, e.g. `RestartSurvivalTest`, `TurnosApiApplicationTests`).

## Unit 2: Startup guards

- [x] 2.1 RED `TEST/infrastructure/StartupGuardTest`: pure-rule cases for "H2 without dev or test is rejected", "H2 with prod is rejected", "H2 with dev is allowed", "H2 with test is allowed", "PostgreSQL passes without dev or test", "demo alone is rejected", "demo with prod is rejected", "no demo, no check".
- [x] 2.2 RED boot cases via `SpringApplicationBuilder`: "Missing URL fails startup" (message names `spring.datasource.url`, no embedded DB), "demo with dev is accepted" (11 tickets for 2026-10-30).
- [x] 2.3 GREEN `MAIN/infrastructure/StartupGuard.java`: `@Configuration` with static `BeanPostProcessor`, pure static decision method, Environment demo check.
- [x] 2.4 Switch `DemoSeedRunnerTest.DemoProfile`, `DemoSeedAtomicityTest`, `TurnoControllerTest` to `{"dev","demo","test"}`; verify the in-memory URL wins (design open question). Full suite green.

## Unit 3: Duplicate detection

- [x] 3.1 RED `JpaTurnoRepositoryTest`: "Duplicate on H2", "Mixed-case constraint name", "Unrelated violation is not a duplicate" (not-null/check), plus fabricated chains (upper, lower, quoted, schema-qualified, null name fallback).
- [x] 3.2 GREEN `JpaTurnoRepository`: cause-chain walk for Hibernate `ConstraintViolationException`, normalize name, message fallback, keep `id == null` gate.
- [x] 3.3 Confirm "Retry exhaustion unchanged" still passes in `TurnoServiceTest`/`SolicitarConcurrencyTest`.

## Unit 4: DB unreachable to 503

- [x] 4.1 RED `JpaTurnoRepositoryTest`: `CannotCreateTransactionException` and `DataAccessResourceFailureException` become `ServicioNoDisponibleException`; other `DataAccessException` is not translated.
- [x] 4.2 RED `ConflictoHandlerTest`/`TurnoControllerTest`: "Solicitar with the database down", "Queue read with the database down", "No internal details leak" (no `jdbc` in body); 409/400 unchanged.
- [x] 4.3 RED "Fast failure": unreachable-host context test, 503 within timeout + 2 s.
- [x] 4.4 GREEN `MAIN/domain/ServicioNoDisponibleException.java`, adapter wrapping per port call, `ApiExceptionHandler` 503 body with Spanish message.
- [x] 4.5 Verify "Existing controller tests" (unmodified suite) pass.

## Unit 5: Probes and graceful shutdown

- [x] 5.1 RED `TEST/HealthGroupsTest`: "Probes UP with a healthy database", "Probes stay UP with the database down", "Only health is exposed" (`/actuator/env`, `/actuator/beans` 404), groups exclude `db`.
- [x] 5.2 RED "Configuration" (`server.shutdown=graceful`, 25s) and "In-flight request completes" (1 s request, context close).
- [x] 5.3 GREEN `application.yml`: exposure `health` only, probes, readiness/liveness include, shutdown settings.

## Unit 6: Testcontainers Postgres suite

- [x] 6.1 RED `TEST/testsupport/EnabledIfDockerOrRequiredTest`: "Skipped without Docker", "Fails when Docker is required" (message mentions `requireDocker`), injected Docker-availability probe.
- [x] 6.2 `pom.xml`: testcontainers-postgresql (test), `requireDocker` property + surefire `systemPropertyVariables`.
- [x] 6.3 GREEN `TEST/testsupport/PostgresSupport` (singleton `postgres:17.6`, `@DynamicPropertySource`) and `EnabledIfDockerOrRequired` ExecutionCondition.
- [x] 6.4 Subclasses: `JpaTurnoRepositoryPostgresTest` ("Duplicate on PostgreSQL", "Empty PostgreSQL database", "Time precision on PostgreSQL" with JVM UTC vs Bogota, "Runs with Docker"), `SolicitarConcurrencyPostgresTest` ("Concurrent solicitar on PostgreSQL", "Version conflict on PostgreSQL"), `RestartSurvivalPostgresTest` ("Restart survival on PostgreSQL"); add cleanup/arg hooks to base classes. Run with `-DrequireDocker=true`.

## Unit 7: Frontend

- [x] 7.1 RED `FE/src/services/turnosService.test.js`: "Service maps 503" via `stubFetchResponse(503, ...)`, "409 mapping unchanged".
- [x] 7.2 GREEN `turnosService.js` `request()`: map `SERVICIO_NO_DISPONIBLE` to Error with body message, `code` set, not `refreshable`.

## Unit 8: Docs

- [x] 8.1 `README.md`: env var table (`SPRING_DATASOURCE_*`, `TURNOS_DB_POOL_SIZE`, `TURNOS_DB_CONNECT_RETRIES`, `TURNOS_DB_PATH`, `SPRING_PROFILES_ACTIVE`), `--spring.profiles.active=dev`, Docker/requireDocker note.
- [x] 8.2 `docs/01-current-state.md`, `docs/02-gaps-and-questions.md`: PostgreSQL default, probes, operational notes (ALB path, pool vs `max_connections`, `socketTimeout`, no `test`/`demo` in prod).
