# Archive Report: postgres-adapter

**Change**: postgres-adapter
**Project**: systemturn
**Artifact store**: hybrid (openspec filesystem + engram)
**Archived**: 2026-11-03 to `SystemTurn/openspec/changes/archive/2026-11-03-postgres-adapter/`
**Close state**: archived. Verify verdict PASS WITH WARNINGS, 0 CRITICAL.
**Delivery**: code, tests, docs, and openspec staged in the index; NOT committed. Accepted as `size:exception`, single PR.

## Sources Read (Final-State Authority)

| Artifact | Engram observation | Rank used for |
|----------|--------------------|---------------|
| proposal | #388 | Intent, scope, capabilities |
| spec (delta index) | #389 | Capability list; delta files read from openspec |
| design | #390 | Design decisions |
| tasks | #392 (full `tasks.md` read in the Task Completion Gate) | Completion visibility (rank 2) |
| apply-progress | #393 | Snapshot only (rank 4) |
| verify-report | #394 (full file `openspec/changes/postgres-adapter/verify-report.md`) | Snapshot only (rank 4) |
| Launch prompt final-state facts | n/a | Final numbers and carried warnings (rank 3) |

## Gates

- **Native review gate**: `reviewGate` is structurally absent. `gentle-ai review mode status` reports receipt-driven development off (decided by default). `gentle-ai review status --next-transition` reports `applicability: unrelated`, and neither `reviewGate` nor `reviewOffer` is present. Archive proceeds under ordinary repository policy. No `sdd/postgres-adapter/review/*` topic was read.
- **Task Completion Gate**: passed. `tasks.md` has 27 items marked `[x]` and 0 unchecked. No stale-checkbox reconciliation was performed.
- **CRITICAL findings**: none (verify #394, `critical_findings: 0`).

## Final State at Close

Final-state facts from the launch prompt outrank the intermediate snapshots. The prompt's values are corroborated by #393 and #394 where they overlap.

- Backend: 147/147 with `./mvnw test -DrequireDocker=true`, 0 skipped on the PostgreSQL classes. Image pinned to `postgres:17.10`.
- Frontend: 11/11 tests, lint clean, build OK.
- Verify snapshot (#394, at verification time): PASS WITH WARNINGS, 14/14 requirements, 43/43 scenarios, `evidence_revision: sha256:65122fb093079125802481abc04b732db6c966ff008b09e3eb0a250c91ad665a`. First-run verdict, validator admitted.
- Size: ~1,900 changed lines (code, tests, docs), accepted as `size:exception`. The `tasks.md` forecast was ~1,100.

### Contradictions Recorded (not silently resolved)

1. **Task count**: `tasks.md` (rank 2) has 27 checkbox items, all `[x]`. #394 says "28/28 tasks complete". The artifact's count is used. Zero tasks are open either way, so this does not change closure.
2. **Postgres image**: design #390 says `postgres:17.6`. Final pin is `postgres:17.10` (#393, #394, launch prompt). Final value used.
3. **Test count**: #393 "Test Summary" shows 93, which is stale. Final value is 147.
4. **Staging state**: the preflight said openspec artifacts were untracked. At session start, git showed `design.md`, `exploration.md`, `proposal.md`, and the delta specs as staged (A), `apply-progress.md` and `tasks.md` as AM, and `verify-report.md` as untracked. The archive move and staging step below normalize this.

## Carried Warnings (open at close)

These were not fixed in this change. They are obligations for later changes.

Owner `infra-cicd`:
- (a) Add a full-stack 503 test. The current proof composes three tests (adapter unit, real Hikari fail-fast, WebMvc handler) and relies on `TurnoService` not catching the exception.
- (b) The Postgres suite relies on sequential classes sharing one container. No parallel test execution. `SolicitarConcurrencyPostgresTest` takes ~80-100 s, which is the test-time budget.
- (c) CI must run with `-DrequireDocker=true`, or the Postgres suite skips silently.

Other open items:
- The missing-URL boot test does not assert the absence of an embedded database.
- `TurnoControllerTest` has a one-line profile edit, which conflicts with the spec's "unmodified" wording.
- The 503 message wording drifts between backend and the frontend fallback.
- `docs/README.md` has a stale generic "in-memory storage" line.
- Verify suggestions: demo-with-dev boot test asserts today's date; pin the `test` profile in the H2 guard message assertion; the V1 checksum is LF-normalized, so watch line endings in CI.

Owner `infra-app` (per the launch prompt, referencing engram #391, not re-read in this phase):
- `socketTimeout` and `sslmode` in the RDS URL.
- Never run the `test` or `demo` profile in production.
- ALB health check path `/actuator/health/readiness`.
- RDS major version 17.
- Pool size times task count must stay below RDS `max_connections`.
- ALB idle timeout for websockets.

## Specs Synced

| Domain | Action | Details |
|--------|--------|---------|
| ticket-persistence | Updated | 2 MODIFIED (Durable storage: 2 scenarios; Versioned schema: 4 scenarios). Domain isolation preserved (not in delta). |
| demo-seed | Updated | 1 ADDED (Production guard: 4 scenarios). Profile-gated seed, Seed content, Idempotent seed preserved. |
| concurrency-conflicts | Updated | 1 ADDED (Engine-independent duplicate detection: 7 scenarios). Unique daily numbers, Optimistic locking, 409 contract, Frontend conflict handling preserved, including the operational note line. |
| runtime-operations | Created (NEW capability) | 10 requirements, 0 modified. Purpose and "Operational notes" section kept verbatim. |
| daily-queue | Untouched | No delta targets it. |

Scenario check: 6 (ticket-persistence) + 4 (demo-seed) + 7 (concurrency-conflicts) + 26 (runtime-operations) = 43, matching the verify count.

### Merge Warnings (rules.archive: warn before destructive deltas)

- **W1, scenario removed by MODIFIED block**: the `Versioned schema` delta replaces the whole requirement. The prior main scenario "Time precision" (generic H2 save-and-reload, 08:34:27) is not in the delta, so it is no longer in the main spec. Only "Time precision on PostgreSQL" remains. The delta has no `(Reason:)` note for it. It is one scenario, not a large section, so the merge followed the delta as directed. It is flagged for review, and a follow-up change can restore an H2-scoped scenario if wanted.
- **W2**: the `(Previously: ...)` annotation in `Durable storage` is kept verbatim, because the MODIFIED block was copied as delivered.
- **W3, line endings**: the main specs are CRLF and the delta specs are LF. Inserted delta excerpts were normalized to CRLF to match each destination file. `runtime-operations/spec.md` was copied byte-identical, so it is LF. Main-spec line endings are therefore mixed across files.

### Merge Readback (byte-level)

- Prefix and suffix preserved byte-identical to the originals (`cmp -n` and `diff` of head/tail ranges). Exit 0 each.
- Each inserted delta block equals its archived delta source, modulo CRLF (`tr -d '\r'`). Exit 0 each.
- `runtime-operations`: `diff -r` against the archived delta, empty.
- `daily-queue/spec.md`: no git diff.

## Archive Move Readback (mandatory)

Snapshot taken with `cp -R` before the move. Moved with `git mv` (the change folder was partly staged; the move succeeded, and the source was confirmed absent afterward). The archive contains the untracked `verify-report.md` as well.

```
$ diff -r "<snapshot of openspec/changes/postgres-adapter>" "openspec/changes/archive/2026-11-03-postgres-adapter"
(no output)
exit=0
```

Active `openspec/changes/` has no remaining change folder.

## Docs

`SystemTurn/docs/02-gaps-and-questions.md`, section 5: added the archived status line and `Next change: infra-foundation`. Section 6 operational notes are unchanged.

## Staging

`git add -A SystemTurn/openspec` and `git add SystemTurn/docs/02-gaps-and-questions.md` were run after this report was written. Nothing was committed.

## Key Learnings

1. Building merged main specs from `head`/`tail` line ranges and verifying with `cmp` and `diff` keeps untouched requirements byte-identical without model-generated text.
2. Delta specs in this repo are LF while main specs are CRLF, so inserted excerpts must be normalized or the main spec ends up with mixed line endings.
3. A MODIFIED requirement block replaces the whole requirement, so any scenario omitted from the delta silently disappears from the main spec.
4. `git mv` on a partially staged change folder moved untracked files too, and the `diff -r` readback against the pre-move snapshot confirmed the full tree.
5. An absent `reviewGate` with the kill switch off means archive proceeds under ordinary policy, not that a receipt is missing.
