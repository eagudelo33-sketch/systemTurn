# Delta for infra-app (NEW)

Scope: reusable module `SystemTurn/infra/modules/app/`: ECR, CloudWatch logs, RDS PostgreSQL, ECS Fargate API behind an ALB, and the React SPA on S3 + CloudFront, with least-privilege IAM. Every resource here bills, so roots instantiate the module behind a switch (see `infra-environments` and `infra-cost-guardrails`). No application, API, backend or frontend change occurs.

Verification: offline via `terraform test` in `infra/modules/app/tests/app.tftest.hcl` with `mock_provider "aws"` (run-block names are scenario IDs). Runs that compare generated ids or decode generated JSON use `command = apply`; with a mocked provider this apply is fully offline and creates nothing in AWS.

## ADDED Requirements

### Requirement: API compute is off until scaled
The ECS service MUST take `desired_count` from variable `desired_count` (default `0`, validated as a non-negative integer). Tasks MUST run on Fargate in the `private_app` subnets with `assign_public_ip = false`.

#### Scenario: desired_count_defaults_to_zero
- GIVEN the module planned with only the required network inputs
- WHEN `desired_count_defaults_to_zero` runs
- THEN `aws_ecs_service.api.desired_count` is `0`
- AND the launch type is `FARGATE`, the subnets equal `private_app_subnet_ids`, and no public IP is assigned

#### Scenario: desired_count_rejects_negative
- GIVEN `desired_count = -1`
- WHEN `desired_count_rejects_negative` plans with `expect_failures = [var.desired_count]`
- THEN the plan fails on that variable validation

### Requirement: Private database in the data tier
RDS MUST be PostgreSQL `17` on `db.t4g.micro` with 20 GB `gp3`, encrypted storage, `publicly_accessible = false`, and a subnet group made of exactly the data subnets. The DB security group MUST be the only security group on the instance and MUST have exactly one ingress rule: TCP 5432 from the ECS service security group (no CIDR).

#### Scenario: rds_private_in_data_subnets
- GIVEN the default module inputs
- WHEN `rds_private_in_data_subnets` runs
- THEN the instance is not publicly accessible and its subnet group equals `data_subnet_ids`
- AND it is encrypted `postgres` `17`, `db.t4g.micro`, 20 GB `gp3`

#### Scenario: db_sg_ingress_only_from_service_sg
- GIVEN the default module inputs
- WHEN `db_sg_ingress_only_from_service_sg` runs
- THEN the only ingress rule on the DB security group is 5432 from the service security group with `cidr_ipv4 = null`
- AND the instance uses only the DB security group

### Requirement: Database password managed by RDS
RDS MUST own the master password (`manage_master_user_password = true`, no `password` argument), so no password exists in code, variables or as a Terraform input. The ECS container MUST receive `SPRING_DATASOURCE_PASSWORD` only through `secrets`, from the `password` key of the RDS-managed secret, and never as a plain environment variable. The container environment MUST be exactly `SPRING_DATASOURCE_URL` (with `sslmode=require&socketTimeout=30`), `SPRING_DATASOURCE_USERNAME`, `TURNOS_DB_POOL_SIZE` and `TURNOS_ALLOWED_ORIGINS` (the CloudFront HTTPS origin). The container MUST expose port 8080, log to the module log group, and use `stopTimeout = 30`.

#### Scenario: task_gets_password_from_secrets
- GIVEN a mocked RDS address and secret ARN
- WHEN `task_gets_password_from_secrets` runs
- THEN `secrets` contains `SPRING_DATASOURCE_PASSWORD` with `valueFrom = "<secret_arn>:password::"`
- AND `environment` has no `SPRING_DATASOURCE_PASSWORD`
- AND `environment` equals the four expected names and values
- AND `stopTimeout` is 30, the container port is 8080 and the log group is the module log group

### Requirement: Database lifecycle is configurable
Defaults MUST suit dev: `deletion_protection = false`, `skip_final_snapshot = true`, `backup_retention_period = 1`. Variables `db_deletion_protection` and `db_skip_final_snapshot` MUST reach the instance; when the final snapshot is not skipped, `final_snapshot_identifier` MUST be `<name>-final`.

