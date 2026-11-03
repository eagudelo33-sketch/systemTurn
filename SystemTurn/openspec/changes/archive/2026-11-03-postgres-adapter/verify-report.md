```yaml
schema: gentle-ai.verify-result/v1
evidence_revision: sha256:65122fb093079125802481abc04b732db6c966ff008b09e3eb0a250c91ad665a
verdict: pass_with_warnings
blockers: 0
critical_findings: 0
requirements: 14/14
scenarios: 43/43
test_command: ./mvnw test -DrequireDocker=true
test_exit_code: 0
test_output_hash: sha256:e3f8c73f5b735297c8e603e1757d8c811e6be622575f6c17b11a6a51be7e2911
build_command: npm run build
build_exit_code: 0
build_output_hash: sha256:d4c81def6303315d071ea926d9fcbbc70ca8e0312a8a1357ce089ad7c9e20d69
```

## Verification Report

**Change**: postgres-adapter
**Version**: N/A (delta specs: ticket-persistence, demo-seed, concurrency-conflicts, runtime-operations)
**Mode**: Strict TDD

### Completeness
| Metric | Value |
|--------|-------|
| Tasks total | 28 (8 units: 1.1-1.4, 2.1-2.4, 3.1-3.3, 4.1-4.5, 5.1-5.3, 6.1-6.4, 7.1-7.2, 8.1-8.2) |
| Tasks complete | 28 |
| Tasks incomplete | 0 |
| Requirements (counted from specs) | 14 (ticket-persistence 2, demo-seed 1, concurrency-conflicts 1, runtime-operations 10) |
| Scenarios (counted from specs) | 43 (6 + 4 + 7 + 26); "Operational notes" excluded |

### Build & Tests Execution
**Backend tests**: PASS. `./mvnw test -DrequireDocker=true` (SystemTurn/backend/turnos-api): Tests run: 147, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS, exit 0 (about 3 min, real postgres:17.10 container via Testcontainers).
- Surefire XML, Postgres classes: JpaTurnoRepositoryPostgresTest tests=12 skipped=0; SolicitarConcurrencyPostgresTest tests=3 skipped=0 (80.97 s); RestartSurvivalPostgresTest tests=2 skipped=0. Zero skipped elements across all report files.

**Frontend tests**: PASS. `npm test` 11/11 (output hash sha256:374bda55f36f84b75d0599a986c07ed42f8800d104b904039d7e071d6c9a825b).
**Lint**: PASS. `npm run lint` exit 0.
**Build**: PASS. `npm run build` exit 0 (22 modules, vite). Backend compilation is exercised by `./mvnw test`.
**Coverage**: not available (no coverage tool configured; skipped, not a failure).
evidence_revision derivation: sha256 of "<git index tree 7e1a58f4f8c13ad395a69b390dcd5ed046137fbb>|<test_output_hash hex>|<build_output_hash hex>".

### TDD Compliance
| Check | Result | Details |
|-------|--------|---------|
| TDD Evidence reported | PASS | Table present in apply-progress.md |
| All tasks have tests | PASS | Every code task maps to an existing test file; 1.2, 5.3, 6.2, 8.x are structural or docs and say so |
| RED confirmed (tests exist) | PASS with note | All listed test files exist. 4.3, 5.1/5.2 behavioral cases and 6.4 have no genuine failing run (see WARNING 3) |
| GREEN confirmed (tests pass) | PASS | 147/147 now; per-class counts match the table (Config 9, Guard 17, Translation 15+5, DbDown 2, Health 4, Shutdown 2, Handler 6, Retry 2, Docker 5, Postgres 12/3/2) |
| Triangulation adequate | PASS | 7 constraint-name forms, fallbacks, 3 port calls, 2 time-precision cases, 3 frontend cases |
| Safety Net for modified files | PASS | Modified files (RestartSurvivalTest, JpaTurnoRepository, handler, demo tests) show baseline N/N |

**TDD Compliance**: 6/6 checks passed (one with notes).

### Test Layer Distribution
| Layer | Tests | Files | Tools |
|-------|-------|-------|-------|
| Unit | about 60 (Translation 20, Guard rules 12, Config binding 7, Docker condition 5, ...) | 6 | JUnit 5, AssertJ, Mockito |
| Integration | about 72 (H2 boots, real HTTP, PostgreSQL 17) | 14 | Spring Boot Test, Testcontainers |
| Slice | 8 (ConflictoHandlerTest 6, RetryExhaustionTest 2) | 2 | MockMvc |
| Frontend unit | 11 | 1 | Vitest |
| **Total** | **147 backend + 11 frontend** | | |

