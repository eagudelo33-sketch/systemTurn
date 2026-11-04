# Tasks: Infra Foundation

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~1,100 (range 1,000-1,200), incl. tests ~215+, locks ~75, docs ~95 |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR A = Units 1-3 + 5a (~700) -> PR B = Units 4, 5b, 6, 7 (~400) |
| Delivery strategy | auto-chain, stacked PRs (budget 800) |
| Chain strategy | stacked-to-main (PR A: feat/infra-foundation, PR B: feat/infra-foundation-envs) |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: stacked-to-main
400-line budget risk: High

The design estimate (~815) undercounted: spec requires two bootstrap test files (state_bucket + budget, ~170), per-env `terraform.tfvars.example`, ~16 check functions in the script (~130 + ~45 self-test), `.trivyignore`.

Per-unit lines: U2 ~198, U3 ~390, U4 ~250, U5 ~71, U6 ~75 (generated), U7 ~113. Total ~1,097.

Pre-identified cuts (all together ~-50, NOT enough to reach 800):
- Drop `.terraform-docs.yml` (-10): CONFLICTS with spec "MUST commit"; needs spec amendment.
- Runbook 70 lines (-25): allowed by spec (<=100), 9 headings fit.
- Merge env `providers.tf` into `main.tf` (-8): CONFLICTS with spec ("own providers.tf").
Remaining options: (1) `size:exception` single PR (tests+locks+docs ~45% of lines); (2) split into PR A/PR B (feature-branch-chain). Must be chosen before apply.

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|-----------|----------------------|-----------------|-------------------|
| 1 | Spike: offline mock test viability (no commit) | none | `terraform init -backend=false && terraform test` in scratch dir | scratch root, creds env unset | nothing committed |
| 2 | Repo conventions + check script | A | `bash SystemTurn/infra/scripts/check-conventions.sh` + `test-check-conventions.sh` | Git Bash from repo root and `envs/dev` | .gitignore/.gitattributes/scripts |
| 3 | Bootstrap root + tests | A | `terraform -chdir=SystemTurn/infra/bootstrap test` | mock provider, no AWS | `infra/bootstrap/` |
| 4 | Env skeletons dev/prod + tests | B | `terraform -chdir=SystemTurn/infra/envs/<env> test` | mock provider | `infra/envs/` |
| 5 | Static gates config (5a: tflint/trivy/terraform-docs; 5b: pre-commit) | A / B | `terraform fmt -check -recursive`, `tflint --chdir`, `trivy config` | gates on 3 roots | root config files |
| 6 | Lock files (windows_amd64 + linux_amd64) | B | `check-conventions.sh lockfile_pins_aws_6_69_1 lockfile_covers_both_platforms` | `providers lock` (registry download, no creds) | 3 lock files |
| 7 | openspec config + runbook + final gate | B | full `check-conventions.sh` | all gates + all `terraform test` | docs/config |

Staging rule: stage only `SystemTurn/infra`, root `.gitignore`/`.gitattributes`, `SystemTurn/docs`, `.pre-commit-config.yaml`, `.tflint.hcl`, `trivy.yaml`, `.trivyignore`, `.terraform-docs.yml` and `SystemTurn/openspec/config.yaml` (U7). NOT `SystemTurn/openspec/changes/` before tracker settlement. One conventional commit per unit; no AI attribution (user rule).

## Phase 1: Spike (Unit 1, STOP gate)

- [x] 1.1 In the scratchpad, create a scratch root with partial `backend "s3" {}`, aws `~> 6.69`, a bucket from `var`, `.bucket` sub-resource refs, `jsonencode` policy with `"arn:aws:s3:::${var.x}"`, and one `mock_provider "aws" {}` test.
- [x] 1.2 Confirm (a): `terraform init -backend=false` + `terraform test` pass on 1.16.6 with AWS_* env unset, ignoring the S3 backend and with network to AWS not needed. Confirm (b): `.bucket`, `jsondecode(policy)` and `budget` notification set are assertable at plan time.
- [x] 1.3 Record findings in engram `sdd/infra-foundation/spike`. If (a) or (b) fails: STOP and report to the orchestrator (design must be revisited).
- [x] 1.4 Install/verify tools without credentials: `tflint --version`, `trivy --version`, `pre-commit --version`, `terraform-docs --version` (via brew/scoop/pip; `tflint --init` for the aws plugin; pinned 0.49.0 — 0.50.0 was under 14 days old).

