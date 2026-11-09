# Apply progress: infra-cicd (PR1 = Phase 0 + A1 + A2; PR2 = A3 + A4)

Status: PR1 complete (13/13). PR2 complete (7/7 tasks in Phases 3-4; 20/20 overall). PR3 complete (Phase 5: 5/5). Unit 6.2 done 2026-11-09; 6.1 is an archive note. All tasks complete (26/26).
Branch: feat/infra-cicd. Commits: 487a993 (A1 OIDC), c982e9b (A2 scripts). Not pushed.
Changed lines (code+tests, excl. OpenSpec): 363 insertions, 4 deletions across 8 files.

## Phase 0 verified table (today 2026-11-05; cutoff on/before 2026-10-22; verified via gh api)

Action pins (tag -> commit SHA; none were annotated tags, all `git/ref/tags` type=commit):

| Action | Tag | Commit SHA | Release date |
|---|---|---|---|
| actions/checkout | v7.0.1 | 3d3c42e5aac5ba805825da76410c181273ba90b1 | 2026-07-20 |
| actions/setup-java | v6.1.0 | b843ebf9b913146b38da2eaa7aeddf76074ae1ad | 2026-10-14 |
| actions/setup-node | v7.1.0 | 949feb2413d6458794dcd2491c4babbbce0c15c1 | 2026-10-08 |
| actions/upload-artifact | v7.0.2 | cf430e030ddbb5b0abf93d22962f4752f3646cd9 | 2026-10-07 |
| hashicorp/setup-terraform | v4.0.1 | dfe3c3f87815947d99a8997f908cb6525fc44e9e | 2026-05-12 |
| aws-actions/configure-aws-credentials | v6.3.0 | e1253824e5c10ff9df46874f81ed3ec929e19cfd | 2026-09-15 |

Newer tags excluded as too new: setup-java v6.2.0 (2026-11-04). Terraform for setup-terraform: 1.16.6 released 2026-10-14 (verified; 1.16.7 is 2026-10-22, 1.17.x newer).

Tools (linux amd64 tarballs; sha256 from the release checksum files):

| Tool | Version | Release date | sha256 |
|---|---|---|---|
| tflint | v0.65.0 | 2026-10-22T17:30Z (exactly 14 days; BORDERLINE, fallback v0.64.0 2026-07-17; local tflint is 0.65.0) | d4c545ed2e29d6338604b586f34f544f380f70f243aa72f7b484713835eaec61 (tflint_linux_amd64.zip) |
| trivy | v0.76.1 | 2026-10-21 (v0.76.2 is 2026-11-05, too new) | c7cbe3137fb8a48a17574bede3dd6beb16742d1e9772929940fc90082c39d9c0 (trivy_0.76.1_Linux-64bit.tar.gz) |
| actionlint | v1.7.12 | 2026-03-30 | 8aca8db96f1b94770f1b0d72b6dddcb1ebb8123cb3712530b08cc387b349a3d8 (actionlint_1.7.12_linux_amd64.tar.gz) |
| pre-commit | 4.7.0 (pipx/pip) | 2026-10-13 | n/a (pip pin) |
| terraform | 1.16.6 | 2026-10-14 | n/a (setup-terraform) |

pre-commit-terraform v1.109.3 (tag object 443ffdff1cc1663b701ae73e7459029fe883455a): `hooks/terraform_tflint.sh` runs `tflint --init` itself, so no extra init step is needed in the static job.

Other Phase 0 findings:
- aws 6.69.1 `aws_iam_openid_connect_provider.thumbprint_list` is optional+computed (verified with `terraform providers schema -json`), so no thumbprints are used. `aws_iam_role_policy_attachments_exclusive` exists.
- Gotcha: `terraform providers schema -json` fails in a dir with `backend "s3" {}` even after `init -backend=false`; verified in a scratch dir with only required_providers `= 6.69.1`.
- Bootstrap root lock already carries `linux_amd64` h1 hashes: `terraform providers lock -platform=linux_amd64 -platform=windows_amd64` on a scratch copy reported "no need for changes". Roots dev/prod/bootstrap each have 2 `h1:` entries.
- RISK for PR2: `modules/network` and `modules/app` locks (aws 6.71.0) have only 1 `h1:` each, and terraform-test-all.sh uses `-lockfile=readonly` for them. On Linux this relies on `zh:` hashes of the registry package; not verified. PR2 must confirm in CI, else run `terraform providers lock -platform=linux_amd64 -platform=windows_amd64` there.
- `git diff --exit-code -- '*.lock.hcl'` is clean after all work.

## TDD evidence

