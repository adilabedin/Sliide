# Spec Delta

## REMOVED Requirements

### Requirement: Users inside the Undo window are not replayed
**Reason**: The cache and the sync queue no longer know about the Undo window; a user being undone is still an ordinary
cached user until its delete runs.
**Migration**: None. Replay sends every entry; a delete issued for a temporary id after its CREATE synced is covered by
"A delete reaches a user re-keyed meanwhile".

## MODIFIED Requirements

### Requirement: Replay can target a single user
The system SHALL be able to replay one user's pending entry on its own. A single-user replay SHALL apply exactly the
same success and rejection rules as a full replay, and SHALL leave every other user's entry untouched.

#### Scenario: Single-user replay succeeds
- **GIVEN** users 41 and 42 each have a pending UPDATE
- **WHEN** user 42's entry is replayed on its own and the API responds 200
- **THEN** user 42's entry is removed and user 41's entry is still queued

#### Scenario: Single-user replay rejected
- **WHEN** a pending CREATE for temporary id -1 is replayed on its own and the API responds 422
- **THEN** the entry is removed, the sync result counts one rejected write, and the cache is left for the next refresh,
  as in a full replay

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

## ADDED Requirements

### Requirement: A delete reaches a user re-keyed meanwhile
A delete requested under a temporary id whose CREATE has already synced in the same process SHALL delete the user under
its server id, with the same local-first, queueing and rejection rules as any other delete.

#### Scenario: CREATE syncs during the Undo window
- **GIVEN** a user created offline under id -1 is hidden by a confirmed delete
- **WHEN** its CREATE syncs and the API returns id 7012, and then the delete for id -1 runs
- **THEN** the cache holds neither -1 nor 7012, and a DELETE request targets id 7012