### Changed File Coverage
Coverage analysis skipped. No coverage tool detected.

### Assertion Quality
| File | Line | Assertion | Issue | Severity |
|------|------|-----------|-------|----------|
| StartupGuardTest.java | 113-118 | missing-URL boot asserts only property names in the stack trace | Title claims it never uses an embedded database but nothing asserts that | WARNING |
| StartupGuardTest.java | 37-43 | H2-without-dev rule asserts `dev` but not `test` | Spec wants both profiles named; the message contains both, the test pins one | SUGGESTION |
| HealthGroupsTest.java | 106, 108 | `doesNotContain("db")` | Substring negative, paired with status 200 and `contains UP`; acceptable | SUGGESTION |

**Assertion quality**: 0 CRITICAL, 1 WARNING. No tautologies, ghost loops, or smoke-only tests. Time-bound tests use upper bounds and latches instead of sleeps.

### Quality Metrics
**Linter**: PASS (eslint, no errors or warnings). **Type Checker**: not applicable (plain JS; Java compiled by Maven).

### Spec Compliance Matrix
Code references: BE = `SystemTurn/backend/turnos-api/src/main/...`, FE = `.../react-client/src/services/turnosService.js`. Tests are under `src/test/java/com/systemturn/turnos/`. Prefixes: TP ticket-persistence, DS demo-seed, CC concurrency-conflicts, RO runtime-operations.

