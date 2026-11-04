# Skeleton root: no resources yet. Network and app stacks arrive in later changes.
locals {
  environment = "prod"

  default_tags = {
    Project     = "systemturn"
    Environment = local.environment
    ManagedBy   = "terraform"
    Owner       = var.owner
  }
}

output "environment" {
  description = "Environment name of this root."
  value       = local.environment
}

output "region" {
  description = "AWS region used by the provider."
  value       = var.region
}

output "default_tags" {
  description = "Tags applied to every resource by the provider."
  value       = local.default_tags
}
