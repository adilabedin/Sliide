# Design

## Context

- `UsersViewModel` (`:users:presentation`) holds a private `RefreshState(isRunning, lastError, bannerDismissed)`.
  `startRefresh()` is called from the state flow's first `onStart` (the launch refresh) and from
  `UsersAction.OnRefresh` / `OnRetry`. It returns early if `refreshJob` is still active.
- `buildState` derives:
  - `content`: `Loading` while the cache is unknown, or while it is empty and a refresh is running.
  - `isRefreshing = refresh.isRunning && cards.isNotEmpty()`.
  - Nothing records who started the refresh, which is the bug (proposal: Why).
- `UsersScreen` passes `state.isRefreshing` to Material 3's `PullToRefreshBox` only in the `FeedContent.Users` branch.
  The `Empty` branch is a `FullScreenMessage` with no action. That component already takes
  `actionLabel`/`onAction` (default icon: Refresh), and the `Error` branch uses it with `Res.string.users_retry` and
  `UsersAction.OnRetry`.
- One test, `pullWhileRefreshingDoesNotStartAnotherRefresh`, asserts that `isRefreshing` is true straight after
  launch. That assertion encodes the behaviour being removed.

## Goals / Non-Goals

**Goals:**
- The indicator reflects only what the user asked for. The decision lives in the ViewModel's state, where unit tests
  can cover it.
- Retry from the empty state reuses the existing action, string and component.

**Non-Goals:**
- No change to when refreshes run (launch, pull, retry), to the shimmer rules, or to the banner and error behaviour.
- No new design-system component or string.

## Decisions

### D1. Track the trigger in `RefreshState`
Replace `isRunning: Boolean` with `running: RefreshTrigger?`, where `RefreshTrigger` is a private enum
(`Launch`, `Pull`, `Retry`) and `null` means idle. `startRefresh(trigger)` records it:
- `onStart` passes `Launch`;
- `OnRefresh` passes `Pull`;
- `OnRetry` passes `Retry`.

`buildState` then uses:
- `Loading` when `running != null` and the cache is empty, which is unchanged in meaning;
- `isRefreshing = running == RefreshTrigger.Pull && cards.isNotEmpty()`.

*Alternative considered:* hiding the indicator in the composable, for example by remembering locally whether the user
pulled. Rejected for two reasons. Under MVI the screen renders state and doesn't decide it. And a local flag can't
know when a refresh the ViewModel started has finished.

### D2. A pull during a running refresh upgrades it to `Pull`
If a refresh is already running when `OnRefresh` arrives, keep the current job (no second refresh, as the spec
requires) and set `running = Pull`. The indicator then shows until that refresh ends. Without this, `PullToRefreshBox`
would snap back on release while work is still running, which reads as "nothing happened".

Only `Pull` upgrades. `OnRetry` can only come from the `Empty` or `Error` states, and those are only shown while no
refresh runs, so a running refresh never needs a Retry upgrade. When the job completes, `running` returns to `null`
exactly as `isRunning = false` does today.

### D3. Retry on the empty state reuses `OnRetry`
In the `FeedContent.Empty` branch, pass `actionLabel = stringResource(Res.string.users_retry)` and
`onAction = { onAction(UsersAction.OnRetry) }` to `FullScreenMessage`. The `Retry` trigger with an empty cache
already makes `content` become `Loading` (the shimmer), and D1 keeps the pull indicator off. So this needs no
ViewModel change beyond D1. "Retry" is chosen over a new "Try again" string so it matches the full-screen error state.

## Testing

- **`UsersViewModelTest`:**
  - **Launch with cached users:** `isRefreshing` is false while the gated launch refresh runs.
  - **First launch:** the cache empty to start with, users arrive while the launch refresh is still gated, `content` is
    `Users` and `isRefreshing` is false.
  - **Pull during the launch refresh:** `isRefreshing` becomes true, `refreshCount` stays 1, and it turns false when
    the gate completes. This replaces the old assertion in `pullWhileRefreshingDoesNotStartAnotherRefresh`.
  - **Pull when idle:** `isRefreshing` is true while running and false after.
  - **Retry from `Empty`:** `content` is `Loading` with `isRefreshing` false, then `Users` (or `Empty` again).
- **`UsersScreenTest` (instrumented):** with `FeedContent.Empty`, the Retry button is shown and dispatches
  `UsersAction.OnRetry`.
- **Previews:** the four existing `UsersScreenEmpty*` previews render the button. No new previews are needed, because
  this adds no new visual state.

## Risks / Trade-offs

- [A user who opens the app and waits gets no visible sign that cached data is being refreshed] → Accepted, and
  intended: the cache is the source of truth, and a failure still shows the banner. If a quiet signal is ever
  wanted, it belongs in a separate change.
- [A pull that lands just as the launch refresh finishes shows the indicator only briefly] → Acceptable. It's the same
  as a fast pull today.
