# Design: Infra Foundation

## Technical Approach

Three independent Terraform roots under `SystemTurn/infra/` (`bootstrap`, `envs/dev`, `envs/prod`), no shared modules yet. Every root uses the same byte-identical `versions.tf` with a partial `backend "s3" {}`. All configuration needed by tests is plain input (no data sources), so `terraform init -backend=false && terraform test` with `mock_provider "aws"` runs offline with no credentials. Repo hygiene is enforced by one bash script based on `git` plumbing, plus pre-commit hooks.

## Architecture Decisions

| Topic | Choice | Rejected (tradeoff) |
|---|---|---|
| Version pins | Byte-identical `versions.tf` copied into each root (`~> 1.11`, `aws ~> 6.69`, `backend "s3" {}`); the script checks they are identical | Symlinks (Git on Windows checks them out as plain text by default). Shared module (roots still need `required_providers`). Terragrunt (adds another tool) |
| Account ID | `state_bucket_name` variable kept in gitignored tfvars; the example uses the `systemturn-tfstate-<account-id>` placeholder | `data.aws_caller_identity` (needs `override_data` in tests, and the real ID leaks into plans and outputs) |
| Bootstrap local→remote | Committed code is the **final** state (`backend "s3" {}`). Phase 1 uses a gitignored `backend_override.tf` (`backend "local" {}`). Phase 2 deletes it and runs `init -migrate-state -backend-config=backend.hcl` | Commenting the backend in and out (the committed file changes between phases and is easy to get wrong). Adding the backend later (a fresh clone would then disagree with the real state) |
| Backend values | Partial config: gitignored `backend.hcl`, committed `backend.hcl.example` (bucket placeholder, `key`, `region`, `use_lockfile = true`, `encrypt = true`) | Values in code (the backend block cannot use variables, and the bucket name would be exposed) |
| Bucket policy | `jsonencode()`, with the ARN built as `"arn:aws:s3:::${var.state_bucket_name}"` so the policy is known at plan time and can be asserted | `aws_iam_policy_document` (the mocked data source returns random JSON). `aws_s3_bucket.state.arn` (may be unknown at plan time under mocks) |
| Sub-resource refs | `bucket = aws_s3_bucket.state.bucket` (an input, known at plan time) | `.id` (computed, so unknown at plan time) |
| SSE | AES256 (SSE-S3) | KMS CMK (out of scope; costs $1/month plus API calls) |
| Env identity | `local.environment = "dev"` or `"prod"` hardcoded per root | A variable (easy to plan prod with dev values) |
| Env content | No resources. Provider with `default_tags`, validated variables, outputs `environment`, `region`, `default_tags` | `data.aws_caller_identity` (adds nothing offline) |
| Convention check | `SystemTurn/infra/scripts/check-conventions.sh` (bash, runs in Git Bash and Linux) using `git check-ignore`, `git check-attr`, `git ls-files`, `git grep` | A Terraform test (cannot read Git metadata). PowerShell (does not run on Linux CI) |
| Secret scan | pre-commit-hooks `detect-private-key`, `detect-aws-credentials --allow-missing-credentials`, plus a script check for 12-digit IDs | gitleaks (one more tool for little gain at this size) |
| `.gitattributes` scope | Only Terraform/HCL/shell patterns get `eol=lf` | `* text=auto` (would renormalize existing Java and JS files into a large diff) |
| terraform-docs | Config only, run on demand to stdout. No hook, no committed READMEs | `inject` mode (adds generated lines and review noise) |

## Data Flow

    dev laptop (SSO session)
      bootstrap: phase1 local state ──apply──> S3 bucket + budget
                 phase2 init -migrate-state ──> s3://<bucket>/bootstrap/terraform.tfstate (+ .tflock)
      envs/dev|prod: init -backend-config=backend.hcl ──> s3://<bucket>/<env>/terraform.tfstate
    offline/CI: init -backend=false → fmt/validate/tflint/trivy/test(mock) → check-conventions.sh

## File Changes (lines: authored / generated)

