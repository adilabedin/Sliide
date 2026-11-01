# Design

## Context

- `:core:database` has one Room KMP table, `users` (`UserEntity`: id, name, email, gender, status,
  `createdAtEpochMillis`), and `UserDao` (`observeAll`, `upsert`, `deleteById`). `SliideDatabase` is at version 1.
- `:users:domain` has `UserRepository` with only `observeUsers()`, and `DataError` with only a `Local` family.
- `:users:data` has `RoomUserRepository`, which is read-only, plus `FakeUserDao` and the mapper tests.
- There is no network layer yet. GoRest assigns user ids itself, does not return timestamps, and its ids are positive.
- See proposal.md for the motivation, and `specs/user-cache` and `specs/offline-sync` for the behaviour contract.

## Goals / Non-Goals

**Goals:**

- A durable outbox with deterministic merge rules that are testable in plain `commonTest`. Every read-merge-write
  across both tables is atomic.
- The repository logic depends only on interfaces (`UserRemoteDataSource`, the DAOs, a transaction runner and a
  `Clock`), so it can be unit-tested fully without Room or Ktor.

**Non-Goals:**

- The Ktor implementation of `UserRemoteDataSource`, the bearer token handling, and the DTOs.
- Scheduling background sync. A follow-up change adds a shared `SyncScheduler` interface (a justified platform split)
  backed by Android WorkManager periodic work and iOS `BGTaskScheduler`, roughly every 30 minutes; the OS makes the
  timing approximate. That job calls **only `syncPending()`**, never `refresh()`, so rows never vanish while the user is
  looking at the list. This change only makes `syncPending()` safe to call from a background job (D5, D7).
- Telling the user about rejected replays. A rejected queued write is dropped silently (D7); the user chose this
  over a persisted failure log, which was built and then removed as overcomplicating things.
- Connectivity monitoring.
- Showing a "pending sync" badge in the UI. The schema allows a later `LEFT JOIN`, but `User` does not change here.
- Retry limits or backoff. Only retryable errors are queued, so an entry can't loop forever on a permanent error.

## Decisions

### D1. `pending_sync` schema: one row per user, embedded snapshot, no foreign key

```
@Entity(tableName = "pending_sync", primaryKeys = ["user_id"])
pending_sync
  user_id, user_name, user_email, user_gender, user_status, user_createdAtEpochMillis
                         -- @Embedded(prefix = "user_") UserEntity snapshot; its id column IS the primary key
                         -- (same id space as users.id, so temp ids are negative)
  operation              TEXT     -- PendingOperation: CREATE | UPDATE | DELETE
  queued_at_epoch_millis INTEGER  -- first time this user was queued; kept on merge
```

There is no separate key field. The embedded snapshot's `user_id` column is the key, declared with
`@Entity(primaryKeys = ["user_id"])`. A standalone `user_id` field next to the prefixed embed would create a duplicate
column, which Room rejects.

- Making `user_id` the primary key enforces "at most one entry per user" (spec: offline-sync) in the schema itself.
  *Alternative:* an append-only log with an auto-increment key. Rejected: replaying it would send redundant calls
  (create → update → delete would make three requests for a user that never needs to reach the server), and every
  queued operation would have to be repointed after a temp-id create syncs.
- The snapshot is a full `UserEntity`, embedded. A pending DELETE has to keep the user's data after the `users` row is
  gone, so it can be restored if the delete is permanently rejected. A CREATE or UPDATE needs the payload anyway.
- **No foreign key to `users`**: a pending DELETE outlives its `users` row by design.
- `queued_at` stays the same when a new write merges into an entry, so the user keeps their place in the replay order.
  Ties are broken by `user_id`.
- `PendingOperation` lives in `:core:database`, next to the entity, because core can't see domain types. Room stores
  enums as TEXT by name natively, so no `TypeConverter` is needed.

### D2. Merge rules live in a pure function in `:users:data`

`PendingSyncMerger.merge(existing: PendingSyncEntity?, incoming: PendingSyncEntity): PendingSyncEntity?` returns the
row to upsert, or `null` to delete the existing row:

