# user-deletion Specification

## Purpose
Deleting a user from the feed or from the profile. A confirmation step comes first, then the user is removed locally
straight away, then there is a 5-second Undo window, and only after that is the delete finalised. Every delete ends in
exactly one outcome: undone or finalised.

## Requirements

### Requirement: Long-press asks for confirmation
Long-pressing a user card SHALL give haptic feedback and open a confirmation dialog. The dialog SHALL name the user and
explain that they will have 5 seconds to undo. It SHALL offer a destructive "Delete" action and a "Cancel" action.
Cancelling or dismissing the dialog SHALL change nothing.

#### Scenario: Long-press
- **WHEN** the user long-presses Jane Doe's card
- **THEN** a dialog asks to delete Jane Doe and says they will have 5 seconds to undo

#### Scenario: Cancel
- **WHEN** the user taps Cancel or dismisses the dialog
- **THEN** the dialog closes and Jane Doe's card is unchanged

### Requirement: Delete is reachable without long-press
Each user card SHALL expose a "Delete" custom accessibility action that opens the same confirmation dialog. Users of
screen readers or switch access can then delete without a long-press gesture.

#### Scenario: Screen reader
- **WHEN** a screen-reader user picks the "Delete" action on a card
- **THEN** the same confirmation dialog opens

### Requirement: Profile delete uses the same flow
The profile page's Delete button SHALL open the same confirmation dialog. Confirming SHALL close the profile and
continue with the same local removal and Undo window on the users screen.

#### Scenario: Delete from profile
- **WHEN** the user presses Delete on Jane Doe's profile and confirms
- **THEN** the profile closes, Jane Doe's card animates out of the feed, and the Undo snackbar appears

### Requirement: Confirmed delete removes the user locally at once
Confirming a delete SHALL remove the user from the cache straight away, so the card animates out of the feed. No
network request SHALL be sent at this point.

#### Scenario: Confirm
- **WHEN** the user confirms deleting Jane Doe
- **THEN** her card animates out at once and no delete request has been sent yet

### Requirement: Undo snackbar with a 5-second countdown
After a confirmed delete, the users screen SHALL show a snackbar in its snackbar host. It SHALL name the deleted user,
offer an "Undo" action and show a visual countdown of the 5-second window. On a two-pane layout it SHALL appear over
the feed pane.

#### Scenario: Snackbar shown
- **WHEN** a delete of Jane Doe is confirmed
- **THEN** a snackbar reads "Jane Doe deleted" with an Undo action and a countdown that empties over 5 seconds

### Requirement: Undo restores the user exactly
Pressing Undo within the window SHALL put the user back in the cache exactly as it was before the delete, and the card
SHALL animate back into its original position. The snackbar SHALL then close. No network request SHALL be sent.

#### Scenario: Undo
- **WHEN** the user presses Undo 2 seconds after confirming
- **THEN** Jane Doe's card reappears in its previous position with unchanged details and no delete request is ever sent

### Requirement: Delete is finalised when the window ends
When the 5-second window ends without Undo, the snackbar SHALL close and the delete SHALL be finalised through the
repository. A server-known user is then deleted on the server, or queued for later if the failure is retryable.

#### Scenario: Window expires online
- **WHEN** 5 seconds pass after confirming without Undo
- **THEN** the snackbar closes and a delete request for Jane Doe is sent

#### Scenario: Window expires offline
- **WHEN** 5 seconds pass while offline
- **THEN** the snackbar closes and the delete is queued for later sync, with no error shown

### Requirement: Swiping the snackbar away does not undo
Dismissing the snackbar early, by swipe, SHALL finalise the delete immediately rather than wait out the window. It SHALL
NOT undo.

#### Scenario: Swipe away
- **WHEN** the user swipes the snackbar away after 1 second
- **THEN** the delete is finalised right away

### Requirement: Every delete ends in exactly one outcome
Each confirmed delete SHALL end in exactly one outcome: undone or finalised, never both and never neither. This SHALL
hold even if the users screen leaves the screen during the window, or the device is rotated. Only process death is
exempt, because it already resolves the delete safely.

#### Scenario: App backgrounded during the window
- **WHEN** the user confirms a delete and then leaves the app before 5 seconds pass
- **THEN** the delete is still finalised when the window ends

#### Scenario: Rotation during the window
- **WHEN** the device is rotated during the window
- **THEN** the snackbar is shown again with the remaining time, and Undo still works

### Requirement: A second delete finalises the first
If another delete is confirmed while an Undo window is open, the first delete SHALL be finalised immediately. A new
5-second window and snackbar SHALL start for the second user.

#### Scenario: Two deletes in a row
- **WHEN** the user deletes Jane Doe and then deletes Rahul Sharma 2 seconds later
- **THEN** Jane Doe's delete is finalised at once, and the snackbar now shows "Rahul Sharma deleted" with a fresh 5-second countdown

### Requirement: A failed finalise restores the user and says so
If finalising a delete fails permanently, the user SHALL reappear in the feed. A snackbar SHALL say the user could not
be deleted.

#### Scenario: Server refuses
- **WHEN** the finalised delete is rejected as unauthorised
- **THEN** Jane Doe's card reappears and a snackbar reads "Couldn't delete Jane Doe"
