# Infra Environments Specification

## Purpose

Scope: `SystemTurn/infra/envs/dev/` and `SystemTurn/infra/envs/prod/` skeletons. No resources are declared. `prod` is plan-only until `infra-app`: this change defines no requirement to apply it. No application, API, backend or frontend change occurs.

Verification: offline via `terraform test` (`command = plan`, `mock_provider "aws"`) in `infra/envs/<env>/tests/env.tftest.hcl` (run-block names are scenario IDs), `terraform validate`, `fmt -check`, and repo-file checks.

## Requirements

### Requirement: Independent env roots
Each of `dev` and `prod` MUST be an independent Terraform root with its own `versions.tf`, `providers.tf`, `variables.tf`, `.terraform.lock.hcl` and tests. Each MUST validate with `terraform init -backend=false` followed by `terraform validate`.

#### Scenario: env_roots_validate
- GIVEN each env directory
- WHEN `terraform init -backend=false && terraform validate` runs
- THEN both exit 0 for `dev` and for `prod`

#### Scenario: env_roots_declare_no_resources
- GIVEN each env root planned under `mock_provider "aws"`
- WHEN `env_roots_declare_no_resources` runs
- THEN the plan contains zero resource changes

### Requirement: Version pins and lock files
Each env MUST require Terraform `~> 1.11` and provider `hashicorp/aws` `~> 6.69`. Each committed `.terraform.lock.hcl` MUST pin `aws` 6.69.1 and contain hashes for both `windows_amd64` and `linux_amd64` (an `h1:` hash plus `zh:` hashes sufficient for both platforms).

#### Scenario: version_constraints_pinned
- GIVEN `versions.tf` of each env
- WHEN `check_version_pins` parses it
- THEN `required_version = "~> 1.11"` and `aws` `version = "~> 6.69"` with source `hashicorp/aws`

#### Scenario: lockfile_pins_aws_6_69_1
- GIVEN `.terraform.lock.hcl` of each env and of `bootstrap`
- WHEN `check_lockfiles` parses it
- THEN the `hashicorp/aws` provider block has `version = "6.69.1"`

#### Scenario: lockfile_covers_both_platforms
- GIVEN the same lock files
- WHEN `check_lockfiles` inspects the hash set
- THEN more than one `h1:` or `zh:` hash entry is present, as produced by `terraform providers lock -platform=windows_amd64 -platform=linux_amd64`
- AND the lock file is not git-ignored

### Requirement: Region variable
Each env MUST expose variable `region` (string, default `us-east-1`) and the AWS provider MUST use `region = var.region`. The provider MUST NOT set `profile`, `access_key` or `secret_key`.

#### Scenario: region_default_us_east_1
- GIVEN an env plan with no variable overrides
- WHEN `region_default_us_east_1` runs
- THEN `var.region` equals `us-east-1`

#### Scenario: region_override
- GIVEN `region = "us-west-2"`
- WHEN `region_override` runs
- THEN the plan succeeds and the provider region resolves to `us-west-2`

#### Scenario: provider_has_no_credentials_or_profile
- GIVEN `providers.tf` of each env
- WHEN `check_provider_hygiene` parses it
- THEN no `profile`, `access_key`, `secret_key` or `shared_credentials_files` attribute exists

### Requirement: Default tags
The provider MUST declare `default_tags` with keys `Project` (`systemturn`), `Environment` (equal to the env name: `dev` or `prod`), `ManagedBy` (`terraform`) and `Owner` (from a variable `owner`, validated non-empty).

#### Scenario: default_tags_dev
- GIVEN the dev root with `owner = "edgar"`
- WHEN `default_tags_dev` runs
- THEN tags equal {Project = "systemturn", Environment = "dev", ManagedBy = "terraform", Owner = "edgar"}

#### Scenario: default_tags_prod
- GIVEN the prod root with `owner = "edgar"`
- WHEN `default_tags_prod` runs
- THEN tags equal {Project = "systemturn", Environment = "prod", ManagedBy = "terraform", Owner = "edgar"}

#### Scenario: owner_rejects_empty
- GIVEN `owner = ""`
- WHEN `owner_rejects_empty` plans with `expect_failures = [var.owner]`
- THEN the plan fails on that variable validation

### Requirement: Env tfvars examples only
Each env MUST commit a `terraform.tfvars.example` (placeholders only, no real values). Real `*.tfvars` MUST NOT be committed.

#### Scenario: env_tfvars_example_present
- GIVEN each env directory
- WHEN `check_tfvars_example` lists files
- THEN `terraform.tfvars.example` exists and no `terraform.tfvars` is tracked by git

## Operational notes (NOT scenarios)

- `prod` is plan-only until `infra-app`; nothing here requires applying it.
- Real `terraform init -backend-config=backend.hcl` against the S3 bucket, state migration, and real provider authentication via IAM Identity Center SSO are runbook steps requiring an AWS account.
