# Tasks

## 1. Database schema (`:core:database`)

- [x] 1.1 Add `PendingOperation` enum (`CREATE`, `UPDATE`, `DELETE`) and `PendingSyncEntity` (`@Entity(primaryKeys = ["user_id"])` where `user_id` comes from the `@Embedded(prefix = "user_") UserEntity` snapshot, with no separate key field; plus `operation`, `queued_at_epoch_millis`, no foreign key) per design D1; register it in `SliideDatabase` (still version 1). Verify: `./gradlew :core:database:build` succeeds.
- [x] 1.2 Add `PendingSyncDao`: `getAllOrdered()` (by `queued_at`, `user_id`), `getByUserId(id)`, `upsert(entity)`, `deleteByUserId(id)`, `count()`. Verify: compiles and the KSP-generated code builds for Android and iOS targets.
- [x] 1.3 Extend `UserDao`: `getById(id)`, `@Transaction replaceAll(users)`, `nextTempId()` (design D3: `min(-1, MIN(users.id ∪ pending_sync.user_id) - 1)`), and change `observeAll` ordering to `createdAtEpochMillis DESC, id DESC` (D10). Verify: compiles.
- [x] 1.4 Add `DatabaseTransactionRunner` interface + `RoomDatabaseTransactionRunner` (`useWriterConnection { immediateTransaction { } }`) and bind both DAOs and the runner in `databaseModule` (D4). Verify: Koin module compiles; `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` succeeds.
- [x] 1.5 Instrumented tests (android-integration-testing, in-memory Room): extend `UserDaoTest` (replaceAll swaps the whole table, tie-break ordering, `nextTempId` returns -1 on empty DB and stays below the min across both tables) and add `PendingSyncDaoTest` (upsert replaces by user id, ordering by queued_at then user_id, snapshot round-trips, entry survives with no matching `users` row, rollback of `RoomDatabaseTransactionRunner` when the block throws). Verify: `./gradlew :core:database:connectedAndroidDeviceTest` passes on an emulator started via android-cli.

## 2. Domain contracts (`:users:domain`)

- [x] 2.1 Add `DataError.Remote` (`NO_INTERNET`, `REQUEST_TIMEOUT`, `SERVER_ERROR`, `TOO_MANY_REQUESTS`, `UNAUTHORIZED`, `NOT_FOUND`, `VALIDATION`, `SERIALIZATION`, `UNKNOWN`) and `isRetryable` (D6), with a `commonTest` covering every enum value of `isRetryable`. Verify: `./gradlew :users:domain:allTests` passes.
- [x] 2.2 Add `UserDetails`, `RemoteUser`, `WriteStatus`, `SyncReport` models and the `UserRemoteDataSource` interface (D8). Verify: compiles.
- [x] 2.3 Expand `UserRepository` with `createUser`, `updateUser`, `removeUserLocally`, `restoreUser`, `finalizeDelete`, `syncPending`, `refresh` (KDoc each with its sync semantics), and update `FakeUserRepository` in `:users:presentation` commonTest to implement them. Verify: `./gradlew :users:presentation:allTests` still passes.

## 3. Merge rules and mappers (`:users:data`)

- [x] 3.1 Implement `PendingSyncMerger.merge(existing, incoming)` exactly per the D2 table. Unit-test every cell of the table (android-unit-testing, AssertK), including that merges keep the original `queuedAt`. Verify: `./gradlew :users:data:allTests` passes.
- [x] 3.2 Add mappers `UserDetails`/`RemoteUser` ↔ `UserEntity`, `UserEntity` ↔ `PendingSyncEntity`, with mapper unit tests (including keeping the local `createdAt` when mapping a `RemoteUser` onto an existing entity). Verify: `./gradlew :users:data:allTests` passes.

## 4. Offline-first repository (`:users:data`)

