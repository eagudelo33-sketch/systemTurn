# GitHub Actions OIDC trust for CI. Applied from a workstation only: CI never applies bootstrap.
# AWS ignores thumbprints for GitHub's issuer, so thumbprint_list is left unset (optional in provider 6.x).
locals {
  github_oidc_host = "token.actions.githubusercontent.com"
  state_bucket_arn = "arn:aws:s3:::${var.state_bucket_name}"
}

resource "aws_iam_openid_connect_provider" "github" {
  url            = "https://${local.github_oidc_host}"
  client_id_list = ["sts.amazonaws.com"]
}

# Plan role: assumable only from pull_request runs of var.github_repository. Read-only.
resource "aws_iam_role" "plan" {
  name = "systemturn-github-plan"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRoleWithWebIdentity"
      Principal = { Federated = aws_iam_openid_connect_provider.github.arn }
      Condition = {
        StringEquals = {
          "${local.github_oidc_host}:aud" = "sts.amazonaws.com"
          "${local.github_oidc_host}:sub" = "repo:${var.github_repository}:pull_request"
        }
      }
    }]
  })
}

resource "aws_iam_role_policy_attachments_exclusive" "plan" {
  role_name   = aws_iam_role.plan.name
  policy_arns = ["arn:aws:iam::aws:policy/ReadOnlyAccess"]
}

# Explicit state-read floor; ReadOnlyAccess already covers it today.
resource "aws_iam_role_policy" "plan_state" {
  name = "state-read"
  role = aws_iam_role.plan.name

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["s3:ListBucket"]
        Resource = [local.state_bucket_arn]
      },
      {
        Effect   = "Allow"
        Action   = ["s3:GetObject"]
        Resource = ["${local.state_bucket_arn}/dev/*", "${local.state_bucket_arn}/prod/*"]
      },
    ]
  })
}

# Apply role: assumable only from the `prod` GitHub Environment. Prod state access only.
resource "aws_iam_role" "apply" {
  name = "systemturn-github-apply"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRoleWithWebIdentity"
      Principal = { Federated = aws_iam_openid_connect_provider.github.arn }
      Condition = {
        StringEquals = {
          "${local.github_oidc_host}:aud" = "sts.amazonaws.com"
          "${local.github_oidc_host}:sub" = "repo:${var.github_repository}:environment:prod"
        }
      }
    }]
  })
}

resource "aws_iam_role_policy_attachments_exclusive" "apply" {
  role_name   = aws_iam_role.apply.name
  policy_arns = []
}

resource "aws_iam_role_policy" "apply_state" {
  name = "prod-state"
  role = aws_iam_role.apply.name

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["s3:ListBucket"]
        Resource = [local.state_bucket_arn]
      },
      {
        Effect   = "Allow"
        Action   = ["s3:GetObject", "s3:PutObject"]
        Resource = ["${local.state_bucket_arn}/prod/*"]
      },
      {
        Effect   = "Allow"
        Action   = ["s3:DeleteObject"]
        Resource = ["${local.state_bucket_arn}/prod/*.tflock"]
      },
    ]
  })
}
