# Exploration: infra-foundation

Terraform/AWS foundation for SystemTurn: remote state with locking, network, budget, environment layout, and the conventions later infra changes build on. Researched 2026-11-03. Out of scope: app hosting (infra-app), CI/OIDC (infra-cicd).

## Current state

- Repo root holds `.git` and `SystemTurn/`. No Terraform, no `infra/`.
- Root `.gitignore` does not ignore `.terraform/`, `*.tfstate*`, `*.tfvars`, `*.tfplan`.
- No `.gitattributes`; previous changes hit CRLF/LF drift (matters for `.tf` and lock files on Windows).
- Local: Terraform 1.16.6, Git Bash. No AWS CLI, no confirmed AWS account. tflint/trivy/terraform-docs/pre-commit availability unchecked.
- `openspec/config.yaml` has no infra test block (runner `terraform test` + static gates).

## Affected areas (new unless noted)

`SystemTurn/infra/bootstrap/` (state bucket, account guardrails) · `SystemTurn/infra/modules/network/` (+ tests) · `SystemTurn/infra/modules/budget/` (or budget inside bootstrap) · `SystemTurn/infra/envs/{dev,prod}/` · `.gitignore` (edit) · `.gitattributes` (`eol=lf` for `*.tf`, `*.hcl`, `*.tftest.hcl`) · `.tflint.hcl`, `.pre-commit-config.yaml`, `.terraform-docs.yml` · `openspec/config.yaml` (edit) · `docs/` AWS onboarding runbook.

## Findings

### 1. Repo layout

| Approach | Pros | Cons |
|----------|------|------|
| **A. `infra/{bootstrap,modules,envs/dev,envs/prod}`, one root + state key per env (recommended)** | Explicit, isolated, standard portfolio pattern | Some repeated boilerplate |
| B. Workspaces | No duplication | Backend/provider can't differ per workspace; easy to apply to the wrong env |
| C. Terragrunt | DRY | Extra tool; hides raw Terraform skill |

`bootstrap/` is its own root: `prevent_destroy` on the state bucket would break any combined destroy. Env roots grow over time (infra-app adds ECS/RDS to the same root).

### 2. Remote state

- `use_lockfile` (S3 native locking) is GA since Terraform **1.11.0** (2025-02-27). DynamoDB locking arguments are **deprecated**.
- Lock object `<key>.tflock` needs `s3:GetObject`, `s3:PutObject`, `s3:DeleteObject`. Versioning strongly recommended.
- Bucket hardening: versioning; SSE (AES256, KMS optional); all four Block Public Access; `BucketOwnerEnforced`; deny `aws:SecureTransport = false`; lifecycle expiring noncurrent versions and aborting incomplete uploads; `prevent_destroy = true`, `force_destroy = false`.
- Keys: `bootstrap/terraform.tfstate`, `dev/terraform.tfstate`, `prod/terraform.tfstate`. Backend blocks can't use variables → `backend.hcl` per root.
- Bucket name `systemturn-tfstate-<account-id>` (account IDs are not secret; user to confirm for a public repo).

| Bootstrap | Pros | Cons |
|-----------|------|------|
| **A. Apply `bootstrap/` with local state, then add backend + `init -migrate-state` (recommended)** | Pure Terraform, reproducible | Two-phase manual apply |
| B. Bucket via CLI/console | Simple | Not IaC, not testable |
| C. Bootstrap state local forever | No migration | Lost with the laptop |

### 3. Version pinning

- AWS provider major 6; latest 6.71.0 (2026-10-28). Newest release ≥14 days old: **6.69.1**.
- Terraform latest 1.17.1; local 1.16.6 is fine.
- `required_version = "~> 1.11"` (floor from `use_lockfile`), `aws = "~> 6.69"`; lock file pins the exact version.
- Commit `.terraform.lock.hcl` per root, generated with `terraform providers lock -platform=windows_amd64 -platform=linux_amd64` (plain `init` on Windows records only local hashes → breaks Linux CI).

### 4. Region

