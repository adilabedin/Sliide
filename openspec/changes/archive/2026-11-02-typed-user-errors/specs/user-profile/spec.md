# Spec Delta

## MODIFIED Requirements

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
