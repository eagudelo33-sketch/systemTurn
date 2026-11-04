# Design: Infra Network + App

## Technical Approach

Two reusable modules under `SystemTurn/infra/modules/` (`network`, `app`) and one composition root (`envs/dev`). Modules declare only a minimum provider version (`aws >= 6.69`); roots keep the exact pins and the committed lock files, so module lock files are local test artifacts and are gitignored. Each module has its own offline `terraform test` suite with `mock_provider "aws"`. The paid layer is a `count` on the app module controlled by `enable_app`.

```
                    viewer (HTTPS)
                         |
                    CloudFront ----(default)----> S3 bucket (SPA, OAC, private)
                         |
                      (/api/*) HTTP
                         |
 VPC 10.20.0.0/16   +----v-----------------------------+
  public      (2)   | ALB :80  (SG: CloudFront prefix) |   IGW, NAT (if nat_mode != none)
  private_app (2)   | ECS Fargate tasks :8080          |   S3 gateway endpoint (free)
  data        (2)   | RDS PostgreSQL 17 :5432          |   no route outside the VPC
                    +----------------------------------+
```

## Architecture Decisions

| Topic | Choice | Alternatives considered (tradeoff) |
|---|---|---|
| Not billing yet | `enable_app` switch (`count = 0/1`) on the app module; default `false` | Stop/pause resources: AWS has no pause for an ALB, RDS storage and secrets bill while stopped, and a stopped RDS restarts after 7 days. Separate root for the app: extra state and backend wiring for little gain |
| Dev egress | `nat_mode` variable `none` / `single` / `per_az`, default `none` | A boolean `enable_nat` (cannot express single vs per-AZ). Always-on NAT (~$33-70/month while idle) |
| Egress when the app is on | Recommend `nat_mode = "single"` (~$33) in dev | Interface endpoints (~$58 for 4 x 2 AZs): no internet egress at all, but more expensive at low traffic and still no third-party APIs. Kept as opt-in `enable_vpc_endpoints` |
| S3 access | Gateway endpoint always on `private_app` route tables | Through NAT only (pays $0.045/GB for ECR layers). Gateway endpoints are free |
| Data tier | Own route table with `route = []`, not associated with the S3 endpoint | Sharing the private route table (would inherit the NAT route). `route = []` also removes routes added by hand |
| Subnet layout | `cidrsubnet(cidr, 4, n)`: public 0-1, private_app 2-3, data 4-5; first two AZs sorted | Variable lists of CIDRs (more inputs, easy to overlap). Three AZs (more NAT cost in per_az) |
| DB credentials | `manage_master_user_password = true`; ECS `secrets` reads `<arn>:password::` | `random_password` + own secret (password lands in state). Variable password (lands in tfvars and state) |
| DB URL options | `sslmode=require&socketTimeout=30` in `SPRING_DATASOURCE_URL` | Defaults (no TLS enforcement from the client; hung queries on failover). Obligation from `postgres-adapter` |
| Spring profile | No `SPRING_PROFILES_ACTIVE` set; base config + env vars | Setting `prod` (no profile file exists today). Never `test` or `demo` (obligation from `runtime-operations`) |
| API path | CloudFront `/api/*` ordered behavior to the ALB, caching disabled, all viewer headers except Host | Calling the ALB directly from the SPA (mixed content: HTTPS page to HTTP ALB; ALB must be open to the internet) |
| SPA routing | CloudFront Function on the default behavior rewrites paths without `.` to `/index.html` | Custom error responses 403/404 -> `index.html` (applies to every origin, so API 404s would become `index.html` with 200) |
| ALB exposure | SG ingress port 80 only from `com.amazonaws.global.cloudfront.origin-facing` prefix list | Open 0.0.0.0/0 (anyone can bypass CloudFront). Secret header check (stronger, not done yet, see gaps) |
| ALB protocol | HTTP :80 only, `drop_invalid_header_fields = true` | ACM + HTTPS listener: needs a domain, which does not exist yet |
| Compute default | `desired_count = 0`; circuit breaker with rollback; `stopTimeout = 30` (> 25 s Spring shutdown phase); deregistration delay 30 s | `desired_count = 1` (bills ~$18/task/month as soon as the switch is on, and fails until an image exists) |
| ALB idle timeout | 300 s | Default 60 s (idle WebSockets would drop; obligation for `live-updates`) |
| Health check | `/actuator/health/readiness`, matcher 200, 15 s interval | `/actuator/health` (includes more than readiness). DB excluded from probes by `runtime-operations` |
| IAM | Execution role: ECR pull on this repo, `GetSecretValue` on this secret, logs on this group, `GetAuthorizationToken` on `*` (no resource scope). Task role: no policies, locked with `aws_iam_role_policies_exclusive` | AWS managed `AmazonECSTaskExecutionRolePolicy` (wider: any repo, any log group) |
| ECR | Immutable tags, scan on push, keep last 10 | Mutable `latest` (no clear rollback, no audit of what runs) |
| Observability cost | Container Insights disabled, logs 14 days | Container Insights (bills per metric) |
| Module version pins | `required_version ~> 1.11`, `aws >= 6.69`; module lock files gitignored | Byte-identical `versions.tf` in modules (would force module consumers onto one minor). The checker's `version_constraints_pinned` keeps covering roots only |
| Tests that need ids | `command = apply` with the mocked provider | `command = plan`: mocked ids stay unknown, so id-to-id comparisons cannot be asserted. Mocked apply is offline and creates nothing |
| trivy findings | Inline `#trivy:ignore` with a reason for `AVD-AWS-0053` (public ALB), `0054` (HTTP listener), `0011` (no WAF), `0104` (task egress 443 to 0.0.0.0/0) | Repo-wide `.trivyignore` entries (hide the same finding on future resources) |

