# Apply Progress: infra-foundation

Mode: Strict TDD (runner: `terraform test`, Terraform 1.16.6 windows_amd64, hashicorp/aws 6.69.1)

## Unit 1 - Spike (DONE, nothing committed)

Scratch root: `<scratchpad>/tf-spike` (outside repo). Env: `AWS_*` unset via `env -u`.

### TDD Cycle Evidence

| Task | Test file | Layer | Safety Net | RED | GREEN | TRIANGULATE | REFACTOR |
|------|-----------|-------|------------|-----|-------|-------------|----------|
| 1.1-1.2 spike | scratch `tests/spike.tftest.hcl`, `tests/unknown.tftest.hcl` | plan-time mock | N/A (new scratch) | `unknown.tftest.hcl` fails as expected (`.id` unknown at plan) | spike: 2/2 pass | bad-name `expect_failures` + 3 notification thresholds + 2 policy resources | N/A (spike) |

### Findings
- (a) PASS: `terraform { backend "s3" {} }` + aws `6.69.1`; `init -backend=false` OK (no backend contact); `terraform test` with file-level `mock_provider "aws" {}` and `command = plan` passes with no credentials. First run ~2+ min (cold provider download/schema); warm run ~6.6 s.
- (b) PASS at plan time: `aws_s3_bucket.state.bucket`, `aws_s3_bucket_versioning.state.versioning_configuration[0].status`, sub-resource `.bucket` ref, `jsondecode(aws_s3_bucket_policy.state.policy).Statement[0].Condition.Bool["aws:SecureTransport"]`, policy `Resource` containing `arn:aws:s3:::<var>/*`, `aws_budgets_budget.monthly.notification` (length, thresholds as set, `subscriber_email_addresses` set equality).
- `.id` / `.arn` assertions FAIL at plan with "Unknown condition value" (second run skipped). Design correctly avoids them.
- (c) PASS: `run` with `variables { state_bucket_name = "BAD_NAME" }` + `expect_failures = [var.state_bucket_name]` passes.
- Gotchas: block-typed attrs are lists (`versioning_configuration[0]`); notification `threshold` from a `toset` for_each compares fine as `toset([...])`; budget notifications are a set so index access is not possible, use `for` expressions; `-filter` takes paths relative to the root (not usable as `tests/x`), prefer running whole dir.
- tflint `--init` with aws plugin 0.50.0 OK; tflint on scratch root: clean.

### Tool availability (Windows, user scope)
| Tool | Before | Action | Now |
|------|--------|--------|-----|
| tflint | missing | `scoop install tflint` | 0.65.0 (shim `~/scoop/shims`) |
| trivy | missing | `scoop install trivy` | 0.76.1 |
| terraform-docs | missing | `scoop install terraform-docs` | 0.24.0 |
| pre-commit | missing | `pip install --user pre-commit` | 4.7.0 (exe in `%APPDATA%\Python\Python312\Scripts`, not on Git Bash PATH; use `python -m pre_commit`) |

Note: `~/scoop/shims` was not on the Git Bash PATH in the agent shell; add `export PATH="$PATH:/c/Users/user/scoop/shims"`.

## Unit 2 - Repo conventions + check script (DONE, committed: PR A e62244a, 4634985, 49855e3; PR B 6fb0cd0, db7eaa3)

Files: `.gitignore` (+15, CRLF kept), `.gitattributes` (8), `SystemTurn/infra/scripts/check-conventions.sh` (206), `SystemTurn/infra/scripts/test-check-conventions.sh` (68). Total +297 lines incl. tests.

### TDD Cycle Evidence

