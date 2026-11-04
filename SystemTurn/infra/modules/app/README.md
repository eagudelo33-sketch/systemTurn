# app module

Paid app layer for SystemTurn. Roots instantiate it behind a switch (`enable_app`) because RDS and the ALB bill every hour they exist.

```
viewer --HTTPS--> CloudFront --(default)--> S3 bucket (SPA, OAC)
                             --(/api/*)---> ALB :80 --> ECS Fargate (private_app) --> RDS PostgreSQL 17 (data)
```

- **ECR**: immutable tags, scan on push, keeps the last 10 images.
- **ECS**: Fargate service `desired_count = 0` by default. Container env: `SPRING_DATASOURCE_URL` (`sslmode=require&socketTimeout=30`), `SPRING_DATASOURCE_USERNAME`, `TURNOS_DB_POOL_SIZE`, `TURNOS_ALLOWED_ORIGINS` (the CloudFront origin). `SPRING_DATASOURCE_PASSWORD` comes through `secrets` from the RDS-managed secret. `stopTimeout` 30s covers the 25s Spring shutdown phase.
- **RDS**: `manage_master_user_password = true`, so RDS creates the secret and no password touches Terraform. Data subnets only, SG allows 5432 from the task SG only.
- **ALB**: SG allows port 80 only from the CloudFront origin-facing prefix list. Health check `/actuator/health/readiness`, `idle_timeout` 300s for WebSockets. HTTP only until a domain and ACM certificate exist.
- **IAM**: execution role pulls from this repository, reads this one secret and writes this log group; the task role has no permissions.

## Deploy flow (after `enable_app = true`)

1. `docker push <ecr_repository_url>:<tag>`, then set `app_image_tag = "<tag>"` and `app_desired_count = 1`.
2. `npm run build`, `aws s3 sync dist/ s3://<frontend_bucket_name> --delete`, then invalidate `<cloudfront_distribution_id>`.

Tasks only start when `private_app` subnets reach ECR, Logs and Secrets Manager (`nat_mode != "none"` or `enable_vpc_endpoints = true`; see `../network/README.md`).

## Tests

`terraform init -backend=false && terraform test` (mocked provider, no credentials).
