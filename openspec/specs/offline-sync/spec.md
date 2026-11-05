# offline-sync Specification

## Purpose
The offline sync queue keeps user writes (create, update, delete) that could not reach the GoRest API for a transient
reason. It replays them later so that no local change is lost and none is sent twice.

## Requirements

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

### Requirement: Pending entries store a snapshot and an operation
Each pending entry SHALL hold the user's data as it was at queue time and an operation that is exactly one of
CREATE, UPDATE or DELETE. Entries SHALL survive the app restarting.

#### Scenario: Queue survives restart
- **WHEN** a write is queued and the app is killed and relaunched
- **THEN** the pending entry is still present with the same snapshot and operation

### Requirement: At most one pending entry per user
The queue SHALL hold at most one entry per user. A new write for a user that already has an entry SHALL be merged
into it according to the merge rules.

#### Scenario: Create then update
- **WHEN** a pending CREATE exists and the same user is updated while offline
- **THEN** one pending CREATE remains, holding the updated data

#### Scenario: Create then delete
- **WHEN** a pending CREATE exists and that user's delete is finalised
- **THEN** the pending entry is removed and no API request is ever made for that user

#### Scenario: Update then update
- **WHEN** a pending UPDATE exists and the same user is updated again while offline
- **THEN** one pending UPDATE remains, holding the latest data

#### Scenario: Update then delete
- **WHEN** a pending UPDATE exists and that user's delete is finalised while offline
- **THEN** one pending DELETE remains

### Requirement: Writes to a never-synced user make no API call
An update or finalised delete for a user that exists only locally, because it has a temporary id, SHALL NOT call the
API. Instead, it SHALL be merged into that user's pending CREATE.

#### Scenario: Edit an offline-created user while online
- **WHEN** a user with a temporary id is updated while the device is online
- **THEN** no PUT request is sent, and the pending CREATE now holds the updated data

#### Scenario: Delete an offline-created user
- **WHEN** the delete of a user with a temporary id is finalised
- **THEN** no DELETE request is sent and no pending DELETE is stored

### Requirement: Replay processes the queue oldest first
Replaying the queue SHALL send each pending entry once, one at a time, in the order they were first queued. An entry
that fails with an error kept for a later sync SHALL stay queued, and replay SHALL continue with the next entry; the sync
then reports the first such error.

#### Scenario: Connectivity drops mid-replay
- **WHEN** three entries are pending, the first replays successfully, the second fails because there is no internet and
  the third succeeds
- **THEN** only the second entry remains in the queue and the sync reports no internet

### Requirement: Successful replay clears the entry
When a replayed entry succeeds, the system SHALL remove it from the queue. A replayed DELETE that gets HTTP 404 SHALL
count as a success.

#### Scenario: Replayed delete for an already-deleted user
- **WHEN** a pending DELETE is replayed and the API responds 404
- **THEN** the entry is removed from the queue

### Requirement: A replayed create swaps in the server id
When a pending CREATE replays successfully, the system SHALL, in one atomic step, replace the cached user under the
temporary id with the server's user under the server id, and remove the pending entry.

#### Scenario: Offline create syncs, then is deleted
- **WHEN** a user created offline under id -1 syncs and the API returns id 7012, and the user's delete is later finalised
- **THEN** the cache holds no user with id -1, and the DELETE request targets id 7012

### Requirement: Permanently rejected replays are dropped
If a replayed entry fails with an error that is not kept for a later sync, the system SHALL delete the entry without
recording it anywhere; it does not fail the sync. Sync SHALL NOT change the cached users for it: the next refresh
replaces the cached list with the server's, which removes a temporary user whose create was rejected, brings back a user
whose delete was refused, and drops a user that no longer exists. Replay SHALL then continue with the next entry.

#### Scenario: Replayed create rejected
- **WHEN** a pending CREATE is replayed and the API responds 422 with a field error
- **THEN** the entry is removed, the sync succeeds, and the next refresh removes the user with the temporary id from
  the cache

#### Scenario: Replayed delete rejected
- **WHEN** a pending DELETE is replayed and the API responds 422
- **THEN** the entry is removed and the sync succeeds

#### Scenario: Replayed update for a user that no longer exists
- **WHEN** a pending UPDATE is replayed and the API responds 404
- **THEN** the entry is removed and the sync succeeds

