# Proposal

## Why

A hands-on pass over the users UI turned up several rough edges:

- The feed's large app bar collapses and moves as the list scrolls.
- On a tablet the detail pane opens empty, even though there are users to show.
- The Create form has a close icon, but the pane slides in like a navigation step.
- The Save action is a small text button that is easy to miss.
- The delete dialog uses two small text buttons, where a single clear destructive action would be better.

The feature's composables are also spread across several packages (`users/components`, `delete`, `listdetail`, and
private helpers inside screen files). Reuse is hard to spot that way, and so is duplication.

## What Changes

- **Feed app bar is pinned.** The users screen uses a standard, fixed top app bar instead of the collapsing
  `LargeTopAppBar`. The bar never moves or resizes. Only the lazily composed list (or grid) beneath it scrolls.
- **First user is selected by default on two-pane layouts.** When the window is expanded and no profile has been
  chosen, the detail pane shows the first user in the feed, and that card is highlighted. The "select a user"
  placeholder appears only when there are no users to show.
- **Create mode uses a back arrow.** The Create profile shows a back arrow (not a close icon) in every layout, because
  the screen slides in as a navigation step.
- **Bigger Save button.** In Edit and Create modes, the app bar's Save action becomes a filled button with larger
  text. It is clearly bigger than an icon button. The existing Edit-icon ↔ Save animation stays.
- **Redesigned delete dialog.** The dialog has a close icon at the top start, a larger bin icon, the existing title and
  body, and one full-width destructive Delete button. That button is the same `DestructiveButton` the profile uses.
  The separate Cancel text button is removed. Closing (the icon, back, or tapping outside) still changes nothing.
- **Feature composables collected in one package.** Every non-Root, non-Screen composable of the users feature moves
  to `com.androidlearning.sliide.users.presentation.composables`. That covers the user card, the feed and placeholder
  lists, the delete dialog, the select-user placeholder, the profile app bar, the Save button, the header, the form, the
  text field, the labelled toggle and the snackbar host. Duplicated pieces become shared ones: one app bar wrapper for
  the users and profile screens, one navigation icon, and one lazy feed layout for both the cards and the shimmer
  placeholders. `:core:designsystem` keeps its theme and generic components, and the feature reuses them.

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `user-feed`: adds a requirement that the feed's app bar is pinned while the lazy list scrolls beneath it.
- `adaptive-layout`: on expanded widths the first user is selected by default. The placeholder is only for an empty
  feed.
- `user-profile`: Create mode shows a back arrow in every layout, and Save is a larger filled button.
- `user-deletion`: the confirmation dialog has a top-start close icon, a larger bin icon and a single full-width
  destructive Delete button, with no Cancel button.

## Impact

- **Code:** `:users:presentation` only.
  - `users/UsersScreen.kt`: the app bar and the extracted snackbar host.
  - `users/components/*`, `delete/DeleteUserDialog.kt` and `listdetail/UsersListDetailRoot.kt`: default selection, and
    `SelectUserPlaceholder` moves out.
  - `profile/ProfileScreen.kt`: its sub-composables move out.
  - A new `composables/` package.
- **Strings:** a content description for the dialog's close icon. The existing `delete_dialog_cancel` ("Cancel") is
  reused for it.
- **Tests:** `UsersScreenTest` and `ProfileScreenTest` (instrumented) are updated for the new dialog and app bar
  controls. New UI tests cover default selection, the pinned app bar and the dialog's close icon.
- **No changes** to the domain, data, database, the `:core:designsystem` public API, or dependencies.
