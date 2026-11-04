# Offline tests: mocked AWS provider, no credentials. Run-block names are scenario IDs.
# Runs that compare resource ids or decode generated JSON use `command = apply`: with a mocked
# provider the apply is fully offline, and it is the only way generated ids reach dependents.
mock_provider "aws" {
  mock_resource "aws_db_instance" {
    defaults = {
      address = "systemturn-test-db.abc123.us-east-1.rds.amazonaws.com"
      port    = 5432
      master_user_secret = [{
        secret_arn    = "arn:aws:secretsmanager:us-east-1:123456789012:secret:rds!db-0000-AbCdEf"
        kms_key_id    = "arn:aws:kms:us-east-1:123456789012:key/00000000-0000-0000-0000-000000000000"
        secret_status = "active"
      }]
    }
  }

  mock_resource "aws_cloudfront_distribution" {
    defaults = {
      arn         = "arn:aws:cloudfront::123456789012:distribution/EDFDVBD6EXAMPLE"
      domain_name = "d111111abcdef8.cloudfront.net"
    }
  }

  # The provider still validates ARN-typed arguments, so referenced ARNs need a valid shape.
  mock_resource "aws_cloudfront_function" {
    defaults = {
      arn = "arn:aws:cloudfront::123456789012:function/systemturn-test-spa-rewrite"
    }
  }

  mock_resource "aws_lb" {
    defaults = {
      arn      = "arn:aws:elasticloadbalancing:us-east-1:123456789012:loadbalancer/app/systemturn-test-alb/50dc6c495c0c9188"
      dns_name = "systemturn-test-alb-123.us-east-1.elb.amazonaws.com"
    }
  }

  mock_resource "aws_lb_target_group" {
    defaults = {
      arn = "arn:aws:elasticloadbalancing:us-east-1:123456789012:targetgroup/systemturn-test-api/73e2d6bc24d8a067"
    }
  }

  mock_resource "aws_iam_role" {
    defaults = {
      arn = "arn:aws:iam::123456789012:role/systemturn-test-api"
    }
  }

  mock_resource "aws_ecr_repository" {
    defaults = {
      arn            = "arn:aws:ecr:us-east-1:123456789012:repository/systemturn-test-api"
      repository_url = "123456789012.dkr.ecr.us-east-1.amazonaws.com/systemturn-test-api"
    }
  }

  mock_resource "aws_cloudwatch_log_group" {
    defaults = {
      arn = "arn:aws:logs:us-east-1:123456789012:log-group:/ecs/systemturn-test-api"
    }
  }

  mock_resource "aws_ecs_task_definition" {
    defaults = {
      arn = "arn:aws:ecs:us-east-1:123456789012:task-definition/systemturn-test-api:1"
    }
  }

  mock_resource "aws_s3_bucket" {
    defaults = {
      arn                         = "arn:aws:s3:::systemturn-test-web-0001"
      bucket_regional_domain_name = "systemturn-test-web-0001.s3.us-east-1.amazonaws.com"
    }
  }

  mock_data "aws_region" {
    defaults = {
      region = "us-east-1"
    }
  }
}

variables {
  name                   = "systemturn-test"
  vpc_id                 = "vpc-0123456789abcdef0"
  public_subnet_ids      = ["subnet-pub-a", "subnet-pub-b"]
  private_app_subnet_ids = ["subnet-app-a", "subnet-app-b"]
  data_subnet_ids        = ["subnet-data-a", "subnet-data-b"]
}

run "desired_count_defaults_to_zero" {
  command = plan

  assert {
    condition     = var.desired_count == 0 && aws_ecs_service.api.desired_count == 0
    error_message = "The ECS service must default to 0 running tasks."
  }

  assert {
    condition = (
      aws_ecs_service.api.launch_type == "FARGATE" &&
      toset(aws_ecs_service.api.network_configuration[0].subnets) == toset(var.private_app_subnet_ids) &&
      aws_ecs_service.api.network_configuration[0].assign_public_ip == false
    )
    error_message = "Tasks must run on Fargate in the private_app subnets without public IPs."
  }
}

run "desired_count_rejects_negative" {
  command = plan

  variables {
    desired_count = -1
  }

  expect_failures = [var.desired_count]
}

