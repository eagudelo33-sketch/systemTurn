resource "aws_ecs_cluster" "this" {
  name = var.name

  # Container Insights bills per metric; plain CloudWatch logs are enough for dev.
  setting {
    name  = "containerInsights"
    value = "disabled"
  }
}

resource "aws_ecs_task_definition" "api" {
  family                   = "${var.name}-api"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.task_cpu
  memory                   = var.task_memory
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = var.cpu_architecture
  }

  container_definitions = jsonencode([{
    name      = local.container_name
    image     = "${aws_ecr_repository.api.repository_url}:${var.image_tag}"
    essential = true

    portMappings = [{
      containerPort = var.container_port
      protocol      = "tcp"
    }]

    environment = [
      { name = "SPRING_DATASOURCE_URL", value = local.jdbc_url },
      { name = "SPRING_DATASOURCE_USERNAME", value = var.db_username },
      { name = "TURNOS_DB_POOL_SIZE", value = tostring(var.db_pool_size) },
      { name = "TURNOS_ALLOWED_ORIGINS", value = local.public_origin },
    ]

    # Injected by the ECS agent from the RDS-managed secret; never stored in the task definition.
    secrets = [
      { name = "SPRING_DATASOURCE_PASSWORD", valueFrom = "${local.db_secret_arn}:password::" },
    ]

    # SIGTERM -> Spring graceful shutdown (25s phase) -> SIGKILL after stopTimeout.
    stopTimeout = var.stop_timeout

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.api.name
        awslogs-region        = data.aws_region.current.region
        awslogs-stream-prefix = local.container_name
      }
    }
  }])
}

resource "aws_ecs_service" "api" {
  name            = "${var.name}-api"
  cluster         = aws_ecs_cluster.this.id
  task_definition = aws_ecs_task_definition.api.arn
  desired_count   = var.desired_count
  launch_type     = "FARGATE"

  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200
  health_check_grace_period_seconds  = 90
  propagate_tags                     = "SERVICE"

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  network_configuration {
    subnets          = var.private_app_subnet_ids
    security_groups  = [aws_security_group.service.id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.api.arn
    container_name   = local.container_name
    container_port   = var.container_port
  }

  depends_on = [aws_lb_listener.http, aws_iam_role_policy.execution]
}
