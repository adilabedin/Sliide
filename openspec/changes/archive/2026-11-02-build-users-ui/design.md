# Design

## Context

See proposal.md for the motivation. The requirements are in `specs/user-feed`, `specs/user-profile`,
`specs/user-deletion`, `specs/adaptive-layout` and `specs/design-system`.

Current state that shapes the approach:

- **The domain interface is already complete.** `UserRepository` exposes:
  - `observeUsers()`: cold, newest first.
  - `createUser`, `updateUser`, and `removeUserLocally` / `restoreUser` / `finalizeDelete` for the Undo window, plus
    `refresh()`.
  - Every suspending call already runs in the application scope (`outlivingCaller`), so a cancelled caller cannot
    corrupt the cache.
  - The contract requires exactly one `restoreUser` **or** `finalizeDelete` per successful `removeUserLocally`.
    Otherwise `refresh` stays blocked for the life of the process.
- **`User` has no "last synced" field.** It has `createdAt`, which is recorded locally and kept across refreshes and
  edits. The user agreed that the card should read **"Added …"**.
- **Temporary ids are negative.** A successful `createUser` re-keys the temp row to the server id, so the profile cannot
  hold on to the id of a pending user.
- **`:users:presentation` depends on `:users:domain` only.** It cannot see `ApplicationScope`, which lives in
  `:core:data`.
- **Today's presentation is a placeholder.** `UsersViewModel` never calls `refresh()`, and `UsersScreen` is a
  `ListItem` list. Nothing uses Compose Navigation yet.
- **Platform versions.** CMP 1.12.2 and material3 `1.12.0-alpha03`. On Maven Central, the JetBrains
  `material3.adaptive:*` artifacts exist at `1.3.0` (stable) and `1.4.0-alpha01`, and `material-icons-core` exists at
  `1.7.3`.

## Goals / Non-Goals

**Goals:**

- One shared UI, with no `expect`/`actual`. Every platform difference used here, namely window size, back handling and
  haptics, is already abstracted by Compose Multiplatform.
- One owner per piece of state: the cache for users, `ProfileViewModel` for the draft, `DeleteUndoController` for the
  Undo window, and the pane navigator for the selection.
- Delete safety: the repository's "exactly one of restore or finalise" contract can never be broken by the UI lifecycle.

**Non-Goals:**

- No changes to `:users:data`, `:core:database`, the network layer or the `UserRepository` interface.
- No app-level Compose Navigation `NavHost`. There is only one top-level destination. See D1.
- No search, sorting, paging or avatar images.
- No background sync worker.

## Decisions

### D1: The profile is the detail pane of an adaptive list-detail scaffold, not a NavHost destination

`UsersListDetailRoot` in `:users:presentation` hosts Material 3 Adaptive's `NavigableListDetailPaneScaffold` with
`rememberListDetailPaneScaffoldNavigator`.

> **Implementation note.** `NavigableListDetailPaneScaffold` turned out to be Android-only; the iOS klib of
> `adaptive-navigation` 1.3.0 doesn't contain it. The root therefore uses the shared `ListDetailPaneScaffold` with
> `AnimatedPane`, driven by the same navigator, and handles back itself with a `BackHandler`, popping with
> `PopUntilContentChange`. When the detail pane is emptied, however that happens, the root sends
> `ProfileAction.OnDismiss`, so a reopened Create form starts blank.

- The **list pane** is `UsersRoot`. The **detail pane** is `ProfileRoot`, or an "Select a user" placeholder when
  nothing is selected.
- The scaffold decides the structure from the window size class:
  - compact and medium widths show one pane, so the profile is full screen and back pops it;
  - expanded widths show both panes.
- **Selection survives size changes.** The navigator saves its back stack, so a selection made on a tablet survives a
  shrink to compact, and the reverse.
- **Content key.** The key is a primitive-saveable `Long`: the user id, or a reserved `CREATE_KEY` sentinel, so it can
  be saved on Android and iOS. Temp ids start at `-1` and count down, so the sentinel is `Long.MIN_VALUE`.
- **Rejected alternative: a NavHost with a separate Profile route and a hand-written two-pane branch.** It duplicates
  what the scaffold already does: pane arrangement, predictive back, and keeping the selection across size changes.
  Navigation-compose is added when a second top-level destination appears.
