# Tasks: Infra Network + App

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Changed lines (measured, uncommitted tree) | ~1,905 infra (modules 1,703 incl. tests 549 and READMEs 64; `envs/dev` +191/-7; `.gitignore` +2) + docs |
| 800-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR A = network module + dev network wiring (~580) -> PR B = app module + `enable_app` wiring + docs (~1,330, mostly tests and declarative resources) |
| Delivery status | Not committed. Delivery strategy is the owner's decision |

## Phase 1: Network module

- [x] 1.1 Tests `modules/network/tests/network.tftest.hcl` (mocked AZs and region): `nat_mode_defaults_to_none`, `nat_mode_single`, `nat_mode_per_az`, `nat_mode_rejects_invalid`, `cidr_rejects_invalid`, `subnets_two_azs_three_tiers`, `public_tier_routes_to_igw`, `data_tier_has_no_internet_route`, `s3_gateway_endpoint_always_present`, `vpc_endpoints_disabled_by_default`, `vpc_endpoints_enabled`.
- [x] 1.2 `variables.tf`: `name`, `cidr` (/16-/24), `nat_mode` (none/single/per_az, default none), `enable_vpc_endpoints` (default false), with validations.
- [x] 1.3 `main.tf`: VPC, locked default SG, IGW, 6 subnets, public/private_app/data route tables, NAT + EIP by `nat_mode`, data route table `route = []`, S3 gateway endpoint, optional interface endpoints + SG.
- [x] 1.4 `outputs.tf` (11 outputs incl. `internet_egress_enabled`), `versions.tf` (`aws >= 6.69`), README with the egress cost tradeoff.

## Phase 2: App module

- [x] 2.1 Tests `modules/app/tests/app.tftest.hcl` (mocked ARNs, RDS secret, CloudFront domain): `desired_count_defaults_to_zero`, `desired_count_rejects_negative`, `rds_private_in_data_subnets`, `rds_dev_defaults`, `rds_final_snapshot_when_not_skipped`, `db_sg_ingress_only_from_service_sg`, `alb_health_check_readiness`, `task_gets_password_from_secrets`, `execution_role_least_privilege`, `ecr_and_logs_defaults`, `frontend_bucket_private_behind_oac`.
- [x] 2.2 `main.tf` (ECR + lifecycle, log group, JDBC URL with `sslmode=require&socketTimeout=30`), `database.tf` (subnet group, RDS with managed password).
- [x] 2.3 `ecs.tf` (cluster without Container Insights, task definition with `secrets`, `stopTimeout`, awslogs; service with circuit breaker), `alb.tf` (ALB, target group readiness check, HTTP listener).
- [x] 2.4 `security_groups.tf` (CloudFront prefix list -> ALB -> service -> DB chain), `iam.tf` (scoped execution role, empty task role with exclusive lock).
- [x] 2.5 `frontend.tf` (private bucket, PAB, ownership, SSE, TLS-only + CloudFront-only policy, OAC, SPA rewrite function, distribution with `/api/*` behavior).
- [x] 2.6 `outputs.tf`, `versions.tf`, README with diagram and deploy flow. Inline trivy ignores with reasons (AWS-0053, 0054, 0011, 0104).

## Phase 3: Dev wiring

- [x] 3.1 Replace dev `env_roots_declare_no_resources` with `dev_default_creates_no_app` and `dev_enable_app_plans`; add mocked AZs/region to the dev test provider.
- [x] 3.2 `envs/dev/variables.tf`: `vpc_cidr`, `nat_mode`, `enable_vpc_endpoints`, `enable_app` (false), `app_image_tag`, `app_desired_count`.
- [x] 3.3 `envs/dev/main.tf`: network module, app module with `count`, dev lifecycle settings, outputs through `try(..., null)`; `terraform.tfvars.example` with cost comments.
- [x] 3.4 Leave `envs/prod` unchanged (still `env_roots_declare_no_resources`).
- [x] 3.5 `.gitignore`: `SystemTurn/infra/modules/**/.terraform.lock.hcl`.

## Phase 4: Docs and specs

- [x] 4.1 Runbook section "8b. Dev cost switch" (runbook stays <= 100 lines).
- [x] 4.2 `docs/infra.md` (what exists, cost, on/off, why a switch); `docs/README.md` status, roadmap, decisions, gaps, obligations.
- [x] 4.3 Main specs: new `infra-network`, `infra-app`; modified `infra-environments`, `infra-cost-guardrails`; `specs/README.md` index.

## Phase 5: Verification

- [x] 5.1 `terraform fmt -recursive -check`; `init -backend=false`, `validate`, `test`, tflint in `modules/network`, `modules/app`, `envs/dev`, `envs/prod`, `bootstrap`; `trivy config`; `check-conventions.sh`; `test-check-conventions.sh`. See `verify-report.md`.
- [ ] 5.2 Commit (owner's decision; not done in this change record).

## Scenario coverage

All 24 new scenarios (network 11, app 11, environments 1, cost-guardrails 1) and the modified `env_roots_declare_no_resources` map to a run block of the same name.
