# Spec Delta

## MODIFIED Requirements

### Requirement: Only retryable failures are queued
A write SHALL be queued only when its API call fails with no connectivity, a timeout, a 5xx response, HTTP 429, HTTP 401
or HTTP 403. Any other failure SHALL NOT be queued.

#### Scenario: No internet
- **WHEN** a create, update or finalised delete fails because the device is offline
- **THEN** a pending entry is stored for that user, and the caller is told the write was saved for later sync

#### Scenario: Server error
- **WHEN** a write fails with HTTP 503
- **THEN** a pending entry is stored for that user

#### Scenario: Rate limited
- **WHEN** a write fails with HTTP 429
- **THEN** a pending entry is stored for that user

#### Scenario: Validation error
- **WHEN** a create fails with HTTP 422
- **THEN** no pending entry is stored and the caller receives the classified validation error

#### Scenario: Unauthorised
- **WHEN** a write fails with HTTP 401
- **THEN** a pending entry is stored for that user, and the caller is told the write was queued because the server could
  not authenticate the app

#### Scenario: Replay rate limited
- **WHEN** a pending entry is replayed and the API responds 429
- **THEN** the entry stays queued for the next sync and the remaining entries are still sent

#### Scenario: Replay unauthorised
- **WHEN** a pending entry is replayed and the API responds 401
- **THEN** the entry stays queued for the next sync, the user keeps its local data, and the remaining entries are still
  sent

### Requirement: Permanently rejected replays are dropped
If a replayed entry fails with an error that is not kept for a later sync, the system SHALL delete the entry without
recording it anywhere and count it as rejected (a DELETE answered 404 counts as synced instead). Sync SHALL NOT change
the cached users for it: the next refresh replaces the cached list with the server's, which removes a temporary user
whose create was rejected, brings back a user whose delete was refused, and drops a user that no longer exists. Replay
SHALL then continue with the next entry.

#### Scenario: Replayed create rejected
- **WHEN** a pending CREATE is replayed and the API responds 422 with a field error
- **THEN** the entry is removed, the sync result counts one rejected write, and the next refresh removes the user with
  the temporary id from the cache

#### Scenario: Replayed delete rejected
- **WHEN** a pending DELETE is replayed and the API responds 422
- **THEN** the entry is removed and the sync result counts one rejected write

#### Scenario: Replayed update for a user that no longer exists
- **WHEN** a pending UPDATE is replayed and the API responds 404
- **THEN** the entry is removed and the sync result counts one rejected write

#### Scenario: Replayed update rejected
- **WHEN** a pending UPDATE is replayed and the API responds 422
- **THEN** the entry is removed and the cached user keeps the locally edited data until the next refresh

#### Scenario: Replayed write with an unexpected status
- **WHEN** a pending CREATE is replayed and the API responds 400
- **THEN** the entry is removed and replay continues with the next entry

### Requirement: Replay processes the queue oldest first
Replaying the queue SHALL send each pending entry once, one at a time, in the order they were first queued. An entry
that fails with an error kept for a later sync SHALL stay queued, and replay SHALL continue with the next entry; the sync
then reports the first such error.

#### Scenario: Connectivity drops mid-replay
- **WHEN** three entries are pending, the first replays successfully, the second fails because there is no internet and
  the third succeeds
- **THEN** only the second entry remains in the queue and the sync reports no internet
