# Apply Progress: postgres-adapter

Mode: Strict TDD. Delivery: size:exception (single PR, accepted by maintainer).

## Completed Tasks
- [x] 1.1-1.4 (Unit 1: deps and config).
- [x] 2.1-2.4 (Unit 2: startup guards).
- [x] 3.1-3.3 (Unit 3: duplicate detection).
- [x] 4.1-4.5 (Unit 4: DB unreachable to 503). 
- [x] 5.1-5.3 (Unit 5: probes and graceful shutdown).
- [x] 6.1-6.4 (Unit 6: Testcontainers PostgreSQL suite).
- [x] 7.1-7.2 (Unit 7: frontend 503 mapping).
- [x] 8.1-8.2 (Unit 8: docs). ALL 8 UNITS COMPLETE.

## TDD Cycle Evidence

| Task | Test file | Layer | Safety net | RED | GREEN | Triangulate | Refactor |
|------|-----------|-------|------------|-----|-------|-------------|----------|
| 1.1 | `src/test/java/com/systemturn/turnos/ConfigBindingTest.java` | Unit (binding) + Integration (2 boots) | 67/67 baseline | Written; 4 of 9 failed (no URL default, defaults, overrides, dev boot SQL) | 9/9 passed | 3 pairs: env values + pool override (4 and env var 7/3), scanner positive/negative cases, dev + test boots | Removed unused import; none needed |
| 1.2 | `ConfigBindingTest` (compile-time/classpath) | Build | 67/67 | n/a (structural) | build + 76/76 | Skipped: purely structural dependency declaration | None needed |
| 1.3 | `ConfigBindingTest` | Unit/Integration | 67/67 | covered by 1.1 RED | passed | covered by 1.1 | None needed |
| 1.4 | `RestartSurvivalTest` (modified: `test` profile) | Integration | 67/67 | n/a (no test relied on default H2 besides this one) | 76/76 | n/a | None needed |
| 2.1 | `infrastructure/StartupGuardTest.java` | Unit (pure rules) | 76/76 baseline | Written; compile error (StartupGuard missing) | 12 pure tests passed | 8 spec cases + embedded-fallback null URL + prod+test + test-without-dev | None needed |
| 2.2 | `StartupGuardTest` | Integration (5 boots) | 76/76 | Written; same compile RED | passed after fix (see Notes) | missing URL, explicit H2 under prod, demo without dev, demo+dev, test-wins-over-dev | None needed |
| 2.3 | `StartupGuardTest` | Unit + Integration | 76/76 | covered by 2.1/2.2 | 17/17 | 1 RED-in-GREEN: first run failed on message (Boot embedded fallback reports a generated h2:mem URL via Hikari), fixed by treating unset `spring.datasource.url` as null | Javadoc clarified |
| 2.4 | `DemoSeedRunnerTest.DemoProfile`, `DemoSeedAtomicityTest`, `TurnoControllerTest` | Integration | 76/76 | Full suite with guard: 9 errors in 3 classes (demo contexts rejected) | 93/93 | n/a (config-only profile edit) | None needed |
| 3.1 | `infrastructure/persistence/JpaTurnoRepositoryTranslationTest.DuplicateDetection` | Unit (fabricated exceptions) | 93/93 | 8 of 15 failed (named constraints upper/lower/mixed/quoted/qualified/deep chain) | 15/15 | 7 name forms, deep chain, 2 message fallbacks, unrelated name, no name/no match, update never duplicate, optimistic lock | Cycle-safe cause walk extracted |
| 3.2 | same | Unit | 93/93 | covered by 3.1 | 20/20 then 117/117 | name decisive when present; message fallback only when no name; `id == null` gate kept | None |
| 3.3 | `TurnoServiceTest`, `SolicitarConcurrencyTest` (unmodified) | Regression | 93/93 | n/a | passed in 117/117 | n/a | None |
| 4.1 | `JpaTurnoRepositoryTranslationTest.DatabaseUnavailable` | Unit | 93/93 | 4 of 5 failed | 5/5 | CannotCreateTransaction, CannotGetJdbcConnection (via DataAccessResourceFailure), all 4 port calls, QueryTimeout and DataIntegrity NOT translated, cause kept but no `jdbc` in message | `sinConexion` wrapper extracted |
| 4.2 | `web/ConflictoHandlerTest` | Slice (WebMvc) | 93/93 | 2 errors (no handler: exception propagates) | 6/6 | solicitar 503 + no `jdbc`, dashboard 503, 409/400/404 shapes unchanged | None |
| 4.3 | `infrastructure/persistence/DatabaseDownFailFastTest` | Integration (closed local port, no sleeps) | 93/93 | Written after the adapter wrap (same cycle); would fail on exception type without 4.4 | 2/2 | each of 3 port calls < Hikari timeout (read from pool, 5000) + 2 s; timeout is 5000 | None |
| 4.4 | handler + adapter + `ServicioNoDisponibleException` | n/a | | covered by 4.1-4.3 | 117/117 | n/a | None |
| 4.5 | existing `TurnoControllerTest` etc. unmodified | Regression | 93/93 | n/a | 117/117 | n/a | None |
| 5.1 | `HealthGroupsTest` | Config binding + Integration (real HTTP, random port, closed-port DB) | 117/117 | Config test failed (No value bound: no explicit exposure/probes/groups). Behavioral scenarios passed immediately: Boot defaults already satisfy them | 123/123 | healthy H2 vs DB down (aggregate health 503 proves DB down, both probes 200 UP, no `db`), env/beans/info 404, no details | None |
| 5.2 | `GracefulShutdownTest` | Config binding + Integration (latch) | 117/117 | Config test failed (No value bound for timeout). In-flight scenario passed immediately (Boot default is already graceful) | 123/123 | deterministic latches (in-flight, close started, port refuses) plus log assertion "Commencing graceful shutdown"; no fixed sleeps | None |
| 5.3 | `application.yml` | n/a | | covered by 5.1/5.2 | 123/123 | n/a | None |
| 6.1 | `testsupport/EnabledIfDockerOrRequiredTest` | Unit (injected probe + flag) | 123/123 | Compile error (DockerOrRequiredCondition missing) | 5/5 | skipped, fails with `requireDocker` message, enabled with/without flag, probe called once, flag parsing (true/TRUE/false/empty/unresolved placeholder/null) | None |
| 6.2 | `pom.xml` | Build | 123/123 | n/a (structural) | build + 5/5 | Skipped: purely structural (dependency, property, surefire systemPropertyVariables) | None |
| 6.3 | `DockerOrRequiredCondition`, `EnabledIfDockerOrRequired`, `PostgresSupport` | Unit + Integration | 123/123 | covered by 6.1 RED | 5/5, then 19/19 with the Postgres classes | n/a | Condition kept pure (injectable); container singleton lazily started |
| 6.4 | `JpaTurnoRepositoryPostgresTest`, `SolicitarConcurrencyPostgresTest`, `RestartSurvivalPostgresTest`, `web/RetryExhaustionTest` | Integration (real PostgreSQL 17.10 via Testcontainers) + slice | 123/123 | Written before running; inherited H2 cases passed unchanged on PostgreSQL (the H2 hardening of Unit 3 already covered the lowercase name), new scenario tests passed first run; RetryExhaustion characterizes existing behavior (no production change) | 19/19 selected, 147/147 full | Time precision: 2 cases (08:34:27 Bogota; 23:30:15 on the Bogota date while UTC is the 30th); restart: inherited + 11-ticket scenario; duplicate: service-level + raw SQL proving PostgreSQL reports `uk_turno_fecha_numero`; retry exhaustion: budget 10 and 3 | Base-class fields made package-private; cleanup hook in the concurrency base |
| 7.1 | `src/services/turnosService.test.js` | Unit (fetch mock) | 8/8 | 1 of 11 failed (no fallback message for 503 without `mensaje`); the other 2 new cases passed immediately because the generic path already forwards body `mensaje` and `code` | 11/11 | body message, fallback message, 503 without the code is not mapped; 409 cases unchanged | None |
| 7.2 | `turnosService.js` | Unit | 8/8 | covered by 7.1 | 11/11, lint clean, build OK | n/a | None |
| 8.1 | `README.md` | Docs | n/a | n/a (docs) | readback | n/a | None |
| 8.2 | `docs/01-current-state.md`, `docs/02-gaps-and-questions.md` | Docs | n/a | n/a (docs) | readback | n/a | None |

