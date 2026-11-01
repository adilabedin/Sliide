# Proposal

## Why

Writes that fail for a retryable reason (no internet, a timeout, a 5xx response, HTTP 429) are saved in the
`pending_sync` queue. Today the queue is replayed only when `refresh()` runs, which happens on launch and on
pull-to-refresh. A user who queues a change offline and does not reopen the app never has it sent. The
`add-offline-pending-sync` design deferred this to a follow-up: a background job, roughly every 30 minutes, that calls
`syncPending()` only. This change is that follow-up, for both Android and iOS.

## What Changes

**Already in place, not part of this change.** The online create, update and delete flow is built:

- `KtorUserApiClient` has `create` (POST, 201), `update` (PATCH, 200) and `delete` (DELETE, 204; a 404 counts as
  success).
- `OfflineFirstUserRepository` calls them first. On success the user's pending entry is deleted, so nothing is synced
  later. A retryable failure keeps or merges the pending entry. A permanent failure (422, 401, 404 on update) is **not**
  queued: it rolls the local change back and returns the error.
- `ProfileViewModel` (create and edit) and `DeleteUndoController` (finalise delete) already call these methods.

The user confirmed that rule stays the same: only retryable failures are queued, and nothing retries a permanent error.

**New in this change:**

- **Android periodic sync.** A WorkManager periodic job with a 30-minute interval calls `UserRepository.syncPending()`
  and nothing else. It requires a connected network, so the OS doesn't run it while offline. It is scheduled once per
  launch as unique work and kept, not replaced, so relaunching doesn't reset the period or cancel a sync that's running.
- **iOS periodic sync.** A `BGAppRefreshTask` is registered at launch. Each run submits the next request, at least
  30 minutes out, and then calls `syncPending()`. iOS decides when the task actually runs. If the device has no network
  when the task starts, the run skips the sync and completes straight away.
- **No retry inside the job.** A failed or skipped background run never asks the OS to retry or back off. The queue
  waits for the next periodic run, or for the next foreground `refresh()`.
- **No user-facing change.** The job runs silently and writes only through the repository. The Room cache stays the
  single source of truth, so the feed updates on its own when a queued create is re-keyed. The job never calls
  `refresh()`, so rows never vanish while the user is looking at the list.

## Capabilities

### New Capabilities

- `background-sync`: when and how the pending-sync queue is replayed while the app is not in the foreground. Covers
  the periodic schedule on each platform, the network requirement, the "sync only, never refresh" rule, the no-retry
  policy, and how the job runs safely alongside foreground work.

### Modified Capabilities

None. `offline-sync` (in the in-flight `add-offline-pending-sync` change) already makes `syncPending()` safe for
background use. Its queueing and replay rules stay the same.

## Impact

- **New dependencies** in `gradle/libs.versions.toml`: `androidx.work:work-runtime` and
  `io.insert-koin:koin-androidx-workmanager` (versioned by the Koin BOM).
- **`:users:domain`**: a new `UserSyncScheduler` interface.
- **`:users:data`**:
  - `commonMain`: a shared mapping from a sync result to a background outcome, plus the per-platform scheduler module
    declaration.
  - `androidMain`: `UserSyncWorker` and `WorkManagerUserSyncScheduler`.
  - `iosMain`: `BackgroundTaskUserSyncScheduler` and the network check.
- **`:composeApp`**: `appModules()` includes the platform sync module. `initKoinIos()` registers the iOS task, and the
  periodic sync is scheduled at startup.
- **`:androidApp`**: the manifest removes the default WorkManager initializer, and `SliideApplication` installs Koin's
  WorkManager factory and schedules the sync.
- **`iosApp`**: `Info.plist` gains `BGTaskSchedulerPermittedIdentifiers` and `UIBackgroundModes` → `fetch`.
- **Tests**: unit tests for the outcome mapping and the iOS handler logic (with fakes), an instrumented WorkManager
  test, and `KoinGraphTest` updated for the worker's parameters.
- **README.md**: the "planned follow-up" note becomes a description of the background sync.
