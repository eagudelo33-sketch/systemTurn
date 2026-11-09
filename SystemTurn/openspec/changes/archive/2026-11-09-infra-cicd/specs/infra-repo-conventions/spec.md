# Delta for infra-repo-conventions

Verification: `infra/scripts/check-conventions.sh` self-test (fixture repos) and `actionlint`; no AWS account or GitHub needed.

## MODIFIED Requirements

### Requirement: No application change
The `no_app_files_touched` check MUST be opt-in: it MUST NOT run in the default `check-conventions.sh` run and MUST run only when selected explicitly by ID. When selected, it MUST fail if the diff against `main` contains any path beginning with `SystemTurn/backend/` or `SystemTurn/frontend/`. Changes that legitimately touch application code (such as test-only additions) MUST NOT fail the default run.
(Previously: the change MUST NOT modify any file under `SystemTurn/backend/` or `SystemTurn/frontend/`, enforced by default.)

#### Scenario: no_app_files_touched_not_in_default_run
- GIVEN a fixture branch that modifies `SystemTurn/backend/x.java`
- WHEN `check-conventions.sh` runs with no arguments
- THEN it exits 0 and the output does not list `no_app_files_touched`

#### Scenario: no_app_files_touched_selectable_by_id
- GIVEN the same fixture branch
- WHEN `check-conventions.sh no_app_files_touched` runs
- THEN it exits non-zero naming `SystemTurn/backend/x.java`
- AND on a branch touching only `SystemTurn/infra/` it exits 0

## ADDED Requirements

### Requirement: Workflow hygiene
Every file under `.github/workflows/` MUST satisfy: every `uses:` reference to a remote action is pinned to a 40-hex-character commit SHA (local `./` paths exempt); a top-level `permissions:` key is declared and is neither `write-all` nor grants any `write` scope at top level; the trigger `pull_request_target` MUST NOT appear; and `id-token: write` MUST appear only in jobs that assume an AWS role. `check-conventions.sh` MUST enforce these and fail with the offending file and line.

#### Scenario: actions_pinned_to_sha
- GIVEN a fixture workflow with `uses: actions/checkout@v4`
- WHEN the workflow checks run
- THEN they fail naming the file and the unpinned reference
- AND a `uses:` with a 40-hex SHA passes

#### Scenario: top_level_permissions_declared
- GIVEN fixture workflows with no top-level `permissions`, with `write-all`, and with `contents: read`
- WHEN the workflow checks run
- THEN the first two fail and the third passes

#### Scenario: pull_request_target_forbidden
- GIVEN a fixture workflow triggered by `pull_request_target`
- WHEN the workflow checks run
- THEN they fail

#### Scenario: id_token_scoped_to_aws_jobs
- GIVEN fixtures where `id-token: write` is set at top level, on a job without `configure-aws-credentials`, and on a job with it
- WHEN the workflow checks run
- THEN the first two fail and the third passes

#### Scenario: repo_workflows_pass_hygiene
- GIVEN the committed `ci.yml` and `infra.yml`
- WHEN the workflow checks and `actionlint` run
- THEN both exit 0
