# adaptive-layout Specification

## Purpose
How the users feed and profile change structure across window sizes: a phone held upright shows one pane; a phone on
its side, tablets and desktop windows show the feed and the profile side by side. The window is classified by a shared
`DeviceConfiguration` (mobile portrait, mobile landscape, tablet portrait, tablet landscape, desktop). The feed and the
open profile are one navigation destination; adding a user is a separate, full-screen destination. The same shared UI
is used on Android and iOS.

## Requirements

### Requirement: A phone held upright shows one pane
When the window width is under 600dp (mobile portrait), the feed SHALL be a single-column list. Opening a profile SHALL
slide the full-screen profile over the feed, and going back SHALL return to the feed at the same scroll position.

#### Scenario: Phone portrait
- **WHEN** the app runs on a phone in portrait and the user taps a card
- **THEN** the profile fills the screen, and back returns to the feed at the same scroll position

### Requirement: Wider windows show master-detail
When the window width is 600dp or more (mobile landscape, tablet portrait, tablet landscape, desktop), the feed SHALL be
shown as a single-column list pane beside a detail pane, separated by a divider. The list pane SHALL take 40% of the
window width, kept between 280dp and 400dp. Tapping a card SHALL show that user's profile in the detail pane while the
list stays visible, and the selected card SHALL be highlighted.

#### Scenario: Phone landscape
- **WHEN** a phone is rotated to landscape with a width between 600dp and 840dp
- **THEN** the feed and the first user's profile are shown side by side, and the profile has no back arrow When the user has not chosen a profile, the detail pane SHALL show the first user in the feed, and that
card SHALL be highlighted. Only when the feed has no users SHALL the detail pane show a calm placeholder that prompts the
user to pick a user.

#### Scenario: Tablet selection
- **WHEN** the user taps Jane Doe's card on a tablet in landscape
- **THEN** Jane Doe's profile appears in the detail pane, the list stays visible and her card is highlighted

#### Scenario: First user selected by default
- **WHEN** a tablet shows the feed, the user has not chosen a profile, and Rahul Sharma is the first card
- **THEN** Rahul Sharma's profile is shown in the detail pane and his card is highlighted

#### Scenario: Default follows the first card
- **WHEN** the default-selected first user is deleted, or a new user is created at the top of the feed
- **THEN** the detail pane shows whichever user is now first, and that card is highlighted

#### Scenario: Nothing selected
- **WHEN** a tablet shows the feed while it is loading, empty or in a full-screen error
- **THEN** the detail pane shows a placeholder prompting the user to select a user

### Requirement: Adding a user is a full-screen destination
The add-user button SHALL open the Create-mode profile as its own navigation destination, filling the screen at every
window size. Leaving it SHALL return to the feed in the arrangement it had. A user it creates SHALL be at the top of the
feed, with the feed scrolled to the top, and any "saved offline" message SHALL be shown on the feed.

#### Scenario: Create on tablet
- **WHEN** the user presses the add-user button on a tablet
- **THEN** the "New user" form fills the screen, and back returns to the feed and profile side by side

#### Scenario: Created user shown
- **WHEN** the user saves a new user on the add-user screen
- **THEN** the feed returns scrolled to the top with the new user's card first, and on a two-pane layout its profile is
  shown in the detail pane by default

### Requirement: Selection survives size changes
The open profile, and its mode, SHALL survive a change of window size class, such as a rotation, a fold or a split-screen
resize, and process death. Which profile is open SHALL be held by the feed's ViewModel rather than by navigation.
Unsaved edit input SHALL survive it too. A profile shown only because it is the default first-user selection,
and still in View mode, SHALL NOT count as an open profile when the window shrinks.

#### Scenario: Tablet to phone width
- **WHEN** Jane Doe's profile is open in the detail pane in Edit mode and the window shrinks to under 600dp
- **THEN** her profile is shown full screen, still in Edit mode with the unsaved input intact

#### Scenario: Phone to tablet width
- **WHEN** a profile is open full screen and the window grows to 600dp or more
- **THEN** the feed and the profile are shown side by side

#### Scenario: Default selection on shrink
- **WHEN** the detail pane shows the default first user in View mode and the window shrinks to under 600dp
- **THEN** the feed is shown as a single list and no profile opens full screen

#### Scenario: Editing the default selection on shrink
- **WHEN** the user switches the default first user's profile to Edit mode and then the window shrinks to under 600dp
- **THEN** that profile is shown full screen, still in Edit mode with the unsaved input intact

### Requirement: Content width is comfortable on large screens
On any window, the profile form SHALL be constrained to a readable maximum width and centred within its pane. Text
fields SHALL NOT stretch across a wide tablet pane.

#### Scenario: Wide detail pane
- **WHEN** the profile is shown in a detail pane wider than 720dp
- **THEN** the form is limited to a readable width and centred
