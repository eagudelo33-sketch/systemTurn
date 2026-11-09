# Proposal: Infra CI/CD (GitHub Actions, AWS OIDC, plan on PR, manual prod approval)

## Intent

There is no CI. Every gate (`./mvnw test -DrequireDocker=true`, `npm run e2e` on Linux, `terraform test`, tflint, trivy, `check-conventions.sh`) runs only on a laptop, and the `infra-cicd` row in `docs/README.md` lists them as owed. Infra also needs a credential-free path to AWS before anything is applied: short-lived OIDC roles, not stored keys.

## Scope

### In Scope
- `infra/bootstrap/github_oidc.tf`: OIDC provider, plan role (`ReadOnlyAccess` + state read), apply role (state bucket and lock objects only). Repo identity is one variable. Mock-provider trust tests.
- `.github/workflows/ci.yml`: backend, frontend (lint/test/build), Linux e2e with report upload.
- `.github/workflows/infra.yml`: static gates without credentials; plan on PR (gated by a repo variable); `apply-prod` via `workflow_dispatch` + `environment: prod`.
- `check-conventions.sh`: `no_app_files_touched` opt-in; new workflow checks (SHA pins, permissions, no `pull_request_target`, `id-token` scoping).
- Full-stack HTTP 503 test without Docker.
- `docs/cicd.md` runbook; `docs/README.md` roadmap and obligations.
- tflint aws 0.50.0, only on/after 2026-11-06 (last task, else follow-up).

### Out of Scope
- Prod wiring, apply-role growth, region/default_tags proof, ALB/SPA/`/api/*` tests (`infra-prod-wiring`).
- ECR push, S3 sync, dev auto-apply, saved plans, PR plan comments, drift schedule.
- Any API or contract change; frontend and backend stay aligned.

## Capabilities

### New Capabilities
- `infra-cicd`: workflows, OIDC trust, role scopes, job permissions, prod approval.

### Modified Capabilities
- `infra-repo-conventions`: "No application change" becomes opt-in; workflow-hygiene requirement added.
- `runtime-operations`: "Database outage returns 503" gains a full-stack scenario.

## Approach

| Decision | Tradeoff |
|---|---|
| OIDC in `bootstrap` (A) | No new root or lock file; CI never applies bootstrap, so it cannot edit its own roles. Widens bootstrap scope |
| Plan role `sub` = `repo:eagudelo33-sketch/systemTurn:pull_request`, `aud` = `sts.amazonaws.com`, `plan -lock=false` | Read-only, no state write. Case-sensitive `sub` |
| Apply role `sub` = `…:environment:prod`, state-only | Least privilege now; must grow later |
| ARNs and bucket in GitHub secrets | Public repo; `no_account_id` stays green |
| SHA pins, top-level `contents: read`, no path filters, concurrency groups | More pin upkeep; required checks never stay pending |

### Product decisions (confirmed by the user, 2026-11-04)

| Question | Decision |
|---|---|
| Apply role scope | State bucket only; grows in `infra-prod-wiring` |
| Who approves prod | The maintainer self-approves; "prevent self-review" off, documented in `cicd.md` as a known tradeoff |
| Required checks on `main` | The credential-free checks are required; `plan` stays optional until AWS is wired |
| Fork PRs | Credential-free checks only (forks get no OIDC token) |
| Public plan logs | Acceptable while `prod` is empty; revisit in `infra-prod-wiring` |

## Affected Areas

| Area | Impact |
|---|---|
| `.github/workflows/` | New |
| `SystemTurn/infra/bootstrap/` (`github_oidc.tf`, vars, outputs, tests) | New/Modified |
| `SystemTurn/infra/scripts/check-conventions.sh`, `.tflint.hcl` | Modified |
| `SystemTurn/backend/turnos-api/src/test/` (503 test) | New |
| `SystemTurn/docs/cicd.md`, `docs/README.md` | New/Modified |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Size ~870 vs 800 | Med | Fallback split: PR-A (OIDC, `infra.yml`, conventions, specs), PR-B (`ci.yml`, 503 test); decided at tasks |
| Public plan logs | Med | Plan only on same-repo PRs; no sensitive outputs |
| Single reviewer vs "prevent self-review" | Med | Documented in `cicd.md` |
| tflint init rate limit, Docker Hub pull | Low | `GITHUB_TOKEN`; retry |

## Operational notes (real AWS/GitHub only)

Bootstrap apply, secrets (`AWS_PLAN_ROLE_ARN`, `AWS_APPLY_ROLE_ARN`, `TF_STATE_BUCKET`), `prod` Environment reviewers, first real assume-role and plan, Linux lock hashes.

## Rollback Plan

Revert the commits. Disable workflows in GitHub settings. After a bootstrap apply: remove `github_oidc.tf` and apply, deleting the roles and provider.

## Success Criteria

- [ ] `ci.yml` and `infra.yml` green on the PR; actionlint passes.
- [ ] `terraform test` passes in all 5 dirs with >0 passed each.
- [ ] Full-stack 503 test passes in plain `./mvnw test`.
- [ ] `check-conventions.sh` green on a branch touching `backend/`.
