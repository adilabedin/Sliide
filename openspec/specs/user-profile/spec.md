# user-profile Specification

## Purpose
A single reusable profile page that shows a user (View), edits that user (Edit) and creates a new user (Create).
It has one app bar, one validated form and one set of save rules shared by all three modes.

## Requirements

### Requirement: Tapping a card opens that user's profile
Tapping a user card SHALL open the profile page in View mode for that user. The page SHALL show the user's details read
from the cache.

#### Scenario: Open profile
- **WHEN** the user taps Jane Doe's card
- **THEN** the profile page opens in View mode showing Jane Doe's name, email, gender and status

### Requirement: Profile app bar layout
The profile app bar SHALL show the title "Profile" at the start (top left in left-to-right layouts). In Create mode the
title SHALL read "New user". When the profile occupies the whole screen, a back arrow SHALL precede the title. On a
two-pane layout the back arrow SHALL be omitted in View mode. In Create mode a back arrow, never a close icon, SHALL
precede the title in every layout. Pressing it SHALL leave Create mode without creating a user.

#### Scenario: View mode title
- **WHEN** a profile is open in View or Edit mode
- **THEN** the app bar title reads "Profile"

#### Scenario: Full-screen back
- **WHEN** the profile fills the screen on a phone
- **THEN** a back arrow appears before the title, and tapping it returns to the feed

#### Scenario: Create mode shows a back arrow
- **WHEN** the user opens the Create-mode profile on a phone or on a tablet
- **THEN** a back arrow labelled "Back" precedes the "New user" title, and no close icon is shown

#### Scenario: Back out of create
- **WHEN** the user has typed a name in Create mode and presses the back arrow
- **THEN** the profile closes, no user is created, and the feed is unchanged

### Requirement: Edit icon turns into Save
In View mode the end of the app bar SHALL show an Edit icon button with an accessible label. Pressing it SHALL switch
the page to Edit mode in place. In Edit and Create modes, that slot SHALL show a filled "Save" button instead. The button
SHALL be at least 48dp tall, wider than an icon button, and use a larger label than the app bar's other actions. The
change between icon and button SHALL be animated. A disabled Save SHALL keep its size and look visibly disabled.

#### Scenario: Enter edit mode
- **WHEN** the user presses the Edit icon
- **THEN** the fields become editable and the Edit icon animates into a filled "Save" button

#### Scenario: Save is prominent
- **WHEN** the profile is in Create mode
- **THEN** the app bar shows a filled "Save" button at least 48dp tall, and clearly larger than the back arrow

#### Scenario: Saving in progress
- **WHEN** a save is running
- **THEN** the Save button keeps its size and shows a progress indicator in place of its label

### Requirement: Same form in every mode
The profile SHALL use one form layout in all modes: name field, email field, a gender toggle and a status toggle. In
View mode the controls SHALL be read-only, with no text cursor and no toggle interaction. In Edit and Create modes all
of them SHALL be editable.

#### Scenario: Read-only in View mode
- **WHEN** the profile is in View mode and the user taps the name field
- **THEN** no keyboard opens and the value cannot change

#### Scenario: Editable in Edit mode
- **WHEN** the profile is in Edit mode
- **THEN** name and email accept typing, and both toggles accept taps

### Requirement: Two-option toggles are mutually exclusive
Gender SHALL be chosen with a two-option toggle, Male or Female, and status with a two-option toggle, Active or
Inactive. Exactly one option of each toggle SHALL be selected at any time. Selecting one SHALL deselect the other with an
animated transition.

#### Scenario: Switch gender
- **WHEN** Male is selected and the user taps Female in Edit mode
- **THEN** Female becomes selected and Male becomes unselected

#### Scenario: Tap the selected option
- **WHEN** the user taps the option that is already selected
- **THEN** the selection does not change

### Requirement: Real-time name validation
The name SHALL be validated as the user types. A name that is blank after trimming SHALL show "Name is required". A name
shorter than 2 characters after trimming SHALL show "Name is too short". An error SHALL NOT be shown for a field the
user has not edited yet in Create mode.

#### Scenario: Cleared name
- **WHEN** the user deletes every character of the name in Edit mode
- **THEN** "Name is required" appears under the field immediately

#### Scenario: Pristine create form
- **WHEN** the profile opens in Create mode
- **THEN** no validation errors are shown, and Save is disabled

### Requirement: Real-time email validation
The email SHALL be validated as the user types. It SHALL be accepted only if, after trimming, it has a non-empty local
part, exactly one "@", and a domain containing a dot with no empty labels. A blank email SHALL show "Email is required".
Any other invalid value SHALL show "Enter a valid email address".

#### Scenario: Invalid email
- **WHEN** the user types "jane@example"
- **THEN** "Enter a valid email address" appears under the email field

#### Scenario: Valid email
- **WHEN** the user corrects it to "jane@example.com"
- **THEN** the error disappears

### Requirement: Save is enabled only for a valid, changed form
Save SHALL be enabled only when every field is valid and no save is in progress. In Edit mode, the form must also differ
from the stored user. While a save runs, Save SHALL show progress, and the form SHALL be read-only.

#### Scenario: No changes
- **WHEN** the user enters Edit mode and changes nothing
- **THEN** Save is disabled

