# Tasks: Persistence and Daily Queue

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~835 (incl. Vitest setup ~45) |
| 400-line budget risk | High |
| Chained PRs recommended | No (user accepted size:exception) |
| Suggested split | Single PR, 8 commits (one per unit) |
| Delivery strategy | exception-ok |
| Chain strategy | size-exception |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: size-exception
400-line budget risk: High

User explicitly accepted ONE oversized PR (size:exception) including Vitest setup; overrides design's split into `frontend-vitest-setup`.

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test | Runtime harness | Rollback |
|---|---|---|---|---|---|
| 1 | Vitest tooling | PR 1 | `npm test` | N/A, tooling | package.json, vite.config.js, baseline test |
| 2 | Domain + port | PR 1 | `mvnw.cmd test -Dtest=TurnoTest` | N/A, pure domain | domain/* |
| 3 | Service rules | PR 1 | `mvnw.cmd test -Dtest=TurnoServiceTest` | N/A, fake repo | TurnoService + test repo |
| 4 | JPA adapter, Flyway, config | PR 1 | `mvnw.cmd test -Dtest=JpaTurnoRepositoryTest,SolicitarConcurrencyTest` | App boots on file DB | persistence/*, pom, yml, SQL |
| 5 | Demo seed | PR 1 | `mvnw.cmd test -Dtest=DemoSeedRunnerTest` | Run with `demo` profile | DemoSeedRunner, CSV |
| 6 | 409 mapping + controller | PR 1 | `mvnw.cmd test -Dtest=ConflictoHandlerTest,TurnoControllerTest` | curl stale PUT | ApiExceptionHandler |
| 7 | Frontend CONFLICTO | PR 1 | `npm test` | Manual two-tab race | service + 2 views |
| 8 | Docs | PR 1 | N/A | N/A, docs only | docs/README |

## Unit 1: Frontend Vitest (~25)

- [x] 1.1 `package.json`, `vite.config.js`: vitest devDep (verify Vite 8 support), `"test":"vitest run"`, `test.environment:'node'` (~7)
- [x] 1.2 RED/GREEN `src/services/turnosService.test.js`: baseline `FILA_VACIA` and network-error mapping, stubbed `fetch` (~18)

## Unit 2: Domain + port (~110)

- [x] 2.1 RED `TurnoTest`: scenario "Position by equality" (equals by id, reloaded instance); `cancelar(hoy)` on past day throws `OperacionInvalidaException` (~25)
- [x] 2.2 GREEN `Turno` (id, fecha, version, seconds), `Turno.nuevo`, `cancelar(LocalDate)`, `TurnoRepository` (`findByFecha`, `findById`, `maxNumero`, `guardar`), `ConflictoException`, `NumeroDuplicadoException` (~63)
- [x] 2.3 REFACTOR: test-scope `InMemoryTurnoRepository` returning copies, version check, failure injection (~55; replaces main one)

## Unit 3: Service rules (~170)

- [x] 3.1 RED `TurnoServiceTest` with fixed zoned Clock: "Midnight boundary", "Yesterday excluded", "First ticket of day", "Cancel yesterday's ticket", "Retry exhaustion" (max 2), "Cancel after a completed attend is not a conflict" (400), stale `guardar` → conflict (~100)
- [x] 3.2 GREEN `TurnoService(repo, Clock, maxIntentos)`: single `now`, today scope, retry loop ≤10 (~70)
- [x] 3.3 REFACTOR: dashboard from one `findByFecha` read

## Unit 4: Persistence adapter (~250)

- [x] 4.1 RED `JpaTurnoRepositoryTest` (`@DataJpaTest`): "Empty database" (V1 + validate), "Time precision", duplicate → `NumeroDuplicado`, "Version conflict" (~70)
- [x] 4.2 GREEN `pom.xml` (data-jpa, flyway, h2), `V1__create_turno.sql`, `TurnoJpaEntity`, `TurnoJpaRepository`, `JpaTurnoRepository` + mapper, `application.yml`, `application-test.yml`, `.gitignore` `data/` (~180)
- [x] 4.3 RED/GREEN `SolicitarConcurrencyTest`: "Concurrent solicitar" 1..10, 10 threads (~45)
- [x] 4.4 RED/GREEN "Restart survival": reload on same file DB; remove `InMemoryTurnoRepository` from main; `TurnosConfig` Clock bean (~25)

## Unit 5: Demo seed (~65)

- [x] 5.1 RED `DemoSeedRunnerTest`: "Demo startup", "Second startup", "New day", "Default profile" (~30)
- [x] 5.2 GREEN `DemoSeedRunner` (`@Profile("demo")`), `CsvSeedLoader.parse`, renumber `turnos_hoy.csv` (~35)

## Unit 6: Web mapping (~36)

- [x] 6.1 RED `ConflictoHandlerTest` (`@WebMvcTest`): "Error body" 409 `CONFLICTO`; `TurnoControllerTest` with `demo`, `T-012`, v1 shapes unchanged (~30)
- [x] 6.2 GREEN `ApiExceptionHandler` maps `ConflictoException` (~6)

## Unit 7: Frontend conflicts (~52)

- [x] 7.1 RED `turnosService.test.js`: "Service mapping" (friendly message, `refreshable`) (~20)
- [x] 7.2 GREEN `turnosService.js` `request()` CONFLICTO mapping (~8)
- [x] 7.3 `AsesorView.jsx`: "Advisor conflict" (refresh, then message; hide retry) (~12)
- [x] 7.4 `ClienteView.jsx`: "Client cancel conflict" (`alConflicto` re-fetch); confirm "Browser holds yesterday's ticket" needs no change (~12)

## Unit 8: Docs (~20)

- [x] 8.1 `SystemTurn/docs/01-current-state.md`: persistence, daily queue, 409 (also `02-gaps-and-questions.md` updated)
- [x] 8.2 Root `README.md`: run steps, `TURNOS_DB_PATH`, demo profile, `npm test`
- [x] 8.3 (PARTIAL: Linux check deferred to CI) Before merge: regenerate `package-lock.json` with a fixed npm (11.3.0 on Windows dropped `libc` fields and needed `--legacy-peer-deps`); verify `npm ci` on Linux (deployment target)
  - Done: lockfile regenerated with `npx -y npm@11.21.0 install` (no `--legacy-peer-deps` needed), 10 `libc` fields restored, `npm ci` + `npm test` (8/8) + `npm run lint` + `npm run build` OK on Windows.
  - Linux npm ci check deferred to CI (Docker daemon not running; not started).
