# user-deletion Specification

## Purpose
Deleting a user from the feed or from the profile. A confirmation step comes first, then the user is removed locally
straight away, then there is a 5-second Undo window, and only after that is the delete finalised. Every delete ends in
exactly one outcome: undone or finalised.

## Requirements

### Requirement: Long-press asks for confirmation
Long-pressing a user card SHALL give haptic feedback and open a confirmation dialog. The dialog SHALL name the user and
explain that they will have 5 seconds to undo. It SHALL show a close icon button labelled "Cancel" at its top start
corner and a large bin icon above the title. It SHALL offer exactly one action button: a full-width destructive
"Delete" button that matches the profile page's Delete button. Closing the dialog SHALL change nothing, whether by
the close icon, back or tapping outside.

#### Scenario: Long-press
- **WHEN** the user long-presses Jane Doe's card
- **THEN** a dialog asks to delete Jane Doe and says they will have 5 seconds to undo

#### Scenario: Dialog layout
- **WHEN** the confirmation dialog is open
- **THEN** a close icon sits at its top start corner, a large bin icon sits above the title, and a single full-width red
  "Delete" button with a bin icon sits at the bottom, with no separate Cancel text button

#### Scenario: Cancel
- **WHEN** the user taps the close icon, presses back or taps outside the dialog
- **THEN** the dialog closes and Jane Doe's card is unchanged

#### Scenario: Confirm
- **WHEN** the user taps the full-width Delete button
- **THEN** the dialog closes and the delete is confirmed

### Requirement: Delete is reachable without long-press
Each user card SHALL expose a "Delete" custom accessibility action that opens the same confirmation dialog. Users of
screen readers or switch access can then delete without a long-press gesture.

#### Scenario: Screen reader
- **WHEN** a screen-reader user picks the "Delete" action on a card
- **THEN** the same confirmation dialog opens

### Requirement: Profile delete uses the same flow
The profile page's Delete button SHALL open the same confirmation dialog. Confirming SHALL close the profile and hand
the delete to the users screen, which SHALL hide the user and run the same Undo window as a delete from the feed.

#### Scenario: Delete from profile
- **WHEN** the user presses Delete on Jane Doe's profile and confirms
- **THEN** the profile closes, Jane Doe's card animates out of the feed, and the Undo snackbar appears

### Requirement: Undo snackbar with a 5-second countdown
After a confirmed delete, the users screen SHALL show a snackbar in its snackbar host. It SHALL name the deleted user,
offer an "Undo" action and show a visual countdown of the 5-second window. On a two-pane layout it SHALL appear over
the feed pane.

#### Scenario: Snackbar shown
- **WHEN** a delete of Jane Doe is confirmed
- **THEN** a snackbar reads "Jane Doe deleted" with an Undo action and a countdown that empties over 5 seconds

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

### Requirement: Swiping the snackbar away does not undo
Dismissing the snackbar early, by swipe, SHALL finalise the delete immediately rather than wait out the window. It SHALL
NOT undo.

#### Scenario: Swipe away
- **WHEN** the user swipes the snackbar away after 1 second
- **THEN** the delete is finalised right away

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

### Requirement: A second delete finalises the first
If another delete is confirmed while an Undo window is open, the first delete SHALL be finalised immediately. A new
5-second window and snackbar SHALL start for the second user.

#### Scenario: Two deletes in a row
- **WHEN** the user deletes Jane Doe and then deletes Rahul Sharma 2 seconds later
- **THEN** Jane Doe's delete is finalised at once, and the snackbar now shows "Rahul Sharma deleted" with a fresh 5-second countdown

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