#### Scenario: Valid change
- **WHEN** the user changes the email to another valid address
- **THEN** Save is enabled

### Requirement: Saving an edit
Saving in Edit mode SHALL write the trimmed values to the cache-first repository. If the write succeeds or is queued for
later sync, the page SHALL return to View mode showing the saved values. A write queued because the device could not
reach the server SHALL show a brief "Saved offline, will sync when online" message. A write queued because the server
could not authenticate the app SHALL show "Couldn't authenticate, will retry" instead.

#### Scenario: Online save
- **WHEN** the user saves a valid edit and the server accepts it
- **THEN** the page returns to View mode with the new values, and the feed card shows them too

#### Scenario: Offline save
- **WHEN** the user saves a valid edit while offline
- **THEN** the page returns to View mode with the new values and shows "Saved offline, will sync when online"

#### Scenario: Unauthorised save
- **WHEN** the user saves a valid edit and the server answers 401
- **THEN** the page returns to View mode with the new values and shows "Couldn't authenticate, will retry"

### Requirement: Rejected saves keep the user's input
If the server rejects a save permanently, the page SHALL stay in Edit or Create mode with the user's input kept and
show the error in a snackbar. A server field error SHALL be shown as the capitalised field name followed by the
server's message; several field errors SHALL be shown together in one snackbar. A rejection naming no field SHALL show a
general "rejected details" message.

#### Scenario: Email taken
- **WHEN** the server rejects a save with `[{"field":"email","message":"has already been taken"}]`
- **THEN** the form stays editable, the input is unchanged, and a snackbar reads "Email has already been taken"

#### Scenario: Several field errors
- **WHEN** the server rejects a save with an `email` error "has already been taken" and a `name` error "can't be blank"
- **THEN** the form stays editable, the input is unchanged, and one snackbar shows "Email has already been taken" and
  "Name can't be blank"

#### Scenario: Rejection without field errors
- **WHEN** the server rejects a save with 422 and a body that names no field
- **THEN** the form stays editable, the input is unchanged, and a snackbar says the server rejected the details

#### Scenario: Unauthorised
- **WHEN** the server answers a save with 401
- **THEN** the save is not treated as rejected: the write is queued, and the page behaves as described in "Saving an
  edit" and "Saving a new user", showing "Couldn't authenticate, will retry"

### Requirement: Leaving Edit mode without saving discards changes
Going back while in Edit mode SHALL return to View mode with the stored values and discard unsaved input. It SHALL NOT
leave the profile. The app bar SHALL show a close (cancel) control in Edit mode for the same purpose.

#### Scenario: Cancel edit
- **WHEN** the user changes the name and then presses back in Edit mode
- **THEN** the page is in View mode showing the original name

### Requirement: Add-user button opens Create mode
The users screen SHALL show a floating add-user button anchored to the bottom end corner. Pressing it SHALL open the
profile page in Create mode with empty name and email, Male selected and Active selected.

#### Scenario: Open create
- **WHEN** the user presses the add-user button
- **THEN** the profile opens titled "New user" with a blank form, a "Save" button and no Delete button

### Requirement: Saving a new user
Saving in Create mode SHALL create the user through the cache-first repository. If the write succeeds or is queued, the
profile SHALL close, and the feed SHALL scroll to the top, where the new user is the first card. A create queued because
the server could not authenticate the app SHALL show "Couldn't authenticate, will retry".

#### Scenario: Created online
- **WHEN** the user saves a valid new user and the server returns 201
- **THEN** the profile closes and the new user is the first card in the feed

#### Scenario: Created offline
- **WHEN** the user saves a valid new user while offline
- **THEN** the profile closes and the new user is the first card, marked "Pending sync"

#### Scenario: Created while unauthorised
- **WHEN** the user saves a valid new user and the server answers 401
- **THEN** the profile closes, the new user is the first card marked "Pending sync", and "Couldn't authenticate, will
  retry" is shown

### Requirement: Delete button pinned to the bottom
In View and Edit modes the profile SHALL show a full-width red Delete button pinned to the bottom of the page. It SHALL
stay visible while the form scrolls and SHALL sit above the on-screen keyboard and system bars. It SHALL NOT be shown
in Create mode.

#### Scenario: Long form scrolls
- **WHEN** the form is taller than the screen and the user scrolls
- **THEN** the Delete button stays fixed at the bottom

### Requirement: Profile closes when its user disappears
If the user shown on the profile is removed from the cache by anything other than this page's own delete, the profile
SHALL close and return to the feed. This covers a refresh that replaces the cache, or a rejected offline create. Unsaved
input is discarded. When the profile is shown only as the default first-user selection on a two-pane layout, it SHALL
instead switch silently to the user who is now first, with no message.

#### Scenario: Removed by refresh
- **WHEN** a profile is open and a refresh removes that user from the cache
- **THEN** the profile closes and a message says the user is no longer available

#### Scenario: Pending user gets its server id
- **WHEN** a profile is open for a "Pending sync" user and its queued create succeeds, so it reappears under a new id
- **THEN** the profile closes without an error message, and the user's card in the feed no longer shows "Pending sync"

#### Scenario: Default selection disappears
- **WHEN** a tablet's detail pane shows the default first user, and that user is deleted from the feed or removed by a
  refresh
- **THEN** the detail pane shows the user who is now first, and no "no longer available" message appears
