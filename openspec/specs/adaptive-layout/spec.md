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
highlighted. With nothing selected, the detail pane SHALL show a calm placeholder that prompts the user to pick a user.

#### Scenario: Tablet selection
- **WHEN** the user taps Jane Doe's card on a tablet in landscape
- **THEN** Jane Doe's profile appears in the detail pane, the list stays visible and her card is highlighted

#### Scenario: Nothing selected
- **WHEN** a tablet shows the feed with no user selected
- **THEN** the detail pane shows a placeholder prompting the user to select a user

#### Scenario: Create on tablet
- **WHEN** the user presses the add-user button on a tablet
- **THEN** the Create-mode profile appears in the detail pane

### Requirement: Selection survives size changes
The open profile, and its mode, SHALL survive a change of window size class, such as a rotation, a fold or a split-screen
resize. Unsaved edit input SHALL survive it too.

#### Scenario: Tablet to phone width
- **WHEN** Jane Doe's profile is open in the detail pane in Edit mode and the window shrinks to compact
- **THEN** her profile is shown full screen, still in Edit mode with the unsaved input intact

#### Scenario: Phone to tablet width
- **WHEN** a profile is open full screen and the window grows to expanded
- **THEN** the feed and the profile are shown side by side

### Requirement: Content width is comfortable on large screens
On any window, the profile form SHALL be constrained to a readable maximum width and centred within its pane. Text
fields SHALL NOT stretch across a wide tablet pane.

#### Scenario: Wide detail pane
- **WHEN** the profile is shown in a detail pane wider than 720dp
- **THEN** the form is limited to a readable width and centred
