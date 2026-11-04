# Dev root. With default variables only the network is created (~$0/month).
# The paid app layer (RDS, ALB, ECS, CloudFront) is behind `enable_app`.
locals {
  environment = "dev"
  name        = "systemturn-${local.environment}"

  default_tags = {
    Project     = "systemturn"
    Environment = local.environment
    ManagedBy   = "terraform"
    Owner       = var.owner
  }
}

module "network" {
  source = "../../modules/network"

  name                 = local.name
  cidr                 = var.vpc_cidr
  nat_mode             = var.nat_mode
  enable_vpc_endpoints = var.enable_vpc_endpoints
}

module "app" {
  source = "../../modules/app"
  count  = var.enable_app ? 1 : 0

  name                   = local.name
  vpc_id                 = module.network.vpc_id
  public_subnet_ids      = module.network.public_subnet_ids
  private_app_subnet_ids = module.network.private_app_subnet_ids
  data_subnet_ids        = module.network.data_subnet_ids

  image_tag     = var.app_image_tag
  desired_count = var.app_desired_count

  # Dev lifecycle: cheap and easy to tear down.
  db_deletion_protection   = false
  db_skip_final_snapshot   = true
  db_backup_retention_days = 1
  ecr_force_delete         = true
  frontend_force_destroy   = true
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

output "vpc_id" {
  description = "VPC id."
  value       = module.network.vpc_id
}

output "public_subnet_ids" {
  description = "Public subnet ids."
  value       = module.network.public_subnet_ids
}

output "private_app_subnet_ids" {
  description = "Private app subnet ids."
  value       = module.network.private_app_subnet_ids
}

output "data_subnet_ids" {
  description = "Data subnet ids."
  value       = module.network.data_subnet_ids
}

output "app_enabled" {
  description = "Whether the paid app layer is instantiated."
  value       = length(module.app) > 0
}

output "ecr_repository_url" {
  description = "ECR repository for API images (null while enable_app = false)."
  value       = try(module.app[0].ecr_repository_url, null)
}

output "ecs_cluster_name" {
  description = "ECS cluster name (null while enable_app = false)."
  value       = try(module.app[0].ecs_cluster_name, null)
}

output "ecs_service_name" {
  description = "ECS service name (null while enable_app = false)."
  value       = try(module.app[0].ecs_service_name, null)
}

output "cloudfront_domain_name" {
  description = "Public HTTPS entry point (null while enable_app = false)."
  value       = try(module.app[0].cloudfront_domain_name, null)
}

output "cloudfront_distribution_id" {
  description = "CloudFront distribution id (null while enable_app = false)."
  value       = try(module.app[0].cloudfront_distribution_id, null)
}

output "frontend_bucket_name" {
  description = "SPA bucket (null while enable_app = false)."
  value       = try(module.app[0].frontend_bucket_name, null)
}

output "db_endpoint" {
  description = "RDS endpoint (null while enable_app = false)."
  value       = try(module.app[0].db_endpoint, null)
}

output "db_secret_arn" {
  description = "RDS-managed secret ARN (null while enable_app = false)."
  value       = try(module.app[0].db_secret_arn, null)
}
