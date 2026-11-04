# Archive Report: infra-foundation

**Closed**: 2026-11-04 · **Verdict**: PASS WITH WARNINGS (first verify run) · **Evidence revision**: `sha256:ee8e6d6f81e632f7a8ca435433ebce53fad7725acde74541ae86f7f74eab4a49`

## Outcome

Terraform/AWS foundation for SystemTurn: repo conventions and gates, a hardened S3 state backend with native locking (`bootstrap/`), an account cost budget, and `envs/dev` + `envs/prod` roots. No application or API change.

| Evidence (no AWS credentials) | Result |
|-------------------------------|--------|
| Requirements / scenarios | 22 / 52, all compliant |
| `check-conventions.sh` / self-test | 25/25 · 15/15 |
| `terraform test` | bootstrap 16 · dev 6 · prod 6 |
| fmt, validate, tflint, trivy | clean (AWS-0132 ignored with written reason) |
| Lock files | aws 6.69.1, windows_amd64 + linux_amd64, unchanged by test runs |

## Main specs created

`openspec/specs/{infra-state-backend,infra-cost-guardrails,infra-environments,infra-repo-conventions}/spec.md` — byte-identical copies of the change specs (13 + 9 + 12 + 18 = 52 scenarios; 22 requirements). They keep the delta header format (`# Delta for ... (NEW)`, `## ADDED Requirements`); future MODIFIED deltas must account for it.

## Delivery

Stacked PRs (stacked-to-main):
- PR A `feat/infra-foundation`: e62244a, 4634985, 49855e3 — conventions, bootstrap, static gates.
- PR B `feat/infra-foundation-envs`: 6fb0cd0, db7eaa3 + this SDD record — env roots, pre-commit, locks, runbook.

## Deviations

- tflint aws plugin pinned to 0.49.0 (0.50.0 was < 14 days old).
- pre-commit-terraform v1.109.3 (v1.109.4 too new); pre-commit-hooks v6.0.0.
- Locks generated with a temporary `= 6.69.1` pin because `~> 6.69` resolves to 6.71.0.
- Archive move done as copy + verified `diff -r` + delete (Windows directory handle blocked `mv`).

## Open warnings → obligations

**infra-network**
1. Prove the region on a provider-backed resource attribute in plan (today only an output asserts it).
2. Prove `default_tags` on a real resource (today proven via the shared local/output).
3. Env roots will gain resources: retire/replace `env_roots_declare_no_resources` with a MODIFIED delta.
4. Bump the tflint aws plugin to 0.50.0 on/after 2026-11-06.

**infra-app** (engram #391): `socketTimeout` + `sslmode` in the RDS URL; never `test`/`demo` profile in prod; ALB health check `/actuator/health/readiness`; RDS Postgres 17; pool × tasks < `max_connections`; ALB idle timeout for websockets.

**infra-cicd**: full-stack 503 test; `-DrequireDocker=true`; sequential Postgres suite (slow concurrency test); run `check-conventions.sh`, `terraform test` (fail on 0 passed), tflint, trivy; never delete lock files before pre-commit.

## Operational (user-run, unverified by design)

Bootstrap apply, state migration, `prevent_destroy` enforcement, lock contention, TLS deny, budget email delivery, SSO login — see the real-apply checklist in `SystemTurn/docs/infra-onboarding.md`.
