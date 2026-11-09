# CI/CD for infrastructure (GitHub Actions + AWS OIDC)

Pull requests are checked by `.github/workflows/infra.yml` **without any AWS credentials**. A read-only `terraform plan` runs on same-repo PRs once you wire AWS. `prod` is applied only by hand, from `main`, after you approve it in GitHub. No long-lived AWS keys exist anywhere: GitHub exchanges a short-lived OIDC token for a role.

Rules: the `infra-cicd` spec (linked from the specs index once archived) and [infra-repo-conventions](../openspec/specs/infra-repo-conventions/spec.md). Account setup first: [infra-onboarding.md](infra-onboarding.md).

## 1. What runs and which credentials it has

| Job | Runs when | AWS access |
|-----|-----------|------------|
| `static` | Every PR (forks too) and every push to `main` | None. Uses only the built-in `github.token` for the tflint plugin download |
| `plan` (`dev`, `prod`) | Same-repo PR **and** repository variable `AWS_PLAN_ENABLED` is `true` | Role `systemturn-github-plan`: `ReadOnlyAccess` + read of the state bucket. Runs with `-lock=false`, so it cannot write state |
| `apply-prod` | Manual run (`workflow_dispatch`) on `main`, after approval | Role `systemturn-github-apply`: only the `prod/` state objects |

`ci.yml` (application CI) has three jobs with no secrets and no `id-token`, so they run for forks too: `backend` (`./mvnw test -DrequireDocker=true`), `frontend` (lint, test, build) and `e2e` (Playwright on Chromium, report and traces uploaded even on failure).

`static` runs pre-commit on all files (fmt, validate, tflint, trivy, conventions), fails if a `.terraform.lock.hcl` changed, runs `terraform test` in all five directories (a directory reporting `0 passed` fails), and runs actionlint. Every tool is pinned: actions by commit SHA, tflint, trivy and actionlint by sha256.

## 2. Bootstrap apply (once, by hand)

The trust lives in `infra/bootstrap/github_oidc.tf`. CI never applies it.

1. `cd SystemTurn/infra/bootstrap && terraform apply` with your own credentials.
2. Read the outputs: `github_plan_role_arn`, `github_apply_role_arn`, `state_bucket_name`.

The repository identity is one variable, `github_repository` (default `eagudelo33-sketch/systemTurn`). If you fork or rename, change it there and nowhere else.

An AWS account has **one** GitHub OIDC provider. If it already exists (`token.actions.githubusercontent.com`), apply fails with `EntityAlreadyExists`. Import it first: `terraform import aws_iam_openid_connect_provider.github arn:aws:iam::123456789012:oidc-provider/token.actions.githubusercontent.com`.

## 3. Secrets and variable

Create them under Settings, Secrets and variables, Actions.

| Name | Kind | Value |
|------|------|-------|
| `AWS_PLAN_ROLE_ARN` | Secret | `github_plan_role_arn` output, like `arn:aws:iam::123456789012:role/systemturn-github-plan` |
| `AWS_APPLY_ROLE_ARN` | Secret | `github_apply_role_arn` output |
| `TF_STATE_BUCKET` | Secret | `state_bucket_name` output |
| `AWS_PLAN_ENABLED` | Variable | `true` to turn `plan` on. Leave unset until AWS is wired |

## 4. The `prod` Environment

Settings, Environments, New environment `prod`:

- Required reviewers: the maintainer.
- Deployment branches: **selected branches, `main` only**. The workflow also checks `github.ref == 'refs/heads/main'`, so two layers apply.
- "Prevent self-review": **off**. With one maintainer, turning it on would block every deploy. Known tradeoff: the approval is a deliberate pause, not a second person. Turn it on when a second reviewer exists.

The apply role trusts only `repo:<repo>:environment:prod`, so no other job can assume it.

## 5. Branch protection

Require these checks on `main`: `static`, `backend`, `frontend` and `e2e` (the last three come from `ci.yml`). Do **not** require `plan` yet: it is skipped for forks and until `AWS_PLAN_ENABLED` is set (GitHub counts a job skipped by its `if` as passing, so requiring it proves nothing), and once enabled it would make every merge depend on AWS being reachable.

## 6. Fork PRs and plan logs

A fork PR gets only the credential-free jobs: `plan` is skipped for it and the workflow never uses `pull_request_target`. For same-repo PRs, plan output is visible in public logs. That is acceptable while `prod` is empty; re-evaluate before it holds real resources.

## 7. Pin upkeep and rollback

- Bump a pin only to a release at least **14 days old**. Resolve the commit SHA with `gh api repos/<owner>/<repo>/git/ref/tags/<tag>` (dereference annotated tags), then update the `# vX.Y.Z (date)` comment. For tools, update the version and sha256 together.
- Rollback: disable the workflow in the Actions tab, or delete `github_oidc.tf` and apply bootstrap.

## Follow-up (not done here)

| Item | Where |
|------|-------|
| Wire `prod` for real (the `plan` matrix already includes it) | `infra-prod-wiring` |
| First real assume-role and plan; confirm Linux provider hashes | After secrets are set |