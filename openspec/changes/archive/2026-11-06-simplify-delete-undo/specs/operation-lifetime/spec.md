# Spec Delta

## MODIFIED Requirements

### Requirement: Repository operations outlive their caller
Every suspending user-repository operation SHALL run in the application scope: create, update, delete, sync and
refresh. Cancelling the caller SHALL cancel only the caller's wait, never the operation itself, which SHALL complete its
local writes, remote call and any rollback or queueing.

#### Scenario: Screen closed during a create
- **WHEN** a ViewModel calls create and is cleared while the POST is in flight
- **THEN** the create still completes: on 201 the temp user is re-keyed to the server id, on a retryable failure it
  stays queued, and on a permanent failure it is rolled back

#### Scenario: Screen closed while finalising a delete
- **WHEN** the caller of delete is cancelled before the DELETE response arrives
- **THEN** the delete completes or is queued exactly as if the caller had waited

#### Scenario: Caller still waiting
- **WHEN** the caller is not cancelled
- **THEN** it receives the operation's result exactly as before this change
