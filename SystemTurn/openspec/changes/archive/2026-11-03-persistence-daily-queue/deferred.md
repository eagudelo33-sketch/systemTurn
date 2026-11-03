# Deferred to `client-identity`

These scenarios were removed from this change's specs on 2026-11-02 (user decision) because they need component tests (Testing Library + jsdom), which this change does not set up. The behavior is already implemented; only the spec promise and its proof move.

The `client-identity` change MUST carry these as spec scenarios and prove them with component tests.

| From spec | Scenario | Implemented in |
|-----------|----------|----------------|
| concurrency-conflicts | **Advisor conflict** — GIVEN the advisor's "call next" races with a concurrent change to the same ticket, WHEN 409 is returned, THEN a friendly message shows and the dashboard reloads (no retry button) | `AsesorView.jsx` |
| concurrency-conflicts | **Client cancel conflict** — GIVEN the client's cancel races with an advisor attending the same ticket, WHEN 409 is returned, THEN the message shows and the ticket is re-fetched | `ClienteView.jsx` |
| daily-queue | **Browser holds yesterday's ticket** — GIVEN the client holds a ticket from a past day, WHEN it queries the ticket today, THEN the generic "not found" message is shown (no special expired message) | `turnosService.js` `consultarTurno` |

Note: the backend half of the stale-ticket rule (past days excluded from queue/dashboard) stays in this change, covered by "Yesterday excluded".
