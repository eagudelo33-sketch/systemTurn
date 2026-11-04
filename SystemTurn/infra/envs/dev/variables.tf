variable "region" {
  type        = string
  description = "AWS region for this environment."
  default     = "us-east-1"

  validation {
    condition     = can(regex("^[a-z]{2}(-[a-z]+)+-[0-9]$", var.region))
    error_message = "region must look like us-east-1."
  }
}

variable "owner" {
  type        = string
  description = "Value of the Owner tag."
  default     = "systemturn"

  validation {
    condition     = length(trimspace(var.owner)) > 0
    error_message = "owner must not be empty."
  }
}

variable "vpc_cidr" {
  type        = string
  description = "IPv4 CIDR of the dev VPC."
  default     = "10.20.0.0/16"
}

variable "nat_mode" {
  type        = string
  description = "NAT for private_app subnets: none ($0, default), single (~$33/month) or per_az (~$66/month)."
  default     = "none"
}

variable "enable_vpc_endpoints" {
  type        = bool
  description = "Interface endpoints for ECR, Logs and Secrets Manager (~$58/month for 2 AZs)."
  default     = false
}

variable "enable_app" {
  type        = bool
  description = "Create the paid app layer (RDS, ALB, ECS, ECR, CloudFront). false keeps dev at ~$0."
  default     = false
}

variable "app_image_tag" {
  type        = string
  description = "API image tag in ECR (immutable tags)."
  default     = "initial"
}

variable "app_desired_count" {
  type        = number
  description = "Running API tasks. Needs nat_mode != none or enable_vpc_endpoints = true to start."
  default     = 0
}
