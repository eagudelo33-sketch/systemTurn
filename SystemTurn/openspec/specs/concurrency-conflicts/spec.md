# Concurrency Conflicts Specification

## Purpose
Concurrent writers never corrupt numbering or state; conflicts surface as 409 `CONFLICTO`.

## Requirements

### Requirement: Unique daily numbers
`(fecha, numero)` MUST be unique in storage. Number assignment MUST retry a bounded number of times on a duplicate.

#### Scenario: Concurrent solicitar
- GIVEN an empty queue on 2026-10-30
- WHEN 10 threads call solicitar simultaneously
- THEN exactly numbers 1..10 are assigned, each once

#### Scenario: Retry exhaustion
- GIVEN every retry hits a duplicate number
- WHEN solicitar is called
- THEN the response is 409 `CONFLICTO`

### Requirement: Optimistic locking
`atender` and `cancelar` MUST fail with 409 `CONFLICTO` when the ticket changed since it was read.

#### Scenario: Version conflict
- GIVEN two actors read ticket 6 at the same version
- WHEN one cancels it, then the other attends it
- THEN the second gets 409 `CONFLICTO` and the ticket stays CANCELADO

### Requirement: 409 contract
The error body MUST use the existing error shape with code `CONFLICTO`; other API v1 shapes MUST NOT change.

#### Scenario: Error body
- GIVEN a stale attend
- WHEN the API responds
- THEN status is 409 and the code is `CONFLICTO`

### Requirement: Frontend conflict handling
`turnosService.js` MUST map `CONFLICTO` to a friendly message flagged refreshable, so every view can react to it.

> View-level reactions (advisor auto-reload, client re-fetch) are implemented but deferred to the `client-identity` change for their spec scenarios and component tests. See `deferred.md`.

#### Scenario: Service mapping
- GIVEN the API returns 409 `CONFLICTO`
- WHEN the service handles it
- THEN it throws an error with a friendly message and a refreshable flag

#### Scenario: Cancel after a completed attend is not a conflict
- GIVEN ticket 6 was attended and that change is already saved
- WHEN the client cancels ticket 6
- THEN the response is 400 `OPERACION_INVALIDA`, not 409