run "rds_private_in_data_subnets" {
  command = apply

  assert {
    condition     = aws_db_instance.this.publicly_accessible == false
    error_message = "RDS must not be publicly accessible."
  }

  assert {
    condition = (
      aws_db_instance.this.db_subnet_group_name == aws_db_subnet_group.this.name &&
      toset(aws_db_subnet_group.this.subnet_ids) == toset(var.data_subnet_ids)
    )
    error_message = "RDS must live in the data subnets only."
  }

  assert {
    condition = (
      aws_db_instance.this.engine == "postgres" &&
      aws_db_instance.this.engine_version == "17" &&
      aws_db_instance.this.instance_class == "db.t4g.micro" &&
      aws_db_instance.this.allocated_storage == 20 &&
      aws_db_instance.this.storage_type == "gp3" &&
      aws_db_instance.this.storage_encrypted
    )
    error_message = "RDS must be encrypted PostgreSQL 17 on db.t4g.micro with 20 GB gp3."
  }
}

run "rds_dev_defaults" {
  command = plan

  assert {
    condition = (
      aws_db_instance.this.manage_master_user_password == true &&
      aws_db_instance.this.password == null &&
      aws_db_instance.this.deletion_protection == false &&
      aws_db_instance.this.skip_final_snapshot == true &&
      aws_db_instance.this.backup_retention_period == 1
    )
    error_message = "RDS must own its password secret and use the dev lifecycle defaults."
  }
}

run "rds_final_snapshot_when_not_skipped" {
  command = plan

  variables {
    db_skip_final_snapshot = false
    db_deletion_protection = true
  }

  assert {
    condition = (
      aws_db_instance.this.deletion_protection == true &&
      aws_db_instance.this.final_snapshot_identifier == "systemturn-test-final"
    )
    error_message = "Lifecycle variables must reach the DB instance."
  }
}

run "db_sg_ingress_only_from_service_sg" {
  command = apply

  assert {
    condition = (
      aws_vpc_security_group_ingress_rule.db_from_service.security_group_id == aws_security_group.db.id &&
      aws_vpc_security_group_ingress_rule.db_from_service.referenced_security_group_id == aws_security_group.service.id &&
      aws_vpc_security_group_ingress_rule.db_from_service.cidr_ipv4 == null &&
      aws_vpc_security_group_ingress_rule.db_from_service.from_port == 5432 &&
      aws_vpc_security_group_ingress_rule.db_from_service.to_port == 5432
    )
    error_message = "The DB security group must allow 5432 from the ECS service security group only."
  }

  assert {
    condition = length([
      for r in [
        aws_vpc_security_group_ingress_rule.alb_http,
        aws_vpc_security_group_ingress_rule.service_from_alb,
        aws_vpc_security_group_ingress_rule.db_from_service,
      ] : r if r.security_group_id == aws_security_group.db.id
    ]) == 1
    error_message = "No other ingress rule may target the DB security group."
  }

  assert {
    condition     = aws_db_instance.this.vpc_security_group_ids == toset([aws_security_group.db.id])
    error_message = "RDS must use only the DB security group."
  }
}

run "alb_health_check_readiness" {
  command = plan

  assert {
    condition = (
      aws_lb_target_group.api.target_type == "ip" &&
      aws_lb_target_group.api.port == 8080 &&
      aws_lb_target_group.api.health_check[0].path == "/actuator/health/readiness"
    )
    error_message = "Target group must be type ip on 8080 with the readiness health check."
  }

  assert {
    condition = (
      aws_lb.this.internal == false &&
      aws_lb.this.idle_timeout == 300 &&
      aws_lb.this.drop_invalid_header_fields == true &&
      aws_lb_listener.http.port == 80 &&
      aws_lb_listener.http.protocol == "HTTP"
    )
    error_message = "ALB must be public, idle_timeout 300 for WebSockets, with an HTTP:80 listener."
  }
}

