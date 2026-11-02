# Design

## Context

All work is in `:users:presentation`. See proposal.md (Why) for motivation and the deltas under `specs/` for the
required behaviour. This is the current state the design builds on:

- **Feed app bar.** `UsersScreen` uses a `LargeTopAppBar` with `exitUntilCollapsedScrollBehavior`, wired through
  `Modifier.nestedScroll`, so the bar collapses and moves while the feed scrolls. The feed is already lazy:
  `UsersFeed` and `UsersFeedPlaceholders` use `LazyColumn` or `LazyVerticalGrid`. Nothing in `composeApp` wraps
  `App()` or `UsersListDetailRoot` in a scrolling container. The perceived "not fixed, not properly scrollable"
  behaviour therefore comes from the collapsing bar alone.
- **Selection.** `UsersListDetailRoot` owns a `ListDetailPaneScaffoldNavigator<Long>`. Its content key is a user id or
  `ProfileKey.CREATE`. On every change of `selectedKey`, a `LaunchedEffect(selectedKey)` sends
  `UsersAction.OnSelectionChange` (with `CREATE` mapped to `null`). It also sends `ProfileAction.OnDismiss` when the key
  is `null`. When the key is `null`, the detail pane renders `SelectUserPlaceholder`.
- **Profile closing.** `ProfileViewModel.onStoredUser(null)` emits `ProfileEvent.Close("no longer available")` for any
  disappearance it did not cause itself.
- **Profile app bar.** `ProfileTopBar` shows `Icons.Filled.Close` in Create mode, and a `TextButton` Save with
  `labelLarge` text.
- **Delete dialog.** `DeleteUserDialog` is an M3 `AlertDialog`, with a centred icon slot and end-aligned text buttons.
- **Where composables live:**
  - `users/components/UserCard.kt`
  - `users/components/UsersFeed.kt`, which also holds `UsersFeedPlaceholders`
  - `delete/DeleteUserDialog.kt`
  - `listdetail/UsersListDetailRoot.kt`, which also holds `SelectUserPlaceholder`
  - private helpers inside `ProfileScreen.kt` (top bar, Save button, header, form, text field, labelled toggle) and
    inside `UsersScreen.kt` (snackbar host)

## Goals / Non-Goals

**Goals:**
- Compute the default first-user selection in shared, unit-tested ViewModel code, not in a composable.
- Keep the default selection "virtual", so that it never alters back-stack or resize behaviour unless the user edits.
- Put every feature sub-composable in one `composables` package. Remove duplication where two screens draw the same
  thing.

**Non-Goals:**
- Moving `:core:designsystem` components into the feature. The user chose "feature only". The design-system spec is
  unchanged.
- Moving `UsersRoot`/`UsersScreen`, `ProfileRoot`/`ProfileScreen` or `UsersListDetailRoot`. Root and Screen composables
  stay next to their ViewModel, State and Action types, as the MVI layout expects. "Every composable" is read as every
  composable *below* the screen level. This assumption is recorded so the user can override it before apply.
- Default selection on compact or medium widths. Auto-opening a full-screen profile would hide the feed.

## Decisions

### D1. Pinned, not collapsing, feed app bar
Replace `LargeTopAppBar` + `exitUntilCollapsedScrollBehavior` with a `TopAppBar` + `TopAppBarDefaults.pinnedScrollBehavior()`.
Keep the `nestedScroll` wiring: the pinned behaviour never moves or resizes the bar, but it still switches the bar to
`scrolledContainerColor` once content scrolls under it. That keeps the tonal elevation cue.
- *Alternative:* no scroll behaviour at all. This was rejected because the bar would lose the "content is under me"
  colour change.
- *Alternative:* `enterAlways`. This was rejected because the user asked for a bar that does not move.