| Requirement | Scenario | Code | Test | Result |
|---|---|---|---|---|
| TP Durable storage | Restart survival | application.yml, application-dev.yml | RestartSurvivalTest (H2 file) | COMPLIANT |
| TP Durable storage | Restart survival on PostgreSQL | same, PostgresSupport | RestartSurvivalPostgresTest > restartSurvivalOnPostgresql | COMPLIANT |
| TP Versioned schema | Empty database | V1__create_turno.sql | ConfigBindingTest > emptyDatabaseOnTestProfileIsMigratedValidatedAndEmpty; JpaTurnoRepositoryTest > emptyDatabase... | COMPLIANT |
| TP Versioned schema | Empty PostgreSQL database | application.yml:29 (validate) | JpaTurnoRepositoryPostgresTest > "Empty PostgreSQL database", "Runs with Docker" | COMPLIANT |
| TP Versioned schema | Time precision on PostgreSQL | JpaTurnoRepository.guardar | JpaTurnoRepositoryPostgresTest > "Time precision on PostgreSQL" x2 (JVM UTC, Bogota clock) | COMPLIANT |
| TP Versioned schema | V1 file untouched | db/migration (no diff in staged changes) | ConfigBindingTest > migrationV1IsUntouchedAndThereIsNoVendorFolder | COMPLIANT |
| DS Production guard | demo alone is rejected | StartupGuard.java:84-90, 31-35 | StartupGuardTest > demoAloneIsRejected; demoWithoutDevFailsStartupEvenWithATestDatabase (boot) | COMPLIANT |
| DS Production guard | demo with prod is rejected | StartupGuard.java:84 | StartupGuardTest > demoWithProdIsRejected, demoWithTestButWithoutDevIsRejected | COMPLIANT |
| DS Production guard | demo with dev is accepted | StartupGuard.java:84 | StartupGuardTest > demoWithDevIsAcceptedAndSeedsElevenTicketsForToday (today, not 2026-10-30; SUGGESTION 5) | COMPLIANT |
| DS Production guard | no demo, no check | StartupGuard.java:66-81 | StartupGuardTest > withoutDemoThereIsNothingToCheck, postgresqlPassesWithoutDevOrTest | COMPLIANT |
| CC Duplicate detection | Duplicate on H2 | JpaTurnoRepository.java:76-104 | JpaTurnoRepositoryTest > duplicateNumberOnTheSameDay... | COMPLIANT |
| CC Duplicate detection | Duplicate on PostgreSQL | same | JpaTurnoRepositoryPostgresTest > "Duplicate on PostgreSQL" plus lowercase-constraint probe | COMPLIANT |
| CC Duplicate detection | Mixed-case constraint name | JpaTurnoRepository.java:96-118 | JpaTurnoRepositoryTranslationTest.DuplicateDetection > constraintNameIsMatchedCaseInsensitively... (7 forms) | COMPLIANT |
| CC Duplicate detection | Unrelated violation is not a duplicate | JpaTurnoRepository.java:82-85 | Translation > unrelatedConstraintNameIsNotADuplicate (+2); integrityViolation... on H2 and PostgreSQL | COMPLIANT |
| CC Duplicate detection | Concurrent solicitar on PostgreSQL | saveAndFlush; TurnoService retry | SolicitarConcurrencyPostgresTest > "Concurrent solicitar on PostgreSQL" (+ container probe) | COMPLIANT |
| CC Duplicate detection | Version conflict on PostgreSQL | JpaTurnoRepository.java:86-88 | JpaTurnoRepositoryPostgresTest > "Version conflict on PostgreSQL" (repo-level ConflictoException; 409 mapping in ConflictoHandlerTest) | COMPLIANT |
| CC Duplicate detection | Retry exhaustion unchanged | TurnoService.java:45 | RetryExhaustionTest (2 cases, 409 CONFLICTO) | COMPLIANT |
| RO Environment datasource | Missing URL fails startup | application.yml (no url); StartupGuard.java:70-74 | StartupGuardTest > missingUrlFailsStartup...; ConfigBindingTest > baseConfigurationHasNoDatasourceUrlDefault | COMPLIANT (WARNING 4) |
| RO Environment datasource | Environment values are applied | application.yml | ConfigBindingTest > environmentValuesAreApplied | COMPLIANT |
| RO Environment datasource | No committed secrets | src/main/resources (rg: none) | ConfigBindingTest > noCommittedSecretsInMainResources (scanner positive and negative cases) | COMPLIANT |
| RO Embedded fail-fast | H2 without dev or test is rejected | StartupGuard.java:66-79 | StartupGuardTest > h2WithoutDevOrTestIsRejected...; anExplicitH2UrlWithoutDevOrTestFailsStartup | COMPLIANT |
| RO Embedded fail-fast | H2 with prod is rejected | StartupGuard.java:75 | StartupGuardTest > h2WithProdIsRejected; anExplicitH2Url... (boot, prod) | COMPLIANT |
| RO Embedded fail-fast | H2 with dev is allowed | StartupGuard.java:67 | StartupGuardTest > h2WithDevIsAllowed | COMPLIANT |
| RO Embedded fail-fast | H2 with test is allowed | StartupGuard.java:67 | StartupGuardTest > h2WithTestIsAllowed | COMPLIANT |
| RO Embedded fail-fast | PostgreSQL passes without dev or test | StartupGuard.java:80 | StartupGuardTest > postgresqlPassesWithoutDevOrTest | COMPLIANT |
| RO Dev profile on H2 | Dev starts empty on H2 | application-dev.yml | ConfigBindingTest > devStartsEmptyOnAnH2FileAndAppliesV1 | COMPLIANT |
| RO Pool/retry config | Defaults | application.yml:22-25, 32 | ConfigBindingTest > defaultsGivePoolOf10FastConnectionTimeoutAndFlywayRetries; DatabaseDownFailFastTest > theConnectionTimeoutBoundIsTheConfiguredFiveSeconds | COMPLIANT |
| RO Pool/retry config | Pool size override | application.yml:24 | ConfigBindingTest > poolSizeAndRetriesAreOverridableByEnvironment (SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=4) | COMPLIANT |
| RO Health probes | Probes UP with a healthy database | application.yml:4-18 | HealthGroupsTest > probesUpWithAHealthyDatabase (real HTTP) | COMPLIANT |
| RO Health probes | Probes stay UP with the database down | application.yml:14-18 | HealthGroupsTest > probesStayUpWithTheDatabaseDown (aggregate health 503 proves DB down) | COMPLIANT |
| RO Health probes | Only health is exposed | application.yml:5-8 | HealthGroupsTest > onlyHealthIsExposedAndWithoutDetails; configurationExposesOnlyHealth... | COMPLIANT |
| RO Database outage 503 | Solicitar with the database down | JpaTurnoRepository.java:68-74; ApiExceptionHandler.java:42-46 | Translation (DatabaseUnavailable) > connectionAcquisitionFailureOnWrite...; ConflictoHandlerTest > solicitarWithTheDatabaseDownReturns503... | COMPLIANT (composed, WARNING 2) |
| RO Database outage 503 | Queue read with the database down | same | ConflictoHandlerTest > queueReadWithTheDatabaseDownReturns503; Translation > resourceFailureBecomesServicioNoDisponibleOnEveryReadCall | COMPLIANT (composed) |
| RO Database outage 503 | No internal details leak | ServicioNoDisponibleException.java:9 | ConflictoHandlerTest (body without "jdbc"); Translation > theOriginalCauseIsKeptForLogsButNotInTheMessage | COMPLIANT |
| RO Database outage 503 | Fast failure | application.yml:25 | DatabaseDownFailFastTest > everyPortCallFailsWithServicioNoDisponibleWithinTheConnectionTimeoutPlusTwoSeconds (real Hikari, closed port) | COMPLIANT (composed) |
| RO Frontend 503 handling | Service maps 503 | turnosService.js:49-53 | turnosService.test.js > "Service maps 503" (3 cases) | COMPLIANT |
| RO Frontend 503 handling | 409 mapping unchanged | turnosService.js:44-48 | turnosService.test.js > "CONFLICTO mapping" (unchanged cases) | COMPLIANT |
| RO Graceful shutdown | Configuration | application.yml:3, 20-21 | GracefulShutdownTest > configurationShutdownIsGracefulWithA25SecondPhaseTimeout | COMPLIANT |
| RO Graceful shutdown | In-flight request completes | application.yml:3 | GracefulShutdownTest > inFlightRequestCompletesWhenTheContextIsClosed (latches, no sleeps) | COMPLIANT |
| RO API v1 unchanged | Existing controller tests | ApiExceptionHandler.java (additive) | TurnoControllerTest 6/6 (one-line ActiveProfiles edit, WARNING 5) | COMPLIANT |
| RO PostgreSQL gating | Skipped without Docker | DockerOrRequiredCondition.java:41-50 | EnabledIfDockerOrRequiredTest > skippedWithoutDockerWhenRequireDockerIsUnset | COMPLIANT (unit level) |
| RO PostgreSQL gating | Fails when Docker is required | DockerOrRequiredCondition.java:45-48 | EnabledIfDockerOrRequiredTest > failsWhenDockerIsRequiredButUnavailable... | COMPLIANT (unit level) |
| RO PostgreSQL gating | Runs with Docker | PostgresSupport.java; pom.xml surefire | JpaTurnoRepositoryPostgresTest > "Runs with Docker" and all *PostgresTest, skipped=0 | COMPLIANT |