| existing \ incoming | UPDATE | DELETE |
|---|---|---|
| none | UPDATE | DELETE |
| CREATE | CREATE with the new snapshot | **null** (cancel, no API call) |
| UPDATE | UPDATE with the new snapshot | DELETE |

No write can follow a pending DELETE, because the user is already gone locally, and an incoming CREATE always has a fresh temp id, so it never has an existing row. Neither case is handled; outside the CREATE cases, the latest write simply wins and keeps the original queue time. The table is the decision. A pure
function makes it testable without fakes.

### D3. Temp ids: `min(-1, MIN(id over users ∪ pending_sync) - 1)`

A single DAO query takes the minimum across both tables, so a temp id is never reused. That covers a temp user that is
only in `pending_sync` as well as one only in `users`. After a refresh replaces the cache, no temp ids are left (the
replace only runs when the queue is empty), so the counter resets naturally. *Alternative:* a UUID string key.
Rejected because it would change `users.id` from `Long` and break the domain `User.id` contract.

### D4. Atomic cross-table writes go through `DatabaseTransactionRunner`

`:core:database` exposes `interface DatabaseTransactionRunner { suspend fun <R> inTransaction(block: suspend () -> R): R }`,
implemented with `SliideDatabase.useWriterConnection { it.immediateTransaction { block() } }`. Unit tests use a fake that
just runs `block`. *Alternative:* `@Transaction` methods on one combined DAO. Rejected because that would push the merge
rules (business logic) into `:core:database`.

### D5. Write flow in `OfflineFirstUserRepository`

Concurrency uses **two mechanisms**:

- **`writeMutex`, held per unit of work.** Every user write holds it for the whole write (local step plus one HTTP
  call). The replay takes it **per entry**, not for the whole queue, so a user write waits at most one request even
  while a long background replay runs. This prevents:
  - replaying a CREATE while the user edits the same temp user, which would lose the edit when the row is re-keyed;
  - finalising a delete while that user's create is still in flight.
- **Single-flight `syncPending`** (D7). The lock is never skipped. Background sync waits for it like any other caller.
  The wait is bounded by one request timeout, well inside the iOS ~30-second background budget.

| Operation | Local step (in a transaction) | Remote step | On retryable failure | On permanent failure |
|---|---|---|---|---|
| `createUser(details)` | insert under temp id, `createdAt = clock.now()` | `create` | upsert pending CREATE → `QUEUED` | delete the temp row → `Failure` |
| `updateUser(user)` (id > 0) | snapshot the old row, upsert the new | `update` | merge pending UPDATE → `QUEUED` | restore the old row → `Failure`; on 404 remove the user and its entry → `Failure(NOT_FOUND)` |
| `updateUser(user)` (id < 0) | upsert, then merge into the CREATE | — (spec: no API call) | — | — |
| `removeUserLocally(id)` | delete the row and return the snapshot | — | — | — |
| `restoreUser(user)` | upsert the snapshot | — | — | — |
| `finalizeDelete(user)` (id > 0) | — | `delete` (a 404 counts as success) | merge pending DELETE → `QUEUED` | restore the snapshot → `Failure` |
| `finalizeDelete(user)` (id < 0) | merge DELETE into the CREATE → the entry is dropped | — | — | — |

When `create` succeeds, the **re-key transaction** runs: delete the temp row, insert the server user under its real id
(keeping the local `createdAt`), and delete the pending row for the temp id. Because D1 merges everything into a single
CREATE, nothing else refers to the temp id, so there is nothing to repoint.

**A successful direct call supersedes the queue.** When a direct remote `update` or `delete` for id > 0 succeeds,
the same transaction that applies the result also deletes any pending entry for that user. Without this, an UPDATE
queued earlier would replay later and overwrite the newer server state, or send a PUT to a user that has since been
deleted.

**The CREATE is queued before the request is sent.** `createUser` writes the temp row and its pending CREATE in the
same transaction, before calling `create`. A crash while the request is in flight therefore can't lose the create; it
replays on the next sync. A request that reached the server before the crash could create a duplicate, which is
accepted. A replay can't pick the entry up while the request is in flight, because the write holds `writeMutex`. On
success the re-key removes the entry; on a permanent failure both rows are deleted. On a retryable failure the entry
simply stays queued. *(Recorded during implementation.)*