## Cost Model (approximate, us-east-1)

| Item | Monthly |
|------|---------|
| VPC, subnets, IGW, route tables, S3 gateway endpoint | $0 |
| NAT gateway (each) | ~$33 + $0.045/GB, + ~$3.65 public IPv4 |
| Interface endpoint (each, per AZ) | ~$7.3 + $0.01/GB |
| ALB | ~$16 + LCU, + ~$7 for 2 public IPv4 |
| RDS db.t4g.micro + 20 GB gp3 | ~$14 |
| Secrets Manager secret | $0.40 |
| Fargate 0.5 vCPU / 1 GB per running task | ~$18 |
| CloudFront, S3, ECR, CloudWatch logs at dev traffic | cents |

## Testing Strategy

| Suite | Runs | Command mix | Proves |
|---|---|---|---|
| `modules/network/tests/network.tftest.hcl` | 11 | 4 plan, 7 apply (mocked) | NAT modes, validation, 2x3 subnets, IGW route, data isolation, endpoints |
| `modules/app/tests/app.tftest.hcl` | 11 | 6 plan, 5 apply (mocked) | desired count, RDS placement and lifecycle, SG, ALB, secret injection, IAM, ECR/logs, S3/OAC |
| `envs/dev/tests/env.tftest.hcl` | 7 | plan | region, tags, owner, `dev_default_creates_no_app`, `dev_enable_app_plans` |
| `envs/prod/tests/env.tftest.hcl` | 6 | plan | unchanged, includes `env_roots_declare_no_resources` |

Not covered by a test (declared in code, listed in the `infra-app` operational notes): `/api/*` behavior, SPA rewrite function, ALB SG prefix list, bucket policy, circuit breaker.

## Known Gaps

| Gap | Impact | Planned |
|-----|--------|---------|
| No ACM / HTTPS on the ALB | CloudFront to ALB traffic is HTTP inside AWS | When a domain exists |
| No WAF | No rate limiting or managed rules | Before a public deploy |
| No secret header CloudFront to ALB | Any CloudFront distribution (any account) can reach the ALB | Before a public deploy |
| No WebSocket path | `live-updates` needs a `/ws/*` behavior | `live-updates` |
| Not verified on a real account | Behavior proven only against mocks | First real apply |
| `prod` not wired | Prod cannot be deployed yet | Later infra change |
| `.trivyignore` AWS-0132 is repo-wide | Also hides SSE-S3 on the frontend bucket (intended, but not scoped) | Scope it when convenient |

## Rollout

1. Default: `terraform apply` in `envs/dev` creates the network only (~$0).
2. App on: `enable_app = true`, `nat_mode = "single"`, apply; push an image to ECR; set `app_image_tag` and `app_desired_count = 1`; apply; upload the SPA build and invalidate CloudFront.
3. Off: `enable_app = false`, `nat_mode = "none"`, apply (or `terraform destroy`). In dev the database is deleted without a final snapshot.
