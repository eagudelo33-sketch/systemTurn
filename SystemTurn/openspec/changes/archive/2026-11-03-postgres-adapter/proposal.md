# Proposal: PostgreSQL Adapter

## Intent

The API stores tickets in an H2 file, so it cannot run as several stateless containers (ECS Fargate + RDS, later). Make PostgreSQL the production database and make the app deployment-ready, while keeping zero-Docker local development.

## Scope

### In Scope
- PostgreSQL driver + Flyway Postgres module; V1 unchanged, no vendor folders.
- `dev` profile on H2 (file) for local work; H2 also backs the fast test suite.
- Fail-fast: default/production startup MUST NOT silently use embedded H2.
- `demo` guarded so it cannot run in production.
- Engine-independent unique-violation detection.
- Env-driven datasource (`SPRING_DATASOURCE_*`), Hikari sizing/timeout, Flyway connect-retries, actuator health with readiness/liveness, graceful shutdown (< ECS 30s).
- Hybrid tests: Testcontainers Postgres suite (skips without Docker locally; CI switch documented).
- Update README and `docs/01-current-state.md`.

### Out of Scope
- Terraform/AWS, Dockerfile (`infra-app`), CI pipeline (`infra-cicd`), client identity.
- Migrating existing H2 file data.

## Capabilities

### New Capabilities
- `runtime-operations`: datasource configuration, fail-fast rule, health probes, graceful shutdown.

### Modified Capabilities
- `ticket-persistence`: "Durable storage" moves from H2 file/`TURNOS_DB_PATH` to a configured PostgreSQL datasource; H2 only under `dev`/tests.
- `demo-seed`: adds a production guard.

## Approach

- Fail-fast, two layers: `application.yml` requires `spring.datasource.url` (no default, so startup fails on a missing URL); a startup guard rejects any H2/embedded URL unless `dev` or `test` is active.
- `demo` is accepted only together with `dev`; otherwise startup fails.
- Detection: walk the cause chain for Hibernate `ConstraintViolationException`, compare constraint name case-insensitively, message fallback; tested on H2 and Postgres.
- Health: expose only `health`; ALB uses `/actuator/health/readiness`, which EXCLUDES the DB (user decision: an RDS failover must not make ECS replace every task); liveness excludes DB. Requests that need the DB while it is unreachable fail fast with 503 `SERVICIO_NO_DISPONIBLE` (frontend shows the message).
- Tests: Postgres suite runs only when Docker is present; a `requireDocker` property makes it fail instead of skip (enabled in CI later).

## Frontend Alignment

No API contract change: routes, payloads, status codes, and the `CONFLICTO` shape stay the same. The Vite `/api` proxy is untouched. Verified by the existing controller tests passing unchanged and the frontend `npm run build`.

## Affected Areas

| Area | Impact |
|------|--------|
| `backend/turnos-api/pom.xml` | Modified (deps) |
| `src/main/resources/application*.yml` | Modified/New (`dev`) |
| `JpaTurnoRepository.java` | Modified (detection) |
| Startup guard (infrastructure) | New |
| `src/test` Postgres suite | New |
| `openspec/specs/ticket-persistence`, `demo-seed` | Delta |
| README, `docs/01-current-state.md` | Modified |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Dev/prod engine gap | Med | Postgres suite, mandatory in CI |
| Postgres tests skipped silently | Med | `requireDocker` switch, documented |
| Pool exhausts RDS connections | Low | Env-overridable pool size |
| Over 10 simultaneous `solicitar` get 409 | Low | Existing behavior, accepted |

## Rollback Plan

Revert the PR. V1 is unchanged, so H2 file data stays readable; no data migration happens.

## Size

Forecast is about 500–700 changed lines, within the 800 budget (single PR).

## Success Criteria

- [ ] Full H2 suite passes; Postgres suite passes when Docker is available.
- [ ] Startup without a datasource URL, or with H2 outside `dev`, fails with a clear error.
- [ ] `demo` without `dev` fails at startup.
- [ ] Duplicate detection proven on both engines.
- [ ] With the DB down, readiness and liveness stay UP (no task churn) and DB-dependent requests return 503 `SERVICIO_NO_DISPONIBLE` quickly.

## Proposal Question Round

Assumptions to review:
1. Existing H2 file tickets are disposable (daily queue): nothing is migrated.
2. `demo` is allowed only with `dev`, never on Postgres.
3. ~~When the DB is down, the instance goes not-ready (ALB stops routing) instead of returning errors.~~ Resolved (user): the DB is excluded from readiness and liveness; DB-dependent requests return a fast 503 `SERVICIO_NO_DISPONIBLE`, so an RDS failover causes no task churn.
