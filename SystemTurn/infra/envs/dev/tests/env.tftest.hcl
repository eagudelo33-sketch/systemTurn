# Offline tests: mocked AWS provider, plan only. Run-block names are spec scenario IDs.
mock_provider "aws" {}

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

# The root declares only variables, locals and outputs; a plan with a mocked
# provider must therefore succeed and expose just the environment identity.
run "env_roots_declare_no_resources" {
  command = plan

  assert {
    condition     = output.environment == "dev"
    error_message = "The root must plan cleanly as environment dev with no resources."
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
