# Exploration: infra-cicd

GitHub Actions CI/CD with AWS OIDC, `terraform plan` on PR, manual prod approval. Mirrored in Engram `sdd/infra-cicd/explore`.

## Current state

- No `.github/`. Remote `eagudelo33-sketch/systemTurn` is **public**: free minutes, Environment reviewers work, but plan logs are public and fork PRs get no OIDC token. The repo name case (`systemTurn`) matters in the OIDC `sub` claim.
- Root configs: `.pre-commit-config.yaml` (pre-commit-terraform v1.109.3; `terraform_validate --retry-once-with-cleanup=true` deletes `.terraform` and lock files on failure), `.tflint.hcl` (aws 0.49.0), `trivy.yaml`, `.trivyignore`. The pre-push `terraform test` hook covers bootstrap/dev/prod only, not the modules.
- Infra: `bootstrap` (state bucket + budget), `envs/dev` (network; app behind `enable_app`; all variables defaulted), `envs/prod` empty, `modules/network` + `modules/app` (mock-resource tests). Nothing applied to a real account.
- `check-conventions.sh`: `no_app_files_touched` fails every branch that touches `backend/` or `frontend/` (and needs `fetch-depth: 0`). `no_account_id` scans `infra/` and `docs/`. `infra-onboarding.md` is 98/100 lines, so CI docs go in a new `docs/cicd.md`.
- Backend: Java 17, Maven wrapper, `-DrequireDocker=true` wired; one shared `postgres:17.10` container, sequential suite. `mvnw` executable bit unverified.
- Frontend: `npm ci/lint/test/build/e2e`; Playwright config already uses `./mvnw` off Windows; e2e needs no Docker or AWS.
- Full-stack 503: proven today only by composing three tests. Missing one HTTP test through controller → service → JPA adapter → real Hikari. Doable without Docker (closed port).

## Obligations

| Item | Decision |
|---|---|
| Whole `infra-cicd` row | Here |
| Scope/remove `no_app_files_touched` | Here (mandatory) |
| tflint aws 0.49.0 → 0.50.0 | Here, date-gated (from 2026-11-06), last task or follow-up |
| Region/default_tags on a real resource, pool × tasks, `/api/*`/SPA/prefix-list tests, wire prod, ACM/WAF | Defer to `infra-prod-wiring` |
| ECR image push, S3 sync, dev auto-apply, saved-plan handoff, PR plan comments, drift schedule, pre-commit-terraform 1.109.4 | Defer |

## Approaches

| OIDC location | Pros | Cons |
|---|---|---|
| **A. `bootstrap/github_oidc.tf`** (recommended) | No new root/lock/backend; CI never applies bootstrap, so it cannot edit its own roles; existing mock-test pattern | Widens bootstrap scope |
| B. New root `infra/ci` | Clean separation | New lock, backend, ROOTS updates, ~+100 lines |

- Plan role: trust `repo:eagudelo33-sketch/systemTurn:pull_request`, `ReadOnlyAccess` + state read, `plan -lock=false`.
- Apply role: trust `…:environment:prod`; state-bucket access only in this change (grows with prod wiring).
- Workflows: `ci.yml` (backend, frontend, e2e with report upload) and `infra.yml` (static checks without credentials, plan on PR gated by a repo variable, `apply-prod` via `workflow_dispatch` + `environment: prod`). SHA-pinned actions, top-level `permissions: contents: read`, no path filters, concurrency groups.
- Offline proof: actionlint, new convention checks (SHA pins, permissions, no `pull_request_target`, `id-token` only where needed), IAM trust tests with `jsonencode`, the 503 test.

## Size

~870 lines ±25% vs 800 budget. Fallback split: PR-A (OIDC, `infra.yml`, conventions, specs) and PR-B (`ci.yml`, 503 test).

## Operational only (real AWS/GitHub)

Bootstrap apply, GitHub secrets (`AWS_PLAN_ROLE_ARN`, `AWS_APPLY_ROLE_ARN`, `TF_STATE_BUCKET`), `prod` Environment reviewers, a real assume-role and plan, Linux lock hashes.

## Risks

Public plan logs; never `pull_request_target`; case-sensitive `sub`; single reviewer vs "prevent self-review"; tflint plugin rate limit; Docker Hub pull; date-gated tflint bump.

Ready for proposal: yes.