**Compliance summary**: 43/43 scenarios compliant (0 untested, 0 failing).

### Correctness (Static Evidence)
| Requirement | Status | Notes |
|---|---|---|
| Durable storage / Versioned schema | Implemented | V1 not in diff; ddl-auto validate; data/ git-ignored (backend .gitignore:36) |
| Production guard (demo) | Implemented | Environment check at BeanPostProcessor factory time, before any singleton |
| Engine-independent duplicate detection | Implemented | Cause-chain walk; name decisive; message fallback only when no name; id==null gate |
| Environment-driven datasource | Implemented | No url or password in base config |
| Embedded fail-fast | Implemented | Property blank rejects; effective Hikari URL checked; EmbeddedDatabase rejected |
| Dev profile | Implemented | application-dev.yml H2 file |
| Pool/retry | Implemented | pool 10 env-overridable, timeout 5000, retries 10 |
| Probes | Implemented | health only; readinessState/livenessState groups; show-details never |
| Database outage 503 | Implemented | Only CannotCreateTransactionException and DataAccessResourceFailureException translated |
| Frontend 503 | Implemented | Not refreshable; code kept |
| Graceful shutdown | Implemented | graceful plus 25s |
| PostgreSQL gating | Implemented | Condition plus surefire systemPropertyVariables, default false |

