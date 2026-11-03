# Apply Progress: persistence-daily-queue

**Mode**: Strict TDD · **Delivery**: single PR, size:exception (user-accepted) · **Branch**: `feat/persistence-daily-queue` (uncommitted)

**Status**: 22/22 tasks complete (Units 1–8). Final suites: backend `./mvnw test` 63/63, frontend `npm test` 8/8, `npm run lint` clean, `npm run build` OK.

## TDD Cycle Evidence

| Task | Test file | Layer | Safety net | RED | GREEN | Triangulate | Refactor |
|------|-----------|-------|------------|-----|-------|-------------|----------|
| 1.1 | — (config) | — | N/A (new tooling) | ➖ structural | `npm test` runs | ➖ structural | ➖ |
| 1.2 | `src/services/turnosService.test.js` | Unit | N/A (no prior frontend tests) | Characterization test forced to fail: FILA_VACIA expectation set to `'WRONG MESSAGE'` → `1 failed \| 3 passed (4)` | 4/4 | 4 cases: FILA_VACIA, network error, unknown 409 keeps code, 200 body | ➖ none needed |
| 2.1 / 2.2 | `domain/TurnoTest` | Unit | 17/17 backend baseline | Compile errors (new `Turno` constructor, `nuevo`, `getVersion` missing) | `-Dtest=TurnoTest` 7/7 | same-day vs past-day vs non-pending cancel; equal vs different vs unsaved ids | ➖ none needed |
| 2.3 | `testsupport/InMemoryTurnoRepositoryTest` | Unit | 24/24 | Compile errors (fake missing) | 5/5 | copies, stale-version conflict, duplicate `(fecha, numero)`, one-shot failure injection | Main adapter slimmed to the port |
| 3.1 | `application/TurnoServiceTest` | Unit | 29/29 (10 service baseline) | `COMPILATION ERROR … constructor TurnoService … required: TurnoRepository,Clock found: …,Clock,int` | — (see 3.2) | — | — |
| 3.2 | `application/TurnoServiceTest` | Unit | — | (3.1) | 22/22; full suite 41/41 | retry after one duplicate vs exhaustion at `maxIntentos=2`; stale write on `llamarSiguiente` and `cancelar`; midnight boundary vs next day; yesterday excluded vs cancel yesterday | — |
| 3.3 | `application/TurnoServiceTest` | Unit | 22/22 | ➖ refactor only | 22/22 | ➖ | Dashboard built from one `findByFecha` read |
| 4.1 / 4.2 | `infrastructure/persistence/JpaTurnoRepositoryTest` (`@DataJpaTest`) | Integration | 41/41 | Compile errors (`JpaTurnoRepository`, `TurnoJpaRepository` missing) | 6/6 | day scoping + `maxNumero`; update bumps version; duplicate on another day allowed | Clean |
| 4.3 | `application/SolicitarConcurrencyTest` | Integration | 47/47 | `NoUniqueBeanDefinition` (`jpaTurnoRepository` + `turnoRepository`) | 2/2 (numbers exactly 1..10 under 10 threads; wired bean is JPA adapter) | bean-type check + 10-thread race | Clean |
| 4.4 | `RestartSurvivalTest` | Integration | 49/49 | `NoSuchElement` (data lost on restart) | 1/1; full suite 50/50 | cancelled state, same number, next number n+1 after restart | Main `InMemoryTurnoRepository` removed |
| 5.1 / 5.2 | `infrastructure/DemoSeedRunnerTest` | Unit + Integration (nested `@SpringBootTest`) | 50/50 | `COMPILATION ERROR … cannot find symbol DemoSeedRunner`; `parse … cannot be applied to given types` → BUILD FAILURE | 9/9; full suite 59/59 | demo startup, second startup, new day, default profile, arrival-order ids, pure parser | Interim `seedInicial` removed |
| 6.1 / 6.2 | `web/ConflictoHandlerTest` (`@WebMvcTest`) | Web slice | 59/59 | 3 errors: `Request processing failed: ConflictoException` | 4/4; full suite 63/63 | 3 endpoints + 404/400 shapes unchanged | ➖ none needed |
| 7.1 / 7.2 | `src/services/turnosService.test.js` | Unit | 4/4 | 3 failed: message was `'No se pudo completar la operación.'`, `refreshable` missing | 8/8 | `callNextTurn`, `cancelarTurno`, body without `mensaje`, non-conflict not refreshable | ➖ none needed |
| 7.3 / 7.4 | — | Manual | lint/build/tests green | ➖ no component test infra (accepted gap) | lint clean, build OK | ➖ | Pre-existing lint errors fixed (`preserve-caught-error`, `set-state-in-effect`) |
| 8.1 / 8.2 | — (docs) | — | — | ➖ structural | — | ➖ | ➖ |
| 8.3 | — (lockfile) | — | 8/8 | ➖ structural | `npm ci` (npm 11.21.0), tests/lint/build green; `libc` fields restored (10); vitest pinned 5.0.5 | ➖ | Linux `npm ci` deferred to CI |
| W3 fix | `infrastructure/DemoSeedAtomicityTest` (`@SpringBootTest`, real H2) | Integration | 63/63 | 2 failed: `expected: 0` tickets after a real duplicate-key failure on the 4th / 11th row (rows 1-3 / 1-10 remained) | `@Transactional` on `DemoSeedRunner.run`; 2/2 | failure midway then full re-seed (11 rows, max 11); failure on last row | Javadoc updated |
| W4 fix | `infrastructure/persistence/JpaTurnoRepositoryTest` | Integration | 63/63 | 2 failed: NOT NULL violation on insert and unique clash on update both surfaced as `NumeroDuplicadoException` | `guardar` maps only insert (id null) + `UK_TURNO_FECHA_NUMERO` to duplicate, else rethrows; 8/8 | existing duplicate-insert test (still maps) vs NOT NULL insert vs unique clash on update | Constant for constraint name |

## Test summary

- Backend: 63 tests (TurnoServiceTest 22, DemoSeedRunnerTest 9, TurnoTest 7, JpaTurnoRepositoryTest 6, TurnoControllerTest 6, InMemoryTurnoRepositoryTest 5, ConflictoHandlerTest 4, SolicitarConcurrencyTest 2, RestartSurvivalTest 1, TurnosApiApplicationTests 1)
- Frontend: 8 tests (turnosService.test.js)

## Deviations

- Vitest included in this change (user chose one size:exception PR) instead of a separate `frontend-vitest-setup` change.
- CSV renumbered in Unit 4 (needed when the main in-memory repo was removed); controller test expectations updated there instead of Unit 6.
- Timezone/`maxIntentos` wiring done in Unit 3; property added to `application.yml` in Unit 4.
- `DemoSeedRunnerTest` adds nested Spring tests (profile gating cannot be proven on a fake).

## Accepted gaps / follow-ups (from verify-report)

- View-level conflict handling is manual-only (no component tests) — planned with client-identity.
- Linux `npm ci` check deferred to CI.
- W3 and W4 FIXED in a correction batch (backend now 67/67): seed is atomic (`@Transactional`); `guardar` narrows duplicate mapping to insert + `UK_TURNO_FECHA_NUMERO`, other integrity violations rethrown as `DataIntegrityViolationException` (no V2 migration needed, V1 names the constraint).
- W2 real Clock in TurnoControllerTest / nested DemoSeedRunnerTest; W5 no `.gitattributes`; partial: frontend not-found path untested. User chose to leave these as follow-ups.