## Phase 2: Repo conventions (Unit 2)

- [x] 2.1 RED: create `SystemTurn/infra/scripts/check-conventions.sh` skeleton (cd to `git rev-parse --show-toplevel`; accepts optional check IDs, default all; exit 1 on any failure) with failing checks `state_and_plan_files_ignored`, `real_tfvars_and_backend_ignored`, `examples_and_locks_not_ignored`, `gitattributes_forces_lf`; run: all fail.
- [x] 2.2 GREEN: edit root `.gitignore` (+16) and create `.gitattributes` (7, scoped eol=lf, no `text=auto`); the four checks pass.
- [x] 2.3 RED then GREEN: add checks `no_sensitive_files_tracked` (uses `git ls-files`), `tracked_infra_files_have_no_crlf`, `no_account_id_in_tracked_infra_files`, `no_app_files_touched` (diff vs main), `no_budget_actions` (`check_no_budget_actions`), `no_generated_readmes`.
- [x] 2.4 RED: create `SystemTurn/infra/scripts/test-check-conventions.sh` (threat matrix): (i) `git add -f` dummy `x.tfstate` => script exit 1 (commit-state); (ii) run from `SystemTurn/infra/envs/dev` => same result as from root (repo selection); cleans up the index. Then make green.
- [x] 2.5 Stub (fail-until-artifact) checks, defined now, turned green by their units: `check_version_pins`+identical `versions.tf`x3, `check_lockfiles`, `check_provider_hygiene`, `check_backend_examples`, `check_prevent_destroy`, `check_tfvars_example`, `tflint_config_enables_aws_ruleset`, `precommit_hooks_present`, `openspec_config_declares_infra_tests`, `runbook_has_required_sections`, `runbook_within_size_limit`.
- [x] 2.6 Commit `chore(infra): add repo conventions and convention checker`. (committed as e62244a "chore(infra): add repo conventions")

## Phase 3: Bootstrap (Unit 3, tests first)

- [x] 3.1 RED: `bootstrap/tests/state_bucket.tftest.hcl` (file-level `mock_provider "aws" {}` + valid `variables`): runs `bucket_versioning_enabled`, `bucket_encryption_aes256`, `bucket_blocks_public_access`, `bucket_owner_enforced`, `bucket_policy_denies_insecure_transport`, `lifecycle_noncurrent_expiration`, `lifecycle_aborts_multipart`, `bucket_name_rejects_invalid`, `bucket_name_accepts_valid`. Fail (no config).
- [x] 3.2 RED: `bootstrap/tests/budget.tftest.hcl`: `budget_defaults`, `budget_amount_override`, `budget_amount_rejects_nonpositive`, `notifications_exact_set`, `alert_emails_rejects_empty`, `alert_emails_rejects_malformed`, `alert_emails_wired_to_all_notifications`.
- [x] 3.3 GREEN: `bootstrap/versions.tf` (partial `backend "s3" {}`, `~> 1.11`, `aws ~> 6.69`), `providers.tf`, `variables.tf` (`state_bucket_name`, `alert_emails`, `budget_amount_usd`=30, `region`, `owner`; validations per design), `main.tf` (bucket with `prevent_destroy`, `force_destroy=false`, versioning, SSE, PAB, ownership, jsonencode TLS-deny, lifecycle `filter {}`), `budget.tf` (dynamic notification over 4 thresholds), `outputs.tf`.
- [x] 3.4 GREEN: `bootstrap/terraform.tfvars.example` (`@example.com` placeholders), `bootstrap/backend.hcl.example`. Run `terraform test`, then `check_prevent_destroy`, `check_tfvars_example`, `no_budget_actions`. Spec scenarios `bucket_declares_prevent_destroy`, `tfvars_example_uses_placeholders`.
- [x] 3.5 Commit `feat(infra): add hardened state bucket and cost budget bootstrap root`. (committed as 4634985)

