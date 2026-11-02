# operation-lifetime Specification

## Purpose
Guarantees that a user-data operation, once started, runs to completion even if the screen or ViewModel that started
it goes away. The cache, the outbox and the server can then never be left half-updated by a cancelled caller.

## Requirements

### Requirement: One application-wide scope for data work
The app SHALL create exactly one application-lifetime coroutine scope per process, on Android and on iOS alike. A
failure in one operation SHALL NOT cancel the scope or any other operation running in it.

#### Scenario: One operation fails
- **WHEN** an operation running in the application scope throws
- **THEN** that operation's caller receives the error, and other operations in the scope keep running

#### Scenario: Same scope everywhere
- **WHEN** the user repository is resolved from dependency injection on Android or iOS
- **THEN** it receives the single application-wide scope

### Requirement: Repository operations outlive their caller
Every suspending user-repository operation SHALL run in the application scope: create, update, local remove, restore,
finalise delete, sync and refresh. Cancelling the caller SHALL cancel only the caller's wait, never the operation
itself, which SHALL complete its local writes, remote call and any rollback or queueing.

#### Scenario: Screen closed during a create
- **WHEN** a ViewModel calls create and is cleared while the POST is in flight
- **THEN** the create still completes: on 201 the temp user is re-keyed to the server id, on a retryable failure it
  stays queued, and on a permanent failure it is rolled back

#### Scenario: Screen closed while finalising a delete
- **WHEN** the caller of finalise delete is cancelled before the DELETE response arrives
- **THEN** the delete completes or is queued exactly as if the caller had waited

#### Scenario: Caller still waiting
- **WHEN** the caller is not cancelled
- **THEN** it receives the operation's result exactly as before this change

### Requirement: Observing users stays bound to the observer
Observing the user list SHALL remain a cold stream tied to its collector, not to the application scope, so it stops when
the observer stops collecting.

#### Scenario: Observer stops collecting
- **WHEN** the collector of the user list is cancelled
- **THEN** the observation stops, and nothing keeps it alive in the application scope
