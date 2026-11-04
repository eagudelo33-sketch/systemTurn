# network module

VPC across the first two available AZs, three tiers per AZ:

| Tier | Route to the internet | Used by |
|------|-----------------------|---------|
| `public` | 0.0.0.0/0 to the internet gateway | ALB, NAT gateways |
| `private_app` | 0.0.0.0/0 to NAT only when `nat_mode != "none"` | ECS Fargate tasks |
| `data` | none, not even S3 (`route = []`) | RDS |

Subnets are `cidrsubnet(cidr, 4, n)`: public 0-1, private_app 2-3, data 4-5. The default security group is locked (no rules).

## Egress tradeoff for `private_app`

Fargate tasks need AWS APIs to start: ECR (image), CloudWatch Logs, Secrets Manager (DB password). With `nat_mode = "none"` and no interface endpoints, tasks cannot start. The S3 gateway endpoint is always created because it is free; it carries the ECR image layers, but it is not enough alone.

| Option | Approx. cost (us-east-1, 2 AZs) | Notes |
|--------|---------------------------------|-------|
| `nat_mode = "none"`, `enable_vpc_endpoints = false` | $0 | Default. Network only, tasks cannot pull images |
| `nat_mode = "single"` | ~$33/month + $0.045/GB | Cheapest working option. One AZ outage cuts egress for both AZs |
| `nat_mode = "per_az"` | ~$66/month + $0.045/GB | Production shape |
| `enable_vpc_endpoints = true` | ~$58/month (4 endpoints x 2 AZs x ~$7.3) + $0.01/GB | No internet egress at all; tasks still cannot call third-party APIs |

For dev, `nat_mode = "single"` is cheaper than interface endpoints. Endpoints win when the app must have no internet egress, or with heavy traffic.

## Inputs

| Name | Default | Description |
|------|---------|-------------|
| `name` | required | Name prefix, 3-32 chars `[a-z0-9-]` |
| `cidr` | required | VPC CIDR, prefix /16 to /24 |
| `nat_mode` | `"none"` | `none`, `single` or `per_az` |
| `enable_vpc_endpoints` | `false` | Interface endpoints for `ecr.api`, `ecr.dkr`, `logs`, `secretsmanager` |

Outputs: `vpc_id`, `vpc_cidr`, `azs`, `public_subnet_ids`, `private_app_subnet_ids`, `data_subnet_ids`, `public_route_table_id`, `private_app_route_table_ids`, `data_route_table_id`, `nat_gateway_ids`, `internet_egress_enabled`.

## Tests

`terraform init -backend=false && terraform test` (mocked provider, no credentials).
