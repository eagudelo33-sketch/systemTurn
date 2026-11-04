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
