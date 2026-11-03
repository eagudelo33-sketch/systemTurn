# Archive Report: persistence-daily-queue

**Change**: persistence-daily-queue
**Archived on**: 2026-11-03
**Archive path**: `openspec/changes/archive/2026-11-03-persistence-daily-queue/`
**Artifact store**: hybrid (openspec + engram project `systemturn`)
**Closure type**: Closed with accepted warnings. Verify verdict PASS WITH WARNINGS, 0 CRITICAL. No partial archive and no stale-checkbox reconciliation.
**Git state**: All change files staged, nothing committed. Delivery is a single PR with the user-accepted `size:exception`.

## Source Observations Read (Engram)

| Artifact | Engram ID | Topic key | Notes |
|----------|-----------|-----------|-------|
| proposal | 375 | `sdd/persistence-daily-queue/proposal` | Read in full |
| spec | 376 | `sdd/persistence-daily-queue/spec` | Read in full; summary of 4 capabilities |
| design | 377 | `sdd/persistence-daily-queue/design` | Read in full; points to design.md |
| tasks | 378 | `sdd/persistence-daily-queue/tasks` | Read in full; STALE snapshot (see Contradictions) |
| apply-progress | 379 | `sdd/persistence-daily-queue/apply-progress` | Read in full; intermediate snapshot |
| verify-report | 380 | `sdd/persistence-daily-queue/verify-report` | Read in full; run 4 (final verify) |

Filesystem artifacts archived (canonical copies, from `openspec/changes/persistence-daily-queue/`): `proposal.md`, `exploration.md`, `design.md`, `tasks.md`, `apply-progress.md`, `verify-report.md`, `deferred.md`, `specs/{ticket-persistence,daily-queue,concurrency-conflicts,demo-seed}/spec.md`.

## Gates

- **Native review gate**: `gentle-ai review mode status` reports receipt-driven development `off (decided by default)`. `reviewGate` is structurally absent, so archive proceeds under ordinary repository policy.
- **Task completion gate**: `tasks.md` has 23 checked and 0 unchecked (`- [ ]`) items. No reconciliation was needed or performed.
- **Verify gate**: run 4 (`verify-report.md`, observation 380), `verdict: pass_with_warnings`, `blockers: 0`, `critical_findings: 0`, requirements 14/14, scenarios 18/18. Validator admitted, `evidence_revision: sha256:dccf37cf1665d0c0d1ead952bf8f5ab721f7ced12d0273cf86097eaaccf2de08`.

## Final State at Close

Ranking applied per the Final-State Authority section. The launch prompt (rank 3) is the most recent account. Verify-report and apply-progress (rank 4) are intermediate snapshots and are cited only as history.

- **Tests (launch prompt; agrees with verify run 4 at its time)**: backend `./mvnw test` 67/67, frontend `npm test` 8/8, lint clean, build OK. The earlier backend count of 63 in apply-progress and the W3/W4 test additions are history, not current state.
- **W3 (atomic demo seed)**: RESOLVED in run 4. `DemoSeedRunner.run` is `@Transactional`; `DemoSeedAtomicityTest` covers it (per verify-report 380, at verification time).
- **W4 (over-broad duplicate mapping)**: RESOLVED in run 4. `JpaTurnoRepository.guardar` maps to `NumeroDuplicadoException` only for insert plus `UK_TURNO_FECHA_NUMERO`; other integrity violations rethrow.
- **Requirement and scenario coverage**: 14/14 requirements and 18/18 scenarios COMPLIANT in run 4.

### Open Warnings Carried Forward (per verify-report 380, run 4)

- **W1**: View-level conflict reactions (AsesorView, ClienteView) and the stale-ticket not-found path are implemented but unproven by component tests. Moved to the client-identity change (see Deferred Obligations). Linux `npm ci` deferred to CI. Delivered with `size:exception`.
- **W2**: `TurnoControllerTest` and nested `DemoSeedRunnerTest` use the real clock. User-accepted follow-up.
- **W5**: No `.gitattributes`; mixed line endings in `turnos-api/.gitignore`. User-accepted follow-up.
- **W6**: Task-count inconsistency across artifacts (26 / 22 / 23). Documentation only. Final count is 23 checked, 0 unchecked (tasks.md).
- **W7**: Non-duplicate integrity violations (NOT NULL, CHECK, unique clash on update) surface as an unhandled `DataIntegrityViolationException` and a generic 500. Not critical; not reachable through validated API paths.
- **Suggestions (verify-report 380)**: S1 multi-ticket `RestartSurvivalTest` to match the spec literally; S2 full-stack stale PATCH 409 test; S3 component-test setup (Testing Library) in client-identity; S4 explicit handler or log line for `DataIntegrityViolationException`.

## Specs Synced (main specs created)

No main specs existed before this archive (`openspec/specs/` contained only `.gitkeep`). Each delta spec was a full spec (`## Purpose` + `## Requirements`, no ADDED/MODIFIED/REMOVED/RENAMED sections), so each was copied mechanically with `cp`.