A1 RED (`terraform test` in infra/bootstrap before github_oidc.tf existed):
`Error: Reference to undeclared resource ... aws_iam_openid_connect_provider.github has not been declared`; 6 run blocks skipped; `Failure! 16 passed, 1 failed, 6 skipped.`
A1 GREEN: `Success! 23 passed, 0 failed.` (one test-side fix: `length(policy_arns) == 0` instead of comparing to `toset([])`, a type warning/failure in the test itself).

A2 RED (`bash test-check-conventions.sh` before the script changes):
`FAIL no_app_files_touched_not_in_default_run`, `FAIL zero_passed_directory_fails (exit 127)`, `FAIL passed_directory_succeeds (exit 127)`; `ran=20 failed=3`.
A2 GREEN: `ran=20 failed=0`. `check-conventions.sh` default run: 24 checks, all PASS (no_app_files_touched excluded); terraform-test-all.sh: bootstrap 23, envs/dev 7, envs/prod 6, modules/network 11, modules/app 11 passed.

Deviation: `no_app_files_touched_not_in_default_run` asserts the default run output lacks the ID while other checks still run (the sandbox default run cannot exit 0 because most checks need real files). The real-repo default run exits 0 and is verified separately.

## Final checks (all green)
bootstrap `terraform test` 23 passed; envs/dev 7, envs/prod 6; `check-conventions.sh` ran=24 no FAIL; `test-check-conventions.sh` ran=20 failed=0; `terraform fmt -check -recursive SystemTurn/infra` ok; tflint (bootstrap) exit 0; `trivy config` on bootstrap clean; pre-commit config validates.

## Files changed
.pre-commit-config.yaml; SystemTurn/infra/bootstrap/{github_oidc.tf,variables.tf,outputs.tf,tests/github_oidc.tftest.hcl}; SystemTurn/infra/scripts/{check-conventions.sh,test-check-conventions.sh,terraform-test-all.sh (mode 100755)}.

## Notes for PR2/PR3
- Local env: `gh` is at /c/Program Files/GitHub CLI (not on Git Bash PATH); bat/fd/eza/jq absent; `python -m pre_commit` works.
- `.pre-commit-config.yaml` now has `\.github/workflows/` in the conventions hook files and the pre-push hook calls terraform-test-all.sh.

## PR2 (branch feat/infra-cicd-workflow, stacked on feat/infra-cicd; not pushed)
Commits: e98041f (A3 hygiene checks + infra.yml), 9d63939 (A4 docs). `git diff --stat feat/infra-cicd...HEAD`: 5 files, 433 insertions, 7 deletions (code+tests+docs; budget 800).

