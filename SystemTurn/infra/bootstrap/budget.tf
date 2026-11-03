locals {
  budget_thresholds = [
    { type = "ACTUAL", percent = 50 },
    { type = "ACTUAL", percent = 80 },
    { type = "ACTUAL", percent = 100 },
    { type = "FORECASTED", percent = 100 },
  ]
}

# Notifies only: no budget actions. At 100% destroy envs/dev manually (see runbook).
resource "aws_budgets_budget" "monthly" {
  name         = "systemturn-monthly-cost"
  budget_type  = "COST"
  time_unit    = "MONTHLY"
  limit_amount = tostring(var.budget_amount_usd)
  limit_unit   = "USD"

  dynamic "notification" {
    for_each = local.budget_thresholds

    content {
      comparison_operator        = "GREATER_THAN"
      threshold                  = notification.value.percent
      threshold_type             = "PERCENTAGE"
      notification_type          = notification.value.type
      subscriber_email_addresses = var.alert_emails
    }
  }
}
