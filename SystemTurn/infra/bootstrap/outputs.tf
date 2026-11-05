output "state_bucket_name" {
  description = "Name of the Terraform state bucket."
  value       = aws_s3_bucket.state.bucket
}

output "state_bucket_arn" {
  description = "ARN of the Terraform state bucket."
  value       = aws_s3_bucket.state.arn
}

output "budget_name" {
  description = "Name of the monthly cost budget."
  value       = aws_budgets_budget.monthly.name
}

output "github_oidc_provider_arn" {
  description = "ARN of the GitHub Actions OIDC provider."
  value       = aws_iam_openid_connect_provider.github.arn
}

output "github_plan_role_arn" {
  description = "ARN of the read-only plan role (pull_request trust). Store as secret AWS_PLAN_ROLE_ARN."
  value       = aws_iam_role.plan.arn
}

output "github_apply_role_arn" {
  description = "ARN of the prod apply role (environment:prod trust). Store as secret AWS_APPLY_ROLE_ARN."
  value       = aws_iam_role.apply.arn
}
