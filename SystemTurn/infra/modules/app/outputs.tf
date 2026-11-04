output "ecr_repository_url" {
  description = "Push API images here (tags are immutable)."
  value       = aws_ecr_repository.api.repository_url
}

output "ecs_cluster_name" {
  description = "ECS cluster name."
  value       = aws_ecs_cluster.this.name
}

output "ecs_service_name" {
  description = "ECS service name."
  value       = aws_ecs_service.api.name
}

output "alb_dns_name" {
  description = "ALB DNS name (reachable only from CloudFront)."
  value       = aws_lb.this.dns_name
}

output "cloudfront_domain_name" {
  description = "Public HTTPS entry point for the SPA and /api/*."
  value       = aws_cloudfront_distribution.frontend.domain_name
}

output "cloudfront_distribution_id" {
  description = "Distribution id, for cache invalidations after a frontend deploy."
  value       = aws_cloudfront_distribution.frontend.id
}

output "frontend_bucket_name" {
  description = "Upload the Vite build (dist/) here."
  value       = aws_s3_bucket.frontend.bucket
}

output "db_endpoint" {
  description = "RDS endpoint (host:port), reachable only from the API tasks."
  value       = aws_db_instance.this.endpoint
}

output "db_secret_arn" {
  description = "ARN of the RDS-managed Secrets Manager secret holding the master credentials."
  value       = local.db_secret_arn
}

output "log_group_name" {
  description = "CloudWatch log group of the API container."
  value       = aws_cloudwatch_log_group.api.name
}
