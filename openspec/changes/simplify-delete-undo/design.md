# Design

## Context

Today a confirmed delete calls `DeleteUndoController.request(id)`, an app-wide singleton with its own scope, mutex and
timer. It calls `UserRepository.removeUserLocally`, then exactly one of `restoreUser` / `finalizeDelete`. The
repository tracks open windows in `awaitingFinalize`: replay skips those users and `refresh()` will not replace the
cache while any are open. Both `UsersViewModel` (the snackbar) and `ProfileViewModel` (the profile's Delete button) talk
to the controller. In `UsersListDetailRoot` both ViewModels exist side by side, and the profile's `onClose` already
forwards messages to `UsersViewModel`.

See proposal.md for why this should be simpler.

## Goals / Non-Goals

**Goals:**
- The Undo window is presentation state, owned by one ViewModel, and the cache only ever sees one delete call.
- No repository contract that a caller can break.

**Non-Goals:**
- Finishing a delete after the app is closed or killed during the window (see Risks).
- Changing the dialog, the snackbar UI or its countdown.

## Decisions

### D1. `UsersViewModel` owns the window as a cancellable job
Modelled on the SliideChallenge feed. The ViewModel keeps the open window (`PendingDelete(user, deadline)`, used by the
snackbar's countdown) and a `hiddenIds` set:
- `confirmDelete(id)` finishes any open window first (spec: a second delete finalises the first), hides the user, and
  launches `delay(undoWindow); finishDelete()` in `viewModelScope`.
- `OnUndoDelete` cancels the timer, closes the window and un-hides the user.
- `OnCommitDelete` (snackbar swiped) and the timer call `finishDelete()`, which closes the window and launches
  `deleteUser` in the injected `ApplicationScope`. The id leaves `hiddenIds` only after the call returns, so the card
  doesn't flash back before the cache drops it.
- `onCleared()` calls `finishDelete()`, so a delete still waiting for Undo is sent when the feed goes for good.

Undo and swipe act on the open window, without an id. The snackbar effect is keyed by the pending user, so a snackbar
for an older window is cancelled before it can report a result. Everything runs on the main dispatcher, so no mutex.

`UsersViewModel` is scoped to the feed's back-stack entry, which lives under the profile, the add-user screen, rotation
and backgrounding.

*Alternative:* keep an app-scoped holder (the old controller). Rejected: `onCleared` plus the app scope covers the
same cases with a few lines.

### D2. Hidden, not removed
`buildState` drops the hidden ids from the cards. The cache is untouched, so a refresh during the window cannot clash
with it, and Undo just un-hides the user (the card animates back from the cache's data).

Because the cache is untouched, the cache no longer closes an open profile on its own. So confirming a delete for the
chosen user also clears the selection (`chosenUserId = null`, as `OnSelectionChange(null)` does). The detail pane then
falls back to the default first visible user, or closes on one pane, through the existing dismiss path.

The app-wide scope is injected as an `ApplicationScope` type from `:core:domain`, which replaces the Koin qualifier in
`:core:data`. Presentation may not depend on data, and a type needs no qualifier.

### D3. The profile hands the delete to the feed
`ProfileViewModel.confirmDelete()` closes the profile and emits `ProfileEvent.DeleteConfirmed(userId)`. It no longer
calls any delete API. `UsersListDetailRoot` forwards it as `UsersAction.OnConfirmDelete(userId)`. The feed resolves the
id against its last cached list; if the user is already gone, nothing happens.

The dialog's confirm in the feed goes through the same path (`OnDeleteConfirm` → `confirmDelete(id)`).

### D4. One repository call: `deleteUser(user: User): Result<WriteStatus, DataError>`
This is the body of today's `finalizeDelete`, renamed and run through `outlivingCaller`. It deletes the row locally,
sends the DELETE (404 counts as success), queues it on a kept failure, and drops the pending entry on a rejection. A
temp-id user only has its row and pending CREATE removed. It takes the `User` snapshot rather than an id, so a user that
a refresh removed from the cache during the window still gets its DELETE sent.

`removeUserLocally`, `restoreUser`, `awaitingFinalize`, the replay skip and the refresh guard on open windows are
deleted.

### D5. Temp ids re-keyed during the window
`rekey` records `tempId → serverId` in an in-memory map guarded by `writeMutex`, like `lastIssuedTempId`, and kept for
the process lifetime. `deleteUser` resolves a temp id through it first: if the CREATE has synced, the delete continues
as a server-known delete of the new id. Without the map, deleting `-1` after the re-key would find no CREATE to cancel
and leave user 7012 on the server.

### D6. Finalise outcomes reach the feed directly
The finalise job already runs in `UsersViewModel`, so its result maps straight to `UsersEvent.ShowMessage`: rejected →
"refused" / "couldn't delete", `QUEUED_AUTH_FAILED` → auth message, `SYNCED` / `QUEUED` → nothing. `DeleteMessage` and
the controller's channel go.

## Risks / Trade-offs

- [The process is killed within 5 s of a confirm] → The delete is never sent and the user stays. The cache was never
  changed, so nothing is left inconsistent. A normal close goes through `onCleared` and does send it.
- [The ViewModel is cleared while `deleteUser` is in flight] → It runs in `ApplicationScope` and the repository's
  `outlivingCaller`, so it completes or is queued; only the failure message has nobody to show it.
- [A temp-id user whose CREATE syncs during its window] → The row is re-keyed to its server id, which is not in
  `hiddenIds`, so its card reappears for the rest of the window. The delete still reaches the server id (D5). This is
  rare, and accepted rather than matching cards by email.
- [The rekey map grows for the process lifetime] → One entry per offline create that synced. This is negligible.

## Migration Plan

This is internal only; there is no data migration. Room's schema is unchanged. Rollback means reverting the commit.
