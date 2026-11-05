#!/usr/bin/env bash
# Self-test for check-conventions.sh. Runs against a throwaway git repo (never the real index).
# Threat matrix: (i) commit-state: a staged tfstate is caught; (ii) repo selection: same result from any cwd.
set -u
HERE=$(cd "$(dirname "$0")" && pwd)
REPO=$(git -C "$HERE" rev-parse --show-toplevel) || exit 1
SB=$(mktemp -d "${TMPDIR:-/tmp}/check-conv.XXXXXX") || exit 1
trap 'rm -rf "$SB"' EXIT
FAILS=0; N=0

mkdir -p "$SB/SystemTurn/infra/scripts" "$SB/SystemTurn/infra/envs/dev" "$SB/SystemTurn/infra/bootstrap"
cp "$HERE/check-conventions.sh" "$SB/SystemTurn/infra/scripts/"
cp "$REPO/.gitignore" "$REPO/.gitattributes" "$SB/"
git -C "$SB" init -q
CHK=SystemTurn/infra/scripts/check-conventions.sh
run() { (cd "$SB/${CWD:-.}" && bash "$SB/$CHK" "$@" 2>&1); }   # sets $? to the script's exit code
expect() { # expect NAME WANT_EXIT -- ids...
  local name=$1 want=$2 got; shift 3; run "$@" >/dev/null; got=$?; N=$((N + 1))
  if [ "$got" -eq "$want" ]; then echo "PASS $name"; else echo "FAIL $name (exit $got, want $want)"; FAILS=$((FAILS + 1)); fi
}

# (i) commit-state: clean index passes, staged tfstate (even though gitignored) fails
expect clean_index_passes 0 -- no_sensitive_files_tracked
echo '{}' > "$SB/SystemTurn/infra/envs/dev/x.tfstate"
git -C "$SB" add -f SystemTurn/infra/envs/dev/x.tfstate
expect staged_tfstate_is_caught 1 -- no_sensitive_files_tracked
git -C "$SB" rm -q -f --cached SystemTurn/infra/envs/dev/x.tfstate
expect unstaged_again_passes 0 -- no_sensitive_files_tracked

# workflow fixtures are staged in the sandbox index; wf NAME reads the file body from stdin
SHA=$(printf 'a%.0s' {1..40})
wf() { mkdir -p "$SB/.github/workflows"; cat > "$SB/.github/workflows/$1"; git -C "$SB" add ".github/workflows/$1"; }
unwf() { git -C "$SB" rm -q -f --cached ".github/workflows/$1"; rm -f "$SB/.github/workflows/$1"; }
good_wf() { # good_wf [PERMISSIONS_BLOCK] [STEP_LINE]: a workflow that passes every hygiene check
  printf 'name: t\non: [pull_request]\n%s\njobs:\n  j:\n    runs-on: ubuntu-latest\n    steps:\n      - uses: %s\n' \
    "${1:-permissions:
  contents: read}" "${2:-actions/checkout@$SHA}"
}
expect no_workflows_fails 1 -- actions_pinned_to_sha
good_wf | wf ok.yml

# (ii) repo selection: identical output and exit from the root and from a nested env dir
IDS="state_and_plan_files_ignored real_tfvars_and_backend_ignored examples_and_locks_not_ignored gitattributes_forces_lf actions_pinned_to_sha"
CWD=. run $IDS > "$SB/out_root.txt"; r1=$?
CWD=SystemTurn/infra/envs/dev run $IDS > "$SB/out_dev.txt"; r2=$?
N=$((N + 1))
if [ $r1 -eq 0 ] && [ $r1 -eq $r2 ] && cmp -s "$SB/out_root.txt" "$SB/out_dev.txt"; then echo "PASS same_result_from_env_dir"
else echo "FAIL same_result_from_env_dir (exit $r1/$r2)"; FAILS=$((FAILS + 1)); fi

# runner guards: zero executed checks or unknown id is a failure
expect unknown_id_fails 1 -- not_a_check
expect mixed_known_unknown_fails 1 -- gitattributes_forces_lf not_a_check

# parsers detect violations (and accept good input)
printf 'resource "aws_budgets_budget_action" "x" {}\n' > "$SB/SystemTurn/infra/bootstrap/b.tf"
git -C "$SB" add SystemTurn/infra/bootstrap/b.tf
expect budget_action_is_caught 1 -- no_budget_actions
git -C "$SB" rm -q -f --cached SystemTurn/infra/bootstrap/b.tf
printf 'a = 1\r\n' > "$SB/SystemTurn/infra/bootstrap/c.tf"; git -C "$SB" add SystemTurn/infra/bootstrap/c.tf
expect crlf_is_caught 1 -- tracked_infra_files_have_no_crlf
printf 'a = 1\n' > "$SB/SystemTurn/infra/bootstrap/c.tf"
expect lf_passes 0 -- tracked_infra_files_have_no_crlf
printf 'x = "%s%s"\n' 1111 22223333 > "$SB/SystemTurn/infra/bootstrap/c.tf"
expect account_id_is_caught 1 -- no_account_id_in_tracked_infra_files
printf 'x = "%s"\n' 123456789012 > "$SB/SystemTurn/infra/bootstrap/c.tf"
expect placeholder_account_id_passes 0 -- no_account_id_in_tracked_infra_files

