# Proposal: Infra Foundation (Terraform/AWS conventions, state, budget, env skeletons)

## Intent

SystemTurn has no infrastructure code. Before any network or hosting work, the repo needs safe conventions (no state or secrets committed, LF endings), a hardened remote state with native locking, a cost alarm on the account, and per-env roots that later changes (`infra-network`, `infra-app`, `infra-cicd`) extend. No AWS account exists yet, so this slice must be fully verifiable offline.

## Scope

### In Scope
- Repo gates: root `.gitignore` (`.terraform/`, `*.tfstate*`, `*.tfvars` except `*.tfvars.example`, `*.tfplan`; lock files tracked), `.gitattributes` (`eol=lf` for `*.tf`, `*.hcl`, `*.tftest.hcl`, lock files), `.tflint.hcl` + aws ruleset, trivy config scan, terraform-docs config, `.pre-commit-config.yaml`, infra test block in `openspec/config.yaml`.
- `SystemTurn/infra/bootstrap/`: hardened S3 state bucket (versioning, SSE, Block Public Access, BucketOwnerEnforced, TLS-only policy, noncurrent-version + aborted-upload lifecycle, `prevent_destroy`); local-then-migrate flow with `use_lockfile`; account monthly COST budget (ACTUAL 50/80/100%, FORECASTED 100%), amount and email as variables, `terraform.tfvars.example`.
- `SystemTurn/infra/envs/{dev,prod}/`: `backend.hcl` (key per env, `use_lockfile`), provider with `region` variable and `default_tags` (Project, Environment, ManagedBy, Owner), pins (`~> 1.11`, `aws ~> 6.69`, locked 6.69.1), multi-platform lock (windows_amd64, linux_amd64). No resources.
- AWS onboarding runbook: account, root MFA, Identity Center, CLI v2, `aws configure sso`, bootstrap apply, state migration, teardown.
- Mocked `terraform test` (`command = plan`, `mock_provider "aws"`) for bootstrap and env roots.

### Out of Scope
- VPC/network module (`infra-network`), app hosting (`infra-app`), CI/OIDC (`infra-cicd`).
- Real `apply` inside SDD; Cost Anomaly Detection; per-env tag budgets; KMS CMK.

## Capabilities

### New Capabilities
- `infra-state-backend`: hardened state bucket, per-env keys, S3 native locking.
- `infra-cost-guardrails`: account budget thresholds and notification wiring.
- `infra-environments`: dev/prod roots, version pins, region variable, default tags.
- `infra-repo-conventions`: ignore/eol rules and static gates.

### Modified Capabilities
- None.

## Approach

Exploration option A: separate `bootstrap/` root (local state first, then `init -migrate-state`), one root per env with `backend.hcl`, no `profile` in provider (keeps future OIDC unchanged). Specs contain only scenarios provable by mocked tests or static gates; real-apply checks live in "Operational notes".

## Frontend/Backend Alignment

No application change. Backend (`backend/turnos-api`) and frontend (`react-client`) code, tests and build stay untouched. Obligations in `decision/systemturn-infra-app-obligations` remain deferred to `infra-app`.

## Affected Areas

| Area | Impact |
|------|--------|
| `.gitignore`, `.gitattributes`, `.tflint.hcl`, `trivy.yaml`, `.terraform-docs.yml`, `.pre-commit-config.yaml` | New/Modified |
| `SystemTurn/infra/bootstrap/` (+ tests, lock, tfvars example) | New |
| `SystemTurn/infra/envs/{dev,prod}/` (+ tests, locks) | New |
| `SystemTurn/docs/` runbook | New |
| `SystemTurn/openspec/config.yaml` | Modified |

## Size Estimate

~780 lines (range 700–880): tooling ~110, bootstrap ~230 + tests ~100, envs ~90 + tests ~60, locks ~75, runbook ~120. High risk against 800. Levers: no generated READMEs, one test file per env, runbook ≤100.

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Mocks do not prove AWS behavior | High | Runbook apply checklist |
| Mocked plan contacts AWS or mocked data breaks policies | Med | First spike task; `jsonencode`, `override_data` |
| Windows tooling/CRLF/lock hashes | Med | `.gitattributes`, `providers lock` multi-platform |
| Budget alerts do not stop spend | Low (no resources) | Documented |

## Rollback Plan

Revert the PR; nothing is applied by SDD. After a user apply: `destroy` env roots; retire bootstrap deliberately (empty versions, remove `prevent_destroy`).

## Success Criteria

- [ ] `fmt -check`, `validate`, tflint, trivy pass on all roots.
- [ ] `terraform test` passes for bootstrap and both envs.
- [ ] No state/tfvars trackable; lock files tracked with both platforms.
- [ ] Runbook covers onboarding through migration and teardown.

## Proposal question round

Assumptions to confirm: region `us-east-1`; budget default $30/month; bucket `systemturn-tfstate-<account-id>` acceptable in a public repo; AdministratorAccess with short session.

1. Should the budget only notify, or should the 100% alert also prompt a documented manual shutdown step?
2. Who receives alerts: one personal email, or also a second address?
3. Should `prod` stay plan-only (never applied) until `infra-app`, or be applied as soon as bootstrap exists?
4. Is exposing the account ID in a public repo acceptable, or should the bucket name come from a gitignored variable?
