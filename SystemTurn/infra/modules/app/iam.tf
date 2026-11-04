locals {
  ecs_tasks_assume_role = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

# Execution role: used by the ECS agent to pull the image, read the DB secret and ship logs.
resource "aws_iam_role" "execution" {
  name               = "${var.name}-api-execution"
  assume_role_policy = local.ecs_tasks_assume_role
}

# ecr:GetAuthorizationToken has no resource-level scope, so it is the only Resource "*".
resource "aws_iam_role_policy" "execution" {
  name = "pull-image-read-secret-write-logs"
  role = aws_iam_role.execution.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "EcrAuth"
        Effect   = "Allow"
        Action   = ["ecr:GetAuthorizationToken"]
        Resource = "*"
      },
      {
        Sid      = "EcrPull"
        Effect   = "Allow"
        Action   = ["ecr:BatchCheckLayerAvailability", "ecr:BatchGetImage", "ecr:GetDownloadUrlForLayer"]
        Resource = aws_ecr_repository.api.arn
      },
      {
        Sid      = "DbSecret"
        Effect   = "Allow"
        Action   = ["secretsmanager:GetSecretValue"]
        Resource = local.db_secret_arn
      },
      {
        Sid      = "Logs"
        Effect   = "Allow"
        Action   = ["logs:CreateLogStream", "logs:PutLogEvents"]
        Resource = "${aws_cloudwatch_log_group.api.arn}:*"
      },
    ]
  })
}

# Task role: the identity of the application itself. The API calls no AWS service today, so it
# has no permissions; the exclusive lock removes any inline policy added out of band.
resource "aws_iam_role" "task" {
  name               = "${var.name}-api-task"
  assume_role_policy = local.ecs_tasks_assume_role
}

resource "aws_iam_role_policies_exclusive" "task" {
  role_name    = aws_iam_role.task.name
  policy_names = []
}
