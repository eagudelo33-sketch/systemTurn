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

# --- GitHub Actions workflow checks (index-based; comments are ignored, line numbers are kept) ---
WFI=.github/workflows/infra.yml
wf_files() { # tracked workflow files that exist on disk; non-zero when there are none
  local f n=0
  while IFS= read -r f; do [ -f "$f" ] && { echo "$f"; n=$((n + 1)); }; done \
    < <(git ls-files -- '.github/workflows/*.yml' '.github/workflows/*.yaml')
  [ "$n" -gt 0 ]
}
wf_list() { wf_files || fail "no workflow files under .github/workflows"; }
nocomment() { sed 's/[[:space:]]#.*//; s/^#.*//' "$1"; }
job_ids() { nocomment "$1" | awk '/^jobs:/ {j=1; next} j && /^[^ ]/ {exit} j && /^  [A-Za-z_][A-Za-z0-9_-]*:/ {sub(/:.*/, ""); print $1}'; }
job() { # job FILE ID: the job block, without comments
  nocomment "$1" | awk -v id="$2" '
    /^jobs:/ {j=1; next}
    j && /^[^ ]/ {if (on) exit; j=0}
    j && /^  [A-Za-z_][A-Za-z0-9_-]*:/ {if (on) exit; if ($1 == id ":") on=1}
    on {print}'
}
job_if() { awk '/^    if:/ {on=1; print; next} on && /^    [A-Za-z_-]+:/ {exit} on {print}'; } # stdin: a job block
top_block() { nocomment "$1" | awk -v key="$2" '$0 ~ "^" key ":" {on=1; print; next} on && /^[A-Za-z_-]+:/ {exit} on {print}'; }
# each_wf CMD: run CMD FILE for every workflow file; stops at the first non-zero result
each_wf() { local f files; files=$(wf_list) || { echo "$files"; return 1; }; for f in $files; do "$1" "$f" || return 1; done; }

pinned_in() {
  local hits
  hits=$(nocomment "$1" | grep -nE '^[[:space:]-]*uses:' | grep -vE "uses:[[:space:]]*['\"]?\./" \
    | grep -vE "@[0-9a-f]{40}([[:space:]'\"]|\$)" | sed "s|^|$1:|")
  [ -z "$hits" ] || fail "unpinned action (need 40-hex SHA): $hits"
}
actions_pinned_to_sha() { each_wf pinned_in; }
permissions_in() {
  local hits
  top_block "$1" permissions | grep -q . || fail "$1: no top-level permissions" || return 1
  hits=$( { top_block "$1" permissions | grep -n 'write'; nocomment "$1" | grep -n 'write-all'; } | sort -u)
  [ -z "$hits" ] || fail "$1: top-level write permission: $hits"
}
top_level_permissions_declared() { each_wf permissions_in; }
prt_in() {
  local hits; hits=$(nocomment "$1" | grep -nw 'pull_request_target' | sed "s|^|$1:|")
  [ -z "$hits" ] || fail "pull_request_target is forbidden: $hits"
}
pull_request_target_forbidden() { each_wf prt_in; }
idtoken_in() {
  local hits id
  hits=$(nocomment "$1" | awk '/^jobs:/ {exit} {print}' | grep -n 'id-token' | sed "s|^|$1:|")
  [ -z "$hits" ] || fail "id-token at top level: $hits" || return 1
  for id in $(job_ids "$1"); do
    job "$1" "$id" | grep -qE 'id-token:[[:space:]]*write' || continue
    job "$1" "$id" | grep -q 'aws-actions/configure-aws-credentials@' || fail "$1: job $id holds id-token: write without assuming an AWS role" || return 1
  done
}
id_token_scoped_to_aws_jobs() { each_wf idtoken_in; }

