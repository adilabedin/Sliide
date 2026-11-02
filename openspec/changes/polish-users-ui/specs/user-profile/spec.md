# Spec Delta

## MODIFIED Requirements

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
