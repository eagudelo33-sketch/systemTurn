# Offline tests: mocked AWS provider, plan only. Run-block names are spec scenario IDs.
mock_provider "aws" {}

variables {
  state_bucket_name = "systemturn-tfstate-test"
  alert_emails      = ["a@example.com"]
}

run "bucket_versioning_enabled" {
  command = plan

  assert {
    condition     = aws_s3_bucket_versioning.state.versioning_configuration[0].status == "Enabled"
    error_message = "Versioning must be Enabled."
  }
  assert {
    condition     = aws_s3_bucket.state.bucket == "systemturn-tfstate-test" && aws_s3_bucket.state.force_destroy == false
    error_message = "Bucket must be named from the variable and never force-destroyed."
  }
  assert {
    condition     = aws_s3_bucket_versioning.state.bucket == "systemturn-tfstate-test"
    error_message = "Versioning must attach to the state bucket."
  }
}

run "bucket_encryption_aes256" {
  command = plan

  assert {
    condition = [
      for r in aws_s3_bucket_server_side_encryption_configuration.state.rule :
      r.apply_server_side_encryption_by_default[0].sse_algorithm
    ] == ["AES256"]
    error_message = "SSE algorithm must be exactly AES256."
  }
}

run "bucket_blocks_public_access" {
  command = plan

  assert {
    condition = alltrue([
      aws_s3_bucket_public_access_block.state.block_public_acls,
      aws_s3_bucket_public_access_block.state.block_public_policy,
      aws_s3_bucket_public_access_block.state.ignore_public_acls,
      aws_s3_bucket_public_access_block.state.restrict_public_buckets,
    ])
    error_message = "All four Block Public Access flags must be true."
  }
  assert {
    condition     = aws_s3_bucket_public_access_block.state.bucket == "systemturn-tfstate-test"
    error_message = "Public access block must attach to the state bucket."
  }
}

run "bucket_owner_enforced" {
  command = plan

  assert {
    condition     = [for r in aws_s3_bucket_ownership_controls.state.rule : r.object_ownership] == ["BucketOwnerEnforced"]
    error_message = "Ownership must be BucketOwnerEnforced."
  }
}

run "bucket_policy_denies_insecure_transport" {
  command = plan

  assert {
    condition     = length([for s in jsondecode(aws_s3_bucket_policy.state.policy).Statement : s if s.Effect == "Deny"]) == 1
    error_message = "Exactly one Deny statement expected."
  }
  assert {
    condition = alltrue([
      for s in jsondecode(aws_s3_bucket_policy.state.policy).Statement :
      s.Action == "s3:*" && s.Principal == "*" && s.Condition.Bool["aws:SecureTransport"] == "false"
    ])
    error_message = "Deny must be s3:* for principal * when aws:SecureTransport is false."
  }
  assert {
    condition = toset(flatten([
      for s in jsondecode(aws_s3_bucket_policy.state.policy).Statement : s.Resource
    ])) == toset(["arn:aws:s3:::systemturn-tfstate-test", "arn:aws:s3:::systemturn-tfstate-test/*"])
    error_message = "Deny must cover the bucket ARN and the object ARN."
  }
}

run "lifecycle_noncurrent_expiration" {
  command = plan

  assert {
    condition = anytrue([
      for r in aws_s3_bucket_lifecycle_configuration.state.rule :
      r.status == "Enabled" && contains([for n in r.noncurrent_version_expiration : n.noncurrent_days], 90)
    ])
    error_message = "An enabled rule must expire noncurrent versions after 90 days."
  }
}

run "lifecycle_aborts_multipart" {
  command = plan

  assert {
    condition = anytrue([
      for r in aws_s3_bucket_lifecycle_configuration.state.rule :
      r.status == "Enabled" && contains([for a in r.abort_incomplete_multipart_upload : a.days_after_initiation], 7)
    ])
    error_message = "An enabled rule must abort incomplete multipart uploads after 7 days."
  }
}

run "bucket_name_rejects_invalid" {
  command = plan

  variables {
    state_bucket_name = "Bad_Name"
  }

  expect_failures = [var.state_bucket_name]
}

run "bucket_name_accepts_valid" {
  command = plan

  variables {
    state_bucket_name = "systemturn-tfstate-test"
  }

  assert {
    condition     = aws_s3_bucket.state.bucket == "systemturn-tfstate-test"
    error_message = "A valid name must plan successfully."
  }
}
