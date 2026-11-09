# Archive Report: infra-cicd

**Archived**: 2026-11-09
**Mode**: hybrid (openspec + Engram project `systemturn`)
**Branch**: `feat/infra-cicd-app-ci` (stacked PRs #9 -> #10 -> #11, not merged yet). Archive commit: `docs(openspec): archive infra-cicd and merge specs`.
**Verdict at close**: PASS WITH WARNINGS (0 CRITICAL, 0 blockers) per `verify-report` at verification time (`evidence_revision: sha256:78455eca366e77c0386fbd9817075f4035ae7c1869188efbb4e48b97a9162fa5`). Final state after later work is in the next section.
**Review gate**: structured status has no `reviewGate` key (review mode not used, no review started for this candidate). Archive proceeded under ordinary repository policy.

## Outcome

- Infrastructure CI/CD is in place: GitHub OIDC provider and two scoped roles in `infra/bootstrap/github_oidc.tf` (plan: read-only; apply: state objects only, `prod` environment trust).
- `.github/workflows/infra.yml`: credential-free static job (pre-commit, `terraform test` across 5 roots with a "0 passed" guard, lock-file check, tflint, trivy, actionlint); same-repo plan gated by `AWS_PLAN_ENABLED`; manual `apply-prod` from `main` only.
- `.github/workflows/ci.yml`: backend (`./mvnw test -DrequireDocker=true`), frontend (lint, test, build), Linux e2e with Playwright report upload (14 days).
- Full-stack 503 test (`DatabaseOutageFullStackTest`) runs in plain `./mvnw test` without Docker.
- No API, backend or frontend contract change.
- Real AWS and GitHub steps are operational and remain manual (see Follow-ups).

## Final state (ranked above intermediate snapshots)

Per the Final-State Authority hierarchy, the launch prompt and repository evidence outrank `verify-report` and `apply-progress`:

- **Tasks**: 26/26 checked in `tasks.md` (0 unchecked). Task 6.2 done in `7145c28`. Task 6.1 (specs index) done by this archive.
- **W1** (design vs `ci.yml` drift): RESOLVED by this archive. `design.md` now states the backend job uploads no surefire reports, and the e2e artifacts are kept 14 days (matches `ci.yml` `retention-days: 14`). Only those two statements changed.
- **W4** (tflint aws 0.50.0 unpushed at verify time): RESOLVED. `7145c28` pushed. CI on PR #11 green: run `42653797487` (ci: backend `-DrequireDocker=true`, frontend, e2e) and run `42653797527` (infra: static). Earlier green runs: `42124412711` (infra), `42124412735` (ci).
- **W6** (change artifacts untracked at verify time): RESOLVED by this archive commit.
- **W2, W3, W5**: accepted as-is. W5: plan, apply-prod and real OIDC assume-role are operational; first real run happens after the bootstrap apply and secret setup.
- **Suggestion S3** (record CI run IDs): done in the list above.
- **Bug found by CI and fixed**: pre-commit trivy hook raced on a cold checks cache. Fixed with `--hook-config=--parallelism-limit=1` in `804952e`.

### Unrankable contradiction recorded

- Engram observation `#434` (`sdd/infra-cicd/spec`, condensed mirror, written 2026-11-04) says the infra-cicd delta has 17 scenarios. The delta file itself contains 18 `#### Scenario` blocks, and `verify-report` (2026-11-09) counts 18 for that domain. Resolution: the file is authoritative (higher rank than the mirror), so 18 is recorded here and the main spec carries all 18.

## Specs synced (main specs under `openspec/specs/`)

| Domain | Action | Requirements before -> after | Scenarios before -> after |
|--------|--------|------------------------------|---------------------------|
| infra-cicd | Created from delta (NEW) | 0 -> 7 | 0 -> 18 |
| infra-repo-conventions | 1 modified (`No application change`: opt-in, 2 scenarios replace 1), 1 added (`Workflow hygiene`, 5 scenarios) | 7 -> 8 | 18 -> 24 |
| runtime-operations | 1 modified (`Database outage returns 503`: full-stack scenario added) | 10 -> 10 | 26 -> 27 |

Totals in `openspec/specs/README.md`: 80 requirements / 175 scenarios -> 88 / 200. The `infra-cicd` row and a new "CI/CD (GitHub Actions)" section were added; the change-history row was added.

Delta scenario count (18 + 7 + 5 = 30) matches the `verify-report` 30/30 scenario count.

Merge method and deliberate deviations:
- `infra-cicd`: delta body copied with shell (`tail`/`head`/`printf`). Only the header changed: the `# Delta for infra-cicd (NEW)` title and the `## ADDED Requirements` heading became `# Infra CI/CD Specification`, a `## Purpose` section built from the delta's Scope and Verification paragraphs, and `## Requirements`.
- `infra-repo-conventions` and `runtime-operations`: MODIFIED and ADDED blocks spliced into the main files by line range from the delta files (no model rewrite of requirement text).
- Deviation: the `(Previously: ...)` notes inside the two MODIFIED requirements were not copied, because the specs README keeps history in the archive, not in the living specs.
- Line endings: all main specs are CRLF; the merged and new files are CRLF. The change-folder artifacts remain as they were.

## Docs

- `SystemTurn/docs/cicd.md` line 5: the `infra-cicd` reference is now a real relative link to `../openspec/specs/infra-cicd/spec.md`.
- `SystemTurn/docs/README.md` roadmap row 7 (`infra-cicd`, `✅ Done (PR #9–#11)`) is consistent with the previous archived changes. No change.

## Deviations against design

None after the W1 fix to `design.md`.

## Follow-ups (manual, outside this archive)

- Delivery: merge PR #9 into `main`; retarget #10 to `main` and merge; retarget #11 to `main` and merge.
- Operational: apply the bootstrap; set secrets `AWS_PLAN_ROLE_ARN`, `AWS_APPLY_ROLE_ARN`, `TF_STATE_BUCKET`; set variable `AWS_PLAN_ENABLED`; create the `prod` Environment with reviewers; require branch protection checks `static`, `backend`, `frontend`, `e2e`.
- Deferred to `infra-prod-wiring`: `prod` wiring, apply-role growth, region and `default_tags` proof, ALB/SPA/API tests, and confirmation of the first real assume-role and plan and Linux provider hashes.

## Traceability (Engram observation IDs, project `systemturn`)

| Artifact | Topic key | Observation ID | Notes |
|----------|-----------|----------------|-------|
| proposal | sdd/infra-cicd/proposal | 432 | read in full |
| spec | sdd/infra-cicd/spec | 434 | condensed mirror; the delta files are authoritative |
| design | sdd/infra-cicd/design | 435 | condensed mirror; `design.md` is authoritative (W1 fix applied after this observation) |
| tasks | sdd/infra-cicd/tasks | 437 | condensed mirror; `tasks.md` is authoritative (26/26) |
| verify-report | sdd/infra-cicd/verify-report | 443 | intermediate snapshot; final state per the section above |

Archive contents: `proposal.md`, `exploration.md`, `specs/` (3 domains), `design.md`, `tasks.md` (26/26), `apply-progress.md`, `verify-report.md`, `archive-report.md`.
