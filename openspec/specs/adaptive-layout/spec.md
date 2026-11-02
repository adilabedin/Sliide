# adaptive-layout Specification

## Purpose
How the users feed and profile change structure across window sizes: phones in portrait, phones in landscape and small
tablets, and large tablets. The same shared UI is used on Android and iOS.

## Requirements

### Requirement: Compact width shows one pane
When the window width is compact (under 600dp), the feed SHALL be a single-column list. Opening a profile SHALL replace
the feed with the full-screen profile, and going back SHALL return to the feed at the same scroll position.

#### Scenario: Phone portrait
- **WHEN** the app runs on a phone in portrait and the user taps a card
- **THEN** the profile fills the screen, and back returns to the feed at the same scroll position

### Requirement: Medium width shows a two-column grid
When the window width is medium (600dp to under 840dp), the feed SHALL lay out its cards in two columns. The shimmer
placeholders SHALL use two columns as well. Opening a profile SHALL still use the full screen.

#### Scenario: Phone landscape
- **WHEN** a phone is rotated to landscape with a width between 600dp and 840dp
- **THEN** the cards are shown in two columns

### Requirement: Expanded width shows master-detail
When the window width is expanded (840dp or more), the feed SHALL be shown as a list pane beside a detail pane. Tapping a
card SHALL show that user's profile in the detail pane while the list stays visible, and the selected card SHALL be
highlighted. When the user has not chosen a profile, the detail pane SHALL show the first user in the feed, and that
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

#### Scenario: Create on tablet
- **WHEN** the user presses the add-user button on a tablet
- **THEN** the Create-mode profile appears in the detail pane

### Requirement: Selection survives size changes
The open profile, and its mode, SHALL survive a change of window size class, such as a rotation, a fold or a split-screen
resize. Unsaved edit input SHALL survive it too. A profile shown only because it is the default first-user selection,
and still in View mode, SHALL NOT count as an open profile when the window shrinks.

#### Scenario: Tablet to phone width
- **WHEN** Jane Doe's profile is open in the detail pane in Edit mode and the window shrinks to compact
- **THEN** her profile is shown full screen, still in Edit mode with the unsaved input intact

#### Scenario: Phone to tablet width
- **WHEN** a profile is open full screen and the window grows to expanded
- **THEN** the feed and the profile are shown side by side

#### Scenario: Default selection on shrink
- **WHEN** the detail pane shows the default first user in View mode and the window shrinks to compact
- **THEN** the feed is shown as a single list and no profile opens full screen

#### Scenario: Editing the default selection on shrink
- **WHEN** the user switches the default first user's profile to Edit mode and then the window shrinks to compact
- **THEN** that profile is shown full screen, still in Edit mode with the unsaved input intact

### Requirement: Content width is comfortable on large screens
On any window, the profile form SHALL be constrained to a readable maximum width and centred within its pane. Text
fields SHALL NOT stretch across a wide tablet pane.

#### Scenario: Wide detail pane
- **WHEN** the profile is shown in a detail pane wider than 720dp
- **THEN** the form is limited to a readable width and centred
