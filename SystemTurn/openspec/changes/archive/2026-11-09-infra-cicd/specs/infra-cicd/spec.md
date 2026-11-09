# Delta for infra-cicd (NEW)

Scope: GitHub Actions workflows (`.github/workflows/ci.yml`, `infra.yml`) and the AWS OIDC trust in `SystemTurn/infra/bootstrap/github_oidc.tf`. No API, backend or frontend contract change occurs.

Verification: offline only. OIDC scenarios run via `terraform test` in `infra/bootstrap/tests/` with `mock_provider "aws"` (run-block names are scenario IDs). Workflow scenarios are YAML checks run by `actionlint` and the `check-conventions.sh` self-test, which parse the workflow files.

## ADDED Requirements

### Requirement: OIDC provider and plan role trust
Bootstrap MUST create the GitHub OIDC provider (`token.actions.githubusercontent.com`, client id `sts.amazonaws.com`) and a plan role. The repository identity MUST be one variable, defaulting to `eagudelo33-sketch/systemTurn`. The plan role trust policy MUST allow only `sts:AssumeRoleWithWebIdentity` with `aud` equal to `sts.amazonaws.com` and `sub` equal to `repo:<repo>:pull_request` (exact match, case-sensitive; no wildcard). The plan role MUST attach `ReadOnlyAccess` plus a policy limited to reading the state bucket, and MUST NOT grant any write action.

#### Scenario: oidc_provider_created
- GIVEN the bootstrap module planned with mocks
- WHEN `oidc_provider_created` runs
- THEN one `aws_iam_openid_connect_provider` has the GitHub URL and client id list `["sts.amazonaws.com"]`

#### Scenario: plan_role_trust_is_pull_request_only
- GIVEN the default repo variable
- WHEN `plan_role_trust_is_pull_request_only` runs
- THEN the trust `sub` is exactly `repo:eagudelo33-sketch/systemTurn:pull_request` and `aud` is `sts.amazonaws.com`
- AND the `sub` condition uses `StringEquals` and contains no `*`

#### Scenario: repo_identity_is_single_variable
- GIVEN `repo` set to `acme/other`
- WHEN `repo_identity_is_single_variable` runs
- THEN both role trusts reference `acme/other` and no tracked bootstrap file hardcodes `eagudelo33-sketch` outside the variable default

#### Scenario: plan_role_is_read_only
- GIVEN the plan role policies
- WHEN `plan_role_is_read_only` runs
- THEN `ReadOnlyAccess` is attached
- AND the inline policy allows only `s3:GetObject` and `s3:ListBucket` on the state bucket
- AND no statement allows `s3:PutObject`, `s3:DeleteObject` or `iam:*`

### Requirement: Apply role scoped to the prod environment
Bootstrap MUST create an apply role whose trust `sub` is exactly `repo:<repo>:environment:prod` with `aud` `sts.amazonaws.com`. Its permissions MUST be limited to the prod state: list the bucket, get/put objects under `<bucket>/prod/`, and delete only the prod lock object (`*.tflock`). It MUST NOT include broader service access.

#### Scenario: apply_role_trust_is_prod_environment_only
- GIVEN the default repo variable
- WHEN `apply_role_trust_is_prod_environment_only` runs
- THEN the trust `sub` is exactly `repo:eagudelo33-sketch/systemTurn:environment:prod`
- AND it differs from the plan role `sub`

#### Scenario: apply_role_is_state_only
- GIVEN the apply role policies
- WHEN `apply_role_is_state_only` runs
- THEN every statement resource is the state bucket or under `<bucket>/prod/`
- AND `s3:DeleteObject` applies only to `*.tflock` objects
- AND only `s3` actions appear, and no managed policy is attached

### Requirement: Credential-free static checks in infra.yml
`infra.yml` MUST run on `pull_request` and `push` to `main` with no path filters. A static job MUST NOT assume any AWS role or read secrets. It MUST run: pre-commit on all files, then fail if any `.terraform.lock.hcl` changed; `terraform test` in `infra/bootstrap`, `infra/envs/dev`, `infra/envs/prod`, `infra/modules/network` and `infra/modules/app`, failing when any directory reports 0 passed; tflint; trivy config; and actionlint.

#### Scenario: static_job_has_no_credentials
- GIVEN `infra.yml`
- WHEN the check parses the static job
- THEN it has no `id-token` permission, no `aws-actions/configure-aws-credentials` step and no `secrets.` reference