| Region | Notes |
|--------|-------|
| **us-east-1 (recommended)** | NAT $0.045/h + $0.045/GB (official). CloudFront ACM certs must be in us-east-1 (needed by infra-app). |
| sa-east-1 | NAT ~$0.093/h (aggregator, unverified). Latency from Colombia not clearly better than Virginia (unmeasured). |
| us-east-2 | Same prices; still needs a us-east-1 alias for CloudFront certs. |

Region as a variable.

### 5. VPC design

- 2 AZs, three tiers: public (ALB, NAT), private-app (Fargate), data (RDS, no internet route).
- CIDRs: dev `10.0.0.0/16`, prod `10.1.0.0/16` (peerable later). Free S3 gateway endpoint. Flow logs off by default.

| Egress | ~Monthly | Notes |
|--------|----------|-------|
| No NAT (dev), tasks in public subnets with SG allowing only ALB | ~$0 NAT + ~$3.65/task public IPv4 | Cheapest; dev ≠ prod |
| Single NAT | ~$32.85 + ~$3.65 EIP + data | Prod-like topology |
| NAT per AZ (prod) | ~$65.70 + 2 EIPs + data | HA |
| Interface endpoints (ECR api/dkr, Logs, Secrets Manager) | ~$58 (unverified) | More than one NAT at this scale |
| fck-nat (t4g.nano) | ~$3–5 | 5 Gbps cap, not fully HA, no Terraform module found |

Hand-write `modules/network` (~150–200 lines) instead of `terraform-aws-modules/vpc` (100+ resource types, ~200 inputs; low showcase value). Inputs `nat_mode` (`none` / `single` / `per_az`) and `create_s3_endpoint`. Dev `none`, prod `per_az`. Public vs private task subnets is an infra-app decision.

### 6. Budget and tagging

- Budgets monitoring is free; `aws_budgets_budget` supports `notification` blocks (ACTUAL, FORECASTED) and tags.
- Monthly COST budget ~$30–50 (variable); alerts ACTUAL 50/80/100%, FORECASTED 100%. Email via gitignored tfvars (public repo).
- Account-level budget in `bootstrap/` (survives env teardown). Cost Anomaly Detection optional (~40 lines). Per-env tag-filtered budgets need cost-allocation tag activation — defer.
- `default_tags`: Project, Environment, ManagedBy, Owner (watch for overlapping resource-level tags causing diffs).

### 7. AWS authentication

- IAM Identity Center + `aws configure sso` → `aws sso login --profile <p>` → `AWS_PROFILE`. PKCE default from CLI v2.22.0.
- No IAM user keys. No `profile` in the provider block (keeps the later OIDC pipeline unchanged).
- `PowerUserAccess` likely lacks IAM (inference) — infra-app needs ECS roles → `AdministratorAccess` with a short session for a solo sandbox, or a custom permission set.

Manual user steps before first apply: create account; root MFA, no root keys; enable Identity Center in us-east-1 (home region hard to change); user + permission set + assignment; AWS CLI v2 ≥ 2.22; `aws configure sso`, `aws sso login`, `aws sts get-caller-identity`; choose budget email and limit.

### 8. Quality gates and tests