- [x] 4.1 Test doubles: update `FakeUserDao` for the new members, add `FakePendingSyncDao`, `FakeDatabaseTransactionRunner` (runs the block) and `FakeUserRemoteDataSource` (scripted results per call, records requests). Verify: commonTest compiles.
- [x] 4.2 Replace `RoomUserRepository` with `OfflineFirstUserRepository` (injected DAOs, runner, remote, `Clock`, `writeMutex` per D5): implement `createUser`, `updateUser`, `removeUserLocally`, `restoreUser`, `finalizeDelete` per the D5 table, including the re-key transaction, a successful direct write deleting the user's pending entry, and the `awaitingFinalize` Undo-window guard. Unit-test every scenario of specs `user-cache` (immediate create, temp ids, local delete/undo with zero remote calls, 4xx rollback) and `offline-sync` (queue only retryable errors, 401/422 not queued, 404 on delete = success, temp-id writes make no API call, create-then-delete cancels, online update/delete after a queued UPDATE leaves no pending entry). Verify: `./gradlew :users:data:allTests` passes.
- [x] 4.3 Implement `syncPending` (per-entry `writeMutex`, single-flight shared `Deferred`, permanent failure deletes the entry and rolls back in one transaction) and `refresh` per D7. Unit-test: oldest-first order, stop at the first retryable failure leaving later entries, permanent failure drops + rolls back + continues, replayed CREATE re-keys (then a finalised delete targets the server id), refresh does not fetch when the replay fails, refresh replaces only when the page differs, existing users keep `createdAt`, entries for users inside the Undo window are skipped but kept (and block replacement), a temp-id user removed during replay then finalised sends no request, two concurrent `syncPending` calls send each entry once and return the same report, a user write issued mid-replay runs before the next entry (gated fake remote), a rejected replay deletes its entry and is counted in `SyncReport.rejected`. Verify: `./gradlew :users:data:allTests` passes.
- [x] 4.4 Add `UnavailableUserRemoteDataSource` (always `NO_INTERNET`, D9) and update `usersDataModule` bindings (repository, remote stub, `Clock.System`). Verify: Koin graph compiles; `./gradlew :composeApp:build` succeeds.
- [x] 4.5 Integration test in `:users:data` androidDeviceTest (android-integration-testing): real `OfflineFirstUserRepository` + in-memory Room + real transaction runner + fake remote — offline create queues and survives DB reopen, re-key after sync leaves exactly one row under the server id, a rejected replay deletes its entry and temp user durably across DB reopen. Verify: `./gradlew :users:data:connectedAndroidDeviceTest` passes on the emulator (add the `androidDeviceTest` source set via the convention plugin if the module lacks it).

## 5. Docs

- [x] 5.1 Update README.md's architecture section with the offline-first outbox (users cache + `pending_sync`, merge table, sync-then-refresh rule, retryable-only queuing, per-entry locking + single-flight sync, keep-vs-delete error table). Verify: README renders and matches design.md D1–D11.

## 6. Simplify rejection handling (user decision after review)

- [x] 6.1 Remove the `sync_failures` feature end to end: `SyncFailureEntity`/`SyncFailureDao` and their registration/binding/test in `:core:database`; `SyncFailure`, `SyncOperation`, `observeSyncFailures`, `dismissSyncFailure` in `:users:domain`; failure mappers, fakes and assertions in `:users:data`; failure members in the presentation `FakeUserRepository`; the README section. Verify: grep finds no `SyncFailure`/`sync_failures`/`SyncOperation` outside openspec/, and every module compiles.
- [x] 6.2 Add `DataError.Remote.TOO_MANY_REQUESTS` as retryable (D6), and make an UPDATE rejected with 404 remove the user and its entry, both on replay and on a direct write (D5, D7). Rewrite the rejected-replay tests to assert entry deletion, local effect and `SyncReport.rejected`; add tests for 429 on replay (kept, replay stops), replayed UPDATE 404 and direct UPDATE 404; replace integration test #3. Verify: `./gradlew :users:domain:allTests :users:data:testAndroidHostTest` passes.

## 7. Verification gate

- [x] 7.1 Run `./gradlew ktlintCheck`. Verify: no violations (fix with `ktlintFormat`, never a baseline).
- [x] 7.2 Run `./gradlew build` (all modules compile, all shared unit tests pass) and `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64`. Verify: both succeed with no disabled or weakened tests.
- [x] 7.3 For every affected module (`:core:database`, `:users:domain`, `:users:data`, `:users:presentation`), run all unit tests (`allTests` / `testAndroidHostTest`) AND all instrumented tests (`connectedAndroidDeviceTest`) on an emulator managed via android-cli. Verify: every run passes.