B=$SB/SystemTurn/infra/bootstrap
printf 'terraform {\n  backend "s3" {}\n}\n' > "$B/versions.tf"
for r in envs/dev envs/prod; do mkdir -p "$SB/SystemTurn/infra/$r"; cp "$B/versions.tf" "$SB/SystemTurn/infra/$r/"; done
expect partial_backend_passes 0 -- backend_block_is_partial
printf 'terraform {\n  backend "s3" {\n    bucket = "b"\n  }\n}\n' > "$B/versions.tf"
expect backend_with_bucket_fails 1 -- backend_block_is_partial
printf 'resource "aws_s3_bucket" "s" {\n  bucket = "b"\n  lifecycle {\n    prevent_destroy = false\n  }\n}\n' > "$B/main.tf"
expect prevent_destroy_false_fails 1 -- bucket_declares_prevent_destroy
printf 'resource "aws_s3_bucket" "s" {\n  tags = {\n  }\n  lifecycle {\n    prevent_destroy = true\n  }\n}\n' > "$B/main.tf"
expect prevent_destroy_true_passes 0 -- bucket_declares_prevent_destroy

# workflow hygiene: each rule fails on a violating fixture (naming file and line) and passes on a good one
good_wf 'permissions:
  contents: read' 'actions/checkout@v4' | wf t.yml
out=$(run actions_pinned_to_sha); r=$?; N=$((N + 1))
if [ $r -eq 1 ] && echo "$out" | grep -q 't.yml:.*checkout@v4'; then echo "PASS actions_pinned_to_sha_tag_fails"
else echo "FAIL actions_pinned_to_sha_tag_fails (exit $r)"; FAILS=$((FAILS + 1)); fi
good_wf | wf t.yml
expect actions_pinned_to_sha_sha_passes 0 -- actions_pinned_to_sha
good_wf 'permissions:
  contents: read' './local-action' | wf t.yml
expect actions_pinned_to_sha_local_path_exempt 0 -- actions_pinned_to_sha
good_wf '# permissions: write-all' | wf t.yml
expect top_level_permissions_missing_fails 1 -- top_level_permissions_declared
good_wf 'permissions: write-all' | wf t.yml
expect top_level_permissions_write_all_fails 1 -- top_level_permissions_declared
good_wf 'permissions:
  contents: write' | wf t.yml
expect top_level_permissions_write_scope_fails 1 -- top_level_permissions_declared
good_wf 'permissions:
  contents: read' | wf t.yml
expect top_level_permissions_read_passes 0 -- top_level_permissions_declared
printf 'name: t\npermissions:\n  contents: read\non:\n  pull_request_target:\njobs: {}\n' | wf t.yml
expect pull_request_target_forbidden 1 -- pull_request_target_forbidden
printf 'name: t\npermissions:\n  contents: read\non: [pull_request] # not pull_request_target\n# pull_request_target\njobs: {}\n' | wf t.yml
expect pull_request_target_commented_out_passes 0 -- pull_request_target_forbidden
good_wf 'permissions:
  contents: read
  id-token: write' | wf t.yml
expect id_token_at_top_level_fails 1 -- id_token_scoped_to_aws_jobs
good_wf | wf t.yml
printf '    permissions:\n      id-token: write\n' >> "$SB/.github/workflows/t.yml"; git -C "$SB" add .github/workflows/t.yml
expect id_token_on_non_aws_job_fails 1 -- id_token_scoped_to_aws_jobs
good_wf 'permissions:
  contents: read' "aws-actions/configure-aws-credentials@$SHA" | wf t.yml
printf '    permissions:\n      id-token: write\n' >> "$SB/.github/workflows/t.yml"; git -C "$SB" add .github/workflows/t.yml
expect id_token_on_aws_job_passes 0 -- id_token_scoped_to_aws_jobs
unwf t.yml

# opt-in check: no_app_files_touched never runs by default, only when selected by ID
GIT="git -C $SB -c user.name=t -c user.email=t@example.com -c commit.gpgsign=false"
$GIT commit -q -m base && $GIT branch -M main && $GIT checkout -q -b feat/app
mkdir -p "$SB/SystemTurn/backend"; echo x > "$SB/SystemTurn/backend/x.java"
$GIT add SystemTurn/backend/x.java && $GIT commit -q -m app
out=$(run); N=$((N + 1))
if echo "$out" | grep -q 'PASS no_sensitive_files_tracked' && ! echo "$out" | grep -q 'no_app_files_touched'; then echo "PASS no_app_files_touched_not_in_default_run"
else echo "FAIL no_app_files_touched_not_in_default_run"; FAILS=$((FAILS + 1)); fi
out=$(run no_app_files_touched); r=$?; N=$((N + 1))
if [ $r -eq 1 ] && echo "$out" | grep -q 'SystemTurn/backend/x.java'; then echo "PASS no_app_files_touched_selectable_by_id"
else echo "FAIL no_app_files_touched_selectable_by_id (exit $r)"; FAILS=$((FAILS + 1)); fi
$GIT checkout -q main && $GIT checkout -q -b feat/infra
echo y > "$SB/SystemTurn/infra/y.txt"; $GIT add SystemTurn/infra/y.txt && $GIT commit -q -m infra
expect infra_only_branch_passes_by_id 0 -- no_app_files_touched

