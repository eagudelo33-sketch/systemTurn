```yaml
schema: gentle-ai.verify-result/v1
evidence_revision: sha256:b997b09ba4e9022cf94886a91fadf237af967832985a88a025f2b8b939ad6c2a
verdict: pass_with_warnings
blockers: 0
critical_findings: 0
requirements: 8/8
scenarios: 15/15
test_command: npm test
test_exit_code: 0
test_output_hash: sha256:cbe167f2ed3e6bc0b2ae82edf254db1b862c2e1a49e2e038a9be1e8cfef721be
build_command: npm run build
build_exit_code: 0
build_output_hash: sha256:a6bc0ee324c1cbb484d0ed7ad132909f45389f8124596ebce284a28840e16b89
```

## Verification Report

**Change**: ux-error-states
**Version**: N/A (3 deltas: browser-e2e, daily-queue, runtime-operations)
**Mode**: Strict TDD
**Evidence revision basis**: sha256 of HEAD 80e95292e02db43ebd8a6196fa30164dc623d8f3 plus the hashes of the npm test, npm run e2e and npm run build outputs (newline-joined, in that order).

### Completeness
| Metric | Value |
|--------|-------|
| Tasks total | 34 checklist items in tasks.md (Phases 1-4) |
| Tasks complete | all; 0 open |
| Tasks incomplete | 0 |

### Build & Tests Execution
**Build**: PASSED (npm run build, exit 0, 23 modules)
**Lint**: PASSED (npm run lint, exit 0)
**Vitest**: 19 passed / 0 failed (npm test, exit 0, 2 files)
**Playwright**: 18 passed / 0 failed (npm run e2e, exit 0, 50.6s). e2e output hash sha256:303ee17ee92ace989d04801e659f303b74369aeef3acf4e31942247065685a14. npx playwright test --list: 18 tests in 3 files.
**Cleanup**: no listeners on 8080/5173 and no java/node processes after the runs.
**Backend**: git diff 1517e13 -- SystemTurn/backend is empty.
**Coverage**: not available (no coverage tool configured).

### Spec Compliance Matrix
Totals: 8 requirements (browser-e2e 5, daily-queue 2, runtime-operations 1) and 15 scenarios (9 + 4 + 2).

| Requirement | Scenario | Test | Result |
|---|---|---|---|
| Mocked advisor service-unavailable recovery | advisor 503 shows Reintentar and recovers | e2e 03 same title | COMPLIANT |
| Mocked conflict and stale-ticket view scenarios | Advisor conflict reloads without retry | e2e 03 same title | COMPLIANT |
| (same) | Client cancel conflict re-fetches | e2e 03 same title | COMPLIANT |
| (same) | Browser holds a ticket missing from today's queue | e2e 03 same title | COMPLIANT |
| Honest advisor load states | Advisor loading state is not reported as connected | e2e 03 same title | COMPLIANT |
| (same) | Advisor empty queue is reported as empty | e2e 03 same title | COMPLIANT |
| (same) | Advisor refresh failure discards stale data | e2e 03 same title | COMPLIANT |
| Client keeps its ticket when refresh fails | Client keeps its ticket when refresh fails | e2e 03 same title | COMPLIANT |
| Accessible state semantics | status, single alert and native disabled | e2e 03 same title (own test: getByRole status, strict getByRole alert, toBeDisabled) | COMPLIANT |
| Stale ticket shows generic not-found | Browser holds a ticket missing from today's queue | e2e 03 same title | COMPLIANT |
| Typed not-found error in the client service | consultarTurno rejects with the not-found code | Vitest turnosService.test.js > consultarTurno > consultarTurno rejects with the not-found code | COMPLIANT |
| (same) | consultarTurno resolves a present ticket | Vitest same file > consultarTurno resolves a present ticket | COMPLIANT |
| (same) | 503 is not converted to the not-found code | Vitest same file > 503 is not converted to the not-found code | COMPLIANT |
| Frontend 503 handling | Service maps 503 | Vitest describe Service maps 503 (3 existing cases) | COMPLIANT |
| (same) | 409 mapping unchanged | Vitest describe CONFLICTO mapping (4 existing cases) | COMPLIANT |

**Compliance summary**: 15/15 scenarios compliant. The per-state copy table is also covered at unit level by 5 estadoPanel tests.

