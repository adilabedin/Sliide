# Spec Delta

## MODIFIED Requirements

### Requirement: Profile delete uses the same flow
The profile page's Delete button SHALL open the same confirmation dialog. Confirming SHALL close the profile and hand
the delete to the users screen, which SHALL hide the user and run the same Undo window as a delete from the feed.

#### Scenario: Delete from profile
- **WHEN** the user presses Delete on Jane Doe's profile and confirms
- **THEN** the profile closes, Jane Doe's card animates out of the feed, and the Undo snackbar appears

### Requirement: Undo restores the user exactly
Pressing Undo within the window SHALL show the user again exactly as it was before the delete, and the card SHALL
animate back into its original position. The snackbar SHALL then close. No network request SHALL be sent.

#### Scenario: Undo
- **WHEN** the user presses Undo 2 seconds after confirming
- **THEN** Jane Doe's card reappears in its previous position with unchanged details and no delete request is ever sent

### Requirement: Delete is finalised when the window ends
When the 5-second window ends without Undo, the snackbar SHALL close and the delete SHALL be finalised: the user is
removed from the cache, then deleted on the server, or queued for later if the failure is one that is kept for a later
sync (including the server failing to authenticate the app).

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

### Requirement: Every delete ends in exactly one outcome
Each confirmed delete SHALL end in exactly one outcome: undone or finalised, never both and never neither. It SHALL be
finalised even if the users screen is covered by another screen during the window, the app is backgrounded, or the
device is rotated. If the users screen is closed for good during the window, the delete SHALL be finalised at once. Only
process death is exempt: the cache was never changed, so the user simply stays.

#### Scenario: App backgrounded during the window
- **WHEN** the user confirms a delete and then leaves the app before 5 seconds pass
- **THEN** the delete is still finalised when the window ends

#### Scenario: Rotation during the window
- **WHEN** the device is rotated during the window
- **THEN** the snackbar is shown again with the remaining time, and Undo still works

#### Scenario: App closed during the window
- **WHEN** the user confirms deleting Jane Doe and closes the app 2 seconds later
- **THEN** the delete for Jane Doe is finalised at once rather than when the window would have ended

## REMOVED Requirements

### Requirement: Confirmed delete removes the user locally at once
**Reason**: The user is no longer removed from the cache when the delete is confirmed; it is hidden from the feed for
the Undo window and removed only when the delete is finalised.
**Migration**: Replaced by "Confirmed delete hides the user at once".

## ADDED Requirements

### Requirement: Confirmed delete hides the user at once
Confirming a delete SHALL hide the user from the feed straight away, so the card animates out. No network request SHALL
be sent and the cached user SHALL be left unchanged until the delete is finalised.

#### Scenario: Confirm
- **WHEN** the user confirms deleting Jane Doe
- **THEN** her card animates out at once and no delete request has been sent yet

#### Scenario: Deleting the user open in the detail pane
- **GIVEN** a two-pane layout with Jane Doe's profile chosen and shown in the detail pane
- **WHEN** the user deletes Jane Doe's card from the feed and confirms
- **THEN** the detail pane stops showing Jane Doe at once, without a "no longer available" message

#### Scenario: Refresh during the window
- **WHEN** the feed refreshes while Jane Doe's Undo window is open
- **THEN** Jane Doe's card stays hidden and Undo still brings it back

#### Scenario: Offline-created user synced during the window
- **GIVEN** a user created offline is hidden by a confirmed delete
- **WHEN** its create syncs during the window and the user receives a server id
- **THEN** its card may reappear under the server id until the window ends, and the delete still reaches that server id
