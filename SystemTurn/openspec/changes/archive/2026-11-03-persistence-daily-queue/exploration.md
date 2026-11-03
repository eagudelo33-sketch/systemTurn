# Exploration: persistence-daily-queue

H2 + Spring Data JPA persistence and daily queue reset.

## Current state (verified in backend/turnos-api)

- `TurnoService` (POJO wired in `TurnosConfig`) is `synchronized`; numbering = `max(findAll) + 1` over ALL tickets; `fila()` = PENDIENTE sorted by `horaSolicitud`, then `id`.
- `Turno` is mutable, `final long id`, `LocalTime` hours, no date, no `equals`. `atender()`/`cancelar()` mutate in place and the service never persists them — this only works because the in-memory repo returns the same instances.
- Port `TurnoRepository`: `findAll`, `findById`, `save(numero, LocalTime)`. No update.
- `posicion()` = `fila().indexOf(turno)` (relies on object identity).
- `TurnosConfig` injects concrete `InMemoryTurnoRepository`, loads the CSV unconditionally, uses `Clock.systemDefaultZone()`.
- The CSV has a duplicate number 7 (cancelled 8:30 + pending 8:34); tests depend on it.
- `pom.xml`: only `starter-validation` + `starter-webmvc` (+ test starters).
- Tests: `TurnoServiceTest` (10, fixed Clock `2026-10-29T09:00Z`), `TurnoControllerTest` (6, real clock, `@DirtiesContext` per method, asserts `id == 12` / `T-011` / position 6), `TurnosApiApplicationTests`.

## Affected areas

| Area | Change |
|------|--------|
| `domain/Turno` | Add `LocalDate fecha`, nullable id, `equals` by id, version |
| `domain/TurnoRepository` | `findByFecha`, `findById`, `maxNumero(fecha)`, `guardar(Turno)` |
| `application/TurnoService` | Today-scoped, persist after mutations, per-day numbering with retry, drop `synchronized`, fix `posicion` |
| `infrastructure` | NEW `TurnoJpaEntity`, Spring Data repo, `JpaTurnoRepository` adapter + mapper, `DemoSeedRunner`. CHANGE `TurnosConfig`, `CsvSeedLoader` → pure parser. `InMemoryTurnoRepository` → test fake |
| resources | `application.yml`, `db/migration/V1__create_turnos.sql`, CSV fix |
| build | `pom.xml` deps, `.gitignore` (`data/`) |
| `web/` | Unchanged (API v1 shape kept) |

## Options and findings

### 1. Domain, port, concurrency

- **Time model** — A (recommended): add `LocalDate fecha`, keep `LocalTime` (store seconds; truncate only for display; unblocks wait estimate). B: `LocalDateTime`/`Instant` (bigger diff).
- **Daily reset** = query filter on today; no cleanup job. Past-day PENDIENTE tickets stay as history. Cancelling a past-day ticket → `OperacionInvalida` (400), recommended.
- **Number assignment**

| Option | Effort | Verdict |
|--------|--------|---------|
| A. `synchronized` + `UNIQUE(fecha, numero)` backstop | Low | Correct only for a single JVM |
| B. `UNIQUE(fecha, numero)` + max+1 + bounded retry; adapter translates `DataIntegrityViolation` → domain exception | Medium | **Recommended** — DB is source of truth |
| C. Counter row + pessimistic lock | High | Overkill, needs service-level transaction |

- **atender/cancelar races**: `@Version` (domain carries opaque version); translate optimistic-lock failure to 409 or one retry.

### 2. Spring Boot 4.1.2 specifics

Verified from docs.spring.io 4.1.2, the 4.0 migration guide and 4.1 release notes.

- Starters: `spring-boot-starter-data-jpa`, `spring-boot-starter-flyway` (required in Boot 4), `com.h2database:h2` (runtime). Test: `spring-boot-starter-data-jpa-test`.
- Versions: Hibernate 7.4, Spring Data 2026.0.0, Flyway 12.4.0.
- H2 console: `spring-boot-h2console` — dev only, skip.
- Test imports:
  - `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`
  - `org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase`
  - `org.springframework.boot.jpa.test.autoconfigure.TestEntityManager`
- URL: `jdbc:h2:file:${TURNOS_DB_PATH:./data/turnos}`. File DB is single-process; needs a persistent volume and backups.
- Schema: Flyway + `ddl-auto=validate` (explicit; embedded default is `create-drop`). `open-in-view=false`.
- Gotchas: Hibernate 7 enum on H2 under `validate` may need `@JdbcTypeCode(VARCHAR)`; `src/test/resources/application.yml` shadows main → use `application-test.yml` / `@ActiveProfiles`; `@DataJpaTest` needs `@Import(adapter)`.

### 3. Hexagonal fit

Separate `TurnoJpaEntity` + mapper (recommended; domain stays free of `jakarta.persistence`, ~60–80 lines) vs annotating `Turno` (couples domain to persistence).

### 4. Time zone

Property `turnos.timezone` (default `America/Bogota`, to confirm) → `Clock.system(ZoneId)`. Service uses only the injected Clock. Boundary test: `2026-10-30T04:30Z` = 23:30 Bogota, previous day.

### 5. Demo seed

`CsvSeedLoader` → pure parser returning `List<Turno>` for a given date. `DemoSeedRunner` (`ApplicationRunner`, `@Profile("demo")`, default off), idempotent (skip if today has tickets), single transaction, dated today from Clock.

**Critical**: duplicate number 7 violates `UNIQUE(fecha, numero)`. Renumber: 1–5 attended, 6 pending, 7 cancelled, 8–11 pending; next ticket `T-012`.

### 6. Tests

- Service tests: fake repo storing/returning COPIES (exposes identity bugs). Fix `cancelledTicketLeavesTheQueue` (stale instance), `doesNotContain` (needs `equals`), CSV numbers. Add: yesterday excluded, numbering restarts daily, past-day cancel 400, retry on duplicate, time-zone boundary.
- NEW `JpaTurnoRepositoryTest` (`@DataJpaTest`: unique constraint, version conflict, round trip). NEW threaded `solicitar` test (exactly 1..N).
- Controller test: `@ActiveProfiles({"test","demo"})`, `@Primary` fixed Clock, keep `@DirtiesContext` (identity not rolled back).
- Context-loads test: test profile (H2 in memory).

## Recommendation

Mapped JPA adapter + Flyway + date-scoped service. ~600–750 changed lines incl. tests (budget 800). Lean fallback: keep `synchronized` + DB unique backstop (saves ~80–100 lines). API v1 unchanged. Out of scope: H2 console, auth, client identity, wait estimate.

## Risks

- Seed duplicate 7 breaks unique constraint.
- Missing `equals` → `posicionFila` returns 0.
- Controller test uses real clock and hard-coded ids.
- Test config shadowing.
- H2 file DB: single instance, volume, backups, no HA (port makes Postgres swap cheap later).
- `America/Bogota` is an assumption.
- Later ticket lookup needs a "ticket from another day" rule.
- Hibernate 7 enum validation; Flyway H2 module check.
- Close to review budget.

## Ready for proposal

Yes. Confirm: time zone, CSV renumbering, concurrency approach, past-day cancel = 400.
