# Tasks

## 1. Dependencies and test wiring

- [x] 1.1 Using the android-version-catalog skill, add the following to `libs.versions.toml`:
  - `compose-material3-adaptive`, `compose-material3-adaptive-layout` and `compose-material3-adaptive-navigation`,
    with an `adaptive` version of `1.3.0`;
  - `compose-material-icons-core` (`1.7.3`);
  - `compose-ui-backhandler` (version ref `composeMultiplatform`);
  - `compose-uiTestManifest`.

  Wire them up as follows (design D9):
  - `:core:designsystem` gets `adaptive` and `material-icons-core` as `api`.
  - `:users:presentation` gets `adaptive-layout`, `adaptive-navigation` and `ui-backhandler`.

  Verify: `./gradlew build` and `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` both succeed. If either
  fails on a binary incompatibility, switch to `1.4.0-alpha01` and re-run both commands.
- [x] 1.2 In `CmpLibraryConventionPlugin`, add `compose-uiTestJunit4` and the ui-test manifest to
  `androidDeviceTestImplementation`. Add a trivial `createComposeRule` smoke test in `:users:presentation`
  `androidDeviceTest`. Verify: `./gradlew :users:presentation:connectedAndroidDeviceTest` passes on an emulator started
  with the android-cli skill.

## 2. Design system (`:core:designsystem`)

- [x] 2.1 Replace the palette in `Color.kt` with the porcelain/ink light scheme and the ink dark scheme, with the
  midnight primary, champagne accent and destructive tokens from design D8. Add `ContrastTest` in `commonTest`, which
  asserts WCAG AA for every foreground and background pair used. Verify: `./gradlew :core:designsystem:allTests` passes,
  and the `ThemePreview` light and dark previews render.
- [x] 2.2 Add the `SliideShapes`, `SliideSpacing` and `SliideMotion` tokens and expose them through the theme. Verify:
  the module compiles and `SliideTheme` previews still render.
- [x] 2.3 Add `DeviceConfiguration` and `currentDeviceConfiguration()` in the `layout` package, following the
  compose-adaptive-layouts skill. Add a unit test that maps sample `WindowSizeClass` values to each configuration.
  Verify: `./gradlew :core:designsystem:allTests` passes.
- [x] 2.4 Following the android-compose-components skill, build these components:
  - `SliideCard`, with a press scale and long-press support through `combinedClickable`;
  - `UserAvatar`, with deterministic tones and a status dot;
  - `Modifier.shimmer()` and `UserCardPlaceholder`.

  Each component gets one preview per state, in light and dark. Verify: the previews render with the android-cli
  skill's `render-compose-preview`.
- [x] 2.5 Build `SegmentedToggle<T>` (an animated pill, read-only and enabled states), `SliideFab` (expanded and
  collapsed), `UndoSnackbar` (with the countdown line), `DestructiveButton`, `ErrorBanner` and `FullScreenMessage`,
  each with one preview per state in light and dark. Verify: the previews render and the module compiles for iOS.

## 3. Domain additions (`:users:domain`)

- [x] 3.1 Add `UserDetailsValidator`, with `validateName` and `validateEmail` returning the `NameError` and
  `EmailError` enums (design D7, specs user-profile "Real-time name validation" and "Real-time email validation"). Add
  `UserDetailsValidatorTest`, covering blank, whitespace only, 1 vs 2 characters, a missing "@", two "@"s, a domain
  without a dot, an empty label, and trimming. Verify: `./gradlew :users:domain:allTests` passes.
- [x] 3.2 Add `User.isLocalOnly` (`id < 0`) with KDoc and a unit test. Verify: `./gradlew :users:domain:allTests` passes.

## 4. Shared presentation logic (`:users:presentation`)

- [x] 4.1 Add the `RelativeTime` sealed type and `relativeAddedTime(createdAt, now)` (design D6). Add a plural string
  resource for each bucket. Add `RelativeTimeTest`, covering every bucket edge and a future timestamp. Verify:
  `./gradlew :users:presentation:allTests` passes.