### D2. Shared app bar and navigation icon
Add `composables/UsersTopAppBar.kt` with a feature-level `UsersTopAppBar(title, navigationIcon, actions, scrollBehavior)`.
It wraps M3 `TopAppBar` with the background and `surfaceContainer` colours that both screens currently duplicate.
Add `NavigationIconButton(kind: Back | Close, contentDescription, onClick)` for the auto-mirrored back arrow and the
close icon. The profile (back or cancel-edit), Create (back) and the delete dialog (close) all use it.

### D3. Default selection lives in `UsersViewModel`
- **Actions.** `UsersAction.OnSelectionChange(key: Long?)` now carries the raw navigator key, `ProfileKey.CREATE`
  included, instead of a pre-mapped id. A new `UsersAction.OnPaneLayoutChange(isTwoPane: Boolean)` reports whether two
  panes are showing.
- **Stored state.** `LocalState` stores `chosenKey` and `isTwoPane`.
- **`buildState` derives two values:**
  - `defaultSelectedUserId = cards.firstOrNull()?.id`, set only when all of these hold: `isTwoPane`,
    `chosenKey == null`, and `content == FeedContent.Users`.
  - `selectedUserId` (the highlight) is `chosenKey` when it is a real user id, `null` when it is `CREATE`, and
    otherwise `defaultSelectedUserId`.
- **Why here.** It is pure state reduction over the cache flow, so it is covered by `commonTest`. Because it is
  derived from the users list, it follows the first card automatically after a delete, an undo, a create or a refresh.
- *Alternative:* `navigator.navigateTo(Detail, firstId)` from the Root. This was rejected for two reasons. The
  navigator destination would survive a shrink to compact and open a full-screen profile, which breaks "Default
  selection on shrink". It would also make back pop to an empty pane, only for the default to be re-pushed.

### D4. Root renders the default without navigating
In `UsersListDetailRoot`:
- `detailKey = selectedKey ?: usersState.defaultSelectedUserId`, where `usersState` is collected from
  `usersViewModel.state`.
- The detail pane renders `ProfileRoot(profileKey = detailKey)` when `detailKey != null`, and `SelectUserPlaceholder`
  otherwise.
- `LaunchedEffect(selectedKey)` sends `OnSelectionChange(selectedKey)` only.
- `LaunchedEffect(detailKey)` sends `ProfileAction.OnDismiss` only when `detailKey == null`. The default profile is
  therefore not dismissed when nothing is chosen.
- `LaunchedEffect(isTwoPane)` sends `OnPaneLayoutChange`.
- **Silent re-target.** `onClose` for a profile shown as the default (`selectedKey == null`) drops the message and does
  not navigate. When the default user vanishes, `ProfileViewModel`'s own observer may emit
  `Close("no longer available")` before the Root re-keys `ProfileRoot`. Swallowing that close in the Root is
  deterministic, whichever collector runs first (spec: "Default selection disappears").
- **Promotion on edit.** While `selectedKey == null && detailKey != null && isTwoPane`, a `LaunchedEffect` watches the
  profile mode together with the user the profile is showing. On `ProfileMode.EDIT`, and only when that user is
  `detailKey`, it calls `navigator.navigateTo(Detail, detailKey)`. Matching the shown user stops a stale Edit mode from
  another profile triggering a promotion while the navigator is between `navigateBack` and `navigateTo`. The key is unchanged, so
  `ProfileRoot`'s `OnOpen` is a no-op (`key == openKey`) and the draft is kept. After that the edit survives a shrink
  like any chosen profile.
- `showBackButton` stays `!isTwoPane`, so a default profile in two-pane has no back arrow.

### D5. Create mode always uses a back arrow
In `ProfileTopBar`, the navigation icon depends on the mode:
- `EDIT`: Close (cancel edit), unchanged.
- `CREATE`: Back, in every layout.
- `VIEW`: Back when `showBackButton` is true.

The Create back arrow uses the existing `profile_back` string and the existing `onBackClick` path, which closes without
saving.