| Task | Test file | Layer | Safety Net | RED | GREEN | TRIANGULATE | REFACTOR |
|------|-----------|-------|------------|-----|-------|-------------|----------|
| 2.1-2.2 ignore/attr checks | check-conventions.sh (4 IDs) | repo-file | N/A (new) | 3/4 FAIL (examples_and_locks_not_ignored passes vacuously pre-rules) | 4/4 PASS after .gitignore/.gitattributes | check-attr on a.tf,b.hcl,c.tftest.hcl,.terraform.lock.hcl,x.sh = lf; Foo.java unspecified | N/A |
| 2.3 six checks | check-conventions.sh | repo-file | 4/4 | stubs: 6/6 FAIL "not implemented" | 6/6 PASS | self-test negatives (crlf, account id, budget action, staged tfstate) | N/A |
| 2.4 self-test | test-check-conventions.sh | sandbox git repo | 25-check script | first run 2 FAIL: crlf_is_caught (MSYS `grep -q` strips CR -> fixed with `grep -qU`), prevent_destroy_true (`$3` unbound under `set -u`) | 15/15 PASS | mutation (worktree `find` instead of `git ls-files`) => unstaged_again_passes FAILS (self-test is sensitive) | `block()` takes explicit END regex |
| 2.5 deferred checks | check-conventions.sh | repo-file | N/A | 15 FAIL (missing artifacts) | green in later units | parsers exercised by self-test (backend partial, prevent_destroy) | N/A |

Green now: state_and_plan_files_ignored, real_tfvars_and_backend_ignored, examples_and_locks_not_ignored, gitattributes_forces_lf, no_sensitive_files_tracked, tracked_infra_files_have_no_crlf, no_account_id_in_tracked_infra_files, no_app_files_touched, no_budget_actions, no_generated_readmes.
Deferred (red until artifact): version_constraints_pinned, lockfile_pins_aws_6_69_1, lockfile_covers_both_platforms (U6), provider_has_no_credentials_or_profile, backend_example_keys_distinct, backend_example_native_locking, backend_block_is_partial, env_tfvars_example_present (U4), bucket_declares_prevent_destroy, tfvars_example_uses_placeholders (U3), tflint_config_enables_aws_ruleset, precommit_hooks_present (U5), openspec_config_declares_infra_tests, runbook_has_required_sections, runbook_within_size_limit (U7).
Note: no_budget_actions / no_generated_readmes / no_sensitive_files_tracked pass vacuously until infra files exist; negatives covered by self-test.
Guards: unknown ID or zero executed checks => exit 1. 2.6 commit left to the orchestrator.

## Unit 3 - Bootstrap root + tests (DONE, committed: PR A e62244a, 4634985, 49855e3; PR B 6fb0cd0, db7eaa3)

Files (460 changed lines staged incl. tests/examples): `infra/bootstrap/{versions,providers,variables,main,budget,outputs}.tf` (13/12/54/85/29/14), `terraform.tfvars.example` (5), `backend.hcl.example` (6), `tests/state_bucket.tftest.hcl` (132), `tests/budget.tftest.hcl` (106), root `.trivyignore` (3), 1-line fix in `infra/scripts/test-check-conventions.sh`.

### TDD Cycle Evidence

| Task | Test file | Layer | Safety Net | RED | GREEN | TRIANGULATE | REFACTOR |
|------|-----------|-------|------------|-----|-------|-------------|----------|
| 3.1 state bucket | `bootstrap/tests/state_bucket.tftest.hcl` (9 runs) | plan-time mock | N/A (new) | tests only, no config: `unknown provider registry.terraform.io/hashicorp/aws`, "0 passed, 0 failed" (both files fail) | 9/9 pass | invalid name vs valid name; Deny statement fields + both ARNs; mutation (Suspended, 30d, kms, SecureTransport true) => "0 passed, 2 failed, 14 skipped" | N/A |
| 3.2 budget | `bootstrap/tests/budget.tftest.hcl` (7 runs) | plan-time mock | N/A (new) | same RED run | 7/7 pass | default 30 vs override 50; empty vs malformed emails; 1 vs 2 emails wired; mutation (80->70, one subscriber) caught | N/A |
| 3.3 config | all above | | | | `terraform test`: **16 passed, 0 failed** (no AWS creds) | | fmt -check clean, validate OK |
| 3.4 examples + checks | check-conventions.sh | repo-file | 10 green from U2 | bucket_declares_prevent_destroy, tfvars_example_uses_placeholders FAIL (missing files) | both PASS; no_budget_actions PASS | | N/A |

