# Tasks

## 1. Dependencies

- [x] 1.1 Add the following to `gradle/libs.versions.toml` with the `android-version-catalog` skill:
  - `androidx-work-runtime` (`androidx.work:work-runtime`) and `androidx-work-testing` (`androidx.work:work-testing`),
    sharing one `androidx-work` version ref. Look up the latest stable version with the android-cli / version-catalog
    skill; don't guess it.
  - `koin-androidx-workmanager` (`io.insert-koin:koin-androidx-workmanager`), versioned by the Koin BOM.

  Wire them in:
  - `:users:data` `androidMain`: work-runtime and koin-androidx-workmanager.
  - `:users:data` `androidDeviceTest`: work-testing.
  - `:androidApp`: koin-androidx-workmanager, for `workManagerFactory()`.

  Verify: `./gradlew :users:data:compileAndroidMain :androidApp:assembleDebug` succeeds.

## 2. Shared contract and run logic (commonMain)

- [x] 2.1 Add `UserSyncScheduler` (`schedulePeriodicSync()`, documented as idempotent) to `:users:domain`
  (design D1). Verify: `./gradlew :users:domain:build` passes.
- [x] 2.2 Add `BackgroundUserSync` and `BackgroundSyncOutcome` (`Synced(report)` / `Stopped(error)`) to `:users:data`
  `commonMain`, under a `sync` package (design D2). The rules:
  - it calls only `syncPending()`;
  - `CancellationException` is rethrown;
  - any other throwable becomes `Stopped(UNKNOWN)`.

  Add `BackgroundUserSyncTest` in `commonTest` with the `android-unit-testing` skill. It uses the real
  `OfflineFirstUserRepository` from `RepositoryHarness`, with fakes. Cover:
  - an empty queue sends no API request;
  - a queued update is sent once and its entry removed → `Synced(1, 0)`;
  - a retryable failure → `Stopped(error)`, and the entry stays queued;
  - a permanent rejection is dropped and counted, and the run continues;
  - `fetchLastPage` is never called and the cache isn't replaced, even when the server list differs;
  - a run that overlaps with `refresh()` sends each entry once;
  - cancellation propagates.

  Verify: `./gradlew :users:data:testAndroidHostTest` passes, and so does `:users:data:iosSimulatorArm64Test`.
- [x] 2.3 Declare `expect val platformUserSyncModule: Module` in `:users:data` `commonMain`, with a comment justifying the
  platform split (design D1). Verify: it compiles once both actuals from groups 3 and 4 exist; it is checked in 3.2.

## 3. Android: WorkManager

- [x] 3.1 In `:users:data` `androidMain`, add:
  - a `WorkerResult` typealias;
  - `UserSyncWorker` (a `CoroutineWorker` that calls `BackgroundUserSync.run()` and always returns `success()`;
    design D3/D4);
  - `WorkManagerUserSyncScheduler`, which enqueues unique periodic work `"sync_pending_users"` every 30 minutes with
    `KEEP`, `NetworkType.CONNECTED`, no initial delay and no backoff.

  Follow the `android-background` skill, except that REPLACE becomes KEEP.
- [x] 3.2 Add the `actual val platformUserSyncModule` in `androidMain`: `workerOf(::UserSyncWorker)`, the
  `BackgroundUserSync` factory, and the `WorkManagerUserSyncScheduler` bound to `UserSyncScheduler`. Verify:
  `./gradlew :users:data:compileAndroidMain` succeeds.
- [x] 3.3 In `:users:data` `androidDeviceTest`, add `WorkManagerUserSyncSchedulerTest` with the
  `android-integration-testing` skill (`WorkManagerTestInitHelper`). Cover:
  - one schedule call produces exactly one `ENQUEUED` periodic work item with a 30-minute interval and a `CONNECTED`
    constraint;
  - a second call keeps the same work id (KEEP);
  - a `TestListenableWorkerBuilder` run of `UserSyncWorker`, with a fake repository whose `syncPending` fails with
    `SERVER_ERROR`, returns `success()`, not `retry()`.

  Verify: `./gradlew :users:data:connectedAndroidDeviceTest` passes on an emulator started with the android-cli skill.

## 4. iOS: BGTaskScheduler

- [x] 4.1 In `:users:data` `iosMain`, add `SyncTaskHandle`, `ConnectivityCheck` and `IosBackgroundSyncRunner`
  (design D6). Each run:
  1. re-submits the next request first;
  2. if offline, completes with `true` without syncing;
  3. otherwise runs `BackgroundUserSync` in the application scope and completes with `true`.

  The expiration handler cancels the job and completes with `false`. An atomic guard makes sure `complete` is called
  exactly once.

  Add `IosBackgroundSyncRunnerTest` in `iosTest`, with fakes for the handle, connectivity and repository. Cover:
  - offline: no `syncPending` call, `complete(true)`;
  - online: `syncPending` is called once, then `complete(true)`;
  - re-submit happens before the sync;
  - expiry mid-sync cancels the job and completes with `false` once;
  - a sync result arriving after expiry doesn't complete a second time.

  Verify: `./gradlew :users:data:iosSimulatorArm64Test` passes.
