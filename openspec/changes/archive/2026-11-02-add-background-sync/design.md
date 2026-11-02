# Design

## Context

- See proposal.md (Why). The behaviour contract is `specs/background-sync`.
- `UserRepository.syncPending()` (`:users:domain`) is already safe for background use (`add-offline-pending-sync`,
  D5/D7):
  - it takes the write lock per entry;
  - it is single-flight: concurrent callers share one `Deferred`;
  - each entry is cleared in its own transaction, so a pass the OS kills resumes cleanly.
  - It returns `Result<SyncReport, DataError.Remote>`.
- The online create, update and delete paths already exist and already queue only retryable failures. This change
  does not touch them. The user confirmed retryable-only queueing.
- Koin starts in common `initKoin(applicationScope, config)` (`:composeApp`). Android calls it from
  `SliideApplication.onCreate` with `androidContext`. iOS calls it through `initKoinIos()`, which `iOSApp.init()`
  calls, so it runs before the app finishes launching.
- `HttpClientFactory` request timeout: 20 s; connect timeout: 10 s. One stalled request therefore fits inside the
  iOS background budget of about 30 s, but a long queue does not. That is acceptable (see Risks).
- Nothing in the app uses WorkManager or `BGTaskScheduler` yet. `androidApp`'s manifest has no `InitializationProvider`
  override. `iosApp/Info.plist` declares no background modes.

## Goals / Non-Goals

**Goals:**

- One shared definition of "what a background run does", in `commonMain` and unit-tested. Platform code only schedules
  the job and adapts the OS callback to it.
- `expect`/`actual` used once, for the scheduler binding, because WorkManager and `BGTaskScheduler` are genuinely
  different OS facilities.

**Non-Goals:**

- A one-time "sync as soon as we're back online" job triggered when a write is queued. The user asked for the
  30-minute periodic job only.
- Showing sync status in the UI, observing work state, or notifications.
- Changing which errors are queued, or the replay rules.
- iOS `BGProcessingTask`. It only runs while the device is idle or charging, which is too rare for an outbox.

## Decisions

### D1. `UserSyncScheduler` in `:users:domain`, bound per platform from `:users:data`

```kotlin
// :users:domain
interface UserSyncScheduler {
    /** Ensures exactly one periodic background replay is scheduled. Idempotent; safe to call on every launch. */
    fun schedulePeriodicSync()
}
```

`:users:data` `commonMain` declares `expect val platformUserSyncModule: Module`:

- `androidMain` binds `WorkManagerUserSyncScheduler` and registers `UserSyncWorker` with Koin's `workerOf`.
- `iosMain` binds `BackgroundTaskUserSyncScheduler`.

`appModules()` includes `platformUserSyncModule`, following the pattern of `platformHttpEngineModule` and
`platformDatabaseModule`.

This follows the `android-background` skill's placement: the interface in domain, the worker and implementation in
data. It also keeps the "features never depend on features" rule: `:composeApp` only sees the domain interface.

*Alternative:* put the platform code in `:composeApp`. Rejected because the worker belongs to the users feature, and
the convention is that code stays in its feature.

### D2. Shared run logic: `BackgroundUserSync` in `:users:data` `commonMain`

```kotlin
class BackgroundUserSync(private val repository: UserRepository) {
    suspend fun run(): BackgroundSyncOutcome  // Synced(report) | Stopped(error)
}
```

- It calls **only** `repository.syncPending()`, never `refresh()`. That keeps the spec rule "background runs replay the
  queue only": no rows vanish while the list is on screen.
- An empty queue sends no request; `syncPending` already behaves this way.
- Both platform adapters call `run()` and treat **every** outcome as a finished run (D4). The outcome exists for
  logging and tests, not for control flow.
- `CancellationException` propagates. Any other unexpected throwable is caught and reported as
  `Stopped(DataError.Remote.UNKNOWN)`, so a bug can never crash a background process.

### D3. Android: unique periodic work with KEEP and a network constraint

