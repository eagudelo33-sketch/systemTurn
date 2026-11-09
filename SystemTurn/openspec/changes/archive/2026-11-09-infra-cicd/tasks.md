# Tasks: Infra CI/CD (GitHub Actions, AWS OIDC, plan on PR, manual prod approval)

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~1,250-1,350 total (design says ~900; inflated ~40% because past estimates ran 35-70% low). PR-A ~950-1,000, PR-B ~300-350. Excludes OpenSpec artifacts |
| 400-line budget risk | High |
| 800-line budget risk | PR-A High (likely 850-1,000), PR-B Low |
| Chained PRs recommended | Yes |
| Chosen split (user, 2026-11-05) | PR1 = A1+A2 (OIDC roles, `terraform-test-all.sh`, opt-in `no_app_files_touched`, ~600); PR2 = A3+A4 (hygiene checks, `infra.yml`, `cicd.md`, `docs/README.md`, ~400); PR3 = B1+B2 (503 test, `ci.yml`, mvnw bit, README, ~300) |
| Delivery strategy | auto-chain (user chose 3 chained PRs, replacing single-pr) |
| Chain strategy | stacked-to-main (PR1 → main, PR2 → PR1 branch, PR3 → PR2 branch; retarget to main as each merges) |

Decision needed before apply: No (resolved 2026-11-05)
Chained PRs recommended: Yes
Chain strategy: stacked-to-main
400-line budget risk: High (each PR stays under 800)

Branches: `feat/infra-cicd` (PR1), `feat/infra-cicd-workflow` (PR2), `feat/infra-cicd-app-ci` (PR3).

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|-----------|----------------------|-----------------|-------------------|
| A1 | Verified pins + OIDC roles in bootstrap | PR-A | `cd SystemTurn/infra/bootstrap && terraform test` | mock_provider runs (offline) | `github_oidc.tf`, vars, outputs, test file |
| A2 | `terraform-test-all.sh` + opt-in `no_app_files_touched` | PR-A | `bash SystemTurn/infra/scripts/test-check-conventions.sh` | fixture sandbox repos | two scripts + hook line |
| A3 | Hygiene checks + `infra.yml` + infra-workflow checks | PR-A | `bash SystemTurn/infra/scripts/check-conventions.sh && actionlint` | real workflow files | `infra.yml`, check functions |
| A4 | `docs/cicd.md`, `docs/README.md` | PR-A | `pre-commit run --files SystemTurn/docs/cicd.md SystemTurn/docs/README.md` | N/A: docs only | the two docs |
| B1 | 503 full-stack test | PR-B | `cd SystemTurn/backend/turnos-api && ./mvnw -B -ntp test -Dtest=DatabaseOutageFullStackTest` | JVM boot, no Docker | one test file |
| B2 | `ci.yml` + 3 checks + mvnw bit + README | PR-B | `bash SystemTurn/infra/scripts/check-conventions.sh && actionlint` | real workflow files | `ci.yml`, checks, README, mode bit |

## Phase 0: Verification (before any pin; never invent values)

- [x] 0.1 For `checkout`, `setup-java`, `setup-node`, `upload-artifact`, `setup-terraform`, `configure-aws-credentials`: pick newest tag dated on/before 2026-10-21 via `gh api repos/<o>/<r>/git/ref/tags/<tag>` (dereference annotated tags via `git/tags/<sha>`); record SHA, tag, date in a scratch table (A1, ~0 lines).
- [x] 0.2 Verify tflint, trivy, actionlint, pre-commit versions, release dates (>=14 days), and tarball sha256 via `gh api`/release checksums; verify setup-terraform 1.16.6 date.
- [x] 0.3 In `infra/bootstrap`: `terraform providers schema -json` to confirm aws 6.69.1 `thumbprint_list` is optional; if required, fetch and verify GitHub thumbprints first.
- [x] 0.4 `rg "h1:"` each root `.terraform.lock.hcl`; confirm a `linux_amd64`-valid hash exists (else run `terraform providers lock -platform=linux_amd64 -platform=windows_amd64`). Check pre-commit-terraform v1.109.3 `terraform_tflint` runs `tflint --init`.

## Phase 1: OIDC bootstrap (A1; ~420 lines with tests)

- [x] 1.1 RED: create `infra/bootstrap/tests/github_oidc.tftest.hcl` (~170 lines): `oidc_provider_created`, `plan_role_trust_is_pull_request_only`, `apply_role_trust_is_prod_environment_only`, `plan_role_is_read_only`, `apply_role_is_state_only`, `repo_identity_is_single_variable` (+ `expect_failures` on invalid repo). Mock the provider ARN. Confirm failure.
- [x] 1.2 GREEN: `variables.tf` add `github_repository` (default `eagudelo33-sketch/systemTurn`, regex validation) (~15 lines).
- [x] 1.3 GREEN: create `bootstrap/github_oidc.tf` (~110 lines): provider, `systemturn-github-plan` (ReadOnlyAccess, state read on `dev/*`, `prod/*`), `systemturn-github-apply` (list, get/put `prod/*`, delete `prod/*.tflock`), exclusive attachments, ARNs built from `var.state_bucket_name`.
- [x] 1.4 GREEN: `outputs.tf` add `github_oidc_provider_arn`, `github_plan_role_arn`, `github_apply_role_arn` (~15 lines). Run `terraform test` in bootstrap.
- [x] 1.5 Commit A1.

## Phase 2: Scripts (A2; ~230 lines with tests)

