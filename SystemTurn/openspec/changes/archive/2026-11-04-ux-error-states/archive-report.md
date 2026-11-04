# Archive Report: ux-error-states

**Archived**: 2026-11-04
**Mode**: hybrid (openspec + Engram project `systemturn`)
**Branch**: `feat/ux-error-states` (stacked on `feat/e2e-browser-tests`, PR #7). Code and docs committed in `1d217c3` and `80e9529`; change artifacts were untracked at archive time and are staged, not committed.
**Verdict at close**: PASS WITH WARNINGS (0 CRITICAL). Both warnings were stale prose in untracked change artifacts and were fixed before archive.
**Review gate**: no `reviewGate` was supplied in the launch context; archive proceeded under ordinary repository policy.

## Outcome

- The advisor view never presents unknown data as a real state. While the first load is in flight or any load failed, it shows no "Conectado al servidor", "No hay turnos pendientes" or "0 registros"; the previous queue data is cleared (kept only while a refresh is in flight) and "Llamar al siguiente" is natively disabled.
- The stale-next hazard is fixed: a failed refresh after "Llamar al siguiente" no longer leaves the previous next-ticket number visible.
- The client view clears its held ticket only for the typed client-side error `TURNO_NO_ENCONTRADO` (server definitively reported the ticket absent) and keeps it on a 503 or network failure.
- No API or backend change (`git diff 1517e13 -- SystemTurn/backend` empty at verify time).

## Evidence

- Verify: `verify-report` (Engram #427, written at verification time): PASS WITH WARNINGS on the first run, `evidence_revision: sha256:b997b09ba4e9022cf94886a91fadf237af967832985a88a025f2b8b939ad6c2a`, 8 requirements / 15 scenarios compliant, build and lint exit 0.
- Final test counts: Vitest 19 passed, Playwright 18 passed (13 before the change: 2 modified, 4 new, 1 accessibility test, plus unchanged ones).
- Strict TDD: RED recorded before implementation (Vitest 13 pass / 1 fail / 1 suite import failure; Playwright 6 failed / 12 passed), including a RED-by-mutation proof for the client-503 guard (accepted from `apply-progress`; verify was read-only and did not re-execute it).
- Tasks: `tasks.md` has 24 checked and 0 unchecked items at archive; no reconciliation was needed.

## Specs synced (main specs under `openspec/specs/`)

| Domain | Action | Requirements before -> after | Scenarios before -> after |
|--------|--------|------------------------------|---------------------------|
| browser-e2e | 2 modified (full replacement), 3 added | 12 -> 15 | 29 -> 34 |
| daily-queue | 1 modified, 1 added | 5 -> 6 | 5 -> 8 |
| runtime-operations | 1 modified (scope note only, scenarios unchanged) | 10 -> 10 | 26 -> 26 |

No requirement or scenario was lost. Grand totals in `openspec/specs/README.md`: 61/143 -> 65/151. The merge was done by a script that extracts requirement blocks from the delta files (no model rewrite of content); line endings of each main spec were preserved. The browser-e2e Purpose section does not state a test count, so no intro count needed updating.

## Deviations

None against the design. Stale prose (`apply-progress` "nothing committed / Phase 4 pending", `proposal` 13-to-17 count) was fixed before archive. Note: `design.md` Open Questions checkboxes remain unticked in the archived copy (the audit trail was not edited).

## Carried items (not part of this change)

- `client-identity`: AM/PM position bug (pre-existing); upgrade the stale-ticket test once persistence exists; client-view 503 handling; component tests for the views.
- P3: greeting copy.
- P3: dead `turnosSimulation.js`.
- Suggestion: add a unit test for the PM branch of the e2e time helper.
- Informational: the green "Asesor" role dot could be misread as the advisor being online.

## Traceability (Engram observation IDs, project `systemturn`)

| Artifact | Topic key | ID |
|----------|-----------|----|
| explore | sdd/ux-error-states/explore | 421 |
| proposal | sdd/ux-error-states/proposal | 422 |
| spec | sdd/ux-error-states/spec | 423 |
| design | sdd/ux-error-states/design | 424 |
| tasks | sdd/ux-error-states/tasks | 425 |
| apply-progress | sdd/ux-error-states/apply-progress | 426 |
| verify-report | sdd/ux-error-states/verify-report | 427 |

Archive contents: proposal.md, exploration.md, specs/ (3 domains), design.md, tasks.md (24/24), apply-progress.md, verify-report.md, archive-report.md.
