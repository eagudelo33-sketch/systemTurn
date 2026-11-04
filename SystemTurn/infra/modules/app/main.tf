# App layer: ECR, logs, RDS, ECS Fargate API behind an ALB, and the React SPA on S3 + CloudFront.
# Every resource here bills (RDS and ALB hourly), so roots gate this module behind a switch.
data "aws_region" "current" {}

locals {
  container_name = "api"
  db_secret_arn  = aws_db_instance.this.master_user_secret[0].secret_arn
  public_origin  = "https://${aws_cloudfront_distribution.frontend.domain_name}"
  jdbc_url       = "jdbc:postgresql://${aws_db_instance.this.address}:${aws_db_instance.this.port}/${var.db_name}?sslmode=require&socketTimeout=30"
}

# --- Container registry and logs ---

resource "aws_ecr_repository" "api" {
  name                 = "${var.name}-api"
  image_tag_mutability = "IMMUTABLE"
  force_delete         = var.ecr_force_delete

  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_lifecycle_policy" "api" {
  repository = aws_ecr_repository.api.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep the last 10 images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 10
      }
      action = { type = "expire" }
    }]
  })
}

resource "aws_cloudwatch_log_group" "api" {
  name              = "/ecs/${var.name}-api"
  retention_in_days = var.log_retention_days
}
