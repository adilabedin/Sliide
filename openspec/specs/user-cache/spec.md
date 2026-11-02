# user-cache Specification

## Purpose
The local user cache is the single source of truth for the user feed. Writes show up in it immediately, whether or not
the network is reachable. It is refreshed from the last page of the GoRest `/users` endpoint without losing any
pending local writes.

## Requirements

### Requirement: Cache is the single source of truth for the feed
The user feed SHALL read only from the local cache, ordered newest first. Every change to the cache SHALL be pushed to
observers without them having to ask again.

#### Scenario: Cache change is observed
- **WHEN** a user is added to, changed in or removed from the cache
- **THEN** observers of the user list receive the updated list, newest first

### Requirement: Created users appear immediately
A created user SHALL be written to the cache before the network call completes, so the user shows at the top of the
feed whether or not the call succeeds.

#### Scenario: Create while online
- **WHEN** a user is created and the API responds 201 with server id 7012
- **THEN** the cache holds that user under id 7012 at the top of the feed

#### Scenario: Create while offline
- **WHEN** a user is created and the API call fails because there is no internet
- **THEN** the user appears at the top of the feed under a temporary local id

### Requirement: Temporary ids are unique and never collide with server ids
A user created locally that has no server id yet SHALL get a negative temporary id that no other cached user is
using. GoRest ids are positive, so a temporary id can never clash with a server id.

#### Scenario: Two offline creates
- **WHEN** two users are created while offline
- **THEN** each one gets a distinct negative id

### Requirement: Updates apply locally first
An updated user SHALL be written to the cache before the network call completes.

#### Scenario: Update while offline
- **WHEN** a user's name is changed and the API call fails because there is no internet
- **THEN** the feed shows the new name

### Requirement: Delete is local until finalised
Removing a user SHALL take it out of the cache with no network call. Restoring that user before the delete is finalised
SHALL put back the exact previous data, also with no network call.

#### Scenario: Undo a delete
- **WHEN** a user is removed and then restored before the delete is finalised
- **THEN** the user is back in the feed with identical data, and no API request was made

#### Scenario: Delete never finalised
- **WHEN** a user is removed and the app terminates before the delete is finalised
- **THEN** the user is not deleted on the server, and the next successful refresh may show it again

### Requirement: Permanent failures roll back the local write
If the API rejects a write with a non-retryable error, the system SHALL undo that write in the cache and return the
error to the caller.

#### Scenario: Create rejected because the email is taken
- **WHEN** a user is created and the API responds 422
- **THEN** the user is removed from the cache and the caller receives a validation error

#### Scenario: Update for a user that no longer exists
- **WHEN** a user is updated and the API responds 404
- **THEN** the user is removed from the cache, and the caller receives a not-found error

#### Scenario: Update rejected
- **WHEN** a user is updated and the API responds 422
- **THEN** the cached user goes back to its data from before the update, and the caller receives the error

### Requirement: Refresh replaces the cache only when nothing is pending
A refresh SHALL first replay every pending write. Only if no pending writes remain SHALL it fetch the last page of
`/users`, and then it SHALL replace the whole cache with that page if the page differs from the cache.

#### Scenario: Refresh while online with no pending writes
- **WHEN** a refresh runs, the queue is empty, and the last page differs from the cache
- **THEN** the cache contains exactly the users on the last page

#### Scenario: Refresh while offline
- **WHEN** a refresh runs, pending writes exist, and replaying them fails because there is no internet
- **THEN** the last page is not fetched, the cache is unchanged, and the caller receives a no-internet error

#### Scenario: Last page unchanged
- **WHEN** a refresh fetches a last page identical to the cache
- **THEN** the cache is not rewritten and observers receive no new emission

#### Scenario: Locally created users are dropped by the refresh
- **WHEN** a user created in the app has synced, and a later refresh fetches a last page that does not contain it
- **THEN** that user is no longer in the cache