- Static (no AWS): `terraform fmt -check -recursive`, `terraform validate` (after `init -backend=false`), `tflint` + `tflint-ruleset-aws` (v0.50.0), `trivy config` (tfsec is now part of Trivy — don't adopt tfsec), checkov optional, `terraform-docs`, `pre-commit` (pre-commit-terraform hooks).

| Tests | Pros | Cons |
|-------|------|------|
| **Native `terraform test` with `command = plan` + `mock_provider "aws"` (recommended)** | No Go, no credentials, no spend; `expect_failures` for validation; fits RED→GREEN | Mocks don't prove AWS behavior |
| Terratest (Go) | Real integration | Real apply, cost, cleanup |

- `terraform test` needs ≥1.6, `mock_provider` ≥1.7. Whether plan mode touches AWS under mocks: confirm in a first spike task.
- Gotchas (unverified on 1.16): mocked data sources return generated values → use `jsonencode()` for policies, `override_data` for AZ names; assert counts/CIDRs/route targets, not computed IDs; backend config and `prevent_destroy` are not testable with mocks.
- Strict TDD: each Given/When/Then scenario → one `run` block (e.g. `nat_mode = "none"` → 0 NAT gateways, 2+2+2 subnets).

### 9. Cost and teardown

| Component | Dev cost |
|-----------|----------|
| State bucket | < $0.05 |
| Budget (+ anomaly detection) | $0 |
| VPC, subnets, IGW, routes, S3 gateway endpoint | $0 |
| Single NAT (if enabled) | ~$36.5 + $0.045/GB |
| NAT per AZ (prod, if enabled) | ~$73 + data |

Dev foundation with `nat_mode = none` ≈ $0/month. Prod can be planned without applying. `terraform destroy` on `envs/*` is safe; retiring bootstrap is deliberate (empty versions, remove `prevent_destroy`).

### 10. Size

| Piece | ~Lines |
|-------|--------|
| bootstrap | 210 |
| modules/network (+ tests, README) | 450 |
| modules/budget (+ tests) | 130 |
| envs/dev + envs/prod | 120 |
| lock files (generated, tracked) | 75 |
| tooling config | 105 |
| docs + runbook | 150 |
| **Total** | **~1,240** (~1,100 without lock files) |

Over the 800 budget. Split suggestion: **Slice 1** (~550–650): conventions/gates, bootstrap (state + budget), env skeletons (backend, providers, tags), runbook. **Slice 2** (~550–650): `modules/network` + tests + wiring into dev/prod.

## Recommendation

`SystemTurn/infra/` with separate `bootstrap/` and one root per env · S3 backend with `use_lockfile`, hardened bucket, local-then-migrate bootstrap · `~> 1.11` / `aws ~> 6.69` (locked 6.69.1), multi-platform locks · us-east-1 · hand-written 3-tier 2-AZ VPC module with `nat_mode` (dev `none`, prod `per_az`) · account budget + `default_tags` · Identity Center auth, no profile in code · fmt/validate/tflint/trivy/terraform-docs via pre-commit + native mocked `terraform test` · split into two slices.

## Risks

- No AWS account/CLI yet: bootstrap apply and migration can't be verified inside SDD; evidence = static gates + `terraform test`; real apply is a user-run step.
- Mock tests prove configuration logic, not AWS behavior; spike mocked data sources on 1.16 first.
- Dev (no NAT) vs prod (NAT per AZ) drift — mitigated by one module, tests for every mode, `single` mode.
- Budgets notify but don't stop spend; a forgotten NAT ≈ $36/month.
- Windows friction: tool installs, CRLF/LF, missing Linux lock hashes.
- Public repo: budget email/profile names in gitignored tfvars; confirm account ID exposure.
- Unverified: new-account free plan, `PowerUserAccess` IAM scope, sa-east-1 NAT price, interface endpoint rate.

## Ready for proposal

Yes, after: region/layout confirmation, split decision, budget amount + email (can be supplied at apply time).

## Sources

[S3 backend](https://developer.hashicorp.com/terraform/language/backend/s3) · [Terraform 1.11 changelog](https://raw.githubusercontent.com/hashicorp/terraform/v1.11/CHANGELOG.md) · [Terraform releases](https://github.com/hashicorp/terraform/releases) · [Terraform tests](https://developer.hashicorp.com/terraform/language/tests) · [Test mocking](https://developer.hashicorp.com/terraform/language/tests/mocking) · [AWS provider releases](https://github.com/hashicorp/terraform-provider-aws/releases) · [aws_budgets_budget](https://raw.githubusercontent.com/hashicorp/terraform-provider-aws/main/website/docs/r/budgets_budget.html.markdown) · [AWS VPC pricing](https://aws.amazon.com/vpc/pricing/) · [AWS Budgets pricing](https://aws.amazon.com/aws-cost-management/aws-budgets/pricing/) · [NAT pricing by region (aggregator)](https://costgoat.com/pricing/aws-nat-gateway) · [AWS CLI SSO](https://docs.aws.amazon.com/cli/latest/userguide/sso-configure-profile-token.html) · [tfsec status](https://github.com/aquasecurity/tfsec) · [tflint-ruleset-aws](https://github.com/terraform-linters/tflint-ruleset-aws/releases) · [terraform-aws-modules/vpc](https://github.com/terraform-aws-modules/terraform-aws-vpc) · [fck-nat](https://github.com/AndrewGuenther/fck-nat)