- **Medium-width grid.** Inside the list pane, `UsersScreen` reads `currentDeviceConfiguration()`. It uses a
  single-column `LazyColumn` on compact widths and a two-column `LazyVerticalGrid` on medium widths. On expanded widths
  the list pane is narrow, so it uses a single-column `LazyColumn`. The 20 shimmer placeholders use the same branch.

### D2: One `ProfileViewModel`, retargeted by an action

The detail pane uses a single `ProfileViewModel` from `koinViewModel()`, owned by the root's `ViewModelStoreOwner`.
The root calls `ProfileAction.Open(target)` whenever the navigator's content key changes.

- **Rejected alternative: one ViewModel per user id** (`koinViewModel(key = "profile-$id")`). Without a per-destination
  store, these ViewModels pile up in the activity store until the activity finishes.
- **Process death.** The ViewModel keeps its target, mode and draft fields in `SavedStateHandle`, which Koin 4 injects
  through `viewModelOf`. Process death therefore restores the open profile, including an unsaved Edit draft.
- **Mode model.** `ProfileMode` is `View`, `Edit` or `Create`. `ProfileState` holds:
  - `mode` and `original: UserUi?`
  - `draft` (name, email, gender, status)
  - `nameError` / `emailError: UiText?`
  - `nameTouched` / `emailTouched`
  - `isSaving` and `canSave`
- **Derived values.** `canSave` is derived as: valid, and not saving, and in Edit mode only, `draft != original`.
- **Saving.** Save trims the values and calls `updateUser` or `createUser`. On a `Success`, whether `SYNCED` or
  `QUEUED`, Edit returns to View, and Create emits `ProfileEvent.Created`, which closes the pane and scrolls the feed to
  the top. A `QUEUED` result also emits a "Saved offline" message. On a `Failure`, the form stays as typed:
  - `Remote.VALIDATION` sets `emailError = "This email may already be in use"`.
  - Any other error becomes a snackbar message.
- **The user disappears.** The ViewModel observes `observeUsers()`, mapped to the target id. When the row vanishes:
  - after this ViewModel's own delete: close silently;
  - if the user was local-only (a temp id), close silently, because this is a re-key or a rejected create;
  - otherwise, close with "no longer available".
- **Back in Edit mode** is handled by a `BackHandler` (from `org.jetbrains.compose.ui:ui-backhandler`, which follows the
  CMP version). It is enabled only in Edit mode and sends `CancelEdit`. A close icon in the app bar sends the same
  action, which matters on iOS, where there is no system back button.

### D3: `DeleteUndoController` owns the Undo window and outlives every screen

The controller is a Koin `single` in `:users:presentation`. It is constructed with `UserRepository`, a `CoroutineScope`
and a window `Duration` of 5 seconds. Koin creates the scope as `CoroutineScope(SupervisorJob() +
Dispatchers.Main.immediate)`. Tests inject a `TestScope`'s `backgroundScope`.

```
pending: StateFlow<PendingDelete?>   // user snapshot + deadline
messages: Flow<DeleteMessage>        // e.g. FinalizeFailed(name)
fun request(userId)                  // removeUserLocally; finalises any open window first; starts timer
fun undo(userId)                     // restoreUser if userId is the open window
fun commitNow(userId)                // finalizeDelete now (swipe-dismiss)
```

- **Exactly one outcome per delete.** All transitions run under a `Mutex`. Each window resolves through a single
  `resolve(window, outcome)` path that first clears `pending`, so a late timer, a duplicate Undo or a stale swipe for an
  earlier user is ignored.
- **The timer is a `delay(window)` job in the controller's scope.** Leaving the screen, backgrounding the app or
  rotating the device cannot cancel it, which meets the "app backgrounded" scenario.
- **Who calls it.** The users screen calls it for long-press deletes, and `ProfileViewModel` calls it directly for
  profile deletes. Both paths share one window.
- **Rejected alternative: running the timer in `viewModelScope`.** It is cancelled when the activity finishes, which
  leaves `finalizeDelete` uncalled and `refresh` blocked.
- **Rejected alternative: injecting `ApplicationScope`.** That would make presentation depend on `:core:data`, which
  breaks the dependency rules. A dedicated scope owned by a singleton is enough, because `finalizeDelete` itself already
  hops to the application scope.