### Correctness (Static Evidence)
| Item | Status | Notes |
|---|---|---|
| turns null = unknown | Implemented | useState(null); estadoPanel branches on turns === null |
| Any failed load clears turns + nextTurn | Implemented | aplicarResultado failure branch (AsesorView.jsx:38-39) covers mount, Reintentar and post-call refresh |
| Data kept while refresh in flight | Implemented | refreshDashboard only sets status loading; turns stay and render as listo |
| Badge role status, 3 copies, CSS classes | Implemented | AsesorView.jsx:119; classes cargando, error, empty; CSS .data-mode.cargando and .error added, .demo removed |
| Next card / count / table copy per state | Implemented | src/utils/panelAsesor.js matches the spec table, incl. empty-day and no-match copy |
| Call button | Implemented | unchanged condition (no nextTurn, isCalling, status loading), native disabled |
| isDemoMode removed | Implemented | rg isDemoMode outside node_modules/dist: no matches (import and export gone) |
| TURNO_NO_ENCONTRADO typed error | Implemented | exported constant; code set on the absent-ticket error; message unchanged |
| ClienteView clears only for that code | Implemented | ClienteView.jsx:52; 503 and network failure keep the ticket |
| At most one role alert per view | Implemented | one in AsesorView:124, one in ClienteView:132 |
| No API/backend change | Implemented | backend diff empty |

### Coherence (Design)
| Decision | Followed? | Notes |
|---|---|---|
| Unknown marker; clearing in aplicarResultado | Yes | |
| estadoPanel in src/utils/panelAsesor.js | Yes | |
| Appended loading row dropped | Yes | only the panel.mensajeTabla row remains |
| E2E tests appended to 03 (4 to 9) | Yes | |
| Open question 1 (badge wording) | Yes in spec | design.md still lists it unchecked (cosmetic) |

### TDD Compliance
| Check | Result | Details |
|-------|--------|---------|
| TDD Evidence reported | Yes | apply-progress.md has the table; Phase 4 rows present |
| All tasks have tests | Yes | Phase 1-2 rows reference existing test files; docs tasks are N/A |
| RED confirmed (tests exist) | Yes | all listed test files exist |
| GREEN confirmed (tests pass) | Yes | 19/19 and 18/18 re-executed here |
| Triangulation adequate | Yes | 3 + 5 unit cases; single e2e where the spec has one scenario |
| Safety net for modified files | Yes | 11/11 and 13/13 baselines; new files N/A (new) |
| RED-by-mutation (client-503 guard) | Reported, not re-executed | Verify is read-only, so no mutation was run. The guard is keyed on code === TURNO_NO_ENCONTRADO and the test asserts Pendiente and a visible Actualizar estado, so it can fail |

**TDD Compliance**: 6/6 checks passed (mutation proof accepted from apply-progress).

### Test Layer Distribution
| Layer | Tests | Files | Tools |
|-------|-------|-------|-------|
| Unit | 8 new (19 total) | 2 | Vitest |
| Integration | 0 | 0 | not installed (documented gap) |
| E2E | 7 new or modified (18 total) | 1 changed | Playwright |

### Assertion Quality
No tautologies, no ghost loops (esperarAusentes iterates a fixed literal array), no smoke-only tests. **Assertion quality**: all assertions verify real behavior.

### Quality Metrics
**Linter**: no errors. **Type Checker**: not available (JavaScript project).

### Claims and Docs
- Mocked 503 is not claimed for runtime-operations or infra-cicd. The browser-e2e requirement says so explicitly. The runtime-operations delta points to browser-e2e for advisor behavior and keeps client 503 deferred to client-identity. docs/README.md says mocked advisor 503 (not a full-stack 503) and the infra-cicd row still lists the full-stack 503 test.
- docs/README.md: both UX gap rows removed; Dead code lists only turnosSimulation.js; Vitest 19 and Playwright 18; roadmap row 5 done and infra-network next. Accurate.
- docs/app.md: view rows describe the new states; counts 19 and 18; 03 = 9 (listing shows 9 tests); 9 real + 9 mocked = 18. Accurate.

### Suspicious-item review
- Loading test: both StrictMode GETs are held, waitForRequest runs first, routes are aborted at the end. Passed cleanly with no teardown noise. Low flake risk.
- Refresh-failure test needs a non-null next ticket from the real backend (asserted explicitly). Only a drained seed would break it. Low risk.
- No time-of-day dependence introduced (copy is static; the AM/PM position bug is pre-existing and out of scope).
- Strict getByRole alert would fail on a second alert; it passes.

### Issues Found
**CRITICAL**: None
**WARNING**:
1. apply-progress.md prose is stale: it says Nothing committed and Phase 4 (docs) pending, although commits 1d217c3 and 80e9529 exist and the Phase 4 rows are filled in below.
2. proposal.md still says Playwright goes 13 to 17 and All 17 Playwright tests; the actual count is 18. Task 4.3 only covered spec and design.
**SUGGESTION**:
1. design.md Open Questions are still unchecked although resolved.
2. The client-503 guard mutation proof is not reproducible from the repo; consider recording the mutated diff.
3. Component tests for the views remain a documented gap (client-identity).

### Verdict
PASS WITH WARNINGS
All 15 scenarios pass at runtime, the design is followed and the backend is untouched; the warnings are stale prose in untracked change artifacts.
