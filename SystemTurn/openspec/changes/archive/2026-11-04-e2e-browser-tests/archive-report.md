# Archive Report: e2e-browser-tests

**Archived**: 2026-11-04 to `openspec/changes/archive/2026-11-04-e2e-browser-tests/`
**Mode**: hybrid (openspec + Engram project `systemturn`)
**Branch**: feat/e2e-browser-tests (code committed; change artifacts were untracked and are staged, not committed)

## Outcome

A permanent Playwright suite (13 tests, headless Chromium, serial) now proves the client and advisor views through a real browser, the Vite proxy and the real backend (`npm run e2e`). It also proves the three view scenarios deferred since `persistence-daily-queue`. No application or API behavior changed (no edits under `src/` or `backend/`).

## Evidence (at close)

- Verify verdict: PASS WITH WARNINGS on the first run (no CRITICAL). `evidence_revision: sha256:bcc4130309a28d3913151d7a30a3ecc20aec654f010b6917521c7abc8d94809d`. 14 requirements and 34 scenarios compliant (the change total: 12 + 2 modified/added across the three specs; 29 + 5 scenarios).
- Per `verify-report` (#419), at verification time: `npm run e2e` 13 passed (46.1 s, 1 worker), `npm test` 11 passed (Vitest), build and lint PASS.
- Tasks: 23/23 checked in `tasks.md` (Task Completion Gate passed, no stale checkboxes).
- Review gate: not applicable (no `reviewGate` in play for this candidate).
- Verify warnings are resolved by this archive: (1) the `docs/app.md` link now points to `../openspec/specs/browser-e2e/spec.md`; (2) the `openspec/specs/README.md` how-to-run row was added; (3) the change artifacts are archived and staged. Suggestion 1 (mark 503 as mocked) is applied in `docs/README.md`.

## Specs synced

| Domain | Action | Requirements / scenarios (before to after) |
|--------|--------|--------------------------------------------|
| browser-e2e | Created (mechanical `cp`, only the `## ADDED Requirements` heading renamed to `## Requirements`) | none to 12 / 29 |
| concurrency-conflicts | Updated: `Frontend conflict handling` replaced in full (deferral note removed); 2 scenarios added | 5 / 13 to 5 / 15 |
| daily-queue | Updated: 1 requirement and 1 scenario added | 4 / 4 to 5 / 5 |

Grand total in `openspec/specs/README.md`: 48 req / 111 scenarios to 61 req / 143 scenarios. Nothing lost; the other specs are unchanged.

Readback: `diff -r` of the archived change folder against its pre-move snapshot was empty. `diff` of the archived delta `browser-e2e/spec.md` against the main spec shows only the heading rename (`## ADDED Requirements` to `## Requirements`).

## Deviations

- The backend command on Windows is `.\mvnw.cmd` (spike, tasks 1.1-1.3).
- Characterization tests (2-9) show RED by mutation (assertion changed, failure observed, reverted) instead of a natural RED; accepted in verify.

## Carried items

- `ux-error-states` (next change): advisor 503 still shows "Conectado al servidor", "No hay turnos pendientes" and "0 registros"; a stale ticket shows "No encontramos ese turno." while the card still shows Pendiente with a position (both found in screenshots).
- `client-identity`: fix the AM/PM position bug (ISO times); upgrade the stale-ticket test to a true stored-ticket scenario once persistence exists.
- `infra-cicd`: run `npm run e2e` on Linux (`npx playwright install --with-deps chromium`, `mvnw` mode 100755, upload `playwright-report/` and `test-results/`); the full-stack 503 test is still required (the mocked 503 here does not satisfy it).
- Suggestion: add a unit test for the PM branch of the time parser (`minutosDelDia` / `posicionEsperada`); verification ran only in the morning.

## Artifact traceability (Engram, project systemturn)

| Artifact | Observation |
|----------|-------------|
| explore | #411 |
| proposal | #412 |
| spec | #413 |
| design | #414 |
| tasks | #416 |
| apply-progress | #417 |
| verify-report | #419 |
| archive-report | `sdd/e2e-browser-tests/archive-report` |

Filesystem artifacts: `exploration.md`, `proposal.md`, `specs/`, `design.md`, `tasks.md`, `apply-progress.md`, `verify-report.md`.

## Post-archive checks

- Relative links and anchors in `SystemTurn/README.md`, `SystemTurn/docs/*.md` and `SystemTurn/openspec/specs/**/*.md`: 60 links in 15 files, 0 broken.
- `check-conventions.sh`: 24 of 25 PASS. The failing check `no_app_files_touched` is an `infra-foundation` guard that compares the branch with `main` and rejects any file under `SystemTurn/frontend/`. On this branch it lists only e2e tests and tooling config (`e2e/`, `playwright.config.js`, `vite.config.js`, `eslint.config.js`, `package.json`, `package-lock.json`, `.gitignore`); nothing under `src/` or `backend/`. It is an expected, scope-related failure that clears on `main` after merge, but the guard will keep failing on any future branch that touches frontend files (candidate for `infra-cicd` to scope it to `infra/` branches).
