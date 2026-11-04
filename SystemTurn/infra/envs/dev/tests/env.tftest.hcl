# Offline tests: mocked AWS provider, plan only. Run-block names are spec scenario IDs.
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
  owner = "edgar"
}

run "region_default_us_east_1" {
  command = plan

  assert {
    condition     = var.region == "us-east-1" && output.region == "us-east-1"
    error_message = "region must default to us-east-1."
  }
}

run "region_override" {
  command = plan

  variables {
    region = "us-west-2"
  }

  assert {
    condition     = output.region == "us-west-2"
    error_message = "region override must reach the provider configuration."
  }
}

run "region_rejects_invalid" {
  command = plan

  variables {
    region = "Not_A_Region"
  }

  expect_failures = [var.region]
}

# With default variables dev creates only the near-free network: no app module instance,
# no NAT gateway, and every app output is null.
run "dev_default_creates_no_app" {
  command = plan

  assert {
    condition     = output.environment == "dev" && var.enable_app == false && length(module.app) == 0
    error_message = "enable_app must default to false and instantiate no app module."
  }

  assert {
    condition     = output.app_enabled == false && output.ecr_repository_url == null && output.cloudfront_domain_name == null && output.db_endpoint == null
    error_message = "App outputs must be null while the app layer is off."
  }

  assert {
    condition     = var.nat_mode == "none" && length(module.network.nat_gateway_ids) == 0
    error_message = "Dev must default to nat_mode none (no NAT gateway, no EIP)."
  }

  assert {
    condition     = length(output.public_subnet_ids) == 2 && length(output.private_app_subnet_ids) == 2 && length(output.data_subnet_ids) == 2
    error_message = "Dev network must span 2 AZs x 3 tiers."
  }
}

# Turning the switch on must plan cleanly with the dev wiring.
run "dev_enable_app_plans" {
  command = plan

  variables {
    enable_app = true
  }

  assert {
    condition     = length(module.app) == 1 && output.app_enabled == true
    error_message = "enable_app = true must instantiate exactly one app module."
  }
}

run "default_tags_dev" {
  command = plan

  assert {
    condition = output.default_tags == {
      Project     = "systemturn"
      Environment = "dev"
      ManagedBy   = "terraform"
      Owner       = "edgar"
    }
    error_message = "default_tags must be the exact dev tag set."
  }
}

run "owner_rejects_empty" {
  command = plan

  variables {
    owner = ""
  }

  expect_failures = [var.owner]
}