#### Scenario: rds_dev_defaults
- GIVEN the default module inputs
- WHEN `rds_dev_defaults` runs
- THEN `manage_master_user_password = true` and `password = null`
- AND `deletion_protection = false`, `skip_final_snapshot = true`, `backup_retention_period = 1`

#### Scenario: rds_final_snapshot_when_not_skipped
- GIVEN `db_skip_final_snapshot = false` and `db_deletion_protection = true`
- WHEN `rds_final_snapshot_when_not_skipped` runs
- THEN `deletion_protection = true` and `final_snapshot_identifier = "systemturn-test-final"`

### Requirement: Load balancer health check and timeouts
The target group MUST be type `ip` on port 8080 with health check path `/actuator/health/readiness`. The ALB MUST be internet-facing with `idle_timeout = 300` (for future WebSockets) and `drop_invalid_header_fields = true`, with an HTTP listener on port 80.

#### Scenario: alb_health_check_readiness
- GIVEN the default module inputs
- WHEN `alb_health_check_readiness` runs
- THEN the target group is `ip`, port 8080, path `/actuator/health/readiness`
- AND the ALB is not internal, idle timeout 300, drops invalid headers, and listens on HTTP:80

### Requirement: Least-privilege IAM
The execution role policy MUST allow `secretsmanager:GetSecretValue` only on the RDS-managed secret. Only `ecr:GetAuthorizationToken`, which has no resource-level scope, MAY use `Resource = "*"`. The task role MUST have no inline policies, enforced with `aws_iam_role_policies_exclusive`.

#### Scenario: execution_role_least_privilege
- GIVEN a mocked secret ARN
- WHEN `execution_role_least_privilege` runs
- THEN the secret statement resources equal exactly {secret ARN}
- AND every statement with `Resource = "*"` has only `ecr:GetAuthorizationToken`
- AND the task role exclusive policy list is empty

### Requirement: Container registry and logs
The ECR repository MUST use immutable tags, scan on push, and a lifecycle policy that keeps the last 10 images. The API log group MUST retain logs for 14 days by default.

#### Scenario: ecr_and_logs_defaults
- GIVEN the default module inputs
- WHEN `ecr_and_logs_defaults` runs
- THEN `image_tag_mutability = "IMMUTABLE"`, `scan_on_push = true`, lifecycle `countNumber = 10`
- AND log retention is 14 days

### Requirement: Private frontend bucket behind CloudFront
The SPA bucket MUST block all public access. CloudFront MUST read it through an Origin Access Control (`s3`, signing `always`) and MUST redirect viewers to HTTPS on the default behavior.

#### Scenario: frontend_bucket_private_behind_oac
- GIVEN the default module inputs
- WHEN `frontend_bucket_private_behind_oac` runs
- THEN all four public access block flags are `true`
- AND the OAC is type `s3` with signing `always`, and the default behavior is `redirect-to-https`

## Operational notes (NOT scenarios)

Declared in code but not asserted by any test today (follow-up: add run blocks):
- CloudFront ordered behavior `/api/*` routes to the ALB origin (`http-only`, caching disabled, all viewer headers except Host).
- CloudFront Function `spa_rewrite` on the default behavior rewrites paths without a `.` to `/index.html`, so API 404s stay 404s.
- The ALB security group allows port 80 only from the managed prefix list `com.amazonaws.global.cloudfront.origin-facing`.
- The bucket policy allows `s3:GetObject` only to `cloudfront.amazonaws.com` with `AWS:SourceArn` = the distribution, and denies non-TLS access.
- ECS deployment circuit breaker with rollback; target group deregistration delay 30 s.

Known gaps (accepted for this change, documented in the module README and design):
- No ACM certificate: the ALB is HTTP only (`#trivy:ignore:AVD-AWS-0053`, `AVD-AWS-0054`). Viewers still get HTTPS from CloudFront; CloudFront to ALB is HTTP.
- No WAF on CloudFront (`#trivy:ignore:AVD-AWS-0011`), no secret header from CloudFront to the ALB, no WebSocket path yet.
- Task egress on 443 to `0.0.0.0/0` (`#trivy:ignore:AVD-AWS-0104`) because interface endpoint IPs are not known to the module.
- Not verified against a real AWS account: task start, health check passing, secret injection, CloudFront routing.
