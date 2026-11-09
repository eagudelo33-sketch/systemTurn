#!/usr/bin/env bash
# Repo-convention checks for SystemTurn/infra. Works in Git Bash and Linux; no AWS account needed.
# Usage: check-conventions.sh [CHECK_ID...]   (default: all except opt-in checks). Output label = spec scenario ID.
# Exit 1 on any failure, unknown check ID, or when zero checks ran.
set -u
ROOT=$(git rev-parse --show-toplevel 2>/dev/null) || { echo "FAIL not inside a git repository"; exit 1; }
cd "$ROOT" || exit 1
INFRA=SystemTurn/infra
ROOTS="$INFRA/bootstrap $INFRA/envs/dev $INFRA/envs/prod"
ENVS="$INFRA/envs/dev $INFRA/envs/prod"
RC=0

fail() { echo "  $*"; return 1; }
# ignored/not_ignored evaluate ignore rules only (--no-index), tracked state is irrelevant
ignored() { local p; for p in "$@"; do git check-ignore -q --no-index -- "$p" || fail "not ignored: $p" || return 1; done; }
not_ignored() { local p; for p in "$@"; do ! git check-ignore -q --no-index -- "$p" || fail "ignored: $p" || return 1; done; }
need() { [ -f "$1" ] || fail "missing file: $1"; }
# block FILE START_REGEX [END_REGEX]: print from the first START match through END (default: top-level "^}")
block() { local end="${3:-}"; [ -n "$end" ] || end='^}[ ]*$'; awk -v re="$2" -v end="$end" '$0 ~ re {on=1; if ($0 ~ /\{[ ]*\}/) {print; exit}} on {print} on && $0 ~ end {exit}' "$1"; }

state_and_plan_files_ignored() {
  ignored $INFRA/envs/dev/terraform.tfstate $INFRA/envs/dev/terraform.tfstate.backup \
    $INFRA/bootstrap/out.tfplan $INFRA/bootstrap/.terraform/x $INFRA/bootstrap/backend_override.tf
}
real_tfvars_and_backend_ignored() {
  ignored $INFRA/bootstrap/terraform.tfvars $INFRA/envs/dev/dev.auto.tfvars \
    $INFRA/envs/dev/backend.hcl $INFRA/envs/prod/backend.hcl
}
examples_and_locks_not_ignored() {
  not_ignored $INFRA/bootstrap/terraform.tfvars.example $INFRA/envs/dev/backend.hcl.example \
    $INFRA/envs/dev/.terraform.lock.hcl
}
gitattributes_forces_lf() {
  local p out
  for p in a.tf b.hcl c.tftest.hcl .terraform.lock.hcl x.sh; do
    out=$(git check-attr eol -- "$p")
    [ "${out##* }" = "lf" ] || fail "eol not lf: $out" || return 1
  done
}
no_sensitive_files_tracked() { # index-based: staged files count as tracked
  local hits
  hits=$(git ls-files | grep -E '\.tfstate(\.|$)|\.tfplan$|\.tfvars(\.json)?$|(^|/)backend\.hcl$')
  [ -z "$hits" ] || fail "sensitive files tracked: $hits"
}
tracked_infra_files_have_no_crlf() {
  local f bad=""
  while IFS= read -r f; do
    [ -f "$f" ] && grep -qU $'\r' "$f" && bad="$bad $f"
  done < <(git ls-files -- "$INFRA/*.tf" "$INFRA/*.hcl")
  [ -z "$bad" ] || fail "CRLF in:$bad"
}
no_account_id_in_tracked_infra_files() {
  local hits
  hits=$(git ls-files -- "$INFRA" SystemTurn/docs | grep -v '\.terraform\.lock\.hcl$' \
    | xargs grep -HoE '\b[0-9]{12}\b' 2>/dev/null | grep -vE ':(123456789012|000000000000)$')
  [ -z "$hits" ] || fail "possible account id: $hits"
}
no_app_files_touched() { # working tree vs merge-base with main, plus untracked files
  local base hits
  base=$(git merge-base HEAD main 2>/dev/null || git merge-base HEAD origin/main 2>/dev/null) \
    || fail "cannot resolve main/origin/main" || return 1
  hits=$( { git diff --name-only "$base"; git ls-files --others --exclude-standard; } \
    | grep -E '^SystemTurn/(backend|frontend)/')
  [ -z "$hits" ] || fail "application files touched: $hits"
}
no_budget_actions() {
  local hits
  hits=$(git ls-files -- "$INFRA/*.tf" | xargs grep -l 'aws_budgets_budget_action' 2>/dev/null)
  [ -z "$hits" ] || fail "budget actions in: $hits"
}
no_generated_readmes() {
  local hits
  hits=$(git ls-files -- "$INFRA/*README.md" | xargs grep -l 'BEGIN_TF_DOCS' 2>/dev/null)
  [ -z "$hits" ] || fail "generated docs committed: $hits"
}