### Test Summary
- Tests written: 9 (ConfigBindingTest) + 17 (StartupGuardTest). Suite total now 93 (76 + 17), 0 failures.
- Layers: Unit 7, Integration 2.

## Work Unit Evidence (Unit 1)

| Evidence | Value |
|---|---|
| Focused test | `./mvnw test -Dtest=ConfigBindingTest` -> Tests run: 9, Failures: 0, Errors: 0; BUILD SUCCESS |
| Runtime harness | `spring-boot:run -Dspring-boot.run.profiles=dev` started in 9.8 s on H2 file in a temp dir; `/actuator/health` returned `{"groups":["liveness","readiness"],"status":"UP"}` |
| Rollback boundary | pom.xml, application.yml, application-dev.yml, ConfigBindingTest, RestartSurvivalTest one-line edit |

## Notes
- Boot 4 moved `ConfigDataApplicationContextInitializer` to `org.springframework.boot.test.context`.
- V1 checksum is computed on LF-normalized content (working copy is CRLF via autocrlf, index is LF).
- Without a datasource URL, Boot falls back to embedded H2 until the Unit 2 StartupGuard lands (expected interim state).
- Flyway history table is quoted lowercase on H2 (`"flyway_schema_history"`).

## Work Unit Evidence (Unit 2)

