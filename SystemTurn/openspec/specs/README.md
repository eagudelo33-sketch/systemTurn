# SystemTurn Specifications

The **living specs**: what the system must do today. Each capability has one `spec.md` with RFC 2119 requirements and Given/When/Then scenarios. Every scenario is proven by an automated test; checks that need a real AWS account live in each spec's **Operational notes**, not as scenarios.

How specs change: a change writes *delta* specs under `openspec/changes/<change>/specs/`, and archive merges them here. History (why something changed) lives in the archived change, not in these files.

## Application

| Capability | What it guarantees | Req | Scenarios | Main tests |
|------------|--------------------|-----|-----------|------------|
| [daily-queue](daily-queue/spec.md) | Queue scoped to today (`America/Bogota`), numbering restarts daily, past-day cancel rejected | 5 | 5 | `TurnoServiceTest`, `e2e/03-respuestas-simuladas.spec.js` |
| [ticket-persistence](ticket-persistence/spec.md) | Durable PostgreSQL storage, Flyway `V1` unchanged, domain isolated from persistence | 3 | 8 | `JpaTurnoRepository*Test`, `RestartSurvival*Test` |
| [concurrency-conflicts](concurrency-conflicts/spec.md) | Unique daily numbers, optimistic locking, 409 `CONFLICTO`, engine-independent duplicate detection | 5 | 15 | `SolicitarConcurrency*Test`, `ConflictoHandlerTest`, `turnosService.test.js`, `e2e/03-respuestas-simuladas.spec.js` |
| [demo-seed](demo-seed/spec.md) | CSV seed only under `demo` (+ `dev`), idempotent, atomic | 4 | 8 | `DemoSeedRunnerTest`, `DemoSeedAtomicityTest`, `StartupGuardTest` |
| [runtime-operations](runtime-operations/spec.md) | Env-driven datasource, H2 guard, 503 `SERVICIO_NO_DISPONIBLE`, health probes without DB, graceful shutdown | 10 | 26 | `StartupGuardTest`, `HealthGroupsTest`, `GracefulShutdownTest`, `DatabaseDownFailFastTest` |

## Quality

| Capability | What it guarantees | Req | Scenarios | Main tests |
|------------|--------------------|-----|-----------|------------|
| [browser-e2e](browser-e2e/spec.md) | Playwright suite through a real browser, the Vite proxy and the real backend: client and advisor flows, mocked 503/409/stale-ticket views, fresh deterministic database, pinned toolchain | 12 | 29 | `e2e/01-cliente.spec.js`, `e2e/02-asesor.spec.js`, `e2e/03-respuestas-simuladas.spec.js` |

## Infrastructure (Terraform)

| Capability | What it guarantees | Req | Scenarios | Main tests |
|------------|--------------------|-----|-----------|------------|
| [infra-state-backend](infra-state-backend/spec.md) | Hardened S3 state bucket, native locking, `prevent_destroy`, per-env backend keys | 6 | 13 | `bootstrap/tests/state_bucket.tftest.hcl` |
| [infra-cost-guardrails](infra-cost-guardrails/spec.md) | Monthly cost budget with email alerts (notify only); dev paid layer off by default (`enable_app`) | 5 | 10 | `bootstrap/tests/budget.tftest.hcl`, `envs/dev/tests/env.tftest.hcl` |
| [infra-environments](infra-environments/spec.md) | `dev` / `prod` roots: pinned versions, default tags, no credentials, partial backend; `dev` wires network + gated app, `prod` still empty | 6 | 13 | `envs/*/tests/env.tftest.hcl` |
| [infra-network](infra-network/spec.md) | VPC 2 AZs x 3 tiers, `nat_mode` none/single/per_az, isolated data tier, free S3 endpoint, optional interface endpoints | 5 | 11 | `modules/network/tests/network.tftest.hcl` |
| [infra-app](infra-app/spec.md) | ECR, RDS PostgreSQL 17 (RDS-managed secret), ECS Fargate (`desired_count` 0), ALB readiness check, least-privilege IAM, private SPA bucket behind CloudFront | 8 | 11 | `modules/app/tests/app.tftest.hcl` |
| [infra-repo-conventions](infra-repo-conventions/spec.md) | Ignores, LF line endings, static gates, pre-commit, runbook | 7 | 18 | `infra/scripts/check-conventions.sh` |

**Total: 76 requirements, 167 scenarios.**

## Change history (archive)

| Date | Change | Introduced / changed |
|------|--------|----------------------|
| 2026-11-03 | [persistence-daily-queue](../changes/archive/2026-11-03-persistence-daily-queue/) | daily-queue, ticket-persistence, concurrency-conflicts, demo-seed (3 view scenarios deferred to `client-identity`, see its `deferred.md`) |
| 2026-11-03 | [postgres-adapter](../changes/archive/2026-11-03-postgres-adapter/) | runtime-operations (new); ticket-persistence, demo-seed, concurrency-conflicts (modified) |
| 2026-11-04 | [infra-foundation](../changes/archive/2026-11-04-infra-foundation/) | infra-state-backend, infra-cost-guardrails, infra-environments, infra-repo-conventions |
| 2026-11-04 | [infra-network-app](../changes/archive/2026-11-04-infra-network-app/) | infra-network, infra-app (new); infra-environments, infra-cost-guardrails (modified) |
| 2026-11-04 | [e2e-browser-tests](../changes/archive/2026-11-04-e2e-browser-tests/) | browser-e2e (new); concurrency-conflicts, daily-queue (modified: the 3 view scenarios deferred by `persistence-daily-queue` are now proven) |

Each archived change holds its `proposal.md`, `design.md`, `tasks.md`, `verify-report.md` and `archive-report.md` (open obligations for later changes are listed there).

## How to run the proofs

| Area | Command |
|------|---------|
| Backend (incl. PostgreSQL, needs Docker) | `cd backend/turnos-api && ./mvnw test -DrequireDocker=true` |
| Frontend | `cd frontend/frontend-react/react-client && npm test` |
| Browser E2E (needs Chromium, no Docker or AWS) | `cd frontend/frontend-react/react-client && npm run e2e` |
| Terraform (no AWS credentials) | per root and module in `infra/` (`bootstrap`, `envs/*`, `modules/*`): `terraform init -backend=false && terraform test` |
| Repo conventions | from repo root: `bash SystemTurn/infra/scripts/check-conventions.sh` |
