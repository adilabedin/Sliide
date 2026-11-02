# Tasks

Skills to read while implementing:
- `android-compose-components` for every composable.
- `android-presentation-mvi` and `android-unit-testing` for the ViewModel work.
- `android-ui-testing` for instrumented tests.
- `compose-adaptive-layouts` for group 4.
- `android-cli` for the emulator.

Each group ends green on `./gradlew :users:presentation:allTests` and `./gradlew ktlintCheck`.

## 1. Collect feature composables in `composables/` (no behaviour change)

- [x] 1.1 Create the package `com.androidlearning.sliide.users.presentation.composables`. Move these files into it,
  updating packages and imports, and remove the `users/components` package:
  - `users/components/UserCard.kt`
  - `users/components/UsersFeed.kt`, together with `UsersFeedPlaceholders`, `PLACEHOLDER_COUNT` and
    `PLACEHOLDER_TEST_TAG`
  - `delete/DeleteUserDialog.kt`
  - `users/PreviewData.kt`

  Verify: `./gradlew :users:presentation:compileKotlinMetadata` passes and `users/components` no longer exists.
- [x] 1.2 Extract `SelectUserPlaceholder` from `listdetail/UsersListDetailRoot.kt` into
  `composables/SelectUserPlaceholder.kt`, together with its light and dark previews. Verify: the previews render with
  the `android-cli` preview rendering.
- [x] 1.3 Extract the private profile sub-composables from `profile/ProfileScreen.kt` into `composables/` as `internal`:
  - `ProfileTopBar.kt`
  - `SaveButton.kt`
  - `ProfileHeaderSection.kt`
  - `ProfileForm.kt`, together with `ProfileTextField` and `LabeledToggle`

  Give each its own previews: light and dark, and every distinct state.
  - Top bar: View, Edit and Create.
  - Save: enabled, disabled and saving.
  - Header: synced and pending sync.
  - Form: read-only, editable and with errors.

  Verify: `ProfileScreen.kt` contains only `ProfileRoot`, `ProfileScreen` and the screen previews, and it compiles.
- [x] 1.4 Extract `UsersSnackbarHost` and `UndoSnackbarVisuals` from `users/UsersScreen.kt` into
  `composables/UsersSnackbarHost.kt`. Add previews for the plain message and for the Undo countdown, light and dark.
  Verify: it compiles, and `UndoSnackbarEffect` still builds `UndoSnackbarVisuals`.
- [x] 1.5 Merge the duplicated `LazyColumn`/`LazyVerticalGrid` branches of `UsersFeed` and `UsersFeedPlaceholders`
  into one private `FeedLazyLayout` helper (design D8). Keep the padding, spacing and `animateItem` exactly as they
  are. Verify: the existing `UsersScreenTest.loadingStateHasTwentyPlaceholders` and the populated previews are
  unchanged.
- [x] 1.6 Update `UsersScreenTest` and `ProfileScreenTest` imports for the moved symbols. Verify:
  `./gradlew :users:presentation:connectedAndroidDeviceTest` passes on an emulator started with `android-cli`, and
  `./gradlew :users:presentation:allTests` passes.

## 2. Shared app bar and pinned feed bar

- [x] 2.1 Add `composables/UsersTopAppBar.kt` (design D2). It holds `UsersTopAppBar(title, navigationIcon, actions,
  scrollBehavior)`, which wraps `TopAppBar` with the shared background and `surfaceContainer` scrolled colours. It also
  holds `NavigationIconButton(kind = Back | Close, contentDescription, onClick)`, which uses the auto-mirrored arrow.
  Add previews: with and without a navigation icon, with an action, light and dark, at 320dp and 840dp. Verify: the
  previews render.
- [x] 2.2 Switch `UsersScreen` from `LargeTopAppBar` + `exitUntilCollapsedScrollBehavior` to `UsersTopAppBar` +
  `TopAppBarDefaults.pinnedScrollBehavior()`, keeping `nestedScroll` (design D1). Switch `ProfileTopBar` to
  `UsersTopAppBar` and `NavigationIconButton`. Verify: the existing previews and tests still pass.
- [x] 2.3 Add the `UsersScreenTest` test `appBarStaysPinnedWhileTheFeedScrolls`. It uses 30 users, records the "Users"
  title node's bounds, swipes the list up, and asserts that the bounds are unchanged and that a later card is now
  displayed. Run it on the emulator and confirm it passes.

## 3. Profile: back arrow in Create mode, bigger Save

- [x] 3.1 In `ProfileTopBar`, show `NavigationIconButton(Back, profile_back)` in Create mode in every layout, and
  never Close (design D5). Edit mode keeps Close for cancel. View mode shows Back only when `showBackButton` is true.
  Verify: the Create previews show the arrow.
- [x] 3.2 Rework `SaveButton` into a filled M3 `Button` (design D6):
  - at least 48dp tall
  - `titleSmall` label
  - primary colours
  - a progress indicator that keeps the button's width while saving

  Verify: the Save previews (enabled, disabled, saving) render with the bigger button.
