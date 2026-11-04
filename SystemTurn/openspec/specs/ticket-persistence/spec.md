# Ticket Persistence Specification

## Purpose
Tickets are durable across backend restarts.

## Requirements

### Requirement: Durable storage
The system MUST store every ticket in the PostgreSQL database configured by `SPRING_DATASOURCE_*`. H2 MAY be used only under profile `dev` (file) or `test`. The `data/` directory MUST remain git-ignored. Every state mutation MUST be persisted. Existing H2 file data MUST NOT be migrated.

#### Scenario: Restart survival
- GIVEN tickets 1-11 exist for 2026-10-29 and 1 is ATENDIDO
- WHEN the backend restarts on the same database
- THEN the 11 tickets and their states are returned unchanged

#### Scenario: Restart survival on PostgreSQL
- GIVEN one PostgreSQL container and a context holding tickets 1-11 for 2026-10-29, with 1 ATENDIDO
- WHEN a second context starts against the same container
- THEN the 11 tickets and their states are returned unchanged

### Requirement: Versioned schema
The schema MUST be created by migration `V1`, unchanged and identical for H2 and PostgreSQL (no vendor-specific folders); the app MUST validate, never auto-generate, the schema. Each ticket MUST have `fecha`, a version, and seconds-precision times.

#### Scenario: Empty database
- GIVEN an empty database
- WHEN the backend starts
- THEN the schema is created by `V1`, validation passes, and the queue is empty

#### Scenario: Empty PostgreSQL database
- GIVEN an empty PostgreSQL container
- WHEN the context starts
- THEN `V1` is applied, Hibernate validation passes, and the queue is empty

#### Scenario: Time precision
- GIVEN a ticket requested at 08:34:27
- WHEN it is saved and reloaded
- THEN `horaSolicitud` is 08:34:27

#### Scenario: Time precision on PostgreSQL
- GIVEN a ticket requested at 08:34:27 with clock zone `America/Bogota` while the JVM zone is UTC
- WHEN it is saved and reloaded from PostgreSQL
- THEN `horaSolicitud` is 08:34:27 and `fecha` is unchanged

#### Scenario: V1 file untouched
- GIVEN the migrations folder
- WHEN it is listed
- THEN it contains `V1` with its original checksum and no vendor-specific subfolder

### Requirement: Domain isolation
The domain model MUST NOT depend on persistence annotations, and ticket equality MUST be by id.

#### Scenario: Position by equality
- GIVEN a ticket reloaded as a new instance
- WHEN its queue position is computed
- THEN the position matches the one for the original instance
