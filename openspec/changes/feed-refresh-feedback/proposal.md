# Proposal

## Why

The pull-to-refresh spinner appears when the feed opens even though the user never pulled. `UsersViewModel` starts a
refresh on launch, and the indicator is driven by "a refresh is running and there are cards", whoever started it. On
every launch after the first, the spinner sits over the cached list. On the first launch, it pops up over the newly
arrived cards for the last moment of the refresh. The shimmer already tells the user that the first load is running,
so the spinner is noise. Separately, the "No users yet" empty state is a dead end: the only way to look for users again
is to restart the app or pull down, and the empty state can't be pulled.

## What Changes

- The pull-to-refresh indicator shows only for a refresh the user started by pulling:
  - The automatic launch refresh no longer shows it. An empty cache still shows the shimmer, and cached users show
    without any indicator.
  - A pull during an in-flight launch refresh shows the indicator until that refresh finishes, still without starting a
    second refresh.
- The "No users yet" empty state gains a Retry button that starts a new refresh. The shimmer shows while it runs, then
  the users appear or the empty state returns.

## Capabilities

### New Capabilities
<!-- None -->

### Modified Capabilities
- `user-feed`: "Pull to refresh" now says the indicator is shown only for user-initiated pulls, never for the
  automatic launch refresh, and adds a scenario for a pull during the launch refresh. "Empty state" gains a Retry
  action that starts a new refresh.

## Impact

- `:users:presentation`:
  - `UsersViewModel` tracks what started the running refresh, and `isRefreshing` is derived from that.
  - `UsersScreen` passes the existing Retry label and `UsersAction.OnRetry` to the empty state's `FullScreenMessage`.
  - The existing empty-state previews pick up the button.
- Tests: `UsersViewModelTest` (one test whose assertion encodes the old behaviour is rewritten, and new ones are added),
  and one `UsersScreenTest` case for the empty-state Retry button.
- No changes to domain, data, the design system, strings or public API. `FullScreenMessage` already supports an action,
  and the "Retry" string exists.
