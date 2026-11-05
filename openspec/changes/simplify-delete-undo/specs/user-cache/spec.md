# Spec Delta

## REMOVED Requirements

### Requirement: Delete is local until finalised
**Reason**: The cache no longer has a separate local-remove and restore step. The Undo window is handled by the feed
without touching the cache, and the cache only sees the final delete.
**Migration**: Use the single delete operation ("Deletes apply locally first"). Undo before the delete simply never
calls it.

## ADDED Requirements

### Requirement: Deletes apply locally first
Deleting a user SHALL remove it from the cache before the network call completes. A user with a temporary id SHALL be
removed locally and its pending create cancelled, with no network call.

#### Scenario: Delete while offline
- **WHEN** user 42 is deleted and the API call fails because there is no internet
- **THEN** the feed no longer shows user 42 and the delete is queued

#### Scenario: Delete of a never-synced user
- **WHEN** a user with temporary id -1 and a pending CREATE is deleted
- **THEN** the user is removed from the cache, no pending entry remains, and no API request is made
