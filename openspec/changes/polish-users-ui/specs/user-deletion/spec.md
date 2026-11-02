# Spec Delta

## MODIFIED Requirements

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
