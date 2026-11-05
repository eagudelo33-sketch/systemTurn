# Offline tests: mocked AWS provider. Run-block names are spec scenario IDs (infra-cicd).
# Role runs use `apply` against the mock so the provider ARN is known and compared in the trust.
mock_provider "aws" {
  mock_resource "aws_iam_openid_connect_provider" {
    defaults = {
      arn = "arn:aws:iam::123456789012:oidc-provider/token.actions.githubusercontent.com"
    }
  }
}

variables {
  state_bucket_name = "systemturn-tfstate-test"
  alert_emails      = ["a@example.com"]
}

run "oidc_provider_created" {
  command = plan

  assert {
    condition     = aws_iam_openid_connect_provider.github.url == "https://token.actions.githubusercontent.com"
    error_message = "Provider URL must be the GitHub Actions OIDC issuer."
  }
  assert {
    condition     = aws_iam_openid_connect_provider.github.client_id_list == toset(["sts.amazonaws.com"])
    error_message = "client_id_list must be exactly sts.amazonaws.com."
  }
}

run "plan_role_trust_is_pull_request_only" {
  command = apply

  assert {
    condition     = length(jsondecode(aws_iam_role.plan.assume_role_policy).Statement) == 1
    error_message = "Trust must have exactly one statement."
  }
  assert {
    condition = (
      jsondecode(aws_iam_role.plan.assume_role_policy).Statement[0].Action == "sts:AssumeRoleWithWebIdentity" &&
      jsondecode(aws_iam_role.plan.assume_role_policy).Statement[0].Principal.Federated == aws_iam_openid_connect_provider.github.arn
    )
    error_message = "Trust must allow only AssumeRoleWithWebIdentity from the GitHub OIDC provider."
  }
  assert {
    condition = (
      jsondecode(aws_iam_role.plan.assume_role_policy).Statement[0].Condition.StringEquals["token.actions.githubusercontent.com:sub"] == "repo:eagudelo33-sketch/systemTurn:pull_request" &&
      jsondecode(aws_iam_role.plan.assume_role_policy).Statement[0].Condition.StringEquals["token.actions.githubusercontent.com:aud"] == "sts.amazonaws.com"
    )
    error_message = "sub must be exactly repo:<repo>:pull_request and aud sts.amazonaws.com."
  }
  assert {
    condition = (
      !contains(keys(jsondecode(aws_iam_role.plan.assume_role_policy).Statement[0].Condition), "StringLike") &&
      !strcontains(jsondecode(aws_iam_role.plan.assume_role_policy).Statement[0].Condition.StringEquals["token.actions.githubusercontent.com:sub"], "*")
    )
    error_message = "No StringLike and no wildcard in sub."
  }
}

run "apply_role_trust_is_prod_environment_only" {
  command = apply

  assert {
    condition = (
      jsondecode(aws_iam_role.apply.assume_role_policy).Statement[0].Action == "sts:AssumeRoleWithWebIdentity" &&
      jsondecode(aws_iam_role.apply.assume_role_policy).Statement[0].Principal.Federated == aws_iam_openid_connect_provider.github.arn &&
      jsondecode(aws_iam_role.apply.assume_role_policy).Statement[0].Condition.StringEquals["token.actions.githubusercontent.com:aud"] == "sts.amazonaws.com"
    )
    error_message = "Apply trust must be AssumeRoleWithWebIdentity from the provider with aud sts.amazonaws.com."
  }
  assert {
    condition     = jsondecode(aws_iam_role.apply.assume_role_policy).Statement[0].Condition.StringEquals["token.actions.githubusercontent.com:sub"] == "repo:eagudelo33-sketch/systemTurn:environment:prod"
    error_message = "sub must be exactly repo:<repo>:environment:prod."
  }
  assert {
    condition = (
      jsondecode(aws_iam_role.apply.assume_role_policy).Statement[0].Condition.StringEquals["token.actions.githubusercontent.com:sub"] !=
      jsondecode(aws_iam_role.plan.assume_role_policy).Statement[0].Condition.StringEquals["token.actions.githubusercontent.com:sub"]
    )
    error_message = "Apply and plan subs must differ."
  }
}

run "plan_role_is_read_only" {
  command = apply

  assert {
    condition     = aws_iam_role_policy_attachments_exclusive.plan.policy_arns == toset(["arn:aws:iam::aws:policy/ReadOnlyAccess"])
    error_message = "Plan role must have exactly ReadOnlyAccess attached."
  }
  assert {
    condition = toset(flatten([
      for s in jsondecode(aws_iam_role_policy.plan_state.policy).Statement : s.Action
    ])) == toset(["s3:GetObject", "s3:ListBucket"])
    error_message = "Plan inline policy must allow only s3:GetObject and s3:ListBucket."
  }
  assert {
    condition = toset(flatten([
      for s in jsondecode(aws_iam_role_policy.plan_state.policy).Statement : s.Resource
      ])) == toset([
      "arn:aws:s3:::systemturn-tfstate-test",
      "arn:aws:s3:::systemturn-tfstate-test/dev/*",
      "arn:aws:s3:::systemturn-tfstate-test/prod/*",
    ])
    error_message = "Plan inline policy must be limited to the state bucket and its dev/ and prod/ objects."
  }
}

run "apply_role_is_state_only" {
  command = apply

  assert {
    condition     = length(aws_iam_role_policy_attachments_exclusive.apply.policy_arns) == 0
    error_message = "Apply role must have no managed policy attached."
  }
  assert {
    condition = alltrue([
      for a in flatten([for s in jsondecode(aws_iam_role_policy.apply_state.policy).Statement : s.Action]) :
      startswith(a, "s3:")
    ])
    error_message = "Apply role may use only s3 actions."
  }
  assert {
    condition = alltrue([
      for r in flatten([for s in jsondecode(aws_iam_role_policy.apply_state.policy).Statement : s.Resource]) :
      r == "arn:aws:s3:::systemturn-tfstate-test" || startswith(r, "arn:aws:s3:::systemturn-tfstate-test/prod/")
    ])
    error_message = "Every resource must be the state bucket or under <bucket>/prod/."
  }
  assert {
    condition = alltrue([
      for s in jsondecode(aws_iam_role_policy.apply_state.policy).Statement :
      s.Resource == ["arn:aws:s3:::systemturn-tfstate-test/prod/*.tflock"]
      if contains(s.Action, "s3:DeleteObject")
    ])
    error_message = "s3:DeleteObject must apply only to prod/*.tflock."
  }
  assert {
    condition = length([
      for s in jsondecode(aws_iam_role_policy.apply_state.policy).Statement : s if contains(s.Action, "s3:DeleteObject")
    ]) == 1
    error_message = "Exactly one statement may grant s3:DeleteObject."
  }
}

run "repo_identity_is_single_variable" {
  command = apply

  variables {
    github_repository = "acme/other"
  }

  assert {
    condition = (
      jsondecode(aws_iam_role.plan.assume_role_policy).Statement[0].Condition.StringEquals["token.actions.githubusercontent.com:sub"] == "repo:acme/other:pull_request" &&
      jsondecode(aws_iam_role.apply.assume_role_policy).Statement[0].Condition.StringEquals["token.actions.githubusercontent.com:sub"] == "repo:acme/other:environment:prod"
    )
    error_message = "Both trusts must follow var.github_repository."
  }
}

run "repo_identity_rejects_invalid" {
  command = plan

  variables {
    github_repository = "not a repo"
  }

  expect_failures = [var.github_repository]
}
