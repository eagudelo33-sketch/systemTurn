# Delta for Concurrency Conflicts

## ADDED Requirements

### Requirement: Engine-independent duplicate detection
An insert violating the `(fecha, numero)` unique constraint MUST be translated to a duplicate-number outcome on every supported engine, comparing the constraint name case-insensitively (H2 reports `UK_TURNO_FECHA_NUMERO`, PostgreSQL `uk_turno_fecha_numero`). Other integrity violations MUST NOT be reported as duplicate numbers. Number assignment, the bounded retry, and 409 `CONFLICTO` behavior MUST be identical on H2 and PostgreSQL.

#### Scenario: Duplicate on H2
- GIVEN ticket number 1 exists for 2026-10-30 on H2
- WHEN another ticket with number 1 for 2026-10-30 is saved
- THEN `NumeroDuplicadoException` is raised

#### Scenario: Duplicate on PostgreSQL
- GIVEN ticket number 1 exists for 2026-10-30 on PostgreSQL
- WHEN another ticket with number 1 for 2026-10-30 is saved
- THEN `NumeroDuplicadoException` is raised

#### Scenario: Mixed-case constraint name
- GIVEN a unique violation whose constraint name is `Uk_Turno_Fecha_Numero`
- WHEN the repository translates it
- THEN it is a duplicate-number outcome

#### Scenario: Unrelated violation is not a duplicate
- GIVEN a not-null or check violation on a ticket insert
- WHEN the repository translates it
- THEN it is NOT a duplicate-number outcome

#### Scenario: Concurrent solicitar on PostgreSQL
- GIVEN an empty queue on 2026-10-30 in PostgreSQL
- WHEN 10 threads call solicitar simultaneously
- THEN exactly numbers 1..10 are assigned, each once

#### Scenario: Version conflict on PostgreSQL
- GIVEN two actors read ticket 6 at the same version on PostgreSQL
- WHEN one cancels it, then the other attends it
- THEN the second gets 409 `CONFLICTO` and the ticket stays CANCELADO

#### Scenario: Retry exhaustion unchanged
- GIVEN every retry hits a duplicate number
- WHEN solicitar is called
- THEN the response is 409 `CONFLICTO`
