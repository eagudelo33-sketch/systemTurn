# Proposal: Infra Network + App (VPC module, app module, dev cost switch)

## Intent

The owner wants all AWS infrastructure for SystemTurn written and tested now, but **not billing yet**. AWS cannot "pause" a full stack: an ALB bills every hour it exists, RDS storage and its secret bill while the instance is stopped, and a stopped RDS instance starts again by itself after 7 days. So the paid part is a **switch** (`enable_app`), not a pause. The default `terraform apply` in `dev` creates only the network, which costs about $0.

This change merges the roadmap items `infra-network` and `infra-app` into one record, because the app module is only useful together with the network it runs in.

## Scope

### In Scope
- `infra/modules/network`: VPC in 2 AZs x 3 tiers (`public`, `private_app`, `data`); `data` has no route outside the VPC; `nat_mode` = `none` | `single` | `per_az` (default `none`); free S3 gateway endpoint always; optional interface endpoints (`enable_vpc_endpoints`, default `false`). README + 11 offline tests.
- `infra/modules/app`: ECR (immutable, scan on push, keep 10), CloudWatch logs (14 days), RDS PostgreSQL 17 `db.t4g.micro` in the data subnets with an RDS-managed master password in Secrets Manager, ECS Fargate cluster/task/service (`desired_count` default 0, port 8080, `stopTimeout` 30 s), ALB (health `/actuator/health/readiness`, idle 300 s, ingress only from the CloudFront origin-facing prefix list), S3 + CloudFront with OAC, `/api/*` routed to the ALB, SPA fallback with a CloudFront Function, least-privilege IAM. README + 11 offline tests.
- `envs/dev`: wires the network (`nat_mode` default `none`) and the app behind `enable_app` (default `false`, `count`); app outputs via `try(..., null)`. Test `env_roots_declare_no_resources` replaced in dev by `dev_default_creates_no_app` and `dev_enable_app_plans`.
- Runbook section "8b. Dev cost switch"; `.gitignore` ignores module lock files.

### Out of Scope
- Wiring `envs/prod` (stays an empty skeleton; still has `env_roots_declare_no_resources`).
- ACM certificate / HTTPS on the ALB, custom domain, WAF, a secret header from CloudFront to the ALB, a WebSocket path (`live-updates`).
- CI/CD and image build/push automation (`infra-cicd`).
- Any real `apply` or AWS call. Nothing here was run against an AWS account.
- Application, API, backend or frontend changes.

## Justifications

| Decision | Why |
|----------|-----|
| Paid layer is a switch (`enable_app`), not a pause | ALB bills hourly while it exists (~$16-20/month); RDS storage and Secrets Manager bill while stopped; a stopped RDS restarts after 7 days. Only "does not exist" is reliably $0 |
| `nat_mode = "none"` in dev, `per_az` intended for prod | Dev pays $0 while the app is off. Per-AZ NAT (~$66-70/month) keeps egress alive if one AZ fails |
| `nat_mode = "single"` when the dev app is on | ~$33/month, cheaper than 4 interface endpoints x 2 AZs (~$58/month) |
| Data tier with no internet route | Defense in depth: even a misconfigured security group cannot give RDS a path out |
| RDS-managed secret | The password is never in code, variables or Terraform inputs; ECS injects it through `secrets` |
| `/api/*` through CloudFront | Avoids mixed content (HTTPS site calling an HTTP ALB) and lets the ALB accept only CloudFront |
| CloudFront Function for the SPA fallback | Custom error responses would turn API 404s into `index.html`; the function only rewrites the default (S3) behavior |
| `desired_count = 0` by default | Even with `enable_app = true`, Fargate compute costs nothing until tasks are scaled up |

## Cost table (approximate, us-east-1, on-demand)

| Configuration | Monthly cost |
|---------------|--------------|
| Default dev apply: network only (VPC, subnets, IGW, route tables, S3 gateway endpoint) | ~$0 |
| + `enable_app = true`, `desired_count = 0`, `nat_mode = "none"` | ~$38: RDS db.t4g.micro + 20 GB gp3 ~$14, ALB ~$16 + 2 public IPv4 ~$7, secret $0.40; S3, CloudFront, ECR, logs cost cents |
| + `nat_mode = "single"` (needed for tasks to start) | + ~$33 + $0.045/GB processed (+ ~$3.65 for the NAT public IPv4) |
| + 1 running task (0.5 vCPU / 1 GB) | + ~$18 |
| `nat_mode = "per_az"` instead of `single` | + ~$66-70 total for NAT |
| `enable_vpc_endpoints = true` instead of NAT | + ~$58 (4 endpoints x 2 AZs) + $0.01/GB |

Prices are estimates for planning, not tested values.

## Capabilities

### New Capabilities
- `infra-network`: VPC tiers, NAT modes, data-tier isolation, endpoints.
- `infra-app`: compute, database, load balancer, IAM, registry/logs, frontend hosting.

### Modified Capabilities
- `infra-environments`: `env_roots_declare_no_resources` now applies to `prod` only; new dev composition requirement.
- `infra-cost-guardrails`: new requirement "Dev paid layer is a switch, off by default".

## Affected Areas

| Area | Impact |
|------|--------|
| `SystemTurn/infra/modules/network/` (+ README, tests) | New |
| `SystemTurn/infra/modules/app/` (+ README, tests) | New |
| `SystemTurn/infra/envs/dev/` (`main.tf`, `variables.tf`, `terraform.tfvars.example`, tests) | Modified |
| `SystemTurn/docs/infra-onboarding.md`, `docs/README.md`, new `docs/infra.md` | Modified / New |
| `.gitignore` | Modified (module lock files) |
| `SystemTurn/openspec/specs/` | 2 new, 2 modified specs |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Mocked tests do not prove AWS behavior | High | Operational notes in specs; first real apply checklist in `docs/infra.md` |
| Someone sets `enable_app = true` and forgets it | Med | Budget alerts from `bootstrap`; turning off is one variable + apply |
| Tasks fail to start with `nat_mode = "none"` | Expected | Documented: tasks need NAT or interface endpoints |
| ALB is HTTP only, reachable from any CloudFront distribution | Med | Prefix list limits it to CloudFront; secret header / ACM are tracked gaps |

## Rollback Plan

Nothing is applied by this change. To roll back the code: revert the commit(s). After a user apply: set `enable_app = false` and apply (removes the paid layer; dev deletes the database without a final snapshot), or `terraform destroy` in `envs/dev`.

## Success Criteria

- [x] `fmt -check`, `validate`, tflint and trivy pass on every root and module.
- [x] `terraform test` passes in `modules/network`, `modules/app`, `envs/dev`, `envs/prod`, `bootstrap`.
- [x] Default dev plan creates no app and no NAT.
- [x] `check-conventions.sh` stays green (runbook <= 100 lines).
