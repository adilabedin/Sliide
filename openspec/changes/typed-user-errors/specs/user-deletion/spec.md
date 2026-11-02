# Spec Delta

## MODIFIED Requirements

### Requirement: Delete is finalised when the window ends
When the 5-second window ends without Undo, the snackbar SHALL close and the delete SHALL be finalised through the
repository. A server-known user is then deleted on the server, or queued for later if the failure is one that is kept
for a later sync (including the server failing to authenticate the app).

#### Scenario: Window expires online
- **WHEN** 5 seconds pass after confirming without Undo
- **THEN** the snackbar closes and a delete request for Jane Doe is sent

#### Scenario: Window expires offline
- **WHEN** 5 seconds pass while offline
- **THEN** the snackbar closes and the delete is queued for later sync, with no error shown

#### Scenario: Window expires while unauthorised
- **WHEN** 5 seconds pass and the delete request is answered with 401
- **THEN** Jane Doe stays removed from the feed, the delete is queued for later sync, and a message says "Couldn't
  authenticate, will retry"

### Requirement: A failed finalise restores the user and says so
If finalising a delete fails permanently, a snackbar SHALL say the user could not be deleted, and the delete SHALL no
longer be queued; the user SHALL reappear in the feed with the next refresh. When the server refused the delete, the
snackbar SHALL say the server refused it.

#### Scenario: Server refuses
- **WHEN** the finalised delete is answered with 422
- **THEN** a snackbar reads "The server refused to delete Jane Doe" and Jane Doe's card reappears after the next refresh

#### Scenario: Unexpected failure
- **WHEN** the finalised delete is answered with 400
- **THEN** a snackbar reads "Couldn't delete Jane Doe" and Jane Doe's card reappears after the next refresh
