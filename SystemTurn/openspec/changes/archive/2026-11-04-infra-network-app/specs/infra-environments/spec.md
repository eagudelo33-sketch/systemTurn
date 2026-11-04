# Delta for infra-environments

Purpose (updated): `dev` now composes `modules/network` and, behind `enable_app`, `modules/app`. `prod` stays a plan-only skeleton with no resources until a later change wires it.

## MODIFIED Requirements

### Requirement: Independent env roots
Each of `dev` and `prod` MUST be an independent Terraform root with its own `versions.tf`, `providers.tf`, `variables.tf`, `.terraform.lock.hcl` and tests. Each MUST validate with `terraform init -backend=false` followed by `terraform validate`. `prod` MUST declare no resources until a change wires it.
(Previously: both `dev` and `prod` had to plan with zero resource changes.)

#### Scenario: env_roots_validate
- GIVEN each env directory
- WHEN `terraform init -backend=false && terraform validate` runs
- THEN both exit 0 for `dev` and for `prod`

#### Scenario: env_roots_declare_no_resources
- GIVEN the `prod` root planned under `mock_provider "aws"`
- WHEN `env_roots_declare_no_resources` runs (in `envs/prod/tests/env.tftest.hcl` only)
- THEN the plan contains zero resource changes

## ADDED Requirements

### Requirement: Dev composes network and app modules
The `dev` root MUST instantiate `modules/network` with `name = "systemturn-dev"` and variables `vpc_cidr` (default `10.20.0.0/16`), `nat_mode` (default `none`) and `enable_vpc_endpoints` (default `false`). It MUST instantiate `modules/app` with `count = var.enable_app ? 1 : 0`, wired to the network outputs and to `app_image_tag` and `app_desired_count`, with dev lifecycle settings (no deletion protection, skip final snapshot, 1-day backups, force-delete ECR and frontend bucket). App outputs MUST be exposed through `try(..., null)` so they are `null` while the app is off.

#### Scenario: dev_enable_app_plans
- GIVEN the dev root with `enable_app = true`
- WHEN `dev_enable_app_plans` runs
- THEN exactly one `module.app` instance is planned and `output.app_enabled` is `true`

## Operational notes (NOT scenarios)

- The dev default (`enable_app = false`) is specified in `infra-cost-guardrails` (scenario `dev_default_creates_no_app`).
- `region_rejects_invalid` exists in both env test files; it predates this change and is not a spec scenario.
- The obligations "prove the region and `default_tags` on a real resource in plan" remain open: the tests still assert them through outputs.