- [x] 4.2 Add `UiText` and `DataError.toUiText()`, with distinct strings for every `Remote` value and a generic string
  for `Local`. Add `DataErrorUiTextTest`. Verify: the tests pass.
- [x] 4.3 Extend `FakeUserRepository` so each method can be scripted per call (success, queued or failure), records its
  calls, and can be gated with a `CompletableDeferred`. Verify: the existing presentation tests still compile and pass.
- [x] 4.4 Implement `DeleteUndoController` with an injected scope, window and repository (design D3). Bind it in
  `usersPresentationModule` as a `single` with its own `SupervisorJob` + `Main.immediate` scope. Add
  `DeleteUndoControllerTest`, covering every user-deletion outcome scenario with `advanceTimeBy`:
  - Undo restores and never finalises;
  - expiry finalises once;
  - commit-now (swipe);
  - a second delete finalises the first;
  - a stale userId is ignored;
  - a failed finalise emits a message;
  - collectors leaving do not stop the timer.

  Verify: `./gradlew :users:presentation:allTests` passes.

## 5. Users feed (`:users:presentation`)

- [x] 5.1 Rewrite `UserUi`, `UsersState`, `UsersAction` and `UsersEvent`, then `UsersViewModel` as one `combine` of
  `observeUsers`, the minute ticker, the controller's `pending` state and the refresh status (design D5). Cover:
  - refresh once on the first subscription;
  - the pull and Retry guard;
  - the dialog state for confirming a delete;
  - undo and commit forwarded to the controller;
  - scroll-to-top after a create;
  - forwarding of messages.

  Rewrite `UsersViewModelTest` to cover every user-feed scenario that can be checked at ViewModel level (shimmer then
  content, shimmer then error, banner and its clearing, no second refresh, the ticker updates the added time, pending
  sync) and the confirm and undo dispatches. Verify: `./gradlew :users:presentation:allTests` passes.
- [x] 5.2 Build `UsersScreen`:
  - `PullToRefreshBox`;
  - a `LazyColumn` on compact and expanded widths and a two-column `LazyVerticalGrid` on medium widths, using stable
    keys and `animateItem`;
  - 20 lazy shimmer placeholders, announced once as "Loading users";
  - the empty state, the full-screen error with Retry, and the `ErrorBanner`;
  - user cards with tap, long-press with haptics, and a custom "Delete" accessibility action;
  - `DeleteUserDialog`;
  - `SliideFab`, which collapses on scroll;
  - a `SnackbarHost` with `UndoSnackbar`, driven by `pendingDelete` (design D4).

  Add one preview per state: loading, empty, error, banner, populated, pending-sync, dialog open and snackbar shown.
  Each state is shown in light and dark, at 320dp and 840dp. Verify: the previews render.
- [x] 5.3 Add instrumented tests in `:users:presentation` `androidDeviceTest` that run against `UsersScreen` with fixed
  state:
  - a long-press opens the dialog naming the user and the 5-second undo;
  - the "Delete" accessibility action opens the same dialog;
  - Cancel changes nothing;
  - the loading state contains 20 placeholders;
  - Undo on the snackbar dispatches `UndoDelete(userId)`.

  Verify: `./gradlew :users:presentation:connectedAndroidDeviceTest` passes.

## 6. Profile page (`:users:presentation`)

- [x] 6.1 Add `ProfileMode`, `ProfileState`, `ProfileAction` and `ProfileEvent`, and implement `ProfileViewModel` with
  `SavedStateHandle` (design D2):
  - `Open(target)`: View or Create;
  - Edit, CancelEdit, field changes, gender and status changes;
  - touched-gated validation and `canSave`;
  - Save through `updateUser` or `createUser`, with the outcome handling;
  - Delete through the controller;
  - closing when the user disappears, in the three cases (own delete, local-only, anything else).

  Bind it with `viewModelOf`. Add `ProfileViewModelTest`, covering every user-profile scenario that can be checked at
  ViewModel level, plus restoring the draft from `SavedStateHandle`. Verify: `./gradlew :users:presentation:allTests`
  passes.
