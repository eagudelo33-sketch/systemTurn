# Infra Cost Guardrails Specification

## Purpose

Scope: account-level monthly cost budget in `SystemTurn/infra/bootstrap/`, and the cost defaults of the `dev` root (paid resources exist only when a variable turns them on). The budget only notifies; it takes no automated action (no AWS Budget Actions). No application, API, backend or frontend change occurs.

Verification: all scenarios run offline via `terraform test` (`command = plan`, `mock_provider "aws"`) in `infra/bootstrap/tests/budget.tftest.hcl` and `infra/envs/dev/tests/env.tftest.hcl` (run-block names are scenario IDs), or via repo-file checks.

## Requirements

### Requirement: Monthly cost budget
The bootstrap root MUST define one `aws_budgets_budget` with `budget_type = "COST"`, `time_unit = "MONTHLY"`, `limit_unit = "USD"`, and `limit_amount` taken from variable `budget_amount_usd` (type number, default `30`, validated `> 0`).

#### Scenario: budget_defaults
- GIVEN the bootstrap plan with default variables and `alert_emails = ["a@example.com"]`
- WHEN `budget_defaults` runs
- THEN the budget has `budget_type = "COST"`, `time_unit = "MONTHLY"`, `limit_unit = "USD"` and `limit_amount = "30"`

#### Scenario: budget_amount_override
- GIVEN `budget_amount_usd = 50`
- WHEN `budget_amount_override` runs
- THEN `limit_amount` equals `"50"`

#### Scenario: budget_amount_rejects_nonpositive
- GIVEN `budget_amount_usd = 0`
- WHEN `budget_amount_rejects_nonpositive` plans with `expect_failures = [var.budget_amount_usd]`
- THEN the plan fails on that variable validation

### Requirement: Notification thresholds
The budget MUST declare exactly four notifications, all with `comparison_operator = "GREATER_THAN"`, `threshold_type = "PERCENTAGE"`: `ACTUAL` at 50, `ACTUAL` at 80, `ACTUAL` at 100, and `FORECASTED` at 100.

#### Scenario: notifications_exact_set
- GIVEN the default bootstrap plan
- WHEN `notifications_exact_set` runs
- THEN the notification set equals exactly {(ACTUAL,50), (ACTUAL,80), (ACTUAL,100), (FORECASTED,100)}
- AND its length is 4

### Requirement: Alert recipients are a validated list
Variable `alert_emails` MUST be `list(string)` with no default, validated as non-empty and each element matching an email shape (`^[^@\s]+@[^@\s]+\.[^@\s]+$`). Every notification MUST set `subscriber_email_addresses` to that full list.

#### Scenario: alert_emails_rejects_empty
- GIVEN `alert_emails = []`
- WHEN `alert_emails_rejects_empty` plans with `expect_failures = [var.alert_emails]`
- THEN the plan fails on that variable validation

#### Scenario: alert_emails_rejects_malformed
- GIVEN `alert_emails = ["not-an-email"]`
- WHEN `alert_emails_rejects_malformed` plans with `expect_failures = [var.alert_emails]`
- THEN the plan fails on that variable validation

#### Scenario: alert_emails_wired_to_all_notifications
- GIVEN `alert_emails = ["a@example.com", "b@example.com"]`
- WHEN `alert_emails_wired_to_all_notifications` runs
- THEN each of the four notifications has `subscriber_email_addresses` equal to both addresses

### Requirement: No budget actions and no committed recipients
The bootstrap root MUST NOT declare any `aws_budgets_budget_action`. Committed files MUST NOT contain real email addresses; `terraform.tfvars.example` MUST use `example.com` placeholders.

#### Scenario: no_budget_actions
- GIVEN all `.tf` files under `infra/`
- WHEN `check_no_budget_actions` searches for `aws_budgets_budget_action`
- THEN there are zero matches

#### Scenario: tfvars_example_uses_placeholders
- GIVEN `infra/bootstrap/terraform.tfvars.example`
- WHEN `check_tfvars_example` parses it
- THEN every email value ends with `@example.com`
- AND it defines `alert_emails` and `state_bucket_name`

### Requirement: Dev paid layer is a switch, off by default
The `dev` root MUST expose `enable_app` (bool, default `false`). With default variables, `dev` MUST instantiate no app module (no RDS, ALB, ECS, ECR, CloudFront), MUST use `nat_mode = "none"` (no NAT gateway, no Elastic IP), and every app output MUST be `null`. The network (2 AZs x 3 tiers) MUST still be created. The switch MUST be a variable, not a stop or pause step, because AWS keeps billing an existing ALB hourly and restarts a stopped RDS instance after 7 days.

#### Scenario: dev_default_creates_no_app
- GIVEN the dev root planned with only `owner` set
- WHEN `dev_default_creates_no_app` runs
- THEN `var.enable_app` is `false` and `length(module.app) == 0`
- AND `app_enabled` is `false` and `ecr_repository_url`, `cloudfront_domain_name`, `db_endpoint` are `null`
- AND `nat_mode` is `none` and the network has no NAT gateway ids
- AND the network outputs list 2 public, 2 private_app and 2 data subnets

## Operational notes (NOT scenarios; manual via runbook)

- Runbook documents the manual action at 100%: run `terraform destroy` on `infra/envs/dev` (budget alerts do not stop spend).
- Subscribers must confirm the AWS Budgets email; real alert delivery and Cost Explorer data lag (up to ~24h) cannot be verified offline.
- Approximate monthly prices for the dev switch are in `docs/infra.md`; they are estimates, not tested values. Confirm the default dev apply bills about $0 in Cost Explorer.
