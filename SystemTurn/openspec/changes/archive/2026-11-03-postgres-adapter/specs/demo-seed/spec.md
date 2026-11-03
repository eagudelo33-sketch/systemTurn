# Delta for Demo Seed

## ADDED Requirements

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
