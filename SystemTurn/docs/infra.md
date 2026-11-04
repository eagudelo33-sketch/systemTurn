# Infrastructure (AWS): what exists and what it costs

All SystemTurn infrastructure is written in Terraform and tested offline, but **only the free part is meant to be running**. The paid part (database, load balancer, containers, CDN) sits behind one switch in `dev`: `enable_app`. It is off by default, so a plain `terraform apply` in `envs/dev` costs about **$0/month**.

New to AWS here? Set up the account and state first with the [onboarding runbook](infra-onboarding.md). Exact rules: specs [infra-network](../openspec/specs/infra-network/spec.md), [infra-app](../openspec/specs/infra-app/spec.md), [infra-environments](../openspec/specs/infra-environments/spec.md), [infra-cost-guardrails](../openspec/specs/infra-cost-guardrails/spec.md).

## What exists

```
                     viewer (HTTPS)
                          |
                     CloudFront ---- default ----> S3 bucket (React build, private)
                          |
                       /api/*
                          |
  VPC (2 AZs)   +---------v--------------------------+
   public       |  ALB :80   (only CloudFront can reach it)   <- internet gateway
   private_app  |  ECS Fargate API :8080                      <- NAT only if nat_mode != none
   data         |  RDS PostgreSQL 17 :5432                    <- no route outside the VPC
                +------------------------------------+
   always on: VPC, subnets, route tables, free S3 gateway endpoint
   behind enable_app: everything inside the box above, plus CloudFront, S3, ECR
```

| Piece | Code | Exists when |
|-------|------|-------------|
| VPC, 6 subnets (2 AZs x public / private_app / data), internet gateway, S3 gateway endpoint | `infra/modules/network` | Always (dev) |
| NAT gateway(s) | `infra/modules/network` | `nat_mode = "single"` or `"per_az"` |
| Interface endpoints (ECR, Logs, Secrets Manager) | `infra/modules/network` | `enable_vpc_endpoints = true` |
| ECR, logs, RDS, ECS, ALB, S3 + CloudFront, IAM | `infra/modules/app` | `enable_app = true` |
| Running API containers | `infra/modules/app` | `app_desired_count > 0` |
| `prod` | `infra/envs/prod` | Nothing yet: empty skeleton |

## What it costs (approximate, us-east-1, per month)

| You set | You pay | Running |
|---------|---------|---------|
| Defaults | ~$0 | Network only |
| `enable_app = true` | ~$38 | RDS ~$14, ALB ~$16 + ~$7 public IPs, secret $0.40; no containers yet |
| + `nat_mode = "single"` | + ~$37 | Containers can now pull images (NAT ~$33 + IP ~$3.65 + $0.045/GB) |
| + `app_desired_count = 1` | + ~$18 | One API container (0.5 vCPU, 1 GB) |
| `nat_mode = "per_az"` instead | + ~$70 for NAT | Production shape: survives one AZ failing |
| `enable_vpc_endpoints = true` instead of NAT | + ~$58 | No internet egress at all |

CloudFront, S3, ECR and logs cost cents at dev traffic. These are planning estimates, not tested values. The account budget in `bootstrap` emails you at 50/80/100%.

## How to turn it on (dev)

1. In `envs/dev/terraform.tfvars`: `enable_app = true` and `nat_mode = "single"`. Run `terraform apply`.
2. Build and push the API image: `docker push <ecr_repository_url>:<tag>` (tags are immutable, use a new one per release).
3. Set `app_image_tag = "<tag>"` and `app_desired_count = 1`. Run `terraform apply`.
4. Frontend: `npm run build` with `VITE_API_BASE_URL` unset (the API is on the same address), `aws s3 sync dist/ s3://<frontend_bucket_name> --delete`, then invalidate `<cloudfront_distribution_id>`.
5. Open `https://<cloudfront_domain_name>`. The API answers under `/api/*` on the same address.

## How to turn it off (dev)

1. Set `enable_app = false` and `nat_mode = "none"`. Run `terraform apply`. Back to ~$0.
2. Or remove everything, network included: `terraform destroy`.

In dev the database is deleted **without a final snapshot** and ECR images are deleted too. Export anything you need first.

## Why a switch and not "pause"

| AWS piece | What "stopping" does |
|-----------|----------------------|
| ALB | Cannot be stopped. Bills every hour it exists |
| RDS | Can be stopped, but storage and its secret keep billing, and AWS **starts it again after 7 days** |
| NAT gateway | Cannot be stopped. Bills every hour it exists |
| Fargate | `desired_count = 0` really costs $0 (that is why it is the default) |

So the only reliable $0 is "the resource does not exist". `enable_app = false` deletes it; `true` recreates it from code in minutes.

## Security choices, in one line each

- The database has no route to the internet and accepts connections only from the API containers.
- The database password is created and stored by RDS in Secrets Manager; it never appears in code or Terraform variables.
- The browser only talks HTTPS to CloudFront; `/api/*` goes through CloudFront, so the load balancer only accepts CloudFront traffic.
- The containers' IAM role can only pull its own image, read its own secret and write its own logs.

## Not done yet

- HTTPS between CloudFront and the load balancer (needs a domain and ACM certificate).
- WAF, and a secret header so only *our* CloudFront can reach the load balancer.
- A WebSocket path for live updates.
- `prod` wiring, CI/CD (`infra-cicd`).
- Nothing has been applied to a real AWS account yet; all checks are offline (mocked).