### Coherence (Design)
| Decision | Followed? | Notes |
|---|---|---|
| H2 stays runtime scope; guard prevents silent fallback | Yes | pom.xml diff leaves H2 untouched |
| StartupGuard: static BPP, demo rule via Environment, effective DataSource URL plus EmbeddedDatabase | Yes | StartupGuard.java:30-63. Unknown DataSource types with a configured URL fail closed with a misleading "no URL" message (SUGGESTION 3) |
| Guard decisions in pure static methods | Yes | datasourceViolation / demoViolation |
| Adapter non-transactional, saveAndFlush | Yes | JpaTurnoRepository.java:28-33, 78 |
| Case-insensitive constraint detection plus message fallback | Yes | Uppercase contains instead of the design equals/endsWith lowercase: broader, covered by 7 name forms |
| Only the two exception types become ServicioNoDisponible; no broad DataAccessException catch | Yes | JpaTurnoRepository.java:71; QueryTimeout and DataIntegrity pass-through tested |
| 503 body shape | Yes, with drift | Backend message "Servicio no disponible, intenta de nuevo en unos minutos." matches the spec scenario; design text and the frontend fallback use a longer message (SUGGESTION 2) |
| Health: health only; db excluded from readiness/liveness | Yes | application.yml:4-18 |
| Graceful shutdown 25s | Yes | application.yml:3, 20-21 |
| Hikari pool/timeouts; Flyway connect-retries | Yes | application.yml:22-32 |
| V1 unchanged (checksum test) | Yes | SHA-256 on LF-normalized content |
| Testcontainers singleton, DynamicPropertySource, Docker condition | Yes | Image postgres:17.10 (design said 17.6; documented, resolves a design open question) |
| demo tests use dev, demo, test | Yes | In-memory URL wins (testProfileInMemoryUrlWinsOverTheDevFileUrl) |
| No secrets committed | Yes | rg over src/main/resources finds no password or secret; only .env.example is tracked |
| domain/application free of Spring/jakarta | Yes | rg on both packages: 0 matches |
| Frontend/backend alignment | Yes | 503 body (error SERVICIO_NO_DISPONIBLE, mensaje) matches turnosService.js:49; other API v1 shapes asserted unchanged (ConflictoHandlerTest.otherErrorShapesAreUnchanged, TurnoControllerTest) |

### Issues Found
**CRITICAL**: None.

**WARNING**:
1. Design deviation (benign): translation unit tests live in a new JpaTurnoRepositoryTranslationTest instead of JpaTurnoRepositoryTest; Postgres image 17.10 vs design 17.6. Both documented in apply-progress.
2. Three "database down" scenarios (Solicitar, Queue read, Fast failure) are proven by composition (adapter translation, fast failure on real Hikari, WebMvc handler with a mocked service), not by one end-to-end request against an unreachable database. TurnoService only catches NumeroDuplicadoException, so the composition is sound, but no single full-stack POST /api/v1/turnos to 503 test exists.
3. RED evidence is thin for 4.3 (written after the adapter wrap), 5.1/5.2 behavioral cases (Boot defaults already satisfied them; only the config-binding cases were truly RED) and 6.4 (characterization). Documented honestly in the table.
4. "Missing URL fails startup ... no embedded database is created" asserts the error text but not the absence of an embedded database (behavior is correct: the guard rejects at DataSource post-processing, before Flyway).
5. Spec says "Existing controller tests ... unmodified"; TurnoControllerTest received a one-line ActiveProfiles edit to dev, demo, test (needed because the guard rejects demo without dev). Behavior assertions are unchanged.
6. Test cost and isolation: SolicitarConcurrencyPostgresTest takes about 81 s of a roughly 3 min run. Isolation relies on per-class delete-from-turno hooks against one shared container; classes run sequentially in one JVM today, but parallel execution would break it. No flakiness observed in this run.

**SUGGESTION**:
1. docs/README.md:61 still says "in-memory storage" as an example of baseline decisions; it is generic SDD prose, not a current-state claim. README.md, docs/01 and docs/02 are accurate against the code and test counts.
2. Unify the 503 message between ServicioNoDisponibleException and the frontend fallback constant, or note the intentional difference.
3. StartupGuard.effectiveUrl returns null for non-Hikari, non-embedded DataSource types, yielding a misleading "no URL configured" message; consider a distinct message. Fail-closed behavior is correct.
4. apply-progress.md "Test Summary" block still shows Unit 1-2 totals (93); update to 147.
5. demoWithDevIsAcceptedAndSeedsElevenTicketsForToday asserts today (Clock is not overridable in a plain boot) instead of the spec date 2026-10-30; recorded as a deviation.
6. Pin the word test in the StartupGuardTest H2-without-dev message assertion.
7. The V1 checksum is computed on LF-normalized content; keep line-ending policy (.gitattributes) in mind if CI runs on Windows.

### Verdict
PASS WITH WARNINGS
All 14 requirements and 43 scenarios are covered by passing runtime tests (backend 147/147 with 0 skipped on real PostgreSQL 17.10, frontend 11/11, lint and build clean); no CRITICAL findings, six non-blocking warnings.