# --- Repo workflow checks: red until .github/workflows/infra.yml exists ---
ijob() { need "$WFI" || return 1; job "$WFI" "$1" | grep -q . || fail "$WFI: no job $1" || return 1; job "$WFI" "$1"; }
static_job_has_no_credentials() {
  local j; j=$(ijob static) || { echo "$j"; return 1; }
  ! echo "$j" | grep -nE 'id-token|configure-aws-credentials|secrets\.|environment:' \
    || fail "static job must not hold credentials"
}
static_job_runs_all_gates() {
  local j g d; j=$(ijob static) || { echo "$j"; return 1; }
  for g in 'pre-commit run --all-files' "git diff --exit-code -- '\*\.lock\.hcl'" 'terraform-test-all\.sh' tflint trivy 'run: actionlint'; do
    echo "$j" | grep -qE "$g" || fail "static job lacks: $g" || return 1
  done
  need "$INFRA/scripts/terraform-test-all.sh" || return 1
  for d in bootstrap envs/dev envs/prod modules/network modules/app; do
    grep -qF "$d" "$INFRA/scripts/terraform-test-all.sh" || fail "terraform-test-all.sh lacks $d" || return 1
  done
}
nofilter_in() {
  local hits; hits=$(nocomment "$1" | grep -nE '^[[:space:]]*paths(-ignore)?:' | sed "s|^|$1:|")
  [ -z "$hits" ] || fail "path filter: $hits" || return 1
  top_block "$1" concurrency | grep -q 'group:' || fail "$1: no concurrency group"
}
no_path_filters_and_concurrency() { need "$WFI" || return 1; each_wf nofilter_in; }
plan_job_gated_for_same_repo() {
  local c j g; j=$(ijob plan) || { echo "$j"; return 1; }; c=$(echo "$j" | job_if)
  for g in "vars\.AWS_PLAN_ENABLED == 'true'" "github\.event_name == 'pull_request'" 'github\.event\.pull_request\.head\.repo\.full_name == github\.repository'; do
    echo "$c" | grep -qE "$g" || fail "plan job if lacks: $g" || return 1
  done
}
plan_uses_lock_false_and_secrets() {
  local j; j=$(ijob plan) || { echo "$j"; return 1; }
  echo "$j" | grep -q 'terraform plan' || fail "plan job runs no terraform plan" || return 1
  ! echo "$j" | grep 'terraform plan' | grep -vq -- '-lock=false' || fail "terraform plan without -lock=false" || return 1
  ! echo "$j" | grep -qE 'terraform apply' || fail "plan job runs terraform apply" || return 1
  echo "$j" | grep -q 'secrets\.AWS_PLAN_ROLE_ARN' && echo "$j" | grep -q 'secrets\.TF_STATE_BUCKET' \
    || fail "plan role ARN and state bucket must come from secrets" || return 1
  ! echo "$j" | grep -qE 'arn:aws:|[^0-9][0-9]{12}[^0-9]' || fail "literal ARN or account id in plan job"
}
apply_prod_only_manual_on_main() {
  local j c; j=$(ijob apply-prod) || { echo "$j"; return 1; }; c=$(echo "$j" | job_if)
  echo "$c" | grep -q "github\.event_name == 'workflow_dispatch'" && echo "$c" | grep -q "github\.ref == 'refs/heads/main'" \
    || fail "apply-prod if must require workflow_dispatch on refs/heads/main" || return 1
  echo "$j" | grep -qE 'environment:[[:space:]]*prod[[:space:]]*$' || fail "apply-prod lacks environment: prod" || return 1
  echo "$j" | grep -q 'secrets\.AWS_APPLY_ROLE_ARN' || fail "apply-prod must use secrets.AWS_APPLY_ROLE_ARN"
}
isolated_in() {
  local pat='environment:[[:space:]]*prod|AWS_APPLY_ROLE_ARN' total inside
  total=$(nocomment "$1" | grep -cE "$pat"); inside=$(job "$1" apply-prod | grep -cE "$pat")
  [ "$total" -eq "$inside" ] || fail "$1: prod environment or apply role used outside apply-prod"
}
apply_role_isolated_to_apply_prod() { need "$WFI" || return 1; each_wf isolated_in; }
fork_in() { # credential jobs must exclude forks (or be non-PR); credential-free jobs must not be gated on the head repo
  local id j c
  for id in $(job_ids "$1"); do
    j=$(job "$1" "$id"); c=$(echo "$j" | job_if)
    if echo "$j" | grep -qE 'id-token:[[:space:]]*write|secrets\.|environment:'; then
      echo "$c" | grep -qE "head\.repo\.full_name == github\.repository|github\.event_name == 'workflow_dispatch'" \
        || fail "$1: credential job $id reachable from fork PRs" || return 1
    else
      ! echo "$c" | grep -q 'head\.repo' || fail "$1: credential-free job $id is gated on the head repo" || return 1
    fi
  done
}
fork_pr_reaches_only_credential_free_jobs() { need "$WFI" || return 1; each_wf fork_in; }
repo_identity_not_hardcoded() { # the repository slug lives only in bootstrap/variables.tf (tests and docs excluded)
  local hits owner=eagudelo33
  hits=$(git ls-files -- .github "$INFRA" | grep -vE "^$INFRA/bootstrap/variables\.tf$|\.tftest\.hcl$" \
    | xargs grep -Hn "$owner-sketch" 2>/dev/null)
  [ -z "$hits" ] || fail "repository slug hardcoded: $hits"
}
repo_workflows_pass_hygiene() {
  need "$WFI" || return 1
  actions_pinned_to_sha && top_level_permissions_declared && pull_request_target_forbidden && id_token_scoped_to_aws_jobs || return 1
  if command -v actionlint >/dev/null 2>&1; then
    actionlint $(wf_files) || fail "actionlint reported problems"
  else
    echo "  note: actionlint not installed; run in CI"
  fi
}