- [x] 2.1 RED: extend `test-check-conventions.sh`: default run omits `no_app_files_touched` and exits 0 on a `backend/x.java` branch (`no_app_files_touched_not_in_default_run`); by ID it fails naming the file and an infra-only branch passes (`no_app_files_touched_selectable_by_id`). `--guard` returns 1 on `0 passed`, 0 on `3 passed` (`zero_passed_directory_fails`).
- [x] 2.2 GREEN: `check-conventions.sh` add `OPT_IN_CHECKS`, derive `DEFAULT_CHECKS` (~25 lines).
- [x] 2.3 GREEN: create `scripts/terraform-test-all.sh` (init, test, `guard`, `--guard DIR FILE`, 5 default dirs, ~45 lines).
- [x] 2.4 `.pre-commit-config.yaml`: pre-push `terraform-test` calls `terraform-test-all.sh`; add `\.github/workflows/` to the conventions hook `files` (~8 lines). Commit A2.

## Phase 3: Hygiene checks and infra.yml (A3; ~560 lines with tests)

- [x] 3.1 RED: fixtures in `test-check-conventions.sh` (~110 lines): `actions_pinned_to_sha` (tag fails, SHA passes), `top_level_permissions_declared` (none, `write-all`, `contents: write` fail; `contents: read` passes), `pull_request_target_forbidden` (commented-out passes), `id_token_scoped_to_aws_jobs` (top level, non-AWS fail; AWS passes), no workflows fails; extend `same_result_from_env_dir` with `actions_pinned_to_sha`.
- [x] 3.2 GREEN: helpers `wf_files`, `job`, `nocomment` and the four hygiene checks in `check-conventions.sh` (~85 lines).
- [x] 3.3 RED: add repo-workflow checks (fail while `infra.yml` is absent): `static_job_has_no_credentials`, `static_job_runs_all_gates`, `no_path_filters_and_concurrency` (infra.yml part), `plan_job_gated_for_same_repo`, `plan_uses_lock_false_and_secrets`, `apply_prod_only_manual_on_main`, `apply_role_isolated_to_apply_prod`, `fork_pr_reaches_only_credential_free_jobs`, `repo_identity_not_hardcoded`, and `repo_workflows_pass_hygiene` (~110 lines).
- [x] 3.4 GREEN: create `.github/workflows/infra.yml` (~175 lines) with the verified pins from 0.x: `static`, `plan` matrix, `apply-prod`. Run `actionlint` and all checks.
- [x] 3.5 Commit A3.

## Phase 4: Docs (A4; ~120 lines)

- [x] 4.1 Create `SystemTurn/docs/cicd.md` per design outline (7 sections; maintainer self-approval tradeoff; pin upkeep 14-day rule; follow-up obligations).
- [x] 4.2 `docs/README.md`: mark infra-cicd roadmap/status/obligations done; move deferred items to `infra-prod-wiring`. Commit A4. **PR-A ends here.**

## Phase 5: PR-B (needs PR-A merged)

- [x] 5.1 RED B1: create `backend/turnos-api/src/test/java/com/systemturn/turnos/DatabaseOutageFullStackTest.java` (~110 lines): POST `/api/v1/turnos` and GET `/api/v1/turnos/dashboard` return 503, `error` `SERVICIO_NO_DISPONIBLE`, no `jdbc`/`postgresql`/`127.0.0.1`/port, each under timeout+2 s (scenario "Full-stack outage returns 503 without Docker"). It is already GREEN if the handler works; if it fails, fix the cause. Commit B1.
- [x] 5.2 RED B2: add `backend_requires_docker`, `frontend_runs_lint_test_build`, `e2e_uploads_report_on_failure`, and the `ci.yml` side of `no_path_filters_and_concurrency` and `fork_pr_...` (~45 lines).
- [x] 5.3 GREEN: create `.github/workflows/ci.yml` (~115 lines): backend, frontend, e2e.
- [x] 5.4 `git update-index --chmod=+x SystemTurn/backend/turnos-api/mvnw` (mode 100644 to 100755; check `git ls-files -s`).
- [x] 5.5 `SystemTurn/README.md`: CI section in neutral Spanish (~25 lines). Commit B2.

## Phase 6: Archive and last task

- Note (archive): `sdd-archive` adds `infra-cicd` to `openspec/specs/README.md` and merges the delta specs. Not a verify-gated task.
- [x] 6.2 LAST, date-gated to >= 2026-11-06 (done 2026-11-09): `.tflint.hcl` aws plugin 0.49.0 to 0.50.0. Release v0.50.0 published 2026-10-23 (17 days old, verified via `gh api`); pre-commit and check-conventions re-run green.

## Scenario to Test Map

| Scenario | Written in |
|---|---|
| oidc_provider_created, plan_role_trust_is_pull_request_only, apply_role_trust_is_prod_environment_only, plan_role_is_read_only, apply_role_is_state_only, repo_identity_is_single_variable | 1.1 |
| no_app_files_touched_not_in_default_run, no_app_files_touched_selectable_by_id, zero_passed_directory_fails | 2.1 |
| actions_pinned_to_sha, top_level_permissions_declared, pull_request_target_forbidden, id_token_scoped_to_aws_jobs | 3.1 |
| static_job_*, plan_*, apply_*, fork_pr_* (infra.yml), repo_workflows_pass_hygiene, no_path_filters_and_concurrency | 3.3 (ci.yml half in 5.2) |
| backend_requires_docker, frontend_runs_lint_test_build, e2e_uploads_report_on_failure | 5.2 |
| Full-stack outage returns 503 without Docker | 5.1 |