Static gates on bootstrap: `terraform fmt -check -recursive` exit 0; `validate` OK; `tflint` (no config yet, default rules) 0 findings; `trivy config --severity HIGH,CRITICAL` found AWS-0132 (no CMK, HIGH) -> ignored in root `.trivyignore` with written reason (SSE-S3 mandated by spec; CMK out of scope). Re-run: 0 findings. (AWS-0089 S3 logging is MEDIUM, below the HIGH/CRITICAL gate: no ignore needed.)

Deviation/fix: U2 `test-check-conventions.sh:51` embedded a literal 12-digit fixture which made `no_account_id_in_tracked_infra_files` fail on the committed repo; split into two printf args (self-test still 15/15). Single-platform `.terraform.lock.hcl` created by init was deleted (Unit 6 owns locks).
Remaining green-by-later-unit checks: env/lock/tflint/precommit/openspec/runbook IDs (U4-U7).

## Unit 5a - Static gate config (DONE, committed: PR A e62244a, 4634985, 49855e3; PR B 6fb0cd0, db7eaa3)

Files (staged): `.tflint.hcl` (11), `trivy.yaml` (9), `.terraform-docs.yml` (8), `.gitattributes` (+1 `.trivyignore text eol=lf`). ~29 lines.

### TDD Cycle Evidence

| Task | Test file | Layer | Safety Net | RED | GREEN | TRIANGULATE | REFACTOR |
|------|-----------|-------|------------|-----|-------|-------------|----------|
| 5.1-5.2 | check-conventions.sh `tflint_config_enables_aws_ruleset` + static gates | repo-file / static | bootstrap tf test 16/16, self-test 15/15 | check FAIL "missing file: .tflint.hcl"; gates had no config | check PASS; tflint exit 0; trivy exit 0 (AWS-0132 suppressed by .trivyignore); fmt exit 0; terraform-docs renders to stdout | trivy with `--ignorefile /dev/null` finds AWS-0132 and exits 1 (proves ignorefile wiring); gitattributes/crlf/no_generated_readmes green | N/A |

Decisions: aws plugin pinned **0.49.0**, not 0.50.0 (0.50.0 published 2026-10-23, 11 days before 2026-11-03, under the 14-day rule; 0.49.0 is 2026-09-19). terraform ruleset is bundled (0.15.0), enabled via `plugin "terraform"` with preset recommended. `ignorefile: .trivyignore` is relative to cwd (run from repo root). Bootstrap `terraform test` 16/16 after re-init; stray single-platform lock deleted again. Only bootstrap root exists; env roots gated in later units.

## Unit 4 - Env roots dev/prod (DONE, committed: PR A e62244a, 4634985, 49855e3; PR B 6fb0cd0, db7eaa3)

Files (staged, 2 x 149 = 298 lines incl. tests): per env `versions.tf` (13, byte-identical to bootstrap), `providers.tf` (7), `variables.tf` (21), `main.tf` (26), `backend.hcl.example` (6), `terraform.tfvars.example` (3), `tests/env.tftest.hcl` (73).

### TDD Cycle Evidence

| Task | Test file | Layer | Safety Net | RED | GREEN | TRIANGULATE | REFACTOR |
|------|-----------|-------|------------|-----|-------|-------------|----------|
| 4.1-4.2 | `envs/{dev,prod}/tests/env.tftest.hcl` (6 runs each) | plan-time mock | bootstrap 16/16, self-test 15/15 | tests only: `unknown provider hashicorp/aws`, "0 passed, 0 failed" | dev 6/6, prod 6/6 | default vs override region; bad region and empty owner via expect_failures; dev vs prod tag maps | N/A |
| 4.3 | check-conventions.sh | repo-file | 10 green | 6 Unit-4 IDs FAIL (missing env files) | all 6 PASS; validate OK both roots | | N/A |