### D4: The snackbar is a normal `SnackbarHost`, driven by controller state

`UsersScreen` owns a `SnackbarHostState` in its `Scaffold`. The rendering, the matching rules and the countdown work
as follows.

- **Rendering.** `SnackbarHost(hostState) { data -> UndoSnackbar(data, …) }` renders the design-system pill. When
  `state.pendingDelete` changes, a `LaunchedEffect(pendingDelete?.userId)` calls
  `showSnackbar(duration = Indefinite, actionLabel = "Undo")`.
- **Matching the result to the right delete.** The effect captures the userId, so a result is always sent for the
  delete that produced it:
  - `ActionPerformed` sends `UsersAction.UndoDelete(userId)`.
  - `Dismissed` (a swipe) sends `UsersAction.CommitDelete(userId)`.
- **When the window closes elsewhere.** If `pendingDelete` becomes `null` because the timer fired or Undo ran
  elsewhere, the effect dismisses the current snackbar. The resulting `Dismissed` carries the old userId, which the
  controller ignores.
- **Countdown.** `PendingDeleteUi` carries the `deadline`. `UndoSnackbar` animates a thin progress line from
  `remaining / window` to 0, so after a rotation it continues from the right point. "Now" reaches the screen as a
  `currentTime: () -> Instant` parameter, defaulting to `Clock.System::now` and fixed in previews and tests.
- **Rejected alternative: a custom overlay bar.** The user asked for a normal snackbar host on the users screen, and
  the M3 host gives accessibility announcements and swipe-to-dismiss for free.

### D5: The feed state is one `combine`, and the cache is the only data source

`UsersViewModel` combines four inputs into `UsersState`:

- `observeUsers()`;
- a minute ticker: `flow { while (true) { emit(clock.now()); delay(tick) } }`, with `tick` injected;
- `deleteUndoController.pending`;
- a private `MutableStateFlow<RefreshStatus>` (Idle, Running, Failed(error)).

`UsersState` holds:

- `users: List<UserUi>`, where each `UserUi` has id, name, email, initials, avatar tone, `addedAgo: RelativeTime` and
  `isPendingSync`;
- `contentState`: Loading, Content, Empty, or Error(UiText);
- `isRefreshing`;
- `banner: UiText?`;
- `pendingDelete: PendingDeleteUi?`;
- `confirmDelete: UserUi?` (the dialog);
- `selectedUserId: Long?` (the highlight on two-pane layouts).

The content state follows these rules:

- **Loading** until the database has emitted at least once. After that, it stays Loading while the list is empty and
  the first refresh is running. This drives the 20 shimmer cards.
- **Error** when the list is empty and the last refresh failed.
- **Banner** when the list is not empty and the last refresh failed. A later success clears it.

Refresh is triggered once on the first subscription, reusing the existing `hasStartedObserving` guard so a rotation
does not trigger it again. Pull-to-refresh and Retry also trigger it. A guard `if (refreshJob?.isActive == true)
return` keeps pulls from overlapping.

Refresh results never write to `users`. Only `observeUsers()` does. Errors become `UiText` through one shared function,
`DataError.toUiText()`, with a string per `Remote` value and a generic string for `Local`.

### D6: Relative time is a pure shared function

`relativeAddedTime(createdAt: Instant, now: Instant): RelativeTime` lives in `:users:presentation` commonMain. It
returns `JustNow` (under 60 seconds, or in the future), `Minutes(n)`, `Hours(n)`, `Days(n)` (under 30), `Months(n)`
(under 12) or `Years(n)`. The UI maps each value to a `pluralStringResource`, for example "Added %d minutes ago". The
function stays free of string handling so tests can cover every boundary without Compose.

**Rejected alternative: `kotlinx-datetime` formatting.** It does not produce relative phrases and adds a dependency.

### D7: Validation lives in `:users:domain`

`UserDetailsValidator` is an `object` with `validateName(String): NameError?` and `validateEmail(String): EmailError?`.
It holds pure rules on trimmed input, using the email shape defined in the user-profile spec. It lives in the domain
because it is business logic, the Ktor `VALIDATION` mapping relates to it, and the brief requires shared logic.
`ProfileViewModel` maps the error enums to `UiText`. Errors are shown only after a field is touched in Create mode, and
always in Edit mode.

