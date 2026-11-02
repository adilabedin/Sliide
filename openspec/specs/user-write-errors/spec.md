# user-write-errors Specification

## Purpose
Defines what a failed GoRest user write (create, update, delete) means, and decides from the operation and the error
alone what happens to the write: kept for a later sync, rejected and rolled back, or treated as already done.

## Requirements

### Requirement: Write failures are typed
A failed create, update or delete SHALL be reported as exactly one typed error: a validation failure (with the fields
GoRest named), not found, another remote failure (connectivity, timeout, rate limit, server error, authentication,
other HTTP status) or a local cache failure. Reporting SHALL NOT throw.

#### Scenario: Server error
- **WHEN** a create is sent and GoRest answers 503
- **THEN** the result is a remote server-error failure

#### Scenario: No connectivity
- **WHEN** a delete cannot reach GoRest because the device is offline
- **THEN** the result is a remote no-internet failure

#### Scenario: Unknown user
- **WHEN** an update for id 42 is sent and GoRest answers 404
- **THEN** the result is a not-found failure

### Requirement: Field errors keep what GoRest sent
A 422 on create or update SHALL be a validation failure carrying each field name and message from the body unchanged
and in order. The message text SHALL NOT be interpreted.

#### Scenario: Taken email
- **WHEN** a create is sent and GoRest answers 422 with `[{"field":"email","message":"has already been taken"}]`
- **THEN** the result is a validation failure naming `email` with the message "has already been taken"

#### Scenario: Blank name
- **WHEN** a create gets 422 with `[{"field":"name","message":"can't be blank"}]`
- **THEN** the result is a validation failure naming `name` with the message "can't be blank"

### Requirement: A 422 on delete is a refused delete
A validation failure on a delete SHALL be treated as the server refusing the delete, whatever its body contains.

#### Scenario: Seeded-record floor reached
- **WHEN** a finalised delete for Jane Doe is answered with 422 because fewer than 100 seeded records remain
- **THEN** the write is not kept for a later sync, Jane Doe is back in the cache, and the UI says the server refused the
  delete

### Requirement: Failed writes that may later succeed are kept
A write that fails with no connectivity, a timeout, HTTP 5xx, HTTP 429, HTTP 401 or HTTP 403 SHALL be kept for a later
sync, for every operation, whether it was a direct write or a replay.

#### Scenario: Unauthorised create
- **WHEN** a create fails with HTTP 401
- **THEN** the write is kept for a later sync

#### Scenario: Rate-limited delete
- **WHEN** a delete fails with HTTP 429
- **THEN** the write is kept for a later sync

### Requirement: Rejected writes are dropped
A write that fails as a validation failure, HTTP 400 or any other status not listed as kept SHALL be dropped from the
sync queue, and only its pending entry SHALL be removed: the next refresh replaces the cached list with the server's. A
rejected direct create SHALL also remove its new user from the cache at once.

#### Scenario: Rejected direct update
- **WHEN** a direct update fails with a validation failure
- **THEN** no pending entry remains, the cached user keeps the edited data, and the next refresh restores the server's
  data

#### Scenario: Rejected replayed update
- **WHEN** a replayed update fails with a field error for `email`
- **THEN** no pending entry remains and the cached user keeps the locally edited data until the next refresh

#### Scenario: Field error on a direct create
- **WHEN** a direct create fails with `[{"field":"email","message":"has already been taken"}]`
- **THEN** the write is not kept for a later sync and the new user is removed from the cache

### Requirement: Not found depends on the operation
A not-found failure SHALL complete a delete and reject a create or an update. In every case the write is not kept for a
later sync. A direct update that gets 404 SHALL also remove the user from the cache; a replayed one leaves it to the next
refresh.

#### Scenario: Delete of a user GoRest has already reset away
- **WHEN** a delete fails with HTTP 404
- **THEN** the delete counts as done and the user stays removed

#### Scenario: Update of a user GoRest has reset away
- **WHEN** a direct update fails with HTTP 404
- **THEN** the user is removed from the cache and no pending entry remains

### Requirement: Direct writes and replay decide the same way
For a given operation and error, a direct write and a replayed write SHALL reach the same decision: kept, rejected or
done.

#### Scenario: Server error in both paths
- **WHEN** a direct create fails with HTTP 500, and separately a replayed create fails with HTTP 500
- **THEN** both writes are kept for a later sync

#### Scenario: Field error in both paths
- **WHEN** a direct create fails with a field error, and separately a replayed create fails with a field error
- **THEN** in both cases the pending entry is removed and the write counts as rejected
