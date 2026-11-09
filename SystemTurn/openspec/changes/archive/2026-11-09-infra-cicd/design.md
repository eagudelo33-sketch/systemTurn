# Design: Infra CI/CD (GitHub Actions, AWS OIDC, plan on PR, manual prod approval)

## Technical Approach

Two workflows under repo-root `.github/workflows/`. GitHub OIDC trust goes in `infra/bootstrap/github_oidc.tf`, which CI never applies. All workflow scenarios are proven offline by `check-conventions.sh` checks, labelled with their spec scenario IDs, plus `actionlint`. A new `terraform-test-all.sh` loop holds the 0-passed guard. Both the pre-push hook and CI use it. The full-stack 503 test follows the `HealthGroupsTest` boot pattern.

## Architecture Decisions

| Topic | Choice | Rejected (tradeoff) |
|---|---|---|
| OIDC provider | `url = https://token.actions.githubusercontent.com`, `client_id_list = ["sts.amazonaws.com"]`, no `thumbprint_list` | Hardcoded thumbprints. AWS ignores them for GitHub. The argument should be optional since provider 5.81.0, but **this is not verified against the 6.69.1 schema**. Apply verifies it with `terraform providers schema -json`. If it is required, add GitHub's published thumbprints, also verified first |
| Trust shape | `jsonencode`, one statement, `sts:AssumeRoleWithWebIdentity`, `Principal.Federated` = provider ARN, `StringEquals` on `…:aud` and `…:sub` | `StringLike` with wildcards, which widens trust |
| Repo identity | `var.github_repository`, default `eagudelo33-sketch/systemTurn`, validation `^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$`. The spec's "`repo`" means this variable | Literal in each trust (drift, and the spec forbids it) |
| Plan role `systemturn-github-plan` | `ReadOnlyAccess` attachment, plus inline `s3:ListBucket` on the bucket and `s3:GetObject` on `<bucket>/dev/*` and `<bucket>/prod/*`. `aws_iam_role_policy_attachments_exclusive = [ReadOnlyAccess]` | Inline policy only. Note that `ReadOnlyAccess` already grants this S3 read. The inline policy is kept as an explicit floor, so the role still works if `ReadOnlyAccess` is later swapped for a narrower policy |
| Apply role `systemturn-github-apply` | Inline `s3:ListBucket` on the bucket, `s3:GetObject`/`s3:PutObject` on `<bucket>/prod/*`, `s3:DeleteObject` on `<bucket>/prod/*.tflock`. Exclusive attachments `[]` | `<bucket>/*`. The spec's "`<bucket>/*`" is read as "objects of the bucket". The test asserts the prefix `arn:aws:s3:::<bucket>/` |
| State ARNs | Built from `var.state_bucket_name`, as in `main.tf`, so they are known at plan time | `aws_s3_bucket.state.arn`, which is unknown under mocks |
| Gating variable | `vars.AWS_PLAN_ENABLED == 'true'` (spec name) | `AWS_CI_ENABLED` |
| Static job | One job, `static`, using `github.token` (not `secrets.*`) for the tflint plugin download | Split jobs (more required checks), or `secrets.GITHUB_TOKEN`, which would break `static_job_has_no_credentials` |
| Tools in CI | `hashicorp/setup-terraform` (1.16.6, `terraform_wrapper: false`). tflint, trivy and actionlint come from release tarballs checked with `sha256sum -c` against a checksum pinned in `env`. `pipx install pre-commit==<ver>` | Third-party installer actions (more SHA pins and supply-chain surface) |
| Lock files | Roots use `init -lockfile=readonly`. After pre-commit and tests, `git diff --exit-code -- ':(glob)**/.terraform.lock.hcl'` catches the `--retry-once-with-cleanup` deletions | Diff check only |
| Expression injection | `secrets.*`/`vars.*` are passed only through `env:`. `run:` never interpolates `${{ github.event.* }}` | Inline interpolation |
| Prod apply | `workflow_dispatch` plus `if: github.event_name == 'workflow_dispatch' && github.ref == 'refs/heads/main'`, `environment: prod`, job concurrency `tf-prod` with no cancel. The Environment is also limited to `main` in the deployment-branch settings | Environment branch rule alone (single layer) |
| 503 test boot | `SpringApplicationBuilder` with `--server.port=0` (existing pattern) | `@SpringBootTest(RANDOM_PORT)`: it would cache a broken-DB context |