#### Scenario: static_job_runs_all_gates
- GIVEN `infra.yml`
- WHEN the check lists static job steps
- THEN pre-commit with all files, a lock-file-unchanged check, `terraform test` for the five named directories, tflint, trivy and actionlint all appear

#### Scenario: zero_passed_directory_fails
- GIVEN a directory whose `terraform test` output reports `0 passed`
- WHEN the zero-passed guard script runs on that output
- THEN it exits non-zero naming the directory

#### Scenario: no_path_filters_and_concurrency
- GIVEN `ci.yml` and `infra.yml`
- WHEN the check parses their triggers
- THEN neither declares `paths` or `paths-ignore`, and each declares a `concurrency` group

### Requirement: Plan on same-repo pull requests
`infra.yml` MUST contain a `plan` job that runs only for pull requests whose head repository equals the base repository and only when repository variable `vars.AWS_PLAN_ENABLED` is `'true'`. It MUST assume the plan role using `secrets.AWS_PLAN_ROLE_ARN`, run `terraform plan -lock=false`, and hold `id-token: write`. Role ARNs and the state bucket MUST come from secrets, never literals.

#### Scenario: plan_job_gated_for_same_repo
- GIVEN `infra.yml`
- WHEN the check reads the `plan` job `if`
- THEN it requires head repo equal to `github.repository` and `vars.AWS_PLAN_ENABLED == 'true'`

#### Scenario: plan_uses_lock_false_and_secrets
- GIVEN the `plan` job
- WHEN the check scans its steps
- THEN every plan command has `-lock=false`
- AND the role ARN and bucket reference `secrets.*` and no literal ARN or 12-digit ID appears

### Requirement: Manual production apply
`infra.yml` MUST define `apply-prod` that runs only on `workflow_dispatch` against `refs/heads/main`, declares `environment: prod`, and assumes the apply role using `secrets.AWS_APPLY_ROLE_ARN`. No other job MAY reference `environment: prod` or the apply role.

#### Scenario: apply_prod_only_manual_on_main
- GIVEN `infra.yml`
- WHEN the check reads `apply-prod`
- THEN its `if` requires `github.event_name == 'workflow_dispatch'` and `github.ref == 'refs/heads/main'`
- AND it declares `environment: prod`

#### Scenario: apply_role_isolated_to_apply_prod
- GIVEN all jobs in both workflows
- WHEN the check searches for `environment: prod` and `AWS_APPLY_ROLE_ARN`
- THEN matches occur only in `apply-prod`

### Requirement: Application CI workflow
`ci.yml` MUST run on `pull_request` and `push` to `main` with jobs: backend (`./mvnw test -DrequireDocker=true`, with Docker available), frontend (lint, test, build), and e2e on Linux (`ubuntu-*`) that uploads the Playwright report even when tests fail (`if: ${{ !cancelled() }}` or `if: always()`). `ci.yml` MUST NOT use secrets or `id-token`.

#### Scenario: backend_requires_docker
- GIVEN `ci.yml`
- WHEN the check reads the backend job
- THEN its test command includes `-DrequireDocker=true`

#### Scenario: frontend_runs_lint_test_build
- GIVEN `ci.yml`
- WHEN the check reads the frontend job
- THEN steps for lint, test and build all exist

#### Scenario: e2e_uploads_report_on_failure
- GIVEN `ci.yml`
- WHEN the check reads the e2e job
- THEN it runs on a Linux runner and its upload-artifact step has `if: ${{ !cancelled() }}` or `if: always()`

### Requirement: Fork pull requests get credential-free checks only
Every job that holds `id-token: write`, reads a `secrets.*` AWS value, or targets `environment: prod` MUST be unreachable from a fork pull request. Jobs without those (static checks, `ci.yml` jobs) MUST run for forks.

#### Scenario: fork_pr_reaches_only_credential_free_jobs
- GIVEN each job in `infra.yml` and `ci.yml`
- WHEN the check classifies jobs by credential use
- THEN each credential-using job has an `if` excluding forks or a non-PR trigger
- AND the static and `ci.yml` jobs have no such condition

## Operational notes (NOT scenarios)

- Bootstrap apply, creating secrets `AWS_PLAN_ROLE_ARN`, `AWS_APPLY_ROLE_ARN`, `TF_STATE_BUCKET`, and variable `AWS_PLAN_ENABLED`.
- `prod` Environment reviewers; maintainer self-approval with "prevent self-review" off is a documented tradeoff.
- First real assume-role and plan; Linux provider lock hashes; branch-protection required checks.
- Public plan logs accepted while `prod` is empty.