| Domain | Action | Requirements | Scenarios (per verify) |
|--------|--------|--------------|------------------------|
| ticket-persistence | Created | 3 (Durable storage, Versioned schema, Domain isolation) | Restart survival, Empty database, Time precision, Position by equality |
| daily-queue | Created | 4 (Today's date by configured zone, Queue and dashboard scoped to today, Daily numbering restart, Past-day cancel rejected) | Midnight boundary, Yesterday excluded, First ticket of day, Cancel yesterday ticket |
| concurrency-conflicts | Created | 4 (Unique daily numbers, Optimistic locking, 409 contract, Frontend conflict handling) | Concurrent solicitar, Retry exhaustion, Version conflict, Error body, Service mapping, Cancel after completed attend |
| demo-seed | Created | 3 (Profile-gated seed, Seed content, Idempotent seed) | Default profile, Demo startup, Second startup, New day |

Total: 14 requirements. The 18 scenarios listed in verify-report 380 are the scenarios that remain in the specs; the 3 view-level scenarios removed by user decision are in `deferred.md`, not in the main specs.

Main spec files:
- `openspec/specs/ticket-persistence/spec.md`
- `openspec/specs/daily-queue/spec.md`
- `openspec/specs/concurrency-conflicts/spec.md`
- `openspec/specs/demo-seed/spec.md`

## Deferred Obligations (for the client-identity change)

Per `deferred.md` (binding instruction), the client-identity change MUST carry these three scenarios as spec scenarios and prove them with component tests (Testing Library and jsdom):

| From spec | Scenario | Implemented in |
|-----------|----------|----------------|
| concurrency-conflicts | Advisor conflict: 409 on a racing "call next" shows a friendly message and reloads the dashboard (no retry button) | `AsesorView.jsx` |
| concurrency-conflicts | Client cancel conflict: 409 on a racing cancel shows the message and re-fetches the ticket | `ClienteView.jsx` |
| daily-queue | Browser holds yesterday's ticket: the generic "not found" message is shown (no special expired message) | `turnosService.js` `consultarTurno` |

The backend half of the stale-ticket rule (past days excluded from queue and dashboard) stays covered in this change by "Yesterday excluded".

**Naming discrepancy (unresolved)**: `deferred.md` names the target change `client-identity`. `docs/02-gaps-and-questions.md` section 2 and the suggested-changes table name the equivalent change `client-ticket-lookup`. Neither file was renamed. The owner of the next change must pick one name and keep the other as an alias.

## Contradictions and Stale Snapshots Recorded

1. **Tasks observation 378 is stale.** Engram 378 (created 21:17, revision 3) says units 1-7 are done and unit 8 (8.1-8.3) is pending. The persisted `tasks.md` (rank 2) shows 23 checked and 0 unchecked. The final state is complete. Engram 378 was not updated (no `mem_update` performed by this phase).
2. **Apply-progress count is a snapshot.** Apply-progress 379 says 22/22 tasks complete. `tasks.md` has 23 checked. The 26 / 22 / 23 mismatch is W6 (documentation only). Final count taken from `tasks.md`.
3. **Task 8.3 is checked but partial.** `tasks.md` marks 8.3 done; verify W1 and the verify completeness table record that the Linux `npm ci` check is deferred to CI by design. Carried as a warning, not reopened.
4. **verify-report.md staged version was older than the working tree.** At archive start, git status showed `AM` for `verify-report.md` (staged copy differed from working tree). The archive used the working-tree bytes, which carry the admitted run 4 evidence revision. `git add -A SystemTurn/openspec` stages those bytes.
5. **Engram vs. filesystem for verify-report.** Engram 380 is the run 4 summary; the full report on disk is the canonical copy and was archived byte-identically.

No contradiction remained unranked; all were resolved by ranking above or recorded here.

## Mechanical Readback (verbatim `diff -r`)

Spec sync (source = `openspec/changes/persistence-daily-queue/specs/<domain>/spec.md`, destination = `openspec/specs/<domain>/spec.md`):

```
ticket-persistence: (no output, exit 0)
daily-queue: (no output, exit 0)
concurrency-conflicts: (no output, exit 0)
demo-seed: (no output, exit 0)
```

Archive move (source = pre-move recursive snapshot of `openspec/changes/persistence-daily-queue/`, destination = `openspec/changes/archive/2026-11-03-persistence-daily-queue/`), taken before this report was written:

```
(no output, exit 0)
```

Source folder confirmed absent after the move (`openspec/changes/persistence-daily-queue` no longer exists). Move performed with `git mv`.

## Traceability

- Archive folder: `openspec/changes/archive/2026-11-03-persistence-daily-queue/`
- Docs updated: `docs/02-gaps-and-questions.md` section 5, row 3 marked done (persistence-daily-queue, archived 2026-11-03) with deferred-scenario ownership noted.
- Engram: `sdd/persistence-daily-queue/archive-report` (type architecture, project systemturn).

## Risks Accepted at Close

- Warnings W1, W2, W5, W6, W7 and suggestions S1-S4 carried forward as listed above. None are CRITICAL.
- Client-identity scope now includes three deferred view-level scenarios (see Deferred Obligations).
- Single PR at size:exception; the PR has not been opened and nothing has been committed.