```hcl
# Shape of the plan trust (apply: sub = "repo:${var.github_repository}:environment:prod")
Condition = { StringEquals = {
  "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
  "token.actions.githubusercontent.com:sub" = "repo:${var.github_repository}:pull_request" } }
```

## Workflows

| File / job | Trigger and `if` | Permissions | Steps |
|---|---|---|---|
| both, top level | `pull_request` and `push: branches [main]` (`infra.yml` adds `workflow_dispatch`). No `paths`. `concurrency: ${{ github.workflow }}-${{ github.event.pull_request.number \|\| github.ref }}`, `cancel-in-progress: ${{ github.event_name == 'pull_request' }}` | `contents: read` | Every job has `timeout-minutes`. Every checkout uses `persist-credentials: false` |
| `ci.yml` `backend` | always | inherited | setup-java (temurin 17, maven cache), `./mvnw -B -ntp test -DrequireDocker=true` |
| `ci.yml` `frontend` | always | inherited | setup-node 22 (npm cache), `npm ci`, `lint`, `test`, `build` |
| `ci.yml` `e2e` | `ubuntu-latest` | inherited | java + node, `npm ci`, `npx playwright install --with-deps chromium`, `npm run e2e`, upload `playwright-report` and `test-results` with `if: always()`, kept 14 days |
| `infra.yml` `static` | always | inherited | install tools, `pre-commit run --all-files --show-diff-on-failure`, `terraform-test-all.sh`, lock-file diff check, `actionlint`, `test-check-conventions.sh` |
| `infra.yml` `plan` (matrix `envs/dev`, `envs/prod`) | `vars.AWS_PLAN_ENABLED == 'true' && github.event_name == 'pull_request' && github.event.pull_request.head.repo.full_name == github.repository` | `contents: read`, `id-token: write` | configure-aws-credentials (`secrets.AWS_PLAN_ROLE_ARN`, us-east-1), `init -lockfile=readonly -backend-config=bucket=$TF_STATE_BUCKET -backend-config=key=<env>/terraform.tfstate …use_lockfile/encrypt/region`, `plan -lock=false -input=false -no-color` |
| `infra.yml` `apply-prod` | see Prod apply | `contents: read`, `id-token: write` | `secrets.AWS_APPLY_ROLE_ARN`, init with key `prod/terraform.tfstate`, `apply -input=false -auto-approve` |

**Action pins.** Every SHA is TO-VERIFY at apply. None is written here, so none is invented. The actions are `actions/checkout`, `actions/setup-java`, `actions/setup-node`, `actions/upload-artifact`, `hashicorp/setup-terraform` and `aws-actions/configure-aws-credentials`. Tool versions are TO-VERIFY in the same way: tflint, trivy, actionlint and pre-commit, along with the 1.16.6 release date. For each one, pick the newest tag published on or before 2026-10-21 (at least 14 days old). Resolve it with `gh api repos/<o>/<r>/git/ref/tags/<tag>`, and dereference annotated tags through `git/tags/<sha>`. Write it as `uses: o/r@<40-hex> # <tag> (<date>)`.

## check-conventions.sh

- **Opt-in checks.** Add `OPT_IN_CHECKS=(no_app_files_touched)`. `DEFAULT_CHECKS` is `ALL_CHECKS` minus the opt-in checks. Explicit IDs still accept anything in `ALL_CHECKS`.
- **Helpers.** `wf_files` lists the tracked `.github/workflows/*.y*ml` files and fails when there are none. `job FILE ID` prints one job block with awk, from `^  ID:` to the next `^  [A-Za-z_-]+:`. `nocomment` runs `sed 's/[[:space:]]#.*//'`. Failures print `file:line`.
- **Hygiene checks.**
  - `actions_pinned_to_sha`: every `uses:` that is not `./` must match `@[0-9a-f]{40}`.
  - `top_level_permissions_declared`: the awk block from `^permissions:` to the next top-level key must exist and must not contain `write`. `write-all` is forbidden anywhere.
  - `pull_request_target_forbidden`: `grep -w` on uncommented lines.
  - `id_token_scoped_to_aws_jobs`: no `id-token` at top level, and every job holding `id-token: write` contains `aws-actions/configure-aws-credentials@`.