## Phase 4: Env skeletons (Unit 4, tests first)

- [x] 4.1 RED: `envs/dev/tests/env.tftest.hcl`: `region_default_us_east_1`, `region_override`, `env_roots_declare_no_resources`, `default_tags_dev`, `owner_rejects_empty`. Same file for prod with `default_tags_prod` (copy, `Environment=prod`).
- [x] 4.2 GREEN per env: `versions.tf` (byte-identical to bootstrap), `providers.tf` (`region=var.region`, `default_tags`, no profile/keys), `variables.tf`, `main.tf` (`local.environment` hardcoded, outputs), `backend.hcl.example` (distinct `key`, `use_lockfile`, `encrypt`, no `dynamodb_table`), `terraform.tfvars.example`.
- [x] 4.3 Verify via script: `version_constraints_pinned`, `provider_has_no_credentials_or_profile`, `backend_example_keys_distinct`, `backend_example_native_locking`, `backend_block_is_partial`, `env_tfvars_example_present`; `env_roots_validate` via `init -backend=false && validate`.
- [x] 4.4 Commit `feat(infra): add dev and prod environment skeletons`. (committed as 6fb0cd0 "feat(infra): add dev and prod environment roots")

## Phase 5: Static gates (Unit 5)

- [x] 5.1 RED: run `fmt -check -recursive`, `tflint`, `trivy config` before config exists (record fails) for `fmt_check_passes`, `validate_passes_all_roots`, `tflint_passes_all_roots`, `trivy_config_passes`.
- [x] 5.2 GREEN (5a): `.tflint.hcl` (terraform recommended + `plugin "aws"` 0.49.0 enabled (0.50.0 under 14 days old)), `trivy.yaml` (severity HIGH,CRITICAL per spec, exit-code 1), `.terraform-docs.yml`, `.trivyignore` only if findings (S3 logging, CMK) with inline reason; all four gates exit 0; `tflint_config_enables_aws_ruleset` green.
- [x] 5.3 (5b) `.pre-commit-config.yaml`: pin revs >=14 days old via `git ls-remote --tags`; hooks `terraform_fmt`, `terraform_validate`, `terraform_tflint`, trivy, `check-conventions` (pre-commit), `terraform test` (pre-push), `detect-private-key`, `detect-aws-credentials --allow-missing-credentials`; no docs-writing hook; `precommit_hooks_present` green. Commits: `chore(infra): add static gate configuration`.

## Phase 6: Lock files (Unit 6)

- [x] 6.1 RED: `check_lockfiles` (`lockfile_pins_aws_6_69_1`, `lockfile_covers_both_platforms`) fails (no or single-platform locks).
- [x] 6.2 GREEN: in each of the three roots run `terraform providers lock -platform=windows_amd64 -platform=linux_amd64`; verify LF endings, 6.69.1, tracked, not ignored. Commit `chore(infra): add multi-platform provider lock files`.

## Phase 7: Config, runbook, final gate (Unit 7)

- [x] 7.1 RED then GREEN: `SystemTurn/openspec/config.yaml` add `testing.infra` with `terraform test`, `terraform fmt -check`, `terraform validate`, `tflint`, `trivy config`; extend apply/verify commands (`openspec_config_declares_infra_tests`).
- [x] 7.2 RED then GREEN: `SystemTurn/docs/infra-onboarding.md` (<=100 lines; 9 level-2 headings in order: account, root MFA, Identity Center, CLI v2, `aws configure sso`, bootstrap apply, state migration, budget 100% manual `terraform destroy` on `envs/dev`, teardown; plus real-apply checklist); link from `docs/README.md` (`runbook_has_required_sections`, `runbook_within_size_limit`).
- [x] 7.3 Final gate, no credentials: full `check-conventions.sh`, `fmt -check -recursive`, `validate` x3, `tflint` x3, `trivy config`, `terraform test` x3, `test-check-conventions.sh`. Commit `docs(infra): add onboarding runbook and infra test config`.

## Scenario coverage

All 52 spec scenarios map to a named run block or check above; none manual-only.
