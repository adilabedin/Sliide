# Design

## Context

See proposal.md (Why). Current shape, from `openspec/changes/archive/2026-11-02-add-background-sync/design.md`:

- `UserSyncScheduler` (`:users:domain`) has one method, `schedulePeriodicSync()`, called once from `initKoin()`.
  Android binds `WorkManagerUserSyncScheduler` (unique periodic work, `KEEP`, `CONNECTED`); iOS binds
  `BackgroundTaskUserSyncScheduler` (one `BGAppRefreshTaskRequest` pending at a time).
- `BackgroundUserSync` (`commonMain`) is the shared run; `UserSyncWorker` and `IosBackgroundSyncRunner` adapt the OS
  callback to it. Runs never retry (archived D4).
- `OfflineFirstUserRepository` queues a write in three places: the create `KEEP` branch (direct
  `pendingSyncDao.upsert`), and `enqueue(...)` from `updateUser` (temp-id merge and `KEEP`) and `finalizeDelete`
  (`KEEP`). `enqueue` merges through `PendingSyncMerger` and may delete the entry instead (CREATE + DELETE).
- `sendQueuedWrites()` loops over `getAllOrdered()`, takes `writeMutex` per entry, re-reads the entry under the lock,
  skips Undo-window users, and on a `KEEP` failure records the first error and **continues** (as specified by the
  unarchived `typed-user-errors` change). This change keeps that behaviour.
- Every repository operation runs in `applicationScope` and the caller only awaits it (`outlivingCaller`), so
  cancelling a caller never cancels a send.

## Goals / Non-Goals

**Goals:**
- The repository is the one place that decides a write needs syncing, and asks for it there.
- One code path sends a single queued entry; both the full pass and the per-user sync use it.

**Non-Goals:**
- Retry or backoff for background work (archived D4 stays).
- Foreground "replay when connectivity returns" while the app is open (WorkManager covers Android; iOS keeps the
  periodic request and the foreground refresh).
- Changing the full pass's continue-past-failures rule.
- Abstracting `BackgroundUserSync` behind an interface.

## Decisions

### D1. `UserSyncScheduler.requestSync(userId: Long)` in the domain interface

The repository depends on the domain `UserSyncScheduler` and calls `requestSync(userId)` after it queues a write.
Data already implements and consumes domain interfaces, so this adds no new module edge. The method is
non-suspending and fire-and-forget like `schedulePeriodicSync()`; both platform calls only enqueue with the OS.

Alternatives: an observer on the pending table (hard to find from the repository); having the ViewModel
request syncs from `WriteStatus.QUEUED` (presentation would own a data concern, and background replays that re-queue
would never request one).

### D2. Request after every write that leaves a pending entry

Rule: when a write path finishes with a pending entry for the user, call `requestSync(id)`; when it finishes with none
(success, rejection, CREATE + DELETE cancelled by the merger), do not. Concretely:
- `createUser` `KEEP` → request for the temp id.
- `updateUser` `KEEP` and `finalizeDelete` `KEEP` always request: their merge always leaves an entry (the only
  cancelling merge, CREATE + DELETE, is for a temp id, which `finalizeDelete` handles before queuing).
- `updateUser` on a temp id requests only when it merged into a pending CREATE (a temp user whose create was lost
  is edited locally only).

The call is made **after** the transaction commits (never inside `inTransaction`), still inside `writeMutex`. A
re-request for a user that already has one pending is harmless (D3/D4 keep it to one).

Alternative: request only on the first enqueue for a user. Rejected: a running Android job could finish just before
the merge and the new data would wait for the periodic run.

### D3. Android: one unique one-time work per user, `REPLACE`, `CONNECTED`, reusing `UserSyncWorker`

`requestSync(id)` enqueues `OneTimeWorkRequest<UserSyncWorker>` with input `userId = id`, a `CONNECTED` constraint and
no backoff, as unique work `sync_user_<id>` with `ExistingWorkPolicy.REPLACE`.

- **Why `REPLACE`, not `KEEP`:** with `KEEP`, a write merged while that user's job is already running would get no new
  job and wait for the periodic run. `REPLACE` cancels the running job's *await* only; the send itself runs in
  `applicationScope` (operation-lifetime) and completes, and the replacement re-reads the entry and sends whatever is
  left. It still leaves exactly one pending job per user (spec: two writes, one sync).