- **Repo-workflow checks**, one per spec scenario ID. Each is 2 to 4 grep lines over `job` output:
  - `static_job_has_no_credentials`, `static_job_runs_all_gates`, `no_path_filters_and_concurrency`
  - `plan_job_gated_for_same_repo`, `plan_uses_lock_false_and_secrets`
  - `apply_prod_only_manual_on_main`, `apply_role_isolated_to_apply_prod`
  - `backend_requires_docker`, `frontend_runs_lint_test_build`, `e2e_uploads_report_on_failure`
  - `fork_pr_reaches_only_credential_free_jobs`
  - `repo_identity_not_hardcoded`: `eagudelo33-sketch` appears only in `bootstrap/variables.tf`.
- **Hook trigger.** `.pre-commit-config.yaml` adds `\.github/workflows/` to the hook's `files`. The pre-push `terraform-test` hook calls `terraform-test-all.sh`, which also covers the two modules.

## Interfaces

- `terraform-test-all.sh [DIR…]` defaults to the five directories. It runs `init -backend=false` (adding `-lockfile=readonly` when a lock file is tracked), then `terraform test`, and pipes the output to `guard DIR`. `guard` fails with `FAIL DIR: 0 passed` unless the output matches `[1-9][0-9]* passed, 0 failed`.
- `--guard DIR FILE` runs the guard on a saved file, for the self-test.
- Bootstrap outputs: `github_oidc_provider_arn`, `github_plan_role_arn`, `github_apply_role_arn`.

## Testing Strategy

| Suite | Cases |
|---|---|
| `bootstrap/tests/github_oidc.tftest.hcl` | `oidc_provider_created` (plan). The four role runs use `command = apply` with `mock_provider "aws" { mock_resource "aws_iam_openid_connect_provider" { defaults = { arn = "arn:aws:iam::123456789012:oidc-provider/token.actions.githubusercontent.com" } } }` so `Federated` is known and compared, then `jsondecode(...assume_role_policy)`: `plan_role_trust_is_pull_request_only` (no `StringLike`, no `*` in `sub`), `apply_role_trust_is_prod_environment_only`, `plan_role_is_read_only`, `apply_role_is_state_only` (exclusive `policy_arns == []`). `repo_identity_is_single_variable` (`acme/other` in both subs), plus `expect_failures` for an invalid repo |
| `test-check-conventions.sh` fixtures | Staged workflows in the sandbox: tag pin fails and SHA passes; no permissions, `write-all` and `contents: write` fail while `contents: read` passes; `pull_request_target` fails and a commented-out one passes; top-level or non-AWS `id-token` fails and the AWS job passes; no workflow files fails (empty index). The default run never prints `no_app_files_touched`. With `-b main`, a commit, and a branch that touches `SystemTurn/backend/x.java`, selecting the check by ID fails and names the file, and an infra-only branch passes. `--guard` returns 1 on `0 passed` and 0 on `3 passed`. The nested-cwd case extends to `actions_pinned_to_sha` |
| `DatabaseOutageFullStackTest` (`com.systemturn.turnos`) | Boot with a closed port reserved at runtime and the same arguments as `HealthGroupsTest.arrancarConBaseCaida`, plus `hikari.connection-timeout=1000`. JDK `HttpClient` sends `POST /api/v1/turnos` and `GET /api/v1/turnos/dashboard`. Each returns 503. The body contains `"error":"SERVICIO_NO_DISPONIBLE"` and the exact message from `ConflictoHandlerTest`, and does not contain `jdbc`, `postgresql`, `127.0.0.1` or the port. Each response takes less than the timeout plus 2 s. Expected runtime is under 10 s, with no Docker. The field is `error`, not `code` |
| CI on the PR | Real-file checks (`repo_workflows_pass_hygiene`), `actionlint`, five directories reporting more than 0 passed |

