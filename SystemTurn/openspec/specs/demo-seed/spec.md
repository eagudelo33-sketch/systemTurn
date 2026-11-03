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

### Requirement: Production guard
Profile `demo` MUST be accepted only together with profile `dev` (H2). Any other combination that includes `demo` (alone, with `test`, `prod`, or with a PostgreSQL datasource) MUST fail startup with an error naming the rule.

#### Scenario: demo alone is rejected
- GIVEN active profiles `demo` only
- WHEN the startup guard runs
- THEN startup fails with an error mentioning `demo` and `dev`

#### Scenario: demo with prod is rejected
- GIVEN active profiles `demo,prod` and a PostgreSQL URL
- WHEN the startup guard runs
- THEN startup fails

#### Scenario: demo with dev is accepted
- GIVEN active profiles `dev,demo` and an H2 URL
- WHEN the context starts on 2026-10-30
- THEN startup succeeds and 11 tickets dated 2026-10-30 exist

#### Scenario: no demo, no check
- GIVEN active profiles `prod` and a PostgreSQL URL
- WHEN the startup guard runs
- THEN the guard does not reject on demo grounds and no seed runs