- [x] 3.3 Update and extend `ProfileScreenTest`:
  - `createModeShowsBackArrowNotClose`: the content description "Back" is shown and there is no "Cancel editing" node.
  - `backArrowInCreateLeavesWithoutSaving`: tapping it calls `onBackClick`, and no `OnSaveClick` is sent.
  - `saveButtonIsAtLeast48dpTall`: `assertHeightIsAtLeast(48.dp)` on the "Save" node.

  The existing `editIconTurnsIntoSave` and `saveIsDisabledWhileTheFormIsInvalid` must still pass. Run on the emulator.

## 4. Default first-user selection on two-pane layouts

- [x] 4.1 In `UsersAction`:
  - Change `OnSelectionChange` to carry the raw navigator key (`ProfileKey.CREATE` included).
  - Add `OnPaneLayoutChange(isTwoPane: Boolean)`.

  In `UsersViewModel`:
  - Store `chosenKey` and `isTwoPane`.
  - Derive `defaultSelectedUserId`: the first card, only when two-pane, nothing chosen and the content is `Users`.
  - Derive the highlighted `selectedUserId`: the chosen user id, no highlight for `CREATE`, otherwise the default.

  Add `defaultSelectedUserId` to `UsersState` (design D3). Verify: it compiles.
- [x] 4.2 Add `UsersViewModelTest` cases. Each one asserts both `defaultSelectedUserId` and `selectedUserId`:
  - two-pane with nothing chosen selects the first user
  - single pane has no default
  - a chosen user overrides the default
  - `CREATE` highlights nothing and has no default
  - the default follows the first card after the first user is deleted (pending delete removes it from the cache)
  - the default follows the first card after a new user is created at the top
  - loading, empty and error give no default

  Verify: `./gradlew :users:presentation:allTests` passes.
- [x] 4.3 Update `UsersListDetailRoot` (design D4):
  - Collect `usersViewModel.state` and compute `detailKey = selectedKey ?: defaultSelectedUserId`.
  - Render `ProfileRoot(detailKey)`, or `SelectUserPlaceholder` when `detailKey` is null.
  - Send `OnSelectionChange(selectedKey)`.
  - Send `OnPaneLayoutChange(isTwoPane)`.
  - Send `ProfileAction.OnDismiss` only when `detailKey` is null.
  - Swallow the message and skip navigation in `onClose` when the shown profile is the default.
  - Promote the default to a navigator destination when the profile enters Edit mode.

  Verify: manual check on a tablet emulator with `android-cli`. Check each of these:
  - The first user is shown and highlighted.
  - Deleting it moves to the next user without a "no longer available" snackbar.
  - Rotating or resizing to compact shows the list.
  - Editing then shrinking keeps the edit full screen.
- [x] 4.4 Add an instrumented test `UsersListDetailDefaultSelectionTest` in `androidDeviceTest`. It forces an expanded
  window, uses fake repository data and asserts that the first user's profile name is shown in the detail pane without
  any tap. If forcing the window size is not feasible in the device-test setup, test the `UsersScreen` highlight for
  `selectedUserId` instead and record the limitation in the test's KDoc. Run it on the emulator and confirm it passes.

## 5. Delete dialog redesign

- [x] 5.1 Rebuild `composables/DeleteUserDialog.kt` on `BasicAlertDialog` + `Surface` (design D7):
  - `NavigationIconButton(Close, contentDescription = delete_dialog_cancel)` at the top start
  - a 40dp bin in a 72dp `errorContainer` circle
  - the centred title and body
  - a full-width `DestructiveButton(delete_dialog_confirm)`
  - no Cancel text button
  - width capped at 400dp

  Keep the previews: light, dark, and a long name. Verify: the previews render with the new layout.
- [x] 5.2 Update `UsersScreenTest.cancelClosesTheDialogAndChangesNothing` to tap
  `onNodeWithContentDescription("Cancel")`. Add these tests:
  - `dialogHasNoCancelTextButton`: no node with the text "Cancel".
  - `deleteButtonConfirms`: tapping the "Delete" button inside the dialog sends `OnDeleteConfirm`.

  Confirm that `ProfileScreen`'s delete dialog uses the same composable. Run on the emulator and confirm it passes.

## 6. Documentation

- [x] 6.1 Update `README.md`. Describe:
  - the `composables/` package convention: Root and Screen composables stay beside their ViewModel, and sub-composables
    live in `composables/`
  - the pinned app bar
  - the default first-user selection on tablets
  - the redesigned delete dialog

  Verify: the README renders and mentions each item.

## 7. Integration verification

- [x] 7.1 Run `./gradlew build` (all modules compile, and the shared unit tests pass) and
  `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64`. Both must succeed.
- [x] 7.2 Run `./gradlew ktlintCheck` and fix any violation without adding a baseline.
- [x] 7.3 For every affected module (`:users:presentation`), run all unit tests (`./gradlew :users:presentation:allTests`)
  and all instrumented tests (`./gradlew :users:presentation:connectedAndroidDeviceTest`) on an emulator managed with
  `android-cli`. All must pass. Do not disable or weaken any test.
- [x] 7.4 Manual pass with `android-cli` on a phone emulator and a tablet emulator. Check each of these:
  - The feed bar stays fixed while scrolling.
  - Create shows a back arrow and the big Save.
  - The delete dialog has a close icon, a large bin and one Delete button.
  - The tablet opens with the first user selected.

  Capture a screenshot of each.

## Workflow follow-up

- Archive the change with `/opsx:archive` after review.