`User.isLocalOnly` (`id < 0`) is added to the domain model. It keeps the meaning of temp ids inside the domain module
and drives the "Pending sync" chip.

### D8: Design system, with tokens first and then components

All of these live in `:core:designsystem`, because they are design infrastructure, not feature code.

- **Palette** (`Color.kt`).
  - Light mode uses porcelain neutrals: background `#F6F6F3`, cards white, hairline outline `#E3E3DD`, ink text
    `#15171C`, secondary text `#555A64`, a midnight-ink primary `#1E2A4A`, and a champagne accent `#8A6A2C` for
    selection and the Undo action.
  - Dark mode uses an ink background `#0D0E12`, cards `#16181D`, hairline `#262930`, text `#ECECEF`, secondary text
    `#A3A7B2`, a soft periwinkle primary `#B8C4F0`, and a champagne accent `#D9BF86`.
  - The destructive container is `#B3261E` with white text in both modes. Destructive text on dark surfaces is
    `#FF9C94`.
  - The final values are those that pass the contrast test below. Hues stay as described.
- **Tokens.** `SliideShapes` (8 / 14 / 20 / 28 dp), `SliideSpacing`, and `SliideMotion` (short 120 ms, medium 240 ms,
  emphasised easing, shimmer 1200 ms) are exposed through `MaterialTheme` extensions or a `CompositionLocal`.
- **Components.**
  - `SliideCard`: a tonal surface with a hairline border, a press-scale of 0.98 drawn in `graphicsLayer`, and
    `combinedClickable` with long-press.
  - `Modifier.shimmer()` and `UserCardPlaceholder`.
  - `UserAvatar`: initials on one of 6 muted tones, chosen deterministically by id, with an optional status dot.
  - `SegmentedToggle<T>`: two options with an animated selection pill.
  - `SliideFab`: an extended FAB with the "Add user" label that collapses to an icon on scroll.
  - `UndoSnackbar`: an inverse-ink pill with a champagne Undo button and a countdown line.
  - `DestructiveButton`.
  - `ErrorBanner` and `FullScreenMessage`.
  - `DeviceConfiguration` with `currentDeviceConfiguration()`, placed in the designsystem `layout` package. It is
    width-based, with `COMPACT` below 600dp, `MEDIUM` from 600dp to below 840dp, and `EXPANDED` from 840dp, to match
    the adaptive-layout spec. The skill's five-way mapping would classify a landscape phone (about 731×411dp) as
    `DESKTOP`.
- **Icons** come from `org.jetbrains.compose.material:material-icons-core:1.7.3`: Add, Edit, Delete, ArrowBack, Close,
  Refresh, Warning and Person. Hand-written vector paths are not used.
- **Contrast is enforced by a test.** `ContrastTest` in designsystem `commonTest` computes WCAG relative luminance for
  every foreground and background pair actually used, such as onSurface on the card colour or onError on the
  destructive container. It asserts at least 4.5:1, or at least 3:1 for large text and icons.

### D9: Dependencies

The following are added to `libs.versions.toml`:

| Artifact | Version |
|---|---|
| `org.jetbrains.compose.material3.adaptive:adaptive`, `adaptive-layout`, `adaptive-navigation` | `1.3.0` (stable). Fall back to `1.4.0-alpha01` only if 1.3.0 fails against material3 `1.12.0-alpha03` at link time. |
| `org.jetbrains.compose.material:material-icons-core` | `1.7.3` |
| `org.jetbrains.compose.ui:ui-backhandler` | `composeMultiplatform` |

- `adaptive` goes into `:core:designsystem` as `api`.
- `adaptive-layout` and `adaptive-navigation` go into `:users:presentation`.
- `material-icons-core` goes into `:core:designsystem` as `api`, so features use the same icons.
- `ui-backhandler` goes into `:users:presentation`.
- Versions are confirmed with the android-version-catalog skill at implementation time.

### D10: Testing

**Unit tests** live in `commonTest` and run through `androidHostTest`, using AssertK, Turbine and fakes:

- `UserDetailsValidatorTest` (domain): every rule and boundary.
- `RelativeTimeTest`: each bucket edge (59 s, 60 s, 59 m, 60 m, 23 h, 24 h, 29 d, 30 d, 11 mo, 12 mo) and a future
  timestamp.
