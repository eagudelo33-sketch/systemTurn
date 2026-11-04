# Infra State Backend Specification

## Purpose

Scope: `SystemTurn/infra/bootstrap/` and the backend configuration of env roots. No application, API, backend (`backend/turnos-api`) or frontend (`frontend/frontend-react/react-client`) change occurs.

Verification: every scenario below is provable offline by `terraform test` (`command = plan`, `mock_provider "aws"`), `terraform validate`/`fmt -check`, tflint, trivy, or a repo-file check. Test files: `infra/bootstrap/tests/state_bucket.tftest.hcl` (run-block names are the scenario IDs), `infra/scripts/check-conventions.sh` (repo-file checks).

## Requirements

### Requirement: Hardened state bucket
The bootstrap root MUST define exactly one S3 bucket for Terraform state, named from the variable `state_bucket_name` (no account ID literal in committed files). The bucket MUST have versioning `Enabled`, server-side encryption with `AES256`, all four Block Public Access flags `true`, and ownership controls `BucketOwnerEnforced`.

#### Scenario: bucket_versioning_enabled
- GIVEN the bootstrap root planned with `state_bucket_name = "systemturn-tfstate-test"`
- WHEN `terraform test` runs the `bucket_versioning_enabled` run block
- THEN the planned versioning configuration status equals `Enabled`

#### Scenario: bucket_encryption_aes256
- GIVEN the same plan
- WHEN `bucket_encryption_aes256` runs
- THEN the SSE configuration `sse_algorithm` equals `AES256`

#### Scenario: bucket_blocks_public_access
- GIVEN the same plan
- WHEN `bucket_blocks_public_access` runs
- THEN `block_public_acls`, `block_public_policy`, `ignore_public_acls` and `restrict_public_buckets` are all `true`

#### Scenario: bucket_owner_enforced
- GIVEN the same plan
- WHEN `bucket_owner_enforced` runs
- THEN the ownership controls rule `object_ownership` equals `BucketOwnerEnforced`

### Requirement: TLS-only bucket policy
The bucket policy MUST contain a `Deny` statement on `s3:*` for the bucket and its objects with condition `aws:SecureTransport = "false"` and principal `*`.

#### Scenario: bucket_policy_denies_insecure_transport
- GIVEN the bootstrap plan
- WHEN `bucket_policy_denies_insecure_transport` decodes the planned policy JSON
- THEN a statement exists with `Effect = "Deny"`, `Action = "s3:*"`, `Principal = "*"`, and `Condition.Bool["aws:SecureTransport"] = "false"` covering both the bucket ARN and `<bucket ARN>/*`

### Requirement: Lifecycle hygiene
The bucket MUST expire noncurrent object versions after 90 days and MUST abort incomplete multipart uploads after 7 days, via one enabled lifecycle rule applying to all objects.

#### Scenario: lifecycle_noncurrent_expiration
- GIVEN the bootstrap plan
- WHEN `lifecycle_noncurrent_expiration` runs
- THEN an enabled rule has `noncurrent_version_expiration.noncurrent_days = 90`

#### Scenario: lifecycle_aborts_multipart
- GIVEN the bootstrap plan
- WHEN `lifecycle_aborts_multipart` runs
- THEN an enabled rule has `abort_incomplete_multipart_upload.days_after_initiation = 7`

### Requirement: Bucket deletion protection declared
The state bucket resource MUST declare `lifecycle { prevent_destroy = true }`.

#### Scenario: bucket_declares_prevent_destroy
- GIVEN the file `infra/bootstrap/main.tf` (or the file defining the bucket)
- WHEN the repo-file check `check_prevent_destroy` parses the bucket resource block
- THEN `prevent_destroy = true` is present in that block
- AND the runtime enforcement of `prevent_destroy` is NOT asserted here (see Operational notes)

### Requirement: Per-environment state keys with native locking
Each env root (`dev`, `prod`) MUST ship a committed `backend.hcl.example` with `key` distinct per env (`dev/terraform.tfstate`, `prod/terraform.tfstate`), `use_lockfile = true`, `encrypt = true`, `region` placeholder, and a bucket placeholder. No `dynamodb_table` argument MAY appear. The env `backend "s3" {}` block MUST be partial (no bucket, key or account-specific value inline).

#### Scenario: backend_example_keys_distinct
- GIVEN `infra/envs/dev/backend.hcl.example` and `infra/envs/prod/backend.hcl.example`
- WHEN `check_backend_examples` parses both
- THEN dev `key` equals `dev/terraform.tfstate` and prod `key` equals `prod/terraform.tfstate`

#### Scenario: backend_example_native_locking
- GIVEN both `backend.hcl.example` files
- WHEN `check_backend_examples` runs
- THEN each contains `use_lockfile = true` and `encrypt = true`
- AND neither contains `dynamodb_table`

#### Scenario: backend_block_is_partial
- GIVEN `infra/envs/{dev,prod}/versions.tf` (or the file with the `backend "s3"` block)
- WHEN `check_backend_examples` parses the block
- THEN the block declares no `bucket`, `key`, `region`, or 12-digit account ID attribute

### Requirement: Bootstrap bucket name is a validated variable
`state_bucket_name` MUST be a required variable (no default) validated as a valid S3 bucket name (3-63 chars, lowercase letters, digits, hyphens). A committed `terraform.tfvars.example` MUST show a placeholder, never a real account ID.

#### Scenario: bucket_name_rejects_invalid
- GIVEN `state_bucket_name = "Bad_Name"`
- WHEN the run block `bucket_name_rejects_invalid` plans with `expect_failures = [var.state_bucket_name]`
- THEN the plan fails on that variable validation

#### Scenario: bucket_name_accepts_valid
- GIVEN `state_bucket_name = "systemturn-tfstate-test"`
- WHEN `bucket_name_accepts_valid` plans
- THEN the plan succeeds

## Operational notes (NOT scenarios; require a real AWS account, verified manually via the runbook)

- First `apply` of bootstrap with local state, then `terraform init -migrate-state -backend-config=backend.hcl` for each env.
- Real `.tflock` lock behavior under concurrent runs.
- `prevent_destroy` actually blocking `terraform destroy` on the bucket.
- Real bucket policy rejecting plain-HTTP requests.