**Server-id updates and deletes are also queued before the request.** `updateUser` and `finalizeDelete` for a
server-known id apply the local change and upsert the merged pending UPDATE or DELETE in one transaction before the
request, keeping the previous pending entry in memory.
- Success, or 404 on a delete: the entry is deleted.
- Retryable failure: the entry stays.
- Permanent failure: one transaction rolls back the local change and restores the previous entry exactly.

Without this, a cancelled caller or a killed process would leave a local change with no outbox entry, and the next
refresh would silently revert it. *(Review finding, recorded during implementation.)*

**Temp ids only decrease within a process.** The repository issues
`min(userDao.nextTempId(), lastIssuedTempId - 1)`. Otherwise, once -1 had been re-keyed, the next offline create would
get -1 again, and a UI still holding the old `User(-1)` could edit or delete the new user. Keeping this per process is
enough, because a stale UI reference doesn't survive process death. This refines D3. *(Review finding.)*

**Guarding the Undo window.** `removeUserLocally(id)` adds `id` to an in-memory `awaitingFinalize: MutableSet<Long>`,
guarded by the mutex. `restoreUser` and `finalizeDelete` remove it from the set. `syncPending` skips, but keeps, any
entry whose user id is in the set. Without the guard, a pending CREATE for a temp user that is inside its Undo window
could replay and re-key to a server id. `finalizeDelete(-1)` would then find no CREATE to cancel, and the server user
would be orphaned. If the app is killed, the set is lost, and the skipped entry simply replays on the next sync. That
matches the documented behaviour when the app is killed during the Undo window.

Return type: `Result<WriteStatus, DataError>`, where `enum WriteStatus { SYNCED, QUEUED }`. That lets the UI show a
"saved, will sync when online" message (spec: queued writes tell the caller).

### D6. Classifying errors with `DataError.Remote`

`enum class Remote : DataError { NO_INTERNET, REQUEST_TIMEOUT, SERVER_ERROR, TOO_MANY_REQUESTS, UNAUTHORIZED, NOT_FOUND, VALIDATION, SERIALIZATION, UNKNOWN }`
plus `val DataError.Remote.isRetryable` = `NO_INTERNET`, `REQUEST_TIMEOUT`, `SERVER_ERROR` or `TOO_MANY_REQUESTS`
(HTTP 429: GoRest rate limiting is temporary). The token never expires and response bodies are well-formed, so
`UNAUTHORIZED` and `SERIALIZATION` are treated as permanent without special handling. The future Ktor data
source maps exceptions and status codes onto these values. The repository only ever checks `isRetryable` and
`NOT_FOUND`, so it never sees HTTP details.

### D7. Replay (`syncPending`) and `refresh`

- `syncPending()` reads the queue ordered by `queued_at, user_id` and processes the entries one by one under the
  `writeMutex`, which it takes **per entry**:
  - **Skipped** → the user id is in `awaitingFinalize` (D5). The entry stays queued, but the queue counts as not
    empty, so `refresh` won't replace the cache.
  - **Success** → delete the entry. For a CREATE, run the re-key transaction instead.
  - **Retryable failure** → stop. That entry and every later one stay queued. Return `Failure(error)`.
  - **Permanent failure** → in one transaction: delete the entry and roll back its local effect. Nothing is
    recorded; `SyncReport.rejected` counts it. Then continue with the next entry. The effect per operation:
    - CREATE: delete the temp user.
    - UPDATE with 404: delete the user, because it no longer exists on the server.
    - Other UPDATE rejection: keep the local data. The server's state isn't stored, so the next refresh corrects it.
    - DELETE: re-insert the snapshot (a 404 never gets here, because it counts as success).
- The return value is `Result<SyncReport, DataError.Remote>`, where `SyncReport(synced: Int, rejected: Int)`.
- **Single-flight.** The repository holds the `Deferred` of the sync currently running, guarded by its own small lock.
  A concurrent `syncPending()` call, for example the background job together with a pull-to-refresh, awaits that same
  `Deferred` and gets the same result. It never starts a second pass, so no entry is ever sent twice.
  - *Alternative:* the second caller returns at once as "skipped". Rejected because `refresh` would then have nothing
    to act on.