- [x] 4.2 Add `NwPathConnectivityCheck` in `iosMain`: a one-shot `NWPathMonitor` read with a 2-second timeout, where a
  timeout counts as offline. Add `BackgroundTaskUserSyncScheduler` (design D5):
  - a guarded one-time `registerForTaskWithIdentifier("com.androidlearning.sliide.sync-pending-users")`;
  - a `getPendingTaskRequests` check, then a `BGAppRefreshTaskRequest` with `earliestBeginDate = now + 30 min`
    submitted only if none is pending;
  - submit errors logged and swallowed.

  Add the `actual val platformUserSyncModule` for iOS. Verify: `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64`
  succeeds.
- [x] 4.3 In `iosApp/iosApp/Info.plist`, add `BGTaskSchedulerPermittedIdentifiers` = [`com.androidlearning.sliide.sync-pending-users`]
  and `UIBackgroundModes` = [`fetch`]. Verify: the iosApp builds in Xcode, and launching on the Simulator logs the
  expected submit-unavailable message without crashing.

## 5. App wiring

- [x] 5.1 Add `platformUserSyncModule` to `appModules()`, and make `initKoin()` call
  `get<UserSyncScheduler>().schedulePeriodicSync()` after `startKoin` (design D7). Add `WorkerParameters` to
  `KoinGraphTest`'s `extraTypes` (design D8). Verify: `./gradlew :composeApp:testAndroidHostTest` passes.
- [x] 5.2 In `androidApp/src/main/AndroidManifest.xml`, add the `tools` namespace and an `InitializationProvider` entry
  that removes `androidx.work.WorkManagerInitializer`. Add `workManagerFactory()` to `SliideApplication`'s `initKoin`
  config lambda. Extend `AppLaunchSmokeTest` (or add a sibling test) to assert that, after launch, the
  `"sync_pending_users"` unique work exists and is `ENQUEUED`. Verify:
  `./gradlew :androidApp:connectedDebugAndroidTest` passes on an emulator started with the android-cli skill.
- [ ] 5.3 Manual check on Android with the android-cli skill:
  1. Queue an update while in airplane mode, then close the app (Home, not force-stop).
  2. While still offline, run `adb shell cmd jobscheduler run -n androidx.work.systemjobscheduler
     com.androidlearning.sliide <jobId>` (no `-f`, so constraints apply), finding the job id with
     `adb shell dumpsys jobscheduler`.
  3. Turn airplane mode off and wait for the real 30-minute period. *(Recorded during implementation:
     `jobscheduler run -f` can't start periodic work early; WorkManager logs "being executed before schedule" and
     reschedules, so the run has to come from the period itself.)*

  Verify: offline, JobScheduler refuses to start the job. Online, after the period, the update reaches GoRest and the
  entry is removed.
- [x] 5.4 Update `README.md`:
  - replace the "Planned follow-up" note with a description of the background sync: it calls only `syncPending()`,
    runs every 30 min on WorkManager with `CONNECTED`, uses `BGAppRefreshTask` with an `NWPathMonitor` check on iOS,
    never retries, and uses KEEP scheduling;
  - say how it was verified on iOS (device plus LLDB `_simulateLaunchForTaskWithIdentifier`; it doesn't run on the
    Simulator);
  - note the AI-orchestration contribution for this change.

  Verify: the README matches design.md.

## 6. Verification gate

- [x] 6.1 Run `./gradlew ktlintCheck` and fix any violations without adding a baseline. Verify: it passes.
- [ ] 6.2 Run all unit tests and all instrumented tests for every affected module:
  - `:users:domain`, `:users:data` and `:composeApp`: `testAndroidHostTest` plus `iosSimulatorArm64Test` / `allTests`
    where present;
  - `:users:data`: `connectedAndroidDeviceTest`;
  - `:androidApp`: `connectedDebugAndroidTest`.

  Instrumented tests run on an emulator started with the android-cli skill. Verify: every test passes, and none are
  skipped or disabled.
- [x] 6.3 Run `./gradlew build` and `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64`. Verify: both succeed.
- [ ] 6.4 If a physical iOS device is available, run the app from Xcode, background it, and trigger the task with LLDB:
  `e -l objc -- (void)[[BGTaskScheduler sharedScheduler] _simulateLaunchForTaskWithIdentifier:@"com.androidlearning.sliide.sync-pending-users"]`.
  Verify: a queued write is replayed, and a new request is pending afterwards. If no device is available, record that in
  the README instead of claiming it was verified.