# --- ci.yml checks (application CI): red until .github/workflows/ci.yml exists ---
WFC=.github/workflows/ci.yml
cjob() { need "$WFC" || return 1; job "$WFC" "$1" | grep -q . || fail "$WFC: no job $1" || return 1; job "$WFC" "$1"; }
linux_runner() { grep -qE 'runs-on:[[:space:]]*ubuntu-'; }
backend_requires_docker() {
  local j; j=$(cjob backend) || { echo "$j"; return 1; }
  echo "$j" | grep -qE 'mvnw.* test( .*)? -DrequireDocker=true' || fail "backend job does not run tests with -DrequireDocker=true" || return 1
  echo "$j" | linux_runner || fail "backend job must run on an ubuntu runner (Docker available)"
}
frontend_runs_lint_test_build() {
  local j s; j=$(cjob frontend) || { echo "$j"; return 1; }
  for s in 'npm ci' 'npm run lint' 'npm test' 'npm run build'; do
    echo "$j" | grep -qE "run:[[:space:]]*$s([[:space:]]|\$)" || fail "frontend job lacks step: $s" || return 1
  done
}
e2e_uploads_report_on_failure() {
  local j up; j=$(cjob e2e) || { echo "$j"; return 1; }
  echo "$j" | linux_runner || fail "e2e job must run on an ubuntu runner" || return 1
  echo "$j" | grep -q 'npm run e2e' || fail "e2e job does not run npm run e2e" || return 1
  up=$(echo "$j" | awk '/^      - /{if (blk ~ /upload-artifact@/) print blk; blk=""} {blk = blk $0 "\n"} END {if (blk ~ /upload-artifact@/) print blk}')
  [ -n "$up" ] || fail "e2e job has no upload-artifact step" || return 1
  echo "$up" | grep -qE 'if:[[:space:]]*(\$\{\{[[:space:]]*)?(always\(\)|!cancelled\(\))' || fail "upload-artifact step must run on failure (always() or !cancelled())" || return 1
  echo "$up" | grep -q 'playwright-report' && echo "$up" | grep -q 'test-results' || fail "upload must include playwright-report/ and test-results/"
}
ci_uses_no_secrets_or_id_token() {
  need "$WFC" || return 1
  ! nocomment "$WFC" | grep -nE 'secrets\.|id-token|configure-aws-credentials|environment:' || fail "$WFC must not use secrets, id-token, AWS credentials or environments"
}
ci_triggers_pull_request_and_main() {
  need "$WFC" || return 1
  top_block "$WFC" on | grep -qE '^[[:space:]]+pull_request:' && top_block "$WFC" on | grep -qE '^[[:space:]]+push:'     && top_block "$WFC" on | grep -qE 'branches:[[:space:]]*\[main\]' \
    || fail "$WFC must trigger on pull_request and push to main"
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
  actions_pinned_to_sha top_level_permissions_declared pull_request_target_forbidden id_token_scoped_to_aws_jobs
  static_job_has_no_credentials static_job_runs_all_gates no_path_filters_and_concurrency
  plan_job_gated_for_same_repo plan_uses_lock_false_and_secrets apply_prod_only_manual_on_main
  apply_role_isolated_to_apply_prod fork_pr_reaches_only_credential_free_jobs repo_identity_not_hardcoded
  repo_workflows_pass_hygiene
  backend_requires_docker frontend_runs_lint_test_build e2e_uploads_report_on_failure ci_uses_no_secrets_or_id_token
  ci_triggers_pull_request_and_main
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
