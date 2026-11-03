# Ticket Persistence Specification

## Purpose
Tickets are durable across backend restarts.

## Requirements

### Requirement: Durable storage
The system MUST store every ticket in a file-based database whose path comes from `TURNOS_DB_PATH` (default `./data/turnos`). The `data/` directory MUST be git-ignored. Every state mutation MUST be persisted.

#### Scenario: Restart survival
- GIVEN tickets 1-11 exist for 2026-10-29 and 1 is ATENDIDO
- WHEN the backend restarts on the same DB file
- THEN the 11 tickets and their states are returned unchanged

### Requirement: Versioned schema
The schema MUST be created by migration `V1`; the app MUST validate, never auto-generate, the schema. Each ticket MUST have `fecha`, a version, and seconds-precision times.

#### Scenario: Empty database
- GIVEN no DB file exists
- WHEN the backend starts
- THEN the schema is created by `V1` and the queue is empty

#### Scenario: Time precision
- GIVEN a ticket requested at 08:34:27
- WHEN it is saved and reloaded
- THEN `horaSolicitud` is 08:34:27

### Requirement: Domain isolation
The domain model MUST NOT depend on persistence annotations, and ticket equality MUST be by id.

#### Scenario: Position by equality
- GIVEN a ticket reloaded as a new instance
- WHEN its queue position is computed
- THEN the position matches the one for the original instance
