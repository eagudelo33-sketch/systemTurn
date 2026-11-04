# Offline tests: mocked AWS provider, no credentials. Run-block names are scenario IDs.
# Runs that compare resource ids use `command = apply`: with a mocked provider the apply is
# fully offline, and it is the only way the generated ids propagate to dependent resources.
mock_provider "aws" {
  mock_data "aws_availability_zones" {
    defaults = {
      names = ["us-east-1a", "us-east-1b", "us-east-1c"]
    }
  }

  mock_data "aws_region" {
    defaults = {
      region = "us-east-1"
    }
  }
}

variables {
  name = "systemturn-test"
  cidr = "10.20.0.0/16"
}

run "nat_mode_defaults_to_none" {
  command = plan

  assert {
    condition     = var.nat_mode == "none"
    error_message = "nat_mode must default to none."
  }

  assert {
    condition     = length(aws_nat_gateway.this) == 0 && length(aws_eip.nat) == 0
    error_message = "nat_mode none must create no NAT gateway and no EIP."
  }

  assert {
    condition     = length(aws_route.private_app_nat) == 0
    error_message = "nat_mode none must not add a default route to the private_app route tables."
  }
}

run "nat_mode_single" {
  command = apply

  variables {
    nat_mode = "single"
  }

  assert {
    condition     = length(aws_nat_gateway.this) == 1 && length(aws_eip.nat) == 1
    error_message = "nat_mode single must create exactly one NAT gateway and one EIP."
  }

  assert {
    condition = length(aws_route.private_app_nat) == 2 && alltrue([
      for r in aws_route.private_app_nat : r.nat_gateway_id == aws_nat_gateway.this[0].id && r.destination_cidr_block == "0.0.0.0/0"
    ])
    error_message = "Both private_app route tables must route 0.0.0.0/0 to the single NAT gateway."
  }
}

run "nat_mode_per_az" {
  command = apply

  variables {
    nat_mode = "per_az"
  }

  assert {
    condition     = length(aws_nat_gateway.this) == 2 && length(aws_eip.nat) == 2
    error_message = "nat_mode per_az must create one NAT gateway and one EIP per AZ."
  }

  assert {
    condition = alltrue([
      for i, r in aws_route.private_app_nat : r.nat_gateway_id == aws_nat_gateway.this[i].id
    ])
    error_message = "Each private_app route table must use the NAT gateway of its own AZ."
  }

  assert {
    condition = alltrue([
      for i, n in aws_nat_gateway.this : n.subnet_id == aws_subnet.public[i].id
    ])
    error_message = "NAT gateways must live in the public subnets."
  }
}

run "nat_mode_rejects_invalid" {
  command = plan

  variables {
    nat_mode = "always"
  }

  expect_failures = [var.nat_mode]
}

run "cidr_rejects_invalid" {
  command = plan

  variables {
    cidr = "10.20.0.0/28"
  }

  expect_failures = [var.cidr]
}

run "subnets_two_azs_three_tiers" {
  command = apply

  assert {
    condition = (
      length(aws_subnet.public) == 2 &&
      length(aws_subnet.private_app) == 2 &&
      length(aws_subnet.data) == 2
    )
    error_message = "Each tier must have exactly one subnet in each of 2 AZs."
  }

  assert {
    condition = (
      [for s in aws_subnet.public : s.availability_zone] == ["us-east-1a", "us-east-1b"] &&
      [for s in aws_subnet.data : s.availability_zone] == ["us-east-1a", "us-east-1b"]
    )
    error_message = "Subnets must use the first two available AZs."
  }

  assert {
    condition     = length(distinct(concat(output.public_subnet_ids, output.private_app_subnet_ids, output.data_subnet_ids))) == 6
    error_message = "Outputs must expose six distinct subnet ids."
  }

  assert {
    condition     = alltrue([for s in aws_subnet.public : s.map_public_ip_on_launch == false])
    error_message = "Public subnets must not auto-assign public IPs."
  }
}

run "public_tier_routes_to_igw" {
  command = apply

  assert {
    condition     = aws_route.public_internet.gateway_id == aws_internet_gateway.this.id && aws_route.public_internet.destination_cidr_block == "0.0.0.0/0"
    error_message = "The public route table must send 0.0.0.0/0 to the internet gateway."
  }
}

run "data_tier_has_no_internet_route" {
  command = apply

  variables {
    nat_mode = "per_az"
  }

  assert {
    condition     = length(aws_route_table.data.route) == 0
    error_message = "The data route table must declare no routes besides the implicit local route."
  }

  assert {
    condition = alltrue([
      for r in concat(aws_route.private_app_nat, [aws_route.public_internet]) : r.route_table_id != aws_route_table.data.id
    ])
    error_message = "No route resource may target the data route table."
  }

  assert {
    condition     = !contains(aws_vpc_endpoint.s3.route_table_ids, aws_route_table.data.id)
    error_message = "The data tier must not even reach S3."
  }
}

run "s3_gateway_endpoint_always_present" {
  command = apply

  assert {
    condition = (
      aws_vpc_endpoint.s3.vpc_endpoint_type == "Gateway" &&
      aws_vpc_endpoint.s3.service_name == "com.amazonaws.us-east-1.s3" &&
      toset(aws_vpc_endpoint.s3.route_table_ids) == toset(output.private_app_route_table_ids)
    )
    error_message = "A free S3 gateway endpoint must serve the private_app route tables."
  }
}

run "vpc_endpoints_disabled_by_default" {
  command = plan

  assert {
    condition     = length(aws_vpc_endpoint.interface) == 0 && length(aws_security_group.endpoints) == 0
    error_message = "Interface endpoints cost money and must be off by default."
  }
}

run "vpc_endpoints_enabled" {
  command = apply

  variables {
    enable_vpc_endpoints = true
  }

  assert {
    condition = toset(keys(aws_vpc_endpoint.interface)) == toset(["ecr.api", "ecr.dkr", "logs", "secretsmanager"]) && alltrue([
      for e in aws_vpc_endpoint.interface : e.private_dns_enabled && toset(e.subnet_ids) == toset(output.private_app_subnet_ids)
    ])
    error_message = "Enabled endpoints must cover ECR, Logs and Secrets Manager in the private_app subnets."
  }
}
