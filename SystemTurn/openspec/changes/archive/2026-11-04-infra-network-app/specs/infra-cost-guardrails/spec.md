# Delta for infra-cost-guardrails

Purpose (updated): besides the account budget in `bootstrap/`, this capability now covers the cost defaults of the `dev` root: paid resources exist only when a variable turns them on.

## ADDED Requirements

### Requirement: Dev paid layer is a switch, off by default
The `dev` root MUST expose `enable_app` (bool, default `false`). With default variables, `dev` MUST instantiate no app module (no RDS, ALB, ECS, ECR, CloudFront), MUST use `nat_mode = "none"` (no NAT gateway, no Elastic IP), and every app output MUST be `null`. The network (2 AZs x 3 tiers) MUST still be created. The switch MUST be a variable, not a stop or pause step, because AWS keeps billing an existing ALB hourly and restarts a stopped RDS instance after 7 days.

#### Scenario: dev_default_creates_no_app
- GIVEN the dev root planned with only `owner` set
- WHEN `dev_default_creates_no_app` runs
- THEN `var.enable_app` is `false` and `length(module.app) == 0`
- AND `app_enabled` is `false` and `ecr_repository_url`, `cloudfront_domain_name`, `db_endpoint` are `null`
- AND `nat_mode` is `none` and the network has no NAT gateway ids
- AND the network outputs list 2 public, 2 private_app and 2 data subnets

## Operational notes (NOT scenarios)

- Approximate monthly prices (us-east-1, on-demand, November 2026) are documented in `docs/infra.md` and the archived change proposal; they are estimates, not tested values.
- Real billing with the default apply (expected about $0) must be confirmed in Cost Explorer after the first real apply.
