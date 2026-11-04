# Delta for infra-network (NEW)

Scope: reusable module `SystemTurn/infra/modules/network/`. One VPC across the first two available AZs, three subnet tiers per AZ (`public`, `private_app`, `data`). NAT and interface endpoints are opt-in because they bill hourly. No application, API, backend or frontend change occurs.

Verification: offline via `terraform test` in `infra/modules/network/tests/network.tftest.hcl` with `mock_provider "aws"` (run-block names are scenario IDs). Runs that compare generated ids use `command = apply`; with a mocked provider this apply is fully offline and creates nothing in AWS.

## ADDED Requirements

### Requirement: NAT mode switch
The module MUST expose variable `nat_mode` (string, default `"none"`) that accepts only `none`, `single` or `per_az`. `none` MUST create no NAT gateway, no Elastic IP and no default route in the `private_app` route tables. `single` MUST create one NAT gateway and one Elastic IP, and both `private_app` route tables MUST route `0.0.0.0/0` to it. `per_az` MUST create one NAT gateway and one Elastic IP per AZ, each placed in the public subnet of its AZ, and each `private_app` route table MUST use the NAT gateway of its own AZ.

#### Scenario: nat_mode_defaults_to_none
- GIVEN the module planned with only `name` and `cidr`
- WHEN `nat_mode_defaults_to_none` runs
- THEN `var.nat_mode` is `none`
- AND there are zero `aws_nat_gateway`, zero `aws_eip` and zero `aws_route.private_app_nat`

#### Scenario: nat_mode_single
- GIVEN `nat_mode = "single"`
- WHEN `nat_mode_single` runs
- THEN exactly one NAT gateway and one EIP exist
- AND both `private_app` routes send `0.0.0.0/0` to that NAT gateway

#### Scenario: nat_mode_per_az
- GIVEN `nat_mode = "per_az"`
- WHEN `nat_mode_per_az` runs
- THEN two NAT gateways and two EIPs exist
- AND route `i` uses NAT gateway `i`, and NAT gateway `i` lives in public subnet `i`

#### Scenario: nat_mode_rejects_invalid
- GIVEN `nat_mode = "always"`
- WHEN `nat_mode_rejects_invalid` plans with `expect_failures = [var.nat_mode]`
- THEN the plan fails on that variable validation

### Requirement: Two AZs and three tiers
The module MUST create exactly one subnet per tier in each of the first two available AZs (sorted by name), giving six distinct subnets. Subnet CIDRs MUST be `cidrsubnet(cidr, 4, n)` (public 0-1, private_app 2-3, data 4-5). Public subnets MUST NOT auto-assign public IPs. Variable `cidr` MUST be a valid IPv4 CIDR with a prefix between /16 and /24.

#### Scenario: subnets_two_azs_three_tiers
- GIVEN mocked AZs `us-east-1a`, `us-east-1b`, `us-east-1c`
- WHEN `subnets_two_azs_three_tiers` runs
- THEN each tier has exactly 2 subnets in `us-east-1a` and `us-east-1b`
- AND the outputs expose 6 distinct subnet ids
- AND every public subnet has `map_public_ip_on_launch = false`

#### Scenario: cidr_rejects_invalid
- GIVEN `cidr = "10.20.0.0/28"`
- WHEN `cidr_rejects_invalid` plans with `expect_failures = [var.cidr]`
- THEN the plan fails on that variable validation

### Requirement: Public tier reaches the internet
The public route table MUST route `0.0.0.0/0` to the VPC internet gateway.

#### Scenario: public_tier_routes_to_igw
- GIVEN the default module inputs
- WHEN `public_tier_routes_to_igw` runs
- THEN `aws_route.public_internet` targets the internet gateway with destination `0.0.0.0/0`

### Requirement: Data tier has no route outside the VPC
The data route table MUST declare no routes (`route = []`, only the implicit local route remains). No route resource MAY target it, whatever `nat_mode` is, and the S3 gateway endpoint MUST NOT be associated with it.

#### Scenario: data_tier_has_no_internet_route
- GIVEN `nat_mode = "per_az"` (the mode with the most routes)
- WHEN `data_tier_has_no_internet_route` runs
- THEN `aws_route_table.data.route` is empty
- AND no NAT or public route targets the data route table
- AND the S3 gateway endpoint route tables do not include the data route table

### Requirement: VPC endpoints
The module MUST always create a free S3 gateway endpoint for the `private_app` route tables. Variable `enable_vpc_endpoints` (bool, default `false`) MUST control interface endpoints: when `false` there MUST be no interface endpoint and no endpoint security group; when `true` there MUST be exactly the endpoints `ecr.api`, `ecr.dkr`, `logs` and `secretsmanager`, each with private DNS enabled, in the `private_app` subnets.

#### Scenario: s3_gateway_endpoint_always_present
- GIVEN the default module inputs
- WHEN `s3_gateway_endpoint_always_present` runs
- THEN `aws_vpc_endpoint.s3` is type `Gateway`, service `com.amazonaws.us-east-1.s3`, and serves exactly the `private_app` route tables

#### Scenario: vpc_endpoints_disabled_by_default
- GIVEN the default module inputs
- WHEN `vpc_endpoints_disabled_by_default` runs
- THEN there are zero interface endpoints and zero endpoint security groups

#### Scenario: vpc_endpoints_enabled
- GIVEN `enable_vpc_endpoints = true`
- WHEN `vpc_endpoints_enabled` runs
- THEN the interface endpoint keys equal {`ecr.api`, `ecr.dkr`, `logs`, `secretsmanager`}
- AND each has `private_dns_enabled = true` and the `private_app` subnet ids

## Operational notes (NOT scenarios)

- Not checked by a test, visible in code: the default security group is locked (`aws_default_security_group` with no rules); the endpoint security group allows only TCP 443 from the VPC CIDR.
- With `nat_mode = "none"` and `enable_vpc_endpoints = false`, Fargate tasks in `private_app` cannot reach ECR, CloudWatch Logs or Secrets Manager, so they cannot start. This is the intended $0 state.
- Real routing (an instance in `data` cannot reach the internet; NAT failover behavior) needs an AWS account and is not verified.
