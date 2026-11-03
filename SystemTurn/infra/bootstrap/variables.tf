variable "region" {
  type        = string
  description = "AWS region for the state bucket and provider."
  default     = "us-east-1"

  validation {
    condition     = can(regex("^[a-z]{2}(-[a-z]+)+-[0-9]$", var.region))
    error_message = "region must look like us-east-1."
  }
}

variable "state_bucket_name" {
  type        = string
  description = "Globally unique name of the Terraform state bucket (e.g. systemturn-tfstate-<account-id>)."

  validation {
    condition     = can(regex("^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$", var.state_bucket_name))
    error_message = "state_bucket_name must be a valid S3 bucket name (3-63 chars: lowercase letters, digits, dots, hyphens)."
  }
}

variable "alert_emails" {
  type        = list(string)
  description = "Recipients of the budget alerts. Each must confirm the AWS Budgets subscription email."

  validation {
    condition = length(var.alert_emails) > 0 && alltrue([
      for e in var.alert_emails : can(regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", e))
    ])
    error_message = "alert_emails must contain at least one valid email address."
  }
}

variable "budget_amount_usd" {
  type        = number
  description = "Monthly cost budget limit in USD."
  default     = 30

  validation {
    condition     = var.budget_amount_usd > 0
    error_message = "budget_amount_usd must be greater than 0."
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
