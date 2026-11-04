# tflint config. Run from repo root: tflint --init && tflint --config="$PWD/.tflint.hcl" --chdir=<root>
plugin "terraform" {
  enabled = true
  preset  = "recommended"
}

plugin "aws" {
  enabled = true
  version = "0.49.0" # v0.50.0 (2026-10-23) was <14 days old at pin time; bump once aged
  source  = "github.com/terraform-linters/tflint-ruleset-aws"
}