- `DeleteUndoControllerTest`, using the `StandardTestDispatcher` and `advanceTimeBy` from `runTest`:
  - Undo restores the user and never finalises.
  - Expiry finalises and never restores.
  - A swipe finalises immediately.
  - A second request finalises the first.
  - A stale userId is ignored.
  - A failed finalise emits `FinalizeFailed`.
- `UsersViewModelTest`, rewritten for the new state:
  - shimmer, then content;
  - shimmer, then error;
  - banner on a failure with a cache;
  - the banner clears on success;
  - refresh runs once only on the first subscription;
  - the pull guard;
  - the ticker updates `addedAgo`;
  - the confirm dialog flow.
- `ProfileViewModelTest`:
  - mode transitions;
  - the touched-gated errors;
  - `canSave` rules;
  - SYNCED, QUEUED, VALIDATION and other failures for both Edit and Create;
  - cancel restores the original;
  - closing when the user disappears, with each of the three cases;
  - draft restore from `SavedStateHandle`.
- `DataErrorUiTextTest`: one distinct string for each `Remote` value.
- `ContrastTest` (designsystem).

**Fakes.** `FakeUserRepository` is extended to record calls and to script results and failures per method.

**Instrumented tests** live in `:users:presentation` `androidDeviceTest` and use `createComposeRule` on stateless
screens with fixed state:

- a long-press opens the dialog, and the custom "Delete" accessibility action opens it too;
- the Edit icon becomes Save;
- the toggles are exclusive;
- the Delete button is absent in Create mode;
- 20 placeholders are shown in the loading state.

`compose-uiTestJunit4` and `androidx.compose.ui:ui-test-manifest` are added to the `androidDeviceTest` configuration
through `CmpLibraryConventionPlugin`, so that every CMP module gets the same test setup.

**Existing tests.** `AppLaunchSmokeTest` is updated to the new empty-state and loading copy. `KoinGraphTest` must still
pass with `ProfileViewModel` (which needs `SavedStateHandle` passed as an extra type), `DeleteUndoController` and the
`Clock` resolved across modules.

## Risks / Trade-offs

- **[Risk]** The `material3.adaptive` 1.3.0 artifacts were built against Compose 1.10, while the app uses CMP 1.12.2
  and material3 alpha03, so their binaries could be incompatible. → **Mitigation:** the dependency task links the iOS
  framework and runs `./gradlew build` before any UI work, and falls back to `1.4.0-alpha01`.
- **[Risk]** The navigator content key may not be saveable on iOS if it is not a primitive. → **Mitigation:** a `Long`
  key with a `CREATE_KEY` sentinel (D1).
- **[Risk]** A profile opened on a pending (temp-id) user closes when that user syncs, because the id changes. →
  **Trade-off accepted:** it closes silently and the card stays in the feed with its new id. Following the re-key would
  need a repository API change, which is out of scope.
- **[Risk]** The Undo controller scope uses `Dispatchers.Main`, so it cannot run in a test without `setMain`. →
  **Mitigation:** the scope is a constructor parameter, and tests inject `backgroundScope`.
- **[Risk]** The controller holds a 5-second job in a singleton, so a process kill during the window loses the timer. →
  **Accepted:** the repository already handles it. `awaitingFinalize` is held in memory, so after process death the
  user is simply gone locally with no pending DELETE, and the next refresh brings the user back from the server. This
  is the behaviour the user-deletion spec exempts.
- **[Trade-off]** The brief says "on 204, remove with animation". Here the row is removed when the delete is confirmed,
  and the network DELETE happens after the Undo window. This keeps Undo instant and works offline. The `user-cache`
  spec of the earlier change already made the same choice.
- **[Trade-off]** The label reads "Added …" rather than "Last synced …", because the cache records the time it first
  saw a user, not each sync. The user approved this.
- **[Trade-off]** A Status toggle (Active / Inactive) is added next to Gender. GoRest requires status on create, and a
  read-only status would leave the edit page inconsistent.
- **[Trade-off]** On full-screen profiles a back arrow appears before "Profile". iOS has no system back button, so the
  page needs one, but the title stays at the top left as requested.

## Migration Plan

Not applicable. The app is unreleased, and no schema or API changes are made. Rolling back means reverting the change.

## Open Questions

- The exact champagne tone may need a small adjustment after it is seen on a device. Any change stays within the
  ContrastTest bounds.