## Threat Matrix

| Boundary | Applicability | Design response | Planned RED tests |
|---|---|---|---|
| Documentation-like paths | N/A: no executable-file classification | — | — |
| Git repository selection | Applicable: the checks resolve the root with `git rev-parse` | Same output from any cwd | Extend `same_result_from_env_dir` with `actions_pinned_to_sha` |
| Commit state | Applicable: the workflow checks read the index | Staged files count, and an empty set fails | Staged tag-pinned workflow is caught; no workflows fails |
| Push state | N/A: nothing pushes or composes refspecs | — | — |
| PR commands | N/A: no `gh` or PR command composition. PR-trigger safety is covered by the hygiene checks | — | — |

## File Changes and Size

| File | Action | Lines |
|---|---|---|
| `.github/workflows/ci.yml` | Create | ~95 |
| `.github/workflows/infra.yml` | Create | ~150 |
| `infra/bootstrap/github_oidc.tf` | Create | ~95 |
| `infra/bootstrap/variables.tf`, `outputs.tf` | Modify | +27 |
| `infra/bootstrap/tests/github_oidc.tftest.hcl` | Create | ~130 |
| `infra/scripts/check-conventions.sh` | Modify | +110 |
| `infra/scripts/test-check-conventions.sh` | Modify | +70 |
| `infra/scripts/terraform-test-all.sh` | Create | ~35 |
| `.pre-commit-config.yaml` | Modify | ~5 |
| `backend/turnos-api/src/test/java/com/systemturn/turnos/DatabaseOutageFullStackTest.java` | Create | ~85 |
| `backend/turnos-api/mvnw` | Mode `100755` if `git ls-files -s` shows `100644` | 0 |
| `docs/cicd.md`, `docs/README.md` | Create/Modify | ~90 / ~10 |
| `.tflint.hcl` | Modify (from 2026-11-06 only) | ~2 |

The total is about 900 lines, plus the OpenSpec artifacts, which is **over 800**. Split:

- **PR-A**: OIDC, `infra.yml`, the scripts, the hygiene and infra-workflow checks, the hook, `cicd.md` and the specs (about 700 lines).
- **PR-B**: `ci.yml`, the three `ci.yml` checks, the 503 test, `README.md` and `mvnw` (about 230 lines).

PR-A turns `no_app_files_touched` into an opt-in check first. A `single-pr` delivery would need an explicit `size:exception`.

## docs/cicd.md Outline

1. Overview: jobs and their credentials.
2. Bootstrap apply: `github_oidc.tf`, plus importing an existing GitHub OIDC provider if the account already has one.
3. Secrets `AWS_PLAN_ROLE_ARN`, `AWS_APPLY_ROLE_ARN`, `TF_STATE_BUCKET`, and variable `AWS_PLAN_ENABLED`.
4. `prod` Environment: the maintainer is the required reviewer and self-approves, "prevent self-review" is off (known tradeoff), and deployment branches are limited to `main`.
5. Branch protection: required checks `static`, `backend`, `frontend`, `e2e`. `plan` is optional.
6. Fork PRs and public plan logs.
7. Pin upkeep (the 14-day rule) and rollback.

## Migration / Rollout

Merge (workflows go green without AWS), then bootstrap apply, then secrets and the variable. The first PR plan confirms that the role can be assumed. Rollback: disable the workflows, or remove `github_oidc.tf` and apply.

## Open Questions

- [ ] Is `thumbprint_list` optional in 6.69.1? (Apply verifies the schema.)
- [ ] Does `terraform_tflint` in pre-commit-terraform v1.109.3 run `tflint --init` itself? If not, add an explicit init step.
- [ ] Does the committed root lock file carry a `linux_amd64` `h1:` hash? If not, `-lockfile=readonly` or the diff check fails. Fix by running `terraform providers lock -platform=linux_amd64 -platform=windows_amd64`.
