# --- Identity and network wiring ---

variable "name" {
  type        = string
  description = "Name prefix for every app resource (e.g. systemturn-dev)."

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{1,22}[a-z0-9]$", var.name))
    error_message = "name must be 3-24 chars of lowercase letters, digits and hyphens (ALB and target group names are capped at 32)."
  }
}

variable "vpc_id" {
  type        = string
  description = "VPC id from the network module."
}

variable "public_subnet_ids" {
  type        = list(string)
  description = "Public subnets for the ALB (at least 2 AZs)."

  validation {
    condition     = length(var.public_subnet_ids) >= 2
    error_message = "The ALB needs public subnets in at least 2 AZs."
  }
}

variable "private_app_subnet_ids" {
  type        = list(string)
  description = "Private subnets for the ECS tasks."

  validation {
    condition     = length(var.private_app_subnet_ids) >= 1
    error_message = "At least one private_app subnet is required."
  }
}

variable "data_subnet_ids" {
  type        = list(string)
  description = "Data subnets for RDS (at least 2 AZs, required by DB subnet groups)."

  validation {
    condition     = length(var.data_subnet_ids) >= 2
    error_message = "RDS subnet groups need data subnets in at least 2 AZs."
  }
}

# --- Container / ECS ---

variable "container_port" {
  type        = number
  description = "Port the Spring Boot container listens on (server.port)."
  default     = 8080
}

variable "image_tag" {
  type        = string
  description = "Image tag deployed from the ECR repository. Tags are immutable: push a new tag per release."
  default     = "initial"
}

variable "desired_count" {
  type        = number
  description = "Number of running API tasks. 0 keeps the service defined but costs nothing for Fargate."
  default     = 0

  validation {
    condition     = var.desired_count >= 0 && floor(var.desired_count) == var.desired_count
    error_message = "desired_count must be a non-negative integer."
  }
}

variable "task_cpu" {
  type        = number
  description = "Fargate task CPU units."
  default     = 512
}

variable "task_memory" {
  type        = number
  description = "Fargate task memory (MiB)."
  default     = 1024
}

variable "cpu_architecture" {
  type        = string
  description = "Fargate CPU architecture; must match the pushed image (X86_64 or ARM64)."
  default     = "X86_64"

  validation {
    condition     = contains(["X86_64", "ARM64"], var.cpu_architecture)
    error_message = "cpu_architecture must be X86_64 or ARM64."
  }
}

variable "stop_timeout" {
  type        = number
  description = "Seconds ECS waits after SIGTERM before SIGKILL. Must exceed spring.lifecycle.timeout-per-shutdown-phase (25s)."
  default     = 30

  validation {
    condition     = var.stop_timeout >= 2 && var.stop_timeout <= 120
    error_message = "stop_timeout must be between 2 and 120 seconds (Fargate limit)."
  }
}

variable "db_pool_size" {
  type        = number
  description = "Hikari pool size per task (TURNOS_DB_POOL_SIZE). pool size x tasks must stay below max_connections."
  default     = 5
}

variable "log_retention_days" {
  type        = number
  description = "CloudWatch log retention for the API container."
  default     = 14
}

variable "ecr_force_delete" {
  type        = bool
  description = "Allow destroying the ECR repository while it still holds images."
  default     = false
}

# --- Load balancer ---

variable "health_check_path" {
  type        = string
  description = "ALB target group health check path (Spring readiness probe)."
  default     = "/actuator/health/readiness"
}

variable "alb_idle_timeout" {
  type        = number
  description = "ALB idle timeout in seconds. Long enough for idle WebSocket connections."
  default     = 300

  validation {
    condition     = var.alb_idle_timeout >= 1 && var.alb_idle_timeout <= 4000
    error_message = "alb_idle_timeout must be between 1 and 4000 seconds."
  }
}

# --- Database ---

variable "db_instance_class" {
  type        = string
  description = "RDS instance class."
  default     = "db.t4g.micro"
}

variable "db_engine_version" {
  type        = string
  description = "PostgreSQL major version (minor upgrades are automatic)."
  default     = "17"
}

variable "db_allocated_storage" {
  type        = number
  description = "RDS storage in GB (gp3)."
  default     = 20
}

variable "db_name" {
  type        = string
  description = "Initial database name."
  default     = "turnos"
}

variable "db_username" {
  type        = string
  description = "Master username. The password is generated and stored in Secrets Manager by RDS."
  default     = "turnos"
}

variable "db_backup_retention_days" {
  type        = number
  description = "Automated backup retention in days."
  default     = 1
}

variable "db_deletion_protection" {
  type        = bool
  description = "Block deleting the DB instance. Set true outside dev."
  default     = false
}

variable "db_skip_final_snapshot" {
  type        = bool
  description = "Skip the final snapshot on destroy. Set false outside dev."
  default     = true
}

# --- Frontend ---

variable "frontend_force_destroy" {
  type        = bool
  description = "Allow destroying the frontend bucket while it still holds objects."
  default     = false
}

variable "cloudfront_price_class" {
  type        = string
  description = "CloudFront price class."
  default     = "PriceClass_100"
}