- **Safe to run in the background.** Each entry is removed in its own transaction once its request has finished. If
  the OS kills the background job partway through, the next run resumes from the first entry still queued.
- `refresh()` = `syncPending()`; if that fails, or any entry is still queued (for example one that was skipped),
  return without replacing the cache. Otherwise call `fetchLastPage()`. If the fetched
  `(id, name, email, gender, status)` set differs from the cached one, run `UserDao.replaceAll(...)`, which is a
  `@Transaction` delete-all then insert. Users already cached keep their `createdAt`; new ones get `clock.now()`.
- `refresh()` also skips the replacement while any user is in `awaitingFinalize`, even one with no pending entry.
  Otherwise the removed user would reappear while the Undo Snackbar is still showing. *(Recorded during
  implementation.)*
- **Why sync first:** replacing the cache while entries are pending would bring back users with a pending delete and
  drop unsynced creates. *Alternative:* replace, then re-apply the pending entries on top. Rejected as more complex and
  harder to test, and the user chose sync-first.

### D8. Domain contracts

`:users:domain` gets:

- `UserDetails(name, email, gender, status)`: the create and update input.
- `RemoteUser(id, details)`: what the server returns. It has no timestamp.
- `UserRemoteDataSource`:

  ```kotlin
  suspend fun fetchLastPage(): Result<List<RemoteUser>, DataError.Remote>
  suspend fun create(details: UserDetails): Result<RemoteUser, DataError.Remote>
  suspend fun update(id: Long, details: UserDetails): Result<RemoteUser, DataError.Remote>
  suspend fun delete(id: Long): EmptyResult<DataError.Remote>
  ```

- The expanded `UserRepository`.

Putting the remote interface in domain was the user's call. It contains no HTTP or DTO types, so domain stays pure.

### D9. Until Ktor lands: `UnavailableUserRemoteDataSource`

`:users:data` ships a stub whose methods all return `Failure(NO_INTERNET)`, bound in `usersDataModule`. With it the
app runs fully offline: every write is queued and nothing is lost. The Ktor change replaces the binding. This is not
`expect`/`actual`; no platform-specific code is added in this change.

### D10. Feed order tie-break

`replaceAll` gives a whole page the same `createdAt`, so `observeAll` is ordered by `createdAtEpochMillis DESC, id DESC`
to keep the order deterministic.

## Risks / Trade-offs

- [The app is killed during the Undo window, before `finalizeDelete`] → The user is gone locally but still exists on
  the server, and the next refresh brings them back. That's safe (no unintended delete) and documented in
  `specs/user-cache`.
- [A permanently rejected replayed UPDATE leaves stale local data until the next refresh] → Accepted. Storing the
  server baseline would double the snapshot columns for a rare case.
- [A user write waits behind one in-flight replay request] → The wait is bounded by one request timeout (enforced by
  the future Ktor client), because the replay releases the lock between entries.
- [A rejected replay is dropped silently, e.g. a user created offline with a taken email just disappears] → Accepted
  by the user as a minor inconvenience. Direct writes still return their error to the screen.
- [A refresh drops users created in the app once they sync, because they aren't on the last page] → This is the
  intended product behaviour, chosen by the user and stated in `specs/user-cache`.
- [Equal timestamps after `replaceAll` make every refreshed user show "just now"] → An inherent limit, because GoRest
  returns no timestamps. Users that were already cached keep their original `createdAt`.

- [A caller never calls `restoreUser` or `finalizeDelete` after `removeUserLocally`, for example because the
  ViewModel was cleared] → That user stays in `awaitingFinalize` for the life of the process: replay skips it and
  `refresh` never replaces the cache. Documented as a contract in the `UserRepository` KDoc. The presentation change
  MUST call `finalizeDelete` from a scope that outlives the screen, such as an application scope, and never from
  `viewModelScope`.

## Migration Plan

The app has not shipped, so `SliideDatabase` stays at **version 1** and only adds the new entity. There is no Room
migration and no destructive fallback. Rollback means reverting the change.