# terraform-test-all.sh --guard: a directory that reports 0 passed must fail and be named
TTA=$HERE/terraform-test-all.sh
printf 'Success! 0 passed, 0 failed.\n' > "$SB/zero.txt"; printf 'Success! 3 passed, 0 failed.\n' > "$SB/three.txt"
out=$(bash "$TTA" --guard SystemTurn/infra/x "$SB/zero.txt" 2>&1); r=$?; N=$((N + 1))
if [ $r -eq 1 ] && echo "$out" | grep -q 'FAIL SystemTurn/infra/x'; then echo "PASS zero_passed_directory_fails"
else echo "FAIL zero_passed_directory_fails (exit $r)"; FAILS=$((FAILS + 1)); fi
bash "$TTA" --guard SystemTurn/infra/x "$SB/three.txt" >/dev/null 2>&1; r=$?; N=$((N + 1))
if [ $r -eq 0 ]; then echo "PASS passed_directory_succeeds"; else echo "FAIL passed_directory_succeeds (exit $r)"; FAILS=$((FAILS + 1)); fi

# infra.yml checks: the committed file passes; each mutation of a safeguard is caught by the matching check
INFRA_CHECKS="static_job_has_no_credentials static_job_runs_all_gates no_path_filters_and_concurrency plan_job_gated_for_same_repo plan_uses_lock_false_and_secrets apply_prod_only_manual_on_main apply_role_isolated_to_apply_prod fork_pr_reaches_only_credential_free_jobs"
unwf ok.yml
cp "$TTA" "$SB/SystemTurn/infra/scripts/"; git -C "$SB" add SystemTurn/infra/scripts/terraform-test-all.sh
cp "$REPO/.github/workflows/infra.yml" "$SB/.github/workflows/infra.yml" 2>/dev/null; git -C "$SB" add .github/workflows/infra.yml 2>/dev/null
expect committed_infra_yml_passes_infra_checks 0 -- $INFRA_CHECKS
mutate() { # mutate NAME CHECK_ID FILTER...: apply FILTER (reads the real infra.yml) and expect CHECK_ID to fail
  local name=$1 id=$2; shift 2
  "$@" "$REPO/.github/workflows/infra.yml" > "$SB/.github/workflows/infra.yml" 2>/dev/null; git -C "$SB" add .github/workflows/infra.yml
  expect "$name" 1 -- "$id"
}
mutate plan_without_lock_false_fails plan_uses_lock_false_and_secrets sed 's/ -lock=false//'
mutate plan_with_literal_role_fails plan_uses_lock_false_and_secrets sed 's/secrets\.AWS_PLAN_ROLE_ARN/"arn:aws:iam::123456789012:role\/x"/'
mutate plan_without_fork_gate_fails plan_job_gated_for_same_repo sed 's/github\.event\.pull_request\.head\.repo\.full_name == github\.repository/true/'
mutate plan_without_fork_gate_reaches_forks fork_pr_reaches_only_credential_free_jobs sed 's/github\.event\.pull_request\.head\.repo\.full_name == github\.repository/true/'
mutate plan_without_variable_gate_fails plan_job_gated_for_same_repo sed "s/vars\.AWS_PLAN_ENABLED == 'true'/true/"
mutate static_with_secret_fails static_job_has_no_credentials sed 's/\${{ github\.token }}/${{ secrets.GITHUB_TOKEN }}/'
mutate static_without_actionlint_fails static_job_runs_all_gates sed 's/actionlint -color/true/'
mutate apply_prod_on_any_ref_fails apply_prod_only_manual_on_main sed "s/github\.ref == 'refs\/heads\/main'/true/"
mutate apply_prod_without_environment_fails apply_prod_only_manual_on_main sed 's/environment: prod/environment: staging/'
mutate prod_environment_leaks_to_static_fails apply_role_isolated_to_apply_prod awk '{print} /^  static:/{print "    environment: prod"}'
mutate path_filter_fails no_path_filters_and_concurrency awk '{print} /^  pull_request:/{print "    paths: [\"a\"]"}'
mutate missing_concurrency_fails no_path_filters_and_concurrency sed 's/^concurrency:/x-concurrency:/'
git -C "$SB" rm -q -f --cached .github/workflows/infra.yml; rm -f "$SB/.github/workflows/infra.yml"

echo "ran=$N failed=$FAILS"
[ "$N" -gt 0 ] && [ "$FAILS" -eq 0 ]