### D6. Bigger Save
`SaveButton` becomes an M3 `Button` (filled):
- Primary container colours.
- `heightIn(min = 48.dp)` and horizontal content padding of `SliideSpacing.l`.
- `titleSmall` label, up from `labelLarge`.
- A trailing end margin inside the app bar.
- While saving, an 18dp `CircularProgressIndicator` replaces the label inside a `Box` that keeps the label's measured
  width, so the button does not shrink. The progress indicator keeps the `profile_saving` content description.
- The disabled state keeps M3's disabled colours.
- The `AnimatedContent` icon↔button transition is unchanged.

### D7. Delete dialog on `BasicAlertDialog`
M3 `AlertDialog` has no top-start slot, and its buttons are end-aligned. The dialog becomes `BasicAlertDialog` hosting a
`Surface(shape = SliideShapes.extraLarge, color = surfaceContainerLow)`. It contains a `Column` with:
1. A `Row` with `NavigationIconButton(Close, contentDescription = delete_dialog_cancel)` at the start. The label stays
   "Cancel", so screen readers announce the same action.
2. A large bin: `Icons.Filled.Delete` at 40dp, inside a 72dp `errorContainer` circle, centred.
3. The title and body, using the existing strings, centred.
4. `DestructiveButton(text = delete_dialog_confirm)`, full width. It is reused from `:core:designsystem`, the same one
   the profile uses.

`onDismissRequest` (back or outside tap) and the close icon both call `onDismiss`. The width is capped at 400dp so it
reads well on tablets.

### D8. One `composables` package
- **Package:** `com.androidlearning.sliide.users.presentation.composables`. These files move into it:
  - `UserCard.kt`
  - `UsersFeed.kt`, together with `FeedLazyLayout`
  - `DeleteUserDialog.kt`
  - `SelectUserPlaceholder.kt`
  - `UsersTopAppBar.kt`, which also holds `NavigationIconButton`
  - `ProfileTopBar.kt`
  - `SaveButton.kt`
  - `ProfileHeaderSection.kt`
  - `ProfileForm.kt`, which also holds `ProfileTextField` and `LabeledToggle`
  - `UsersSnackbarHost.kt`, which also holds `UndoSnackbarVisuals`
- **Visibility.** Extracted composables become `internal`, which is visible to `androidDeviceTest` in the same module.
- **One lazy layout.** `UsersFeed` and `UsersFeedPlaceholders` share one private `FeedLazyLayout(columns, state,
  modifier, content: (count/items))` helper. It picks `LazyColumn` or `LazyVerticalGrid` once, instead of duplicating
  the branch, the padding and the spacing.
- **What stays put:**
  - `users/`, `profile/` and `listdetail/` keep their Root and Screen files and the MVI types.
  - `delete/` keeps `DeleteUndoController` (logic, not UI).
  - `util/` keeps `ObserveAsEvents` and `asAddedText`. Those are an effect helper and a string formatter, not
    composables that draw UI.
  - `PreviewData.kt` moves to `composables/` because the previews there use it.
- **Previews.** Each moved or new composable carries its own previews, per the project's global preview rule.

## Risks / Trade-offs

- [Risk] `ListDetailPaneScaffold` might not keep both panes visible with no navigator destination on expanded widths.
  → Today's placeholder already relies on that. The UI test "first user selected by default" exercises it.
- [Risk] Promotion on edit adds a back-stack entry, so back after saving returns to the default. → That is the intended
  behaviour. Back from a promoted profile re-shows the default, which is the same user.
- [Risk] Two collectors race when the default user disappears. → The Root swallows the close for default selections
  (D4). That path is not timing-dependent.
- [Trade-off] Many files move. The package move is done in its own task, before any behaviour change, so the diff stays
  reviewable and tests stay green at each step.
- [Trade-off] `delete_dialog_cancel` is now only a content description. The string text is unchanged, so
  translations stay valid.

## Migration Plan

UI only. There is no data migration. To roll back, revert the change.
