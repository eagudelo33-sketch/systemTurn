# Infra Repo Conventions Specification

## Purpose

Scope: repo-level ignore/EOL rules, static-analysis configuration, pre-commit hooks, test-runner config, and the onboarding runbook. No application, API, backend (`backend/turnos-api`) or frontend (`frontend/frontend-react/react-client`) change occurs.

Verification: all scenarios are repo-file checks (`infra/scripts/check-conventions.sh`, a script run with no AWS account, using `git check-ignore`, `git ls-files` and file parsing) or static gates (`terraform fmt -check -recursive`, `terraform validate`, tflint, trivy config).

## Requirements

### Requirement: Ignore rules protect state and secrets
Root `.gitignore` MUST ignore `.terraform/`, `*.tfstate`, `*.tfstate.*`, `*.tfplan`, `*.tfvars`, `backend.hcl` (real files), and MUST NOT ignore `*.tfvars.example`, `backend.hcl.example`, or `.terraform.lock.hcl`.

#### Scenario: state_and_plan_files_ignored
- GIVEN the paths `infra/envs/dev/terraform.tfstate`, `infra/envs/dev/terraform.tfstate.backup`, `infra/bootstrap/out.tfplan`, `infra/bootstrap/.terraform/x`
- WHEN `git check-ignore` is run on each
- THEN every path is reported as ignored

#### Scenario: real_tfvars_and_backend_ignored
- GIVEN the paths `infra/bootstrap/terraform.tfvars`, `infra/envs/dev/dev.auto.tfvars`, `infra/envs/dev/backend.hcl`, `infra/envs/prod/backend.hcl`
- WHEN `git check-ignore` is run on each
- THEN every path is reported as ignored

#### Scenario: examples_and_locks_not_ignored
- GIVEN the paths `infra/bootstrap/terraform.tfvars.example`, `infra/envs/dev/backend.hcl.example`, `infra/envs/dev/.terraform.lock.hcl`
- WHEN `git check-ignore` is run on each
- THEN none is ignored

#### Scenario: no_sensitive_files_tracked
- GIVEN `git ls-files` for the repository
- WHEN the check filters for `*.tfstate*`, `*.tfplan`, `*.tfvars`, and `backend.hcl` (excluding `.example`)
- THEN there are zero matches

#### Scenario: no_account_id_in_tracked_infra_files
- GIVEN all tracked files under `infra/` and the runbook
- WHEN the check searches for a 12-digit AWS account ID pattern (`\b\d{12}\b`) outside obvious placeholders (`123456789012`, `000000000000`)
- THEN there are zero matches

### Requirement: LF line endings for Terraform files
Root `.gitattributes` MUST set `eol=lf` for `*.tf`, `*.hcl`, `*.tftest.hcl` and `.terraform.lock.hcl`.

#### Scenario: gitattributes_forces_lf
- GIVEN the paths `a.tf`, `b.hcl`, `c.tftest.hcl`, `.terraform.lock.hcl`
- WHEN `git check-attr eol` is run on each
- THEN every path reports `eol: lf`

#### Scenario: tracked_infra_files_have_no_crlf
- GIVEN all tracked `*.tf`, `*.hcl`, `*.tftest.hcl` files
- WHEN the check scans their working-tree bytes
- THEN no file contains a `\r\n` sequence

### Requirement: Static gates configured and passing
The repo MUST commit `.tflint.hcl` enabling the `aws` ruleset plugin, `trivy.yaml` configuring a misconfiguration scan of `infra/`, and `.terraform-docs.yml`. `terraform fmt -check -recursive infra`, `terraform validate` (each root), `tflint` (each root) and `trivy config infra` MUST all exit 0.

#### Scenario: tflint_config_enables_aws_ruleset
- GIVEN `.tflint.hcl`
- WHEN the repo-file check parses it
- THEN a `plugin "aws"` block with `enabled = true` exists

#### Scenario: fmt_check_passes
- GIVEN `infra/`
- WHEN `terraform fmt -check -recursive infra` runs
- THEN it exits 0

#### Scenario: validate_passes_all_roots
- GIVEN `bootstrap`, `envs/dev`, `envs/prod`
- WHEN `terraform init -backend=false && terraform validate` runs in each
- THEN all exit 0

#### Scenario: tflint_passes_all_roots
- GIVEN the three roots
- WHEN `tflint --chdir=<root>` runs with `.tflint.hcl`
- THEN each exits 0

#### Scenario: trivy_config_passes
- GIVEN `infra/` and `trivy.yaml`
- WHEN `trivy config` runs with severity HIGH and CRITICAL and exit-code 1
- THEN it exits 0 with zero findings (or only findings suppressed by a committed `.trivyignore` entry with a stated reason)

### Requirement: Generated docs are not committed
terraform-docs generated READMEs MUST NOT be committed; only `.terraform-docs.yml` is. The pre-commit config MUST NOT include a hook that writes generated docs into tracked files.

#### Scenario: no_generated_readmes
- GIVEN `git ls-files infra`
- WHEN the check searches for `README.md` containing the terraform-docs markers `<!-- BEGIN_TF_DOCS -->`
- THEN there are zero matches

### Requirement: Pre-commit and test-runner configuration
`.pre-commit-config.yaml` MUST include hooks for `terraform_fmt`, `terraform_validate`, `terraform_tflint` and trivy config scanning. `openspec/config.yaml` MUST contain an infra test block declaring the commands `terraform test`, `terraform fmt -check`, `terraform validate`, `tflint` and `trivy config`.

#### Scenario: precommit_hooks_present
- GIVEN `.pre-commit-config.yaml`
- WHEN the check parses it
- THEN hook ids `terraform_fmt`, `terraform_validate`, `terraform_tflint` and a trivy hook are present

#### Scenario: openspec_config_declares_infra_tests
- GIVEN `SystemTurn/openspec/config.yaml`
- WHEN the check parses it
- THEN an infra test section lists the five commands above

### Requirement: Onboarding runbook exists
A runbook under `SystemTurn/docs/` MUST contain, in order, headings for: AWS account creation, root MFA, IAM Identity Center, AWS CLI v2, `aws configure sso`, bootstrap apply, state migration, budget 100% manual action (`terraform destroy` on `envs/dev`), and teardown. It MUST be at most 100 lines.

#### Scenario: runbook_has_required_sections
- GIVEN the runbook file
- WHEN the check extracts level-2 headings
- THEN the nine required topics appear in the stated order

#### Scenario: runbook_within_size_limit
- GIVEN the runbook file
- WHEN lines are counted
- THEN the count is <= 100

### Requirement: No application change
The change MUST NOT modify any file under `SystemTurn/backend/` or `SystemTurn/frontend/`.

#### Scenario: no_app_files_touched
- GIVEN the diff of the change branch against `main`
- WHEN the check lists changed paths
- THEN none begins with `SystemTurn/backend/` or `SystemTurn/frontend/`

## Operational notes (NOT scenarios)

- Real IAM Identity Center sign-in, root MFA enrollment, `aws configure sso`, and the actual apply/migrate/teardown sequence are manual runbook steps.
- Obligations in `decision/systemturn-infra-app-obligations` remain deferred to `infra-app`.