| Evidence | Value |
|---|---|
| Focused test | `./mvnw test -Dtest=StartupGuardTest` -> Tests run: 17, Failures: 0; full suite 93/93 BUILD SUCCESS |
| Runtime harness | Boot cases in StartupGuardTest: no profile and `jdbc:h2:mem:x` under prod both fail before Flyway; `dev,demo` seeds 11 tickets |
| Rollback boundary | StartupGuard.java, StartupGuardTest.java, 3 one-line `@ActiveProfiles` edits |

## Unit 2 Notes
- Boot's embedded fallback goes through Hikari with a generated `jdbc:h2:mem:<uuid>` URL, so the guard treats an unset `spring.datasource.url` as "no URL configured" (message names the property).
- Profile precedence verified: `{"dev","demo","test"}` -> test's `jdbc:h2:mem:turnos-<uuid>` wins; no H2 file in the dev path and no `./data`.
- Deviation: boot case "demo with dev" asserts 11 tickets for today (system Bogota clock), not the fixed date 2026-10-30, since the Clock bean is not overridable in a plain boot.

## Work Unit Evidence (Units 3-4)

| Evidence | Value |
|---|---|
| Focused tests | `JpaTurnoRepository*Test,DatabaseDownFailFastTest,ConflictoHandlerTest` -> 36 run; green after handler; full suite 117/117 BUILD SUCCESS (93 + 24) |
| Changed lines | ~385 incl. tests (main 80: exception 11, adapter 61, handler 8; tests 305) |
| Rollback boundary | JpaTurnoRepository, ServicioNoDisponibleException, ApiExceptionHandler, 3 test files |

## Units 3-4 Notes
- Deviation: translation tests live in a new plain-unit class `JpaTurnoRepositoryTranslationTest` (Mockito on `TurnoJpaRepository`) instead of `JpaTurnoRepositoryTest` (a `@DataJpaTest` on real H2); H2 duplicate and unrelated-violation scenarios remain there and still pass.
- Fail-fast test: `@DataJpaTest` with Flyway off, explicit PostgreSQL dialect, `allow_jdbc_metadata_access=false`, `initialization-fail-timeout=-1`, and a port reserved then released at runtime. No profile, so the Hikari timeout comes from application.yml.
- Constraint name is decisive when a Hibernate `ConstraintViolationException` carries one; the message fallback applies only when no name exists in the chain.

## Work Unit Evidence (Unit 5)