# --- Checks whose artifacts arrive in later units: red until the artifact exists ---
version_constraints_pinned() {
  local r
  for r in $ROOTS; do
    need "$r/versions.tf" || return 1
    grep -qE 'required_version *= *"~> 1\.11"' "$r/versions.tf" || fail "$r: terraform pin" || return 1
    grep -qE 'source *= *"hashicorp/aws"' "$r/versions.tf" || fail "$r: aws source" || return 1
    grep -qE 'version *= *"~> 6\.69"' "$r/versions.tf" || fail "$r: aws pin" || return 1
    cmp -s "$r/versions.tf" "$INFRA/bootstrap/versions.tf" || fail "$r: versions.tf differs from bootstrap" || return 1
  done
}
lockfile_pins_aws_6_69_1() {
  local r l
  for r in $ROOTS; do
    l=$r/.terraform.lock.hcl; need "$l" || return 1
    block "$l" '^provider "registry.terraform.io/hashicorp/aws"' | grep -qE 'version *= *"6\.69\.1"' \
      || fail "$l: aws not pinned to 6.69.1" || return 1
  done
}
lockfile_covers_both_platforms() {
  local r l
  for r in $ROOTS; do
    l=$r/.terraform.lock.hcl; need "$l" || return 1
    [ "$(grep -cE '^[ ]*"(h1|zh):' "$l")" -gt 1 ] || fail "$l: <2 hashes" || return 1
    not_ignored "$l" || return 1
  done
}
provider_has_no_credentials_or_profile() {
  local r
  for r in $ROOTS; do
    need "$r/providers.tf" || return 1
    ! grep -qE '^[ ]*(profile|access_key|secret_key|shared_credentials_files?)[ ]*=' "$r/providers.tf" \
      || fail "$r: credentials/profile in providers.tf" || return 1
  done
}
backend_example_keys_distinct() {
  local e
  for e in dev prod; do
    need "$INFRA/envs/$e/backend.hcl.example" || return 1
    grep -qE "^key *= *\"$e/terraform\.tfstate\"" "$INFRA/envs/$e/backend.hcl.example" || fail "$e: key" || return 1
  done
}
backend_example_native_locking() {
  local e f
  for e in dev prod; do
    f=$INFRA/envs/$e/backend.hcl.example; need "$f" || return 1
    grep -qE '^use_lockfile *= *true' "$f" && grep -qE '^encrypt *= *true' "$f" || fail "$f: locking/encrypt" || return 1
    ! grep -q dynamodb_table "$f" || fail "$f: dynamodb_table" || return 1
  done
}
backend_block_is_partial() {
  local r b
  for r in $ROOTS; do
    need "$r/versions.tf" || return 1
    b=$(block "$r/versions.tf" '^[ ]*backend "s3"' '^  }')
    [ -n "$b" ] || fail "$r: no backend \"s3\" block" || return 1
    ! echo "$b" | grep -qE '^[ ]*(bucket|key|region)[ ]*=|[0-9]{12}' || fail "$r: backend block not partial" || return 1
  done
}
bucket_declares_prevent_destroy() {
  need "$INFRA/bootstrap/main.tf" || return 1
  block "$INFRA/bootstrap/main.tf" '^resource "aws_s3_bucket" ' | grep -qE 'prevent_destroy *= *true' \
    || fail "prevent_destroy = true missing on bucket"
}
tfvars_example_uses_placeholders() {
  local f=$INFRA/bootstrap/terraform.tfvars.example
  need "$f" || return 1
  grep -qE '^alert_emails' "$f" && grep -qE '^state_bucket_name' "$f" || fail "$f: missing variables" || return 1
  ! grep -oE '[^" ,]+@[^" ,]+' "$f" | grep -qvE '@example\.com$' || fail "$f: non-placeholder email"
}
env_tfvars_example_present() {
  local e
  for e in $ENVS; do
    need "$e/terraform.tfvars.example" || return 1
    [ -z "$(git ls-files -- "$e/terraform.tfvars")" ] || fail "$e: terraform.tfvars tracked" || return 1
  done
}
tflint_config_enables_aws_ruleset() {
  need .tflint.hcl || return 1
  block .tflint.hcl '^plugin "aws"' | grep -qE 'enabled *= *true' || fail "plugin \"aws\" not enabled"
}
precommit_hooks_present() {
  local h
  need .pre-commit-config.yaml || return 1
  for h in terraform_fmt terraform_validate terraform_tflint trivy; do
    grep -qE "id: *[a-z_-]*$h" .pre-commit-config.yaml || fail "hook missing: $h" || return 1
  done
}
openspec_config_declares_infra_tests() {
  local f=SystemTurn/openspec/config.yaml c
  need "$f" || return 1
  grep -qE '^[ ]*infra:' "$f" || fail "no infra test section" || return 1
  for c in 'terraform test' 'terraform fmt -check' 'terraform validate' 'tflint' 'trivy config'; do
    sed -n '/^[ ]*infra:/,$p' "$f" | grep -qF "$c" || fail "missing command: $c" || return 1
  done
}
RUNBOOK=SystemTurn/docs/infra-onboarding.md
runbook_has_required_sections() {
  need "$RUNBOOK" || return 1
  grep -E '^## ' "$RUNBOOK" | awk '
    BEGIN { n = split("account|mfa|identity center|cli|configure sso|bootstrap|state migration|budget|teardown", re, "|"); i = 1 }
    i <= n && tolower($0) ~ re[i] { i++ }
    END { exit (i > n) ? 0 : 1 }' || fail "required level-2 headings missing or out of order"
}
runbook_within_size_limit() {
  need "$RUNBOOK" || return 1
  [ "$(wc -l < "$RUNBOOK")" -le 100 ] || fail "runbook exceeds 100 lines"
}

ALL_CHECKS=(
  state_and_plan_files_ignored real_tfvars_and_backend_ignored examples_and_locks_not_ignored gitattributes_forces_lf
  no_sensitive_files_tracked tracked_infra_files_have_no_crlf no_account_id_in_tracked_infra_files
  no_app_files_touched no_budget_actions no_generated_readmes
  version_constraints_pinned lockfile_pins_aws_6_69_1 lockfile_covers_both_platforms
  provider_has_no_credentials_or_profile backend_example_keys_distinct backend_example_native_locking
  backend_block_is_partial bucket_declares_prevent_destroy tfvars_example_uses_placeholders
  env_tfvars_example_present tflint_config_enables_aws_ruleset precommit_hooks_present
  openspec_config_declares_infra_tests runbook_has_required_sections runbook_within_size_limit
)

# Opt-in checks are valid IDs but never part of the default run (they depend on branch state).
OPT_IN_CHECKS=(no_app_files_touched)
DEFAULT_CHECKS=()
for id in "${ALL_CHECKS[@]}"; do
  [[ " ${OPT_IN_CHECKS[*]} " == *" $id "* ]] || DEFAULT_CHECKS+=("$id")
done

SELECTED=("$@"); [ ${#SELECTED[@]} -gt 0 ] || SELECTED=("${DEFAULT_CHECKS[@]}")
RAN=0
for id in "${SELECTED[@]}"; do
  if ! [[ " ${ALL_CHECKS[*]} " == *" $id "* ]]; then echo "FAIL $id (unknown check)"; RC=1; continue; fi
  RAN=$((RAN + 1))
  if "$id"; then echo "PASS $id"; else echo "FAIL $id"; RC=1; fi
done
[ "$RAN" -gt 0 ] || { echo "FAIL no checks executed"; RC=1; }
echo "ran=$RAN"
exit $RC
