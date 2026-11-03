# Demo Seed Specification

## Purpose
Sample tickets load only under the `demo` profile.

## Requirements

### Requirement: Profile-gated seed
The CSV seed MUST run only with profile `demo`. Default startup MUST yield an empty queue.

#### Scenario: Default profile
- GIVEN no active profile
- WHEN the backend starts
- THEN no tickets exist

### Requirement: Seed content
The seed MUST create 11 tickets dated today: 1-5 ATENDIDO, 6 PENDIENTE, 7 CANCELADO, 8-11 PENDIENTE. The next requested ticket MUST be `T-012`.

#### Scenario: Demo startup on 2026-10-30
- GIVEN profile `demo` and an empty DB
- WHEN the backend starts
- THEN 11 tickets dated 2026-10-30 exist with the states above
- AND the next solicitar returns `T-012`

### Requirement: Idempotent seed
The seed MUST be skipped when today already has tickets.

#### Scenario: Second startup
- GIVEN the demo seed ran earlier on 2026-10-30
- WHEN the backend restarts under `demo`
- THEN ticket count stays 11

#### Scenario: New day
- GIVEN the seed ran on 2026-10-29
- WHEN the backend starts under `demo` on 2026-10-30
- THEN 11 new tickets dated 2026-10-30 are created
