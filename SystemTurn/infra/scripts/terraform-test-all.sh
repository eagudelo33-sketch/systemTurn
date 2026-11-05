#!/usr/bin/env bash
# Run `terraform test` offline in every Terraform directory and fail when a directory reports 0 passed.
# Usage: terraform-test-all.sh [DIR...]        (default: the five directories below)
#        terraform-test-all.sh --guard DIR FILE (check saved test output; used by the self-test)
set -u
guard() { # guard DIR: reads terraform test output on stdin, echoes it, fails unless >=1 passed and 0 failed
  local out; out=$(cat); echo "$out"
  echo "$out" | grep -qE '[1-9][0-9]* passed, 0 failed' || { echo "FAIL $1: 0 passed"; return 1; }
}
if [ "${1:-}" = "--guard" ]; then guard "$2" < "$3"; exit $?; fi

ROOT=$(git rev-parse --show-toplevel 2>/dev/null) || { echo "FAIL not inside a git repository"; exit 1; }
cd "$ROOT" || exit 1
I=SystemTurn/infra
DIRS=("$@"); [ ${#DIRS[@]} -gt 0 ] || DIRS=($I/bootstrap $I/envs/dev $I/envs/prod $I/modules/network $I/modules/app)
RC=0
for d in "${DIRS[@]}"; do
  lock=(); [ -n "$(git ls-files -- "$d/.terraform.lock.hcl")" ] && lock=(-lockfile=readonly)
  (cd "$d" && terraform init -backend=false -input=false -no-color "${lock[@]}" >/dev/null \
    && terraform test -no-color 2>&1) | guard "$d"
  st=("${PIPESTATUS[@]}")
  [ "${st[0]}" -eq 0 ] && [ "${st[1]}" -eq 0 ] || RC=1
done
exit $RC
