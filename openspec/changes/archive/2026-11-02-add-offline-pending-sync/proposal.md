# Proposal

## Why

The app is meant to work offline first, with Room as the single source of truth for the UI. Right now the database only
caches users. If a create, update or delete is made without internet, or its API call fails for a transient reason, it
is lost. We need a durable outbox that holds those writes and replays them once the network is back. The users table
also needs a clear refresh policy so that a replayed or still-pending write is never overwritten.

## What Changes

- Add a **`pending_sync` table** in `:core:database`. Each row is a snapshot of the user's data plus a
  `PendingOperation` enum (`CREATE`, `UPDATE`, `DELETE`). The table holds **one row per user**, and new writes are
  merged into an existing row (for example, a create followed by a delete cancels both). The `users` table keeps
  holding every cached user and reflects optimistic local state.
- Offline-created users get a **temporary negative local id**. When their queued `CREATE` succeeds, the row is
  re-keyed to the server id in a single transaction. Later writes to a never-synced user are merged into its pending
  `CREATE`, so nothing else refers to the temp id.
- Expand the domain **`UserRepository` interface** with `createUser`, `updateUser`, `removeUserLocally`, `restoreUser`,
  `finalizeDelete`, `syncPending` and `refresh`, and add a domain **`UserRemoteDataSource` interface** as the network boundary. The Ktor
  implementation is out of scope and comes in a later change.
- The repository implementation in `:users:data` writes to Room first. It then calls the remote data source and queues
  the operation **only on retryable failures**: no internet, timeout, a 5xx response or 429 Too Many Requests. Any
  other failure is returned to the caller and never queued. A 404 on `DELETE` counts as success.
- **Delete with Undo** is local first. Confirming a delete calls `removeUserLocally`, which removes the user from the `users` table.
  `restoreUser` (Undo) puts the row back without any network call. The remote `DELETE` is sent only when the
  presentation layer calls `finalizeDelete` after the Snackbar is dismissed. If that call fails for a retryable
  reason, the `DELETE` is queued.
- **Refresh runs sync first.** `refresh` replays the pending queue first. Only when the queue is empty does it fetch the
  last page of `/users`. If that page differs from the cache, the whole `users` table is replaced. If the replay fails,
  the cache is left untouched.
- **Rejected replays are dropped.** When a queued write gets a non-retryable error on replay, its entry is deleted
  and its local effect is reverted. A 404 on an update removes the user, because it no longer exists. Nothing is
  recorded, and `SyncReport.rejected` counts the dropped writes. A user created offline whose email turns out to be
  taken simply disappears from the list, which is an accepted minor inconvenience.
- **Sync is safe for background use.** The lock is taken per entry, a second sync joins the one already running
  instead of starting another, and an interrupted sync resumes cleanly. The 30-minute WorkManager / `BGTaskScheduler`
  job, which only replays the queue, comes in a follow-up change.
- Extend `DataError` with a `Remote` error family so that callers can tell retryable failures from permanent ones.

## Capabilities

### New Capabilities

- `user-cache`: the local `users` table as the UI's single source of truth. Covers optimistic writes, temporary ids
  for offline creates, local delete and restore for Undo, and replacing the table from the last page once the queue is
  empty.
- `offline-sync`: the `pending_sync` outbox. Covers which failures get queued, the merge rules for several writes to
  one user, replay order and outcome handling, and re-keying a temp id to the server id after a successful create.

### Modified Capabilities

<!-- None: openspec/specs/ has no existing capabilities. -->

## Impact

- **`:core:database`**: new `PendingSyncEntity`, `PendingOperation` enum (stored natively as TEXT), `PendingSyncDao`,
  `DatabaseTransactionRunner`, and new `UserDao` queries (`replaceAll`, `getById`, min temp-id lookup). `SliideDatabase` gains the
  new entity and stays at **version 1**, because the app has not shipped and needs no migration.
- **`:users:domain`**: `UserRepository` grows its write and sync methods. New files: `UserRemoteDataSource`, the
  `UserDetails` / `RemoteUser` / `WriteStatus` models and `DataError.Remote`.
- **`:users:data`**: `RoomUserRepository` becomes `OfflineFirstUserRepository`, plus new mappers and Koin bindings. The
  repository needs a `UserRemoteDataSource` binding before the app can run. Until the Ktor change lands, a stub that
  always reports no internet is bound (design.md D9).
- **Tests**: `FakeUserDao` and `FakeUserRepository` must implement the new members. Add new unit tests in
  `:users:data` and extend the `UserDaoTest` / `PendingSyncDaoTest` instrumented tests in `:core:database`.
- **Out of scope**: the Ktor GoRest client, the edit UI and add-user form, the Snackbar and Undo UI, and automatic
  replay on connectivity changes (we only expose `syncPending()` and `refresh()`).
