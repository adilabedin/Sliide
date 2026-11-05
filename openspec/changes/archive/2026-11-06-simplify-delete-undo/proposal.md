# Proposal

## Why

The Undo window is spread across an app-wide `DeleteUndoController` and a three-call repository contract
(`removeUserLocally` → exactly one of `restoreUser` / `finalizeDelete`). Breaking that contract blocks replay and
refresh for the rest of the process, so every caller has to be very careful. The behaviour the user sees (card gone at
once, 5 s to Undo, then delete) does not need any of that: the cache can stay untouched until the window ends, with the
feed hiding the card in the meantime.

## What Changes

- Confirming a delete, from the feed or the profile, hides the user in the feed's state and starts a 5-second job in
  the users feed. Undo cancels the job and shows the card again. When the job ends, the delete runs. Swiping the
  snackbar away, or confirming a second delete, runs it at once.
- The cache is not touched during the window. The delete is one repository call that removes the user locally, then
  sends the DELETE or queues it, like create and update already do.
- The profile no longer starts the delete itself. Confirming closes the profile and hands the user to the feed.
- **BREAKING (internal API)**: `UserRepository.removeUserLocally`, `restoreUser` and `finalizeDelete` are replaced
  by `deleteUser`. The repository's Undo-window bookkeeping goes: replay no longer skips users and refresh is no
  longer blocked by an open window.
- `DeleteUndoController` and its Koin `single` are removed.
- If the feed's ViewModel is cleared during the window (the app is closed), the delete is sent at once in the app-wide
  scope. Backgrounding, rotation and opening other screens still let the window finish. Only process death drops it,
  and then the cache was never changed, so the user simply stays.
- A user created offline whose CREATE syncs during the window is still deleted on the server, under its new server id.
- **BREAKING (internal API)**: `SyncReport` is removed. Nothing in the app read its synced / rejected counts, so both
  `syncPending` overloads return `EmptyResult<DataError.Remote>`: failure is the first kept entry's error, success
  otherwise. A rejected replay still only drops its entry.

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `user-deletion`: the card is hidden rather than removed from the cache during the window; Undo just shows it again;
  closing the app during the window finalises the delete at once; the profile hands its delete to the feed.
- `user-cache`: "Delete is local until finalised" (remove / restore) is replaced by "Deletes apply locally first".
- `offline-sync`: replay no longer skips users inside an Undo window; a delete of a temp user that was re-keyed during
  the window targets the server id.
  A sync no longer reports how many entries were synced or rejected.
- `operation-lifetime`: the list of repository operations drops local remove and restore, and "finalise delete"
  becomes "delete".

## Impact

- `:users:domain`: `UserRepository` delete API; `syncPending` return type; `SyncReport` deleted.
- `:users:data`: `OfflineFirstUserRepository` (delete, replay, refresh, temp-id tracking, sync result),
  `BackgroundUserSync` (`BackgroundSyncOutcome.Synced` carries no report) and their tests; the fake repository used by
  presentation tests.
- `:core:domain`: an `ApplicationScope` type replaces the `ApplicationScope` Koin qualifier in `:core:data`, so
  presentation can inject the app-wide scope without depending on data.
- `:users:presentation`: `UsersViewModel` (owns the window), `ProfileViewModel` / `ProfileEvent` (hand-off),
  `UsersListDetailRoot` (forwards the hand-off), DI module; `DeleteUndoController` and its test deleted;
  ViewModel tests rewritten for the job-based window.
- No new dependencies. No UI change apart from the profile hand-off.