- **Why per user, not one shared "sync now" job:** a failing entry cannot stop another user's job (a slow one delays it by at most one request, see Risks), and a
  job's outcome names the user it was for.
- `UserSyncWorker` reads the optional `userId`: present → `BackgroundUserSync.run(userId)`, absent → full pass (the
  periodic job). One worker class keeps the Koin `workerOf` wiring as it is.
- The periodic job (`sync_pending_users`, `KEEP`) is unchanged.

Alternative: `APPEND_OR_REPLACE` (chains jobs; two pending jobs for one user, the second usually a no-op). Rejected
for the extra jobs and because it breaks "one pending sync per user".

### D4. iOS: `requestSync` makes sure one refresh request is pending

`BGTaskScheduler` has no per-item or "when online" task, and only one request per identifier can be pending.
`BackgroundTaskUserSyncScheduler.requestSync(userId)` ignores the id and runs the existing "submit if none pending"
step. The run stays a full pass, which covers every queued user. This is a platform difference, not a new
`expect`/`actual`: it lives inside the existing iOS binding of a common interface.

### D5. Repository: `syncPending(userId)` and a shared `replayEntry(userId)`

- Extract the body of the current loop iteration into `private suspend fun replayEntry(userId): EntryOutcome`
  (`Synced`, `Rejected`, `Kept(error)`, `Skipped`). It takes `writeMutex`, re-reads the entry, skips Undo-window users,
  sends, and applies `OutboxPolicy` exactly as today.
- `sendQueuedWrites()` loops over `getAllOrdered()` calling `replayEntry`, with the same counting and first-kept-error
  result as today.
- New `override suspend fun syncPending(userId: Long): Result<SyncReport, DataError.Remote>` runs
  `outlivingCaller { replayEntry(userId) }` and maps the outcome to the same result type as the full pass.
- **No separate single-flight for per-user syncs.** Duplicate sends are prevented the same way the full pass already
  prevents them against user writes: `writeMutex` + re-read of the entry under the lock. Whichever replay reaches the
  entry first sends it; the other finds it gone (spec: single-user replay during a full replay). Full passes keep
  their shared `Deferred`.

Alternative: make a per-user sync await an in-flight full pass. Rejected: it reintroduces the "one slow item holds
everything up" behaviour for the per-user path.

### D6. `BackgroundUserSync.run(userId: Long? = null)`

`null` → `repository.syncPending()`; an id → `repository.syncPending(userId)`. Same outcome type and exception
handling. iOS keeps calling `run()`.

### D7. Wiring and test doubles

- `usersDataModule` passes `get<UserSyncScheduler>()` to `OfflineFirstUserRepository`. The scheduler is bound in
  `platformUserSyncModule`, already part of `appModules`, so `KoinGraphTest` keeps resolving.
- New `FakeUserSyncScheduler` in `users/data` `commonTest/fake` records `requestSync` ids and periodic calls;
  `RepositoryHarness` injects it.
- `FakeUserRepository` (`users/presentation` `commonTest`) implements `syncPending(userId)`.

## Risks / Trade-offs

- [Many offline writes → many WorkManager jobs] → each is tiny, they share one `CONNECTED` trigger, run serialised by
  `writeMutex`, and finished one-time work is pruned by WorkManager.
- [`REPLACE` cancels a running worker] → the worker only awaits; the send runs in `applicationScope` and completes.
  Covered by a repository test that cancels a running `syncPending(userId)` caller (what `REPLACE` does to the
  worker), starts a new one, and checks the entry is sent once.
- [All replays share `writeMutex`, held across the HTTP call] → a slow request for one user delays the next user's
  sync by up to one request timeout (20 s). Accepted: per-user locks would also have to guard temp-id issuing and the
  Undo-window set, a large step in complexity for a background job; a failure never stops another user's sync.
- [Process dies between the commit and `requestSync`] → the periodic job is the safety net (spec scenario
  "Per-user sync missed").
- [iOS gains nothing on latency] → OS limitation; documented in the README table.

## Migration Plan

No database or API change. Ship as a normal release; rollback is a revert. Existing periodic work keeps its unique
name and `KEEP` policy, so installed apps keep their schedule across the update.