run "task_gets_password_from_secrets" {
  command = apply

  assert {
    condition = anytrue([
      for s in jsondecode(aws_ecs_task_definition.api.container_definitions)[0].secrets :
      s.name == "SPRING_DATASOURCE_PASSWORD" &&
      s.valueFrom == "${aws_db_instance.this.master_user_secret[0].secret_arn}:password::"
    ])
    error_message = "SPRING_DATASOURCE_PASSWORD must come from the RDS-managed secret via `secrets`."
  }

  assert {
    condition = !contains(
      [for e in jsondecode(aws_ecs_task_definition.api.container_definitions)[0].environment : e.name],
      "SPRING_DATASOURCE_PASSWORD"
    )
    error_message = "The password must never be a plain environment variable."
  }

  assert {
    condition = {
      for e in jsondecode(aws_ecs_task_definition.api.container_definitions)[0].environment : e.name => e.value
      } == {
      SPRING_DATASOURCE_URL      = "jdbc:postgresql://systemturn-test-db.abc123.us-east-1.rds.amazonaws.com:5432/turnos?sslmode=require&socketTimeout=30"
      SPRING_DATASOURCE_USERNAME = "turnos"
      TURNOS_DB_POOL_SIZE        = "5"
      TURNOS_ALLOWED_ORIGINS     = "https://d111111abcdef8.cloudfront.net"
    }
    error_message = "Container environment must carry the DB url/user, pool size and CORS origin."
  }

  assert {
    condition = (
      jsondecode(aws_ecs_task_definition.api.container_definitions)[0].stopTimeout == 30 &&
      jsondecode(aws_ecs_task_definition.api.container_definitions)[0].portMappings[0].containerPort == 8080 &&
      jsondecode(aws_ecs_task_definition.api.container_definitions)[0].logConfiguration.options["awslogs-group"] == aws_cloudwatch_log_group.api.name
    )
    error_message = "Container must expose 8080, log to CloudWatch and allow a graceful stop."
  }
}

run "execution_role_least_privilege" {
  command = apply

  assert {
    condition = toset(flatten([
      for st in jsondecode(aws_iam_role_policy.execution.policy).Statement : st.Resource if contains(flatten([st.Action]), "secretsmanager:GetSecretValue")
    ])) == toset([aws_db_instance.this.master_user_secret[0].secret_arn])
    error_message = "The execution role may read only the RDS-managed secret."
  }

  assert {
    condition = alltrue([
      for st in jsondecode(aws_iam_role_policy.execution.policy).Statement :
      st.Resource != "*" || flatten([st.Action]) == ["ecr:GetAuthorizationToken"]
    ])
    error_message = "Only ecr:GetAuthorizationToken (which has no resource scope) may use Resource *."
  }

  assert {
    condition     = length(aws_iam_role_policies_exclusive.task.policy_names) == 0
    error_message = "The task role must have no inline policies: the app calls no AWS API."
  }
}

run "ecr_and_logs_defaults" {
  command = plan

  assert {
    condition = (
      aws_ecr_repository.api.image_tag_mutability == "IMMUTABLE" &&
      aws_ecr_repository.api.image_scanning_configuration[0].scan_on_push == true &&
      jsondecode(aws_ecr_lifecycle_policy.api.policy).rules[0].selection.countNumber == 10
    )
    error_message = "ECR must use immutable tags, scan on push and keep the last 10 images."
  }

  assert {
    condition     = aws_cloudwatch_log_group.api.retention_in_days == 14
    error_message = "Log retention must default to 14 days."
  }
}

run "frontend_bucket_private_behind_oac" {
  command = plan

  assert {
    condition = (
      aws_s3_bucket_public_access_block.frontend.block_public_acls &&
      aws_s3_bucket_public_access_block.frontend.block_public_policy &&
      aws_s3_bucket_public_access_block.frontend.ignore_public_acls &&
      aws_s3_bucket_public_access_block.frontend.restrict_public_buckets
    )
    error_message = "The frontend bucket must block all public access."
  }

  assert {
    condition = (
      aws_cloudfront_origin_access_control.frontend.origin_access_control_origin_type == "s3" &&
      aws_cloudfront_origin_access_control.frontend.signing_behavior == "always" &&
      aws_cloudfront_distribution.frontend.default_cache_behavior[0].viewer_protocol_policy == "redirect-to-https"
    )
    error_message = "CloudFront must reach S3 through OAC and serve viewers over HTTPS."
  }
}