| Evidence | Value |
|---|---|
| Focused test | `./mvnw test -Dtest="HealthGroupsTest,GracefulShutdownTest"` -> RED: 2 errors (config bindings, "No value bound"); GREEN after yml; full suite 123/123 BUILD SUCCESS (117 + 6) |
| Runtime harness | Tests boot the real app on a random port and issue real HTTP (H2 and unreachable PostgreSQL port); in-flight request held by latch while `ctx.close()` runs |
| Changed lines | ~256 (application.yml +16, HealthGroupsTest 125, GracefulShutdownTest 115) |
| Rollback boundary | management/server.shutdown/lifecycle block in application.yml, two test files |

## Unit 5 Notes
- Boot 4 defaults (health-only exposure, probe groups, graceful shutdown) already satisfy the behavioral scenarios; the explicit yml makes them config-guarded, and the binding tests are the RED evidence.
- Slow endpoint is a RouterFunction singleton registered via an initializer (not a scanned component), so it never leaks into other contexts.

## Work Unit Evidence (Units 6-8)

| Evidence | Value |
|---|---|
| Focused tests | Unit 6: `./mvnw test -Dtest='*PostgresTest,RetryExhaustionTest' -DrequireDocker=true` -> 19 run, 0 failures, 0 skipped. Unit 7: `npm test` 11/11 (RED was 1 failed of 11). Full backend `./mvnw test -DrequireDocker=true` -> 147 run, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS (123 + 24). |
| Postgres surefire counts (skipped=0) | JpaTurnoRepositoryPostgresTest 12, SolicitarConcurrencyPostgresTest 3, RestartSurvivalPostgresTest 2 (17 total, all executed against `postgres:17.10`) |
| Runtime harness | Real PostgreSQL 17.10 container (Docker Desktop, Testcontainers 2.0.5): Flyway V1 applied, `ddl-auto=validate` passed, 10-thread race, two-context restart. Frontend: `npm run lint` clean, `npm run build` OK. |
| Changed lines (this batch) | ~770: pom +15; Unit 6 tests/support ~505 (new 8 files ~500, base-class edits ~28); Unit 7 +43/-1; Unit 8 docs ~178 (README 66+/11-, 01 92+/21-, 02 20+/4-). No lockfile change. |
| Rollback boundary | Unit 6: pom test dep + surefire block, `testsupport/*`, 3 `*Postgres*Test` classes, `RetryExhaustionTest`, base-class edits (JpaTurnoRepositoryTest fields, SolicitarConcurrencyTest cleanup, RestartSurvivalTest `argumentos()`). Unit 7: turnosService.js + test. Unit 8: README, docs/01, docs/02. |

## Units 6-8 Notes
- Image pinned to `postgres:17.10` (design said 17.6): 17.11 was published 2026-11-01 (< 2 weeks), 17.10 is from 2026-08. Doc and tests reference 17.10.
- Docker condition: `DockerOrRequiredCondition` (injectable probe + flag). When required and Docker is missing it throws `IllegalStateException` mentioning `requireDocker`, which JUnit reports as a class-level error. The end-to-end "no Docker" run could not be simulated (`DOCKER_HOST` bogus did not stop Testcontainers from finding Docker Desktop); the three outcomes are covered at unit level.
- JVM zone UTC for the time-precision scenario is set in `@BeforeAll` (before the context and first connection exist) and restored in `@AfterAll`.
- Inherited H2 tests are overridden in the Postgres subclasses only to carry the PostgreSQL scenario names via `@DisplayName`.
- "Retry exhaustion unchanged" has a dedicated `web/RetryExhaustionTest` (real service + controller + handler, fake repo that always collides): 409 CONFLICTO, attempts == budget. Characterization test: passes immediately, no production change.
- Frontend: the generic error path already forwarded body `mensaje` and `code`; the only behavioral change is the Spanish fallback message when a 503 `SERVICIO_NO_DISPONIBLE` body has no `mensaje`, plus the explicit non-refreshable mapping.
- First container start pulled the image (about 3.5 min); later full runs take about 3 min total.
