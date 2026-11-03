# Offline tests: mocked AWS provider, plan only. Run-block names are spec scenario IDs.
mock_provider "aws" {}

variables {
  state_bucket_name = "systemturn-tfstate-test"
  alert_emails      = ["a@example.com"]
}

run "budget_defaults" {
  command = plan

  assert {
    condition = (
      aws_budgets_budget.monthly.budget_type == "COST" &&
      aws_budgets_budget.monthly.time_unit == "MONTHLY" &&
      aws_budgets_budget.monthly.limit_unit == "USD" &&
      aws_budgets_budget.monthly.limit_amount == "30"
    )
    error_message = "Defaults must be a 30 USD monthly COST budget."
  }
}

run "budget_amount_override" {
  command = plan

  variables {
    budget_amount_usd = 50
  }

  assert {
    condition     = aws_budgets_budget.monthly.limit_amount == "50"
    error_message = "limit_amount must follow budget_amount_usd."
  }
}

run "budget_amount_rejects_nonpositive" {
  command = plan

  variables {
    budget_amount_usd = 0
  }

  expect_failures = [var.budget_amount_usd]
}

run "notifications_exact_set" {
  command = plan

  assert {
    condition = toset([
      for n in aws_budgets_budget.monthly.notification : "${n.notification_type}-${n.threshold}"
    ]) == toset(["ACTUAL-50", "ACTUAL-80", "ACTUAL-100", "FORECASTED-100"])
    error_message = "Notifications must be exactly ACTUAL 50/80/100 and FORECASTED 100."
  }
  assert {
    condition     = length(aws_budgets_budget.monthly.notification) == 4
    error_message = "Exactly four notifications expected."
  }
  assert {
    condition = alltrue([
      for n in aws_budgets_budget.monthly.notification :
      n.comparison_operator == "GREATER_THAN" && n.threshold_type == "PERCENTAGE"
    ])
    error_message = "All notifications must be GREATER_THAN on PERCENTAGE."
  }
}

run "alert_emails_rejects_empty" {
  command = plan

  variables {
    alert_emails = []
  }

  expect_failures = [var.alert_emails]
}

run "alert_emails_rejects_malformed" {
  command = plan

  variables {
    alert_emails = ["not-an-email"]
  }

  expect_failures = [var.alert_emails]
}

run "alert_emails_wired_to_all_notifications" {
  command = plan

  variables {
    alert_emails = ["a@example.com", "b@example.com"]
  }

  assert {
    condition     = length(aws_budgets_budget.monthly.notification) == 4
    error_message = "Four notifications expected before checking subscribers."
  }
  assert {
    condition = alltrue([
      for n in aws_budgets_budget.monthly.notification :
      n.subscriber_email_addresses == toset(["a@example.com", "b@example.com"])
    ])
    error_message = "Every notification must notify both addresses."
  }
}