TDD evidence:
- 3.1 RED: fixtures added first; `test-check-conventions.sh` -> `ran=46 failed=8` (positive cases failed: unknown check IDs; same_result_from_env_dir, sha/local-path/read/commented-out/aws-job passes, tag-fails naming file+ref, committed_infra_yml_passes).
- 3.2 GREEN: hygiene checks added -> `ran=46 failed=3` (only infra.yml-dependent cases left).
- 3.3 RED: real repo `check-conventions.sh` -> 14 FAIL ("no workflow files" / "missing file: .github/workflows/infra.yml"; repo_identity_not_hardcoded failed on the script's own literal, fixed by building the slug from `$owner-sketch`).
- 3.4 GREEN: infra.yml created; first runs exposed 3 check/workflow bugs (step name "terraform plan ..." matched the -lock=false scan; gate regex for actionlint matched the install step so mutation survived; terraform-test-all dirs matched with $INFRA prefix). Final: `test-check-conventions.sh ran=46 failed=0`, `check-conventions.sh` 38 checks all PASS, actionlint 1.7.12 exit 0, terraform-test-all 23/7/6/11/11 passed, pre-commit --all-files passed, `git diff --exit-code -- '*.lock.hcl'` clean, links check 19 files 80 links 0 broken.
- Added 12 mutation tests (copy committed infra.yml into the sandbox, break one safeguard, expect the matching check to fail) beyond the planned fixtures.

Findings / deviations:
- Module locks (`modules/**/.terraform.lock.hcl`) are gitignored, so PR1's Linux-hash risk for modules does not apply (terraform-test-all adds -lockfile=readonly only for tracked locks). Bootstrap/dev/prod locks already have linux_amd64 h1 hashes.
- Workflow checks read the git index (git ls-files), so a new workflow file must be staged (git add) before the checks see it.
- repo_workflows_pass_hygiene runs actionlint only when it is on PATH (otherwise prints a note); CI runs actionlint as its own step. tflint/trivy gates are satisfied by the pre-commit hooks (terraform_tflint/terraform_trivy) plus the checksum-verified install step; the check requires their names in the static job.
- Static job also runs test-check-conventions.sh (design table). no_path_filters_and_concurrency iterates all tracked workflows, so it covers ci.yml when PR3 adds it.
- Unverified until first CI run: shellcheck findings that actionlint only reports where shellcheck is installed (local actionlint has none); tflint v0.65.0 is exactly 14 days old (fallback v0.64.0).
- Local actionlint 1.7.12 windows zip sha256 verified against the release checksums (6e7241b5...); stored in scratchpad, not in repo.
- docs/README.md: infra-cicd row is "In progress"; new row 7b `infra-prod-wiring` carries prod wiring; obligations row renamed to `infra-prod-wiring`.

## PR3 (branch feat/infra-cicd-app-ci, stacked on feat/infra-cicd-workflow; not pushed)
Commits: f36b166 (B1 full-stack 503 test), 63f3b42 (B2 ci.yml + checks + mvnw bit + README/docs). `git diff --stat feat/infra-cicd-workflow...HEAD`: 8 files, 274 insertions, 5 deletions (budget 800).

TDD evidence:
- B1: `DatabaseOutageFullStackTest` (2 tests: POST /api/v1/turnos and GET /dashboard, Hikari timeout 1000 ms, unreachable local port, no Docker) was GREEN on first run (2 passed). Mutation proof: `ApiExceptionHandler` SERVICE_UNAVAILABLE -> INTERNAL_SERVER_ERROR made both fail (`expected: 503 but was: 500`); reverted (git checkout), handler untouched.
- B2 RED: added 5 ci.yml checks + `committed_ci_yml_passes_ci_checks` + 11 mutation fixtures before ci.yml existed: `ran=58 failed=4`; real repo `check-conventions.sh` 5 FAIL (backend_requires_docker, frontend_runs_lint_test_build, e2e_uploads_report_on_failure, ci_uses_no_secrets_or_id_token, ci_triggers_pull_request_and_main).
- B2 GREEN: ci.yml created -> one residual failure (`ci_without_push_trigger_fails`: the trigger check matched the substring `push`); tightened to `^\s+push:` and `branches: [main]` -> `test-check-conventions.sh ran=58 failed=0`, `check-conventions.sh` ran=43 all PASS, actionlint 1.7.12 exit 0, links 19 files 81 links 0 broken, pre-commit hooks on changed files passed.
- Full suites: backend `./mvnw.cmd test` 149 tests, 0 failures, 0 skipped (Docker was running locally, PostgreSQL tests ran); frontend `npm test` 19 passed, `npm run lint` clean.

Decisions / deviations:
- Added two checks beyond the three planned: `ci_uses_no_secrets_or_id_token` and `ci_triggers_pull_request_and_main` (spec requirement text). The fork and path-filter/concurrency checks already iterate all tracked workflows, so ci.yml is covered by them; mutation fixtures prove it.
- e2e upload condition is `${{ !cancelled() }}` (per instruction); the check accepts `always()` or `!cancelled()` (spec says `always()`; semantics differ only on cancel).
- Node 22 chosen for setup-node (repo declares no engines/.nvmrc; local Node is 26). Unverified until the first CI run.
- Playwright's backend webServer runs `./mvnw spring-boot:run`, which needs the executable bit (set: 100755).
- Upload artifact uses `if-no-files-found: ignore` and `retention-days: 14`.
- 6.2 (tflint aws 0.50.0) NOT done: date-gated to >= 2026-11-06 (today 2026-11-05). Recorded in docs/README.md `infra-prod-wiring` row; box left unticked with a note. 6.1 is done at archive.
- docs: README row 7 now "Done (PR #9-#11)"; obligations row `infra-cicd (done)`; cicd.md section 1 mentions ci.yml, section 5 lists required checks.
- Unverified until first CI run: ci.yml shellcheck/runtime behaviour, node 22 compatibility, Docker-required backend run on ubuntu.

## PR3 follow-up unit: 6.2 tflint aws 0.50.0 (2026-11-09)
- 6.2 DONE: `gh api` shows tflint-ruleset-aws v0.50.0 published 2026-10-23T15:44:32Z (17 days old, not prerelease); `.tflint.hcl` bumped 0.49.0 to 0.50.0 with updated comment. Removed the follow-up rows in `docs/cicd.md` and `docs/README.md` (infra-prod-wiring).
- 6.1 converted to a plain archive note in tasks.md (sdd-archive merges specs and updates `openspec/specs/README.md`); all checkbox tasks complete (26/26).
- TDD evidence (config-only): gates are check-conventions (ran=43, no FAIL) and pre-commit run --all-files (tflint hook with aws 0.50.0).
