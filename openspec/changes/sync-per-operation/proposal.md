# Proposal

## Why

A write that fails offline is queued, but nothing asks for it to be sent: the only trigger is a 30-minute periodic job
scheduled once at launch in `initKoin()`. A reader cannot see "write failed → write gets retried" in the repository,
and a write queued while offline waits up to 30 minutes after connectivity returns.
The periodic pass also sends the whole queue in one sequential loop, so one slow entry (up to the 20 s request
timeout) delays every entry behind it, and the result only says how many entries synced or were rejected, not which.

## What Changes

- The repository asks a scheduler for a sync **of that user's entry** at the moment it queues a write (create `KEEP`,
  update and delete enqueue). Scheduling becomes visible where the failure is handled.
- `UserSyncScheduler` (domain) gains `requestSync(userId)` next to `schedulePeriodicSync()`.
  - **Android:** unique one-time work per user (`sync_user_<id>`, network-constrained, `KEEP`) that replays only that
    user's entry, so it runs as soon as the device is back online and one entry's failure or slowness never holds up
    another's.
  - **iOS:** iOS has no per-item "run when online" task, so `requestSync` makes sure an app-refresh request is pending
    (the existing one-request-at-a-time rule).
- The repository gains a per-user replay, `syncPending(userId)`, that sends exactly one entry under the same
  single-flight and write-lock rules as the full replay.
- The periodic job stays, re-scoped as the **safety net** for anything a per-user request missed (process killed
  before the request was enqueued, iOS, requests dropped by the OS).

Unchanged: the full replay pass keeps carrying on past an entry whose failure is kept for later, and reports the
first such error (as `typed-user-errors` specifies). Out of scope: abstracting `BackgroundUserSync` behind an
interface.

This change builds on `typed-user-errors`, which is implemented but not yet archived; archive it first so these
deltas apply on top of its version of `background-sync`.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `background-sync`: adds an immediate per-user background sync requested when a write is queued; the periodic
  replay becomes the safety net.
- `offline-sync`: replay can target one user's entry; a single-user replay and a full replay never send the same
  entry twice.

## Impact

- `:users:domain`: `UserSyncScheduler.requestSync(userId)`, `UserRepository.syncPending(userId)`.
- `:users:data`: `OfflineFirstUserRepository` takes a `UserSyncScheduler`; new Android one-time worker input and
  scheduler method; iOS scheduler `requestSync`; `BackgroundUserSync` runs a full or a single-user pass; Koin wiring.
  Fakes and repository/sync tests updated; new tests for the request-on-queue rule.
- `:users:presentation`: `FakeUserRepository` implements the new `syncPending(userId)`; no UI change.
- `:composeApp`: Koin graph test still resolves the repository with its new dependency.
- No new libraries. No database schema change. README background-sync section updated.
