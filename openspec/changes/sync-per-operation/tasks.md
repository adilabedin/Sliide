# Tasks

## 1. Domain contracts

- [x] 1.1 Add `fun requestSync(userId: Long)` to `UserSyncScheduler` (`:users:domain`) with KDoc (per-user, fire-and-forget, one pending sync per user; iOS may widen it to a full pass) and verify `./gradlew :users:domain:compileKotlinMetadata` succeeds
- [x] 1.2 Add `suspend fun syncPending(userId: Long): Result<SyncReport, DataError.Remote>` to `UserRepository` with KDoc (sends at most that user's entry, same rules as the full pass), verify `:users:domain` compiles
- [x] 1.3 Implement `syncPending(userId)` in `FakeUserRepository` (`users/presentation` `commonTest`) and verify `./gradlew :users:presentation:allTests` passes

## 2. Replay: shared single-entry path

- [x] 2.1 Extract `replayEntry(userId): EntryOutcome` from `sendQueuedWrites()` (lock, re-read, Undo-window skip, send, apply `OutboxPolicy`) with no behaviour change; verify the existing `OfflineFirstUserRepositorySyncTest`, `ConcurrencyTest`, `CrashSafetyTest` and `PolicyParityTest` still pass unchanged
- [x] 2.2 Implement `syncPending(userId)` via `outlivingCaller { replayEntry(userId) }`; add tests for every "Replay can target a single user" scenario (success leaves others queued, 422 create counts as rejected and leaves the cache, 503 keeps entry, Undo-window skip, no entry → no request) and for "Single-user replay during a full replay" (entry sent once); verify `./gradlew :users:data:allTests` passes

## 3. Repository requests a sync when it queues

- [x] 3.1 Add `FakeUserSyncScheduler` in `users/data` `commonTest/fake` recording `requestSync` ids, and inject it through `RepositoryHarness`; verify existing data tests still pass
- [x] 3.2 Give `OfflineFirstUserRepository` a `UserSyncScheduler` constructor parameter; call `requestSync(id)` after the queuing transaction commits in the create `KEEP` branch, `updateUser` (temp-id merge and `KEEP`) and `finalizeDelete` `KEEP` (design D2)
- [x] 3.3 Add `OfflineFirstUserRepositoryWriteTest` cases: requested on offline create (temp id), offline update, offline finalised delete, temp-id edit; NOT requested on 201/200/204, on 422 rejections, on 404 GONE, or when CREATE + DELETE cancel; verify they pass
- [x] 3.4 Pass `get<UserSyncScheduler>()` to the repository in `usersDataModule`, update the `OfflineFirstUserRepositoryIntegrationTest` constructor call, and verify `./gradlew :composeApp:testAndroidHostTest` (`KoinGraphTest`) passes

## 4. Shared background run

- [x] 4.1 Change `BackgroundUserSync.run(userId: Long? = null)` to call `syncPending(userId)` when an id is given and `syncPending()` otherwise; extend `BackgroundUserSyncTest` with the per-user path (success, failure, exception → `Stopped(UNKNOWN)`) and verify it passes

## 5. Android: per-user one-time work

- [x] 5.1 In `WorkManagerUserSyncScheduler`, implement `requestSync(id)`: `OneTimeWorkRequest<UserSyncWorker>` with input `userId`, `CONNECTED` constraint, unique name `sync_user_<id>`, `ExistingWorkPolicy.REPLACE` (design D3); keep the periodic work unchanged
- [x] 5.2 Make `UserSyncWorker` read the optional `userId` input and call `BackgroundUserSync.run(userId)`; it still always returns `success()`
- [x] 5.3 Extend `WorkManagerUserSyncSchedulerTest` (instrumented): one enqueued job per user with the `CONNECTED` constraint; two requests for one user leave one pending job; the periodic job is untouched; the worker with a `userId` input sends only that user's entry (test WorkManager + `TestDriver`); verify on the emulator via the android-cli skill

## 6. iOS: request keeps one refresh pending

- [x] 6.1 Implement `BackgroundTaskUserSyncScheduler.requestSync(userId)` as the existing "submit if none pending" step (design D4) and verify `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` succeeds
- [x] 6.2 Run `./gradlew :users:data:iosSimulatorArm64Test` and verify `IosBackgroundSyncRunnerTest` still passes (the iOS run stays a full pass)

## 7. Documentation

- [x] 7.1 Update the README background-sync section: per-user request from the repository on queue, Android `sync_user_<id>` one-time work with `REPLACE`, iOS behaviour, periodic job as safety net; add a row to the platform table; verify every class and work name mentioned exists in the code
- [x] 7.2 Add a short note to the README "how AI accelerated the build" section on how this change was planned and implemented with OpenSpec; verify the README renders and links resolve

## 8. Verification

- [x] 8.1 Run `./gradlew ktlintCheck` and verify no violations
- [x] 8.2 Run `./gradlew build` and `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` and verify both succeed
- [x] 8.3 For every affected module (`:users:domain`, `:users:data`, `:users:presentation`, `:composeApp`, `:androidApp`), run all unit tests and all instrumented tests (`connectedAndroidDeviceTest` / `connectedAndroidTest` on an emulator started through the android-cli skill) and verify all pass, with none skipped or weakened
- [x] 8.4 Manual check on the emulator via android-cli: turn networking off, create a user, background the app, turn networking on, and verify with `adb shell dumpsys jobscheduler` / the feed that the create reaches GoRest without waiting for the periodic run

## Workflow follow-up

- Archive `typed-user-errors` first, then this change (`/opsx:archive`), which merges the deltas into `openspec/specs/background-sync` and `openspec/specs/offline-sync`.
