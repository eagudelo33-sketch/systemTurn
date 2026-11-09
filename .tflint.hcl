# tflint config. Run from repo root: tflint --init && tflint --config="$PWD/.tflint.hcl" --chdir=<root>
plugin "terraform" {
  enabled = true
  preset  = "recommended"
}

plugin "aws" {
  enabled = true
  version = "0.50.0" # v0.50.0 (2026-10-23), 17 days old at bump (2026-11-09)
  source  = "github.com/terraform-linters/tflint-ruleset-aws"
}
