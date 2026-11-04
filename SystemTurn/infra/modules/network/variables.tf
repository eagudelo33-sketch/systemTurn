variable "name" {
  type        = string
  description = "Name prefix for every network resource (e.g. systemturn-dev)."

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{1,30}[a-z0-9]$", var.name))
    error_message = "name must be 3-32 chars of lowercase letters, digits and hyphens, starting with a letter."
  }
}

variable "cidr" {
  type        = string
  description = "IPv4 CIDR of the VPC. Split into six /(prefix+4) subnets: 2 AZs x public, private_app, data."

  validation {
    condition = (
      can(cidrhost(var.cidr, 0)) &&
      try(tonumber(split("/", var.cidr)[1]) >= 16 && tonumber(split("/", var.cidr)[1]) <= 24, false)
    )
    error_message = "cidr must be a valid IPv4 CIDR with a prefix between /16 and /24."
  }
}

variable "nat_mode" {
  type        = string
  description = "NAT gateways for the private_app tier: none (no NAT, no EIP, $0), single (one shared NAT), per_az (one NAT per AZ)."
  default     = "none"

  validation {
    condition     = contains(["none", "single", "per_az"], var.nat_mode)
    error_message = "nat_mode must be one of: none, single, per_az."
  }
}

variable "enable_vpc_endpoints" {
  type        = bool
  description = "Create interface endpoints (ECR api/dkr, CloudWatch Logs, Secrets Manager) in the private_app subnets. Each costs about $7.3/month per AZ."
  default     = false
}
