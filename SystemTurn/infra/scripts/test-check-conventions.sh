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

# (ii) repo selection: identical output and exit from the root and from a nested env dir
IDS="state_and_plan_files_ignored real_tfvars_and_backend_ignored examples_and_locks_not_ignored gitattributes_forces_lf"
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
printf 'x = "%s"\n' 111122223333 > "$SB/SystemTurn/infra/bootstrap/c.tf"
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

echo "ran=$N failed=$FAILS"
[ "$N" -gt 0 ] && [ "$FAILS" -eq 0 ]