- [x] 6.2 Build `ProfileScreen`:
  - an app bar with a back arrow when full screen and "Profile" or "New user" at the start;
  - an `AnimatedContent` swap between the Edit icon and the Save text button (with a progress state), and a close
    control in Edit mode;
  - the form, limited to a readable maximum width and centred;
  - `SegmentedToggle` for gender and status;
  - a `DestructiveButton` pinned in `bottomBar` with IME and navigation-bar insets, hidden in Create mode;
  - `BackHandler` in Edit mode;
  - the shared `DeleteUserDialog`.

  Add one preview per state: view, edit valid, edit invalid, saving, create pristine, create with errors, and the
  delete dialog. Each state is shown in light and dark, at 320dp and 840dp. Verify: the previews render.
- [x] 6.3 Add instrumented tests against `ProfileScreen` with fixed state:
  - the Edit icon becomes Save;
  - View mode is read-only and the keyboard does not open;
  - the toggles are exclusive;
  - Save is disabled when the form is invalid;
  - there is no Delete button in Create mode.

  Verify: `./gradlew :users:presentation:connectedAndroidDeviceTest` passes.

## 7. Adaptive list-detail and app shell

- [x] 7.1 Build `UsersListDetailRoot` with `NavigableListDetailPaneScaffold` and a `Long` content key, where
  `CREATE_KEY = Long.MIN_VALUE` (design D1). It wires:
  - card taps and the FAB to the navigator;
  - the content key to `ProfileAction.Open`;
  - `ProfileEvent.Close` / `Created` to navigating back, and `Created` also to a feed scroll-to-top;
  - `selectedUserId` to the highlight;
  - the "Select a user" placeholder for the empty detail pane.

  Point `App()` at it. Verify: on an emulator started with the android-cli skill, check each layout:
  - phone portrait: one pane, back keeps the scroll position;
  - landscape: two columns;
  - tablet: master-detail;
  - shrinking from tablet to compact keeps the open profile in Edit mode with the draft.

  Capture each with `android screen capture`.
- [x] 7.2 Update `KoinGraphTest` so `ProfileViewModel` (with `SavedStateHandle` as an extra type),
  `DeleteUndoController` and the cross-module `Clock` all resolve. Update `AppLaunchSmokeTest` to the new title and
  empty-state copy, and make it wait past the shimmer. Verify: `./gradlew :composeApp:testAndroidHostTest` and
  `./gradlew :androidApp:connectedDebugAndroidTest` pass.
- [ ] 7.3 Walk through the full flows by hand on Android with the android-cli skill:
  - add a user, online and offline: the user is at the top, and offline shows "Pending sync";
  - edit and save;
  - cancel an edit;
  - long-press delete, then Undo;
  - long-press delete, then let the window expire;
  - delete from the profile;
  - two deletes in a row;
  - dark mode.

  Verify: screenshots of each flow match the specs.
- [x] 7.4 Update `README.md` with:
  - the UI architecture: the list-detail scaffold, a single retargeted `ProfileViewModel`, the `DeleteUndoController`
    lifetime, and the cache as the only data source;
  - the design system: palette, tokens and the contrast test;
  - the accepted deviations: "Added" instead of "Last synced", local removal before the 204, the extra Status toggle,
    and the back arrow before "Profile";
  - how AI orchestration was used for this change.

  Verify: the README renders and matches design.md.

## 8. Verification gate

- [x] 8.1 Run `./gradlew ktlintCheck` and fix any violations without adding a baseline. Verify: it passes.
- [x] 8.2 Run all unit tests and all instrumented tests for every affected module:
  - `:core:designsystem`, `:users:domain`, `:users:presentation` and `:composeApp`, each with `allTests` /
    `testAndroidHostTest`;
  - `:users:presentation` with `connectedAndroidDeviceTest`;
  - `:androidApp` with `connectedDebugAndroidTest`.

  Instrumented tests run on an emulator started with the android-cli skill. Verify: every test passes, and none are
  skipped or disabled.
- [x] 8.3 Run `./gradlew build` and `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64`. Verify: both succeed.

## Workflow follow-up

- Archive this change after `setup-module-structure`, so the `design-system` ADDED requirements merge into an existing
  spec.