Gates: fmt exit 0; tflint 0 on bootstrap/dev/prod; trivy exit 0; self-test 15/15. Remaining red checks belong to U5b-U7 (locks, precommit, openspec config, runbook).
Decisions: default_tags built in `local.default_tags` and shared by provider and output (tests assert the output since provider tags are not visible in plan); `env_roots_declare_no_resources` asserts clean plan + environment output (terraform test cannot count resource changes); extra run `region_rejects_invalid`. Single-platform locks deleted.

## Units 5b, 6, 7 - Pre-commit, locks, config + runbook, final gate (DONE, committed: PR A e62244a, 4634985, 49855e3; PR B 6fb0cd0, db7eaa3)

Files staged: `.pre-commit-config.yaml` (44), 3 x `.terraform.lock.hcl` (27 each = 81 generated), `SystemTurn/openspec/config.yaml` (+13/-3), `SystemTurn/docs/infra-onboarding.md` (88), `SystemTurn/docs/README.md` (+1). Authored ~147 lines + 81 generated.

### TDD Cycle Evidence

| Task | Test file | Layer | Safety Net | RED | GREEN | TRIANGULATE | REFACTOR |
|------|-----------|-------|------------|-----|-------|-------------|----------|
| 5.3 | check-conventions.sh `precommit_hooks_present` | repo-file + real hooks | 18 PASS before | FAIL "missing file: .pre-commit-config.yaml" | PASS; `pre-commit validate-config` OK; `pre_commit run --all-files` hooks all ran | first run found trivy hook FATAL `ignore file not found: .trivyignore` (hook cwd = module dir) -> fixed with `--ignorefile=__GIT_WORKING_DIR__/.trivyignore`; rerun `terraform_trivy` Passed | N/A |
| 6.1-6.2 | `lockfile_pins_aws_6_69_1`, `lockfile_covers_both_platforms` | repo-file | | FAIL missing lock files (also: pre-commit's init produced 6.71.0 lock -> check FAIL "aws not pinned to 6.69.1") | both PASS: 3 locks, aws 6.69.1, 18 hash lines each (h1 + zh for both platforms), constraints `~> 6.69` | 6.71.0 lock rejected by check (sensitivity) | N/A |
| 7.1 | `openspec_config_declares_infra_tests` | repo-file | | FAIL "no infra test section" | PASS | | N/A |
| 7.2 | `runbook_has_required_sections`, `runbook_within_size_limit` | repo-file | | both FAIL missing file | both PASS (88 lines, 9 required + 2 extra level-2 headings in order) | | N/A |
| 7.3 | final gate | all | | | check-conventions 25/25; self-test 15/15; terraform test bootstrap 16/16, dev 6/6, prod 6/6; fmt/validate/tflint/trivy exit 0; AWS_* unset | | N/A |

Pinned revs (today 2026-11-03 ~20:00 UTC): pre-commit-hooks `v6.0.0` (2025-08-09; newest tag); pre-commit-terraform `v1.109.3` (2026-10-20 15:27Z, 14d 4h old; v1.109.4 2026-10-30 too new).
Lock technique: `~> 6.69` resolves to 6.71.0 today, so each root was locked with a temporary `= 6.69.1` pin in versions.tf, `providers lock -platform=windows_amd64 -platform=linux_amd64`, `git checkout versions.tf`, then `init -backend=false` rewrote constraints to `~> 6.69` while keeping 6.69.1. Re-running init/test afterwards leaves locks unchanged.
Decisions: terraform_validate uses `--retry-once-with-cleanup`; `.pre-commit-config.yaml` and runbook are LF in working copy, git warns they will be CRLF (core.autocrlf; .gitattributes only covers tf/hcl/sh). `config.yaml` kept CRLF.