#### Scenario: Replayed update rejected
- **WHEN** a pending UPDATE is replayed and the API responds 422
- **THEN** the entry is removed and the cached user keeps the locally edited data until the next refresh

#### Scenario: Replayed write with an unexpected status
- **WHEN** a pending CREATE is replayed and the API responds 400
- **THEN** the entry is removed and replay continues with the next entry

### Requirement: A successful online write supersedes the pending entry
When a direct API write for a server-known user succeeds, the system SHALL discard any pending entry for that user, so
older queued data is never replayed over it.

#### Scenario: Online edit after an offline edit
- **WHEN** a pending UPDATE exists for user 42, and user 42 is then updated online and the API responds 200
- **THEN** no pending entry remains for user 42, and a later replay sends no request for it

#### Scenario: Online delete after an offline edit
- **WHEN** a pending UPDATE exists for user 42, and user 42's delete is finalised online and the API responds 204
- **THEN** no pending entry remains for user 42

### Requirement: Only one replay runs at a time
Concurrent requests for a full replay SHALL share a single replay pass. A single-user replay MAY run alongside a full
replay, but no pending entry SHALL be sent more than once, whichever replay reaches it first.

#### Scenario: Background sync and pull-to-refresh overlap
- **WHEN** a replay is running and a second replay is requested
- **THEN** the second request receives the same result as the first, and each pending entry is sent at most once

#### Scenario: Single-user replay during a full replay
- **GIVEN** a full replay is running and has not yet reached user 42's entry
- **WHEN** a single-user replay for user 42 runs and sends the entry successfully
- **THEN** the full replay sends no request for user 42 when it reaches that position

### Requirement: User writes are not blocked for a whole replay
While a replay is running, a user write SHALL wait at most for the replay request currently in flight, not for the rest
of the queue.

#### Scenario: Create during a long replay
- **WHEN** ten entries are being replayed and the user creates a user after the first request has started
- **THEN** the create is processed after that first request completes, before the remaining entries are replayed

### Requirement: Interrupted replay resumes without resending
A replay that is interrupted, for example because the OS ends a background task, SHALL leave every completed entry
removed and every unprocessed entry queued.

#### Scenario: Background task killed mid-replay
- **WHEN** two of five entries have replayed successfully and the process is terminated
- **THEN** the next replay starts with the third entry and never resends the first two

### Requirement: Replay can target a single user
The system SHALL be able to replay one user's pending entry on its own. A single-user replay SHALL apply exactly the
same success and rejection rules as a full replay, and SHALL leave every other user's entry untouched.

#### Scenario: Single-user replay succeeds
- **GIVEN** users 41 and 42 each have a pending UPDATE
- **WHEN** user 42's entry is replayed on its own and the API responds 200
- **THEN** user 42's entry is removed and user 41's entry is still queued

#### Scenario: Single-user replay rejected
- **WHEN** a pending CREATE for temporary id -1 is replayed on its own and the API responds 422
- **THEN** the entry is removed, the sync succeeds, and the cache is left for the next refresh, as in a full replay

#### Scenario: Single-user replay keeps a retryable failure
- **WHEN** a pending UPDATE for user 42 is replayed on its own and the API responds 503
- **THEN** the entry stays queued with its snapshot and operation unchanged

#### Scenario: Single-user replay inside the Undo window
- **GIVEN** user 42 has a pending UPDATE and is hidden by a confirmed delete whose Undo window is still open
- **WHEN** user 42's entry is replayed on its own and the API responds 200
- **THEN** the UPDATE is sent and its entry removed, as for any other user

#### Scenario: No pending entry
- **WHEN** a single-user replay runs for a user with no pending entry
- **THEN** no request is sent and nothing changes

### Requirement: A delete reaches a user re-keyed meanwhile
A delete requested under a temporary id whose CREATE has already synced in the same process SHALL delete the user under
its server id, with the same local-first, queueing and rejection rules as any other delete.

#### Scenario: CREATE syncs during the Undo window
- **GIVEN** a user created offline under id -1 is hidden by a confirmed delete
- **WHEN** its CREATE syncs and the API returns id 7012, and then the delete for id -1 runs
- **THEN** the cache holds neither -1 nor 7012, and a DELETE request targets id 7012