| File | Action | ~Lines |
|---|---|---|
| `.gitignore` | Modify: `.terraform/`, `*.tfstate`, `*.tfstate.*`, `*.tfplan`, `crash*.log`, `*.tfvars`, `*.tfvars.json`, `override.tf`, `*_override.tf(.json)`, `SystemTurn/infra/**/backend.hcl`, `.terraformrc`. Lock files stay tracked | +16 |
| `.gitattributes` | Create: `*.tf`, `*.hcl` (covers tests and lock files), `*.tfvars.example`, `*.hcl.example`, `*.sh` → `text eol=lf` | 7 |
| `.tflint.hcl` | `terraform` plugin with the `recommended` preset; `aws` plugin `version = "0.50.0"` | 12 |
| `trivy.yaml` | `severity: [HIGH, CRITICAL]` (per spec), `exit-code: 1`, misconfig scanner | 8 |
| `.terraform-docs.yml` | `markdown table`, sorted, no output file | 10 |
| `.pre-commit-config.yaml` | `pre-commit-hooks` (merge-conflict, private-key, aws-creds), `pre-commit-terraform` (fmt, validate, tflint `--config=__GIT_WORKING_DIR__/.tflint.hcl`, trivy), local `check-conventions` (pre-commit), local `terraform test` (`stages: [pre-push]`). Every hook is scoped with `files: ^SystemTurn/infra/` where relevant | 35 |
| `SystemTurn/openspec/config.yaml` | Modify: `testing.infra` block; append infra to the apply/verify commands | +16 |
| `SystemTurn/infra/scripts/check-conventions.sh` | Create | 40 |
| `infra/bootstrap/versions.tf` (identical copy in each env) | Create | 14 |
| `infra/bootstrap/providers.tf` | `region`, `default_tags` (Environment=`shared`) | 14 |
| `infra/bootstrap/variables.tf` | `region`, `state_bucket_name`, `alert_emails`, `budget_amount_usd=30`, `owner`, plus validations | 45 |
| `infra/bootstrap/main.tf` | Bucket (`force_destroy=false`, `prevent_destroy`), versioning, SSE, PAB, ownership, TLS-deny policy (`depends_on` PAB), lifecycle (`filter {}`, noncurrent 90d, abort multipart 7d) | 85 |
| `infra/bootstrap/budget.tf` | `aws_budgets_budget` COST/MONTHLY, `dynamic "notification"` over a local list of 4 thresholds | 35 |
| `infra/bootstrap/outputs.tf` | bucket name/arn, budget name | 12 |
| `infra/bootstrap/{terraform.tfvars,backend.hcl}.example` | Create | 13 |
| `infra/bootstrap/tests/bootstrap.tftest.hcl` | Create | 90 |
| `infra/envs/{dev,prod}/` `versions.tf`, `providers.tf`, `variables.tf`, `main.tf` (locals and outputs), `backend.hcl.example`, `tests/env.tftest.hcl` | Create | 2 × 97 |
| `.terraform.lock.hcl` ×3 (`providers lock -platform=windows_amd64 -platform=linux_amd64`) | Generated | 75 gen |
| `SystemTurn/docs/infra-onboarding.md`, `docs/README.md` link | Create/Modify | 91 |

**Total ≈ 815 (≈740 authored + 75 generated lock).** This is at the edge of the 800 budget. Levers if it overruns: drop `.terraform-docs.yml`, shorten the runbook to 70 lines, or merge `providers.tf` into `main.tf` in the envs.

## Interfaces / Contracts

```hcl
variable "alert_emails" {
  type = list(string)
  validation {
    condition = length(var.alert_emails) > 0 && alltrue([for e in var.alert_emails :
      can(regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", e))])
    error_message = "alert_emails must contain at least one valid email."
  }
}
# Other validations: state_bucket_name ^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$, budget_amount_usd > 0, region ^[a-z]{2}(-[a-z]+)+-[0-9]$
locals { thresholds = [{ t = "ACTUAL", p = 50 }, { t = "ACTUAL", p = 80 }, { t = "ACTUAL", p = 100 }, { t = "FORECASTED", p = 100 }] }
```

Tags: `{Project="systemturn", Environment=<env>, ManagedBy="terraform", Owner=var.owner}` (`owner` defaults to `"systemturn"`).

## Testing Strategy

