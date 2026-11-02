# Spec Delta

## ADDED Requirements

### Requirement: Replay can target a single user
The system SHALL be able to replay one user's pending entry on its own. A single-user replay SHALL apply exactly the
same success, rejection and Undo-window rules as a full replay, and SHALL leave every other user's entry untouched.

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
- **GIVEN** user 42 has a pending entry and has been removed locally, and the delete is neither finalised nor undone
- **WHEN** user 42's entry is replayed on its own
- **THEN** no request is sent and the entry stays queued

#### Scenario: No pending entry
- **WHEN** a single-user replay runs for a user with no pending entry
- **THEN** no request is sent and nothing changes

## MODIFIED Requirements

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