`WorkManagerUserSyncScheduler.schedulePeriodicSync()` calls:

```
enqueueUniquePeriodicWork(
    "sync_pending_users",
    ExistingPeriodicWorkPolicy.KEEP,
    PeriodicWorkRequestBuilder<UserSyncWorker>(30, MINUTES)
        .setConstraints(NetworkType.CONNECTED),
)
```

- **KEEP, not REPLACE** (the skill's sample uses REPLACE). This is called on every launch. REPLACE would restart the
  period each time and could cancel a run in progress, which the spec forbids.
  - *Alternative:* UPDATE. Rejected because nothing about the request changes between launches.
- **`NetworkType.CONNECTED`** is how the spec's "no background sync while offline" is met on Android: the OS simply
  doesn't start the job without a network.
- **No initial delay.** The first run may happen right after the first launch. It overlaps the launch `refresh()`
  harmlessly, because the two share one replay pass.
- **No `setBackoffCriteria`.** The worker never returns `retry()` (D4), so backoff never applies.

`UserSyncWorker` is a thin `CoroutineWorker`: `BackgroundUserSync.run()`, then `WorkerResult.success()`. The
`WorkerResult` typealias lives next to the worker in `:users:data` `androidMain`, not in `:core:data`. It has only one
user, and code stays in the feature until a second feature needs it.

**Initialization.** The manifest removes `androidx.work.WorkManagerInitializer` from `InitializationProvider`.
`SliideApplication` adds `workManagerFactory()` inside the existing `initKoin { androidContext(...) }` lambda. That
initializes WorkManager with Koin's `WorkerFactory` before any module code runs.

### D4. No retry: every run reports success

This decision is the user's: "No retry". Each run reports a finished run to the OS, whatever happened:

- **Android:** `WorkerResult.success()`. For periodic work, `failure()` would also keep the schedule. `success()` is
  chosen so that a stopped replay doesn't look like a crash in WorkManager's diagnostics.
- **iOS:** `setTaskCompleted(success = true)`. The one exception is expiration, which reports `false`, as Apple
  requires.

If the queue still holds entries, they wait for the next periodic run or the next foreground `refresh()`.

*Alternative:* `retry()` with exponential backoff on retryable errors. Rejected: the user ruled it out.

### D5. iOS: `BGAppRefreshTask`, registered at launch, one request pending at a time

- Task identifier: `com.androidlearning.sliide.sync-pending-users`. It is listed in `BGTaskSchedulerPermittedIdentifiers`,
  and `UIBackgroundModes` includes `fetch`.
- `schedulePeriodicSync()`, called from `initKoin` on both platforms (D7):
  1. On first call, registers the launch handler with `BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier`.
     A process-level flag guards it, because registering the same identifier twice throws. The app is still
     launching here, because `iOSApp.init()` calls `initKoinIos()`.
  2. Calls `getPendingTaskRequestsWithCompletionHandler`, and submits a `BGAppRefreshTaskRequest` with
     `earliestBeginDate = now + 30 min` **only if none is pending**.
     - Submitting again would replace the pending request and push its start time back, which breaks the "relaunch
       keeps the schedule" requirement.
- The launch handler hands the task to `IosBackgroundSyncRunner` (D6).
- **Submit errors are logged and swallowed.** The Simulator rejects `submit` with `BGTaskSchedulerErrorCodeUnavailable`,
  and so does a device with Background App Refresh turned off. Neither may crash the app.

*Alternative:* submit on every transition to the background (Apple's sample does this). Rejected: it adds a scene
lifecycle hook to SwiftUI for no gain, because the handler re-submits on every run (D6).

### D6. iOS run orchestration: `IosBackgroundSyncRunner`, testable without `BGTask`

The runner depends on three small interfaces, so `iosTest` can drive it with fakes:

- `SyncTaskHandle`: `setExpirationHandler`, `complete(success)`. The real implementation wraps `BGTask`.
- `ConnectivityCheck`: `suspend fun isOnline(): Boolean`.
- A `CoroutineScope`: the application scope from Koin.

Each run:

1. **Re-submits the next request first** (now + 30 min). The schedule then continues even if this run expires or the
   process is killed.
2. Launches a job in the application scope:
   1. Checks `ConnectivityCheck.isOnline()`. If offline, it calls `complete(true)` without calling `syncPending()`.
   2. Otherwise it calls `BackgroundUserSync.run()`, then `complete(true)`.
3. The expiration handler cancels that job and calls `complete(false)`.

An atomic "completed" flag makes sure `complete` is called **exactly once**, even if expiration and normal completion
race each other.

Cancelling the job mid-replay is safe. Entries already sent were each cleared in their own transaction, and the
in-flight entry stays queued (the "interrupted runs resume cleanly" spec rule).

**`ConnectivityCheck` on iOS** is a one-shot read from `NWPathMonitor` (`platform.Network`):

- Start the monitor on a background queue and wait for the first path update, with a 2-second timeout.
- Cancel the monitor.
- Return `true` only if the status is `nw_path_status_satisfied`. A timeout counts as offline.

This is the second platform split, and it is justified: `BGAppRefreshTaskRequest` has no network requirement, unlike
WorkManager's constraint.

*Alternative:* skip the check and let the first request fail fast with `NO_INTERNET`. Rejected because the user asked
that the job "not bother" while offline, and a DNS failure can take several seconds of the 30-second budget.

### D7. Scheduling at startup from common `initKoin`

`initKoin()` calls `get<UserSyncScheduler>().schedulePeriodicSync()` straight after `startKoin`, so both platforms go
through the same code:

- **Android:** WorkManager is already initialized at that point by `workManagerFactory()` in the config lambda.
- **iOS:** this runs inside `iOSApp.init()`, before launch completes, which `registerForTaskWithIdentifier` requires.

`KoinGraphTest` builds modules without `startKoin`, so it is unaffected apart from D8.

### D8. DI test update

`KoinGraphTest` (`androidHostTest`) resolves `appModules()`, which now includes the Android `platformUserSyncModule`.
`WorkerParameters` is added to `extraTypes`, because WorkManager supplies it at runtime, just as `SavedStateHandle` is
already handled. `Context` is already there.

## Risks / Trade-offs

- **[Risk] iOS runs rarely or never, because the OS decides based on app usage, battery and Low Power Mode.** →
  Accepted. The spec words this as "no earlier than 30 min, the OS decides". The foreground `refresh()` stays the
  primary replay path.
- **[Risk] A long queue doesn't finish inside the iOS ~30 s budget.** → Each entry is committed on its own, expiry
  cancels cleanly, and the next run resumes from the first remaining entry.
- **[Risk] Force-quitting the app stops background work on both OSes.** → Accepted platform behaviour. The spec scenario
  says "closed (not force-quit)".
- **[Risk] `BGTaskScheduler` doesn't run on the Simulator**, so the full iOS path can't be tested end to end in CI. →
  The runner logic is unit-tested in `iosTest` with fakes. Registration and submission are checked on a physical
  device with LLDB `_simulateLaunchForTaskWithIdentifier`. On the Simulator, the check is only that launch logs the
  submit error and doesn't crash.
- **[Trade-off] No retry/backoff.** A replay that fails at minute 0 waits up to 30 minutes, longer on iOS. → The user
  chose this, and a foreground refresh replays straight away anyway.
- **[Risk] Removing the default WorkManager initializer without `workManagerFactory()` crashes on first use.** → Both
  are part of one task, and an instrumented test schedules real work in the app process.

## Migration Plan

Additive. On first launch after an update, the periodic work and the iOS request are scheduled. Rollback: remove
the scheduling call. Already enqueued Android work then becomes an orphan whose worker class still resolves. If the
worker class is ever deleted, also call `cancelUniqueWork("sync_pending_users")` once.