| Root | `run` block (`command = plan`, file-level `mock_provider "aws" {}` + valid `variables {}`) | Proves |
|---|---|---|
| bootstrap | `state_bucket_hardened` | bucket name = var, `force_destroy=false`, versioning `Enabled`, SSE `AES256`, all 4 PAB flags true, `BucketOwnerEnforced`, sub-resource `bucket` = var |
| bootstrap | `tls_only_policy` | `jsondecode(policy).Statement` has Deny with `aws:SecureTransport = "false"` on the bucket and `/*` |
| bootstrap | `lifecycle_rules` | noncurrent expiration 90, abort multipart 7 |
| bootstrap | `budget_thresholds` | COST, MONTHLY, `limit_amount = "30"`, set of `type-threshold` = {ACTUAL-50, ACTUAL-80, ACTUAL-100, FORECASTED-100}, subscribers = `alert_emails` |
| bootstrap | `rejects_bad_email`, `rejects_empty_emails`, `rejects_bad_bucket_name`, `rejects_zero_budget` | `expect_failures = [var.x]` |
| envs | `defaults` / `region_override` / `rejects_bad_region` | outputs `environment`, `region`, the exact `default_tags` map; validation |
| repo | `check-conventions.sh` | ignored: sample `terraform.tfstate`, `x.tfvars`, `backend.hcl`, `.terraform/`, `p.tfplan`, `backend_override.tf`; NOT ignored: `*.tfvars.example`, `backend.hcl.example`, `.terraform.lock.hcl`; `check-attr eol=lf` on tf/hcl/tftest/lock; no tracked or staged tfstate/tfvars; identical `versions.tf` ×3; `prevent_destroy = true` present; each lock file has ≥2 `h1:` hashes; no `\b[0-9]{12}\b` in tracked infra files except locks |
| static | fmt `-check -recursive`, validate, tflint, `trivy config SystemTurn/infra` | Gates. Expected trivy ignores go inline with a reason: S3 access logging (would need a second bucket) and CMK encryption (out of scope). Confirm the exact IDs at apply time |

Not provable offline (listed in the runbook checklist): `prevent_destroy` behavior, real lock contention, budget email delivery.

## Threat Matrix

| Boundary | Applicability | Design response | RED test |
|---|---|---|---|
| Documentation-like paths | N/A: the script classifies no executables | — | — |
| Git repository selection | Applicable | The script runs `cd "$(git rev-parse --show-toplevel)"` and uses only repo-relative paths | Run from `SystemTurn/infra/envs/dev` gives the same result |
| Commit state | Applicable | The forbidden-file check uses `git ls-files` (index), so staged files count | Stage a dummy `x.tfstate` with `git add -f`: the script exits 1 |
| Push state / PR commands | N/A: no push or PR automation | — | — |

## Migration / Rollout

1. Phase 1 (user): write `backend_override.tf` and `terraform.tfvars`, run `init`, then `apply`.
2. Phase 2 (user): write `backend.hcl`, delete the override, run `init -migrate-state -backend-config=backend.hcl`, then delete the local state.
3. Envs: `init -backend-config=backend.hcl`. Dev may be applied. Prod stays plan-only until `infra-app`.

Runbook outline (`docs/infra-onboarding.md`): tools; account plus root MFA; Identity Center (us-east-1) with an AdministratorAccess 1h session; CLI v2 ≥2.22 `configure sso`; bootstrap phases 1 and 2; env init and plan; 100% alert response (manual `destroy` of env roots); bootstrap retirement; real-apply verification checklist (`aws s3api get-bucket-*`).

## Open Questions

- [ ] Spike (first apply task): confirm `terraform test` under `-backend=false` ignores the partial backend, and that mocked `.bucket` and policy values are known at plan time on 1.16.6.
- [ ] Pin the `pre-commit-terraform` and `pre-commit-hooks` revs to the newest tags that are ≥14 days old (verify with `git ls-remote --tags`). Not verified in this phase.

## Sources

[S3 backend](https://developer.hashicorp.com/terraform/language/backend/s3) · [Override files](https://developer.hashicorp.com/terraform/language/files/override) · [Tests](https://developer.hashicorp.com/terraform/language/tests) · [Mocking](https://developer.hashicorp.com/terraform/language/tests/mocking) · [providers lock](https://developer.hashicorp.com/terraform/cli/commands/providers/lock) · [aws_budgets_budget](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/budgets_budget) · [pre-commit-terraform](https://github.com/antonbabenko/pre-commit-terraform) · exploration.md
