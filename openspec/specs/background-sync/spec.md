# background-sync Specification

## Purpose
Background sync replays the offline write queue while the app is not in the foreground. A change saved offline
then reaches the GoRest API even if the user never reopens the app.

## Requirements

### Requirement: Periodic background replay on both platforms
The app SHALL ask the operating system to replay the pending-sync queue in the background on Android and on iOS,
no more often than once every 30 minutes. The operating system decides when each run actually starts. This periodic
replay is the safety net for any queued write that a per-user background sync did not send.

#### Scenario: Android run after the interval
- **GIVEN** the app has been launched once on Android and a write is queued
- **WHEN** at least 30 minutes have passed, the device is online and the OS starts the job
- **THEN** the queued write is sent to the API and its pending entry is removed

#### Scenario: iOS run after the interval
- **GIVEN** the app has been launched once on iOS and a write is queued
- **WHEN** iOS starts the background refresh task no earlier than 30 minutes after it was requested
- **THEN** the queued write is sent to the API and its pending entry is removed

#### Scenario: App closed
- **GIVEN** a write was queued and the app was then closed (not force-quit)
- **WHEN** a background run starts
- **THEN** the write is replayed without the user opening the app

#### Scenario: Per-user sync missed
- **GIVEN** a write is queued but its per-user background sync was never requested, for example because the process died right after queuing
- **WHEN** the periodic replay runs
- **THEN** the write is sent and its pending entry is removed

### Requirement: The schedule is set up once and kept across launches
Launching the app SHALL make sure exactly one periodic background replay is scheduled. Launching again SHALL NOT
add a second schedule, restart the 30-minute interval, or cancel a background run already in progress.

#### Scenario: Relaunch keeps the existing schedule
- **GIVEN** a periodic replay is already scheduled
- **WHEN** the app is launched again
- **THEN** one periodic replay is still scheduled and its next run time stays the same

#### Scenario: Relaunch during a run
- **GIVEN** a background replay is running
- **WHEN** the app is launched
- **THEN** the running replay is not cancelled

### Requirement: Background runs replay the queue only
A background run SHALL replay pending writes only. It SHALL NOT fetch the users page or replace the local cache, so
no row ever disappears from the list because of a background run.

#### Scenario: Background run with an empty queue
- **WHEN** a background run starts and no write is queued
- **THEN** no request is sent to the API and the cache does not change

#### Scenario: Server list changed meanwhile
- **GIVEN** users were added or removed on the server since the last refresh
- **WHEN** a background run replays the queue
- **THEN** the local list still shows exactly the users it showed before, apart from the effects of the replayed writes

### Requirement: No background sync while offline
A background run SHALL NOT attempt a replay when the device has no network connection. The queue stays as it was
until a later run or a foreground refresh.

#### Scenario: Android offline
- **WHEN** the scheduled time passes while the Android device is offline
- **THEN** the job does not run until a network connection is available

#### Scenario: iOS offline
- **WHEN** iOS starts the background task while the device has no network connection
- **THEN** no request is sent, the queue does not change and the task reports completion straight away

### Requirement: Background runs never retry
A background run that fails or is skipped SHALL NOT ask the operating system to retry it or back off. This applies to
the periodic replay and to per-user syncs. The queue waits for the next periodic run, the next per-user sync request
or the next foreground refresh.

#### Scenario: Retryable failure in the background
- **WHEN** a background run gets a retryable error such as a timeout or HTTP 503 for one entry
- **THEN** that entry stays queued for the next run, the other entries are still sent, and the run ends as finished,
  not as a retry

#### Scenario: Rejected entry in the background
- **WHEN** a background run hits a permanent rejection for one entry
- **THEN** that entry is dropped exactly as in a foreground sync, and the run continues

#### Scenario: Per-user sync fails
- **WHEN** a per-user background sync for user 42 fails with HTTP 503
- **THEN** the sync ends as finished, not as a retry, and user 42's entry stays queued for the periodic replay

### Requirement: Background and foreground sync never send an entry twice
A background run and a foreground refresh that overlap SHALL share one replay pass, so no queued write is sent to the
API twice.

#### Scenario: Pull-to-refresh during a background run
- **GIVEN** a background replay is in progress
- **WHEN** the user pulls to refresh
- **THEN** each queued write is sent at most once and both callers see the same replay result

### Requirement: Interrupted background runs resume cleanly
When the operating system stops a background run before it finishes, writes already replayed SHALL stay replayed,
and the remaining writes SHALL stay queued for the next run.

#### Scenario: iOS time budget expires
- **GIVEN** three writes are queued
- **WHEN** iOS expires the background task after the first write has been sent
- **THEN** the first write's entry is removed, the other two stay queued, and the next run starts with them

### Requirement: Successful foreground writes are never synced again
A create, update or finalised delete whose direct API call succeeds SHALL leave no pending entry for that user, so a
later background run sends nothing for it. A write SHALL be queued only when its call fails with an error that is kept
for a later sync (no connectivity, timeout, 5xx, 429, 401 or 403). Rejected writes are rolled back and never queued.

#### Scenario: Online create
- **WHEN** a user is created and the API responds 201
- **THEN** no pending entry exists for that user and the next background run sends no request for it

#### Scenario: Offline update then background run
- **GIVEN** an update failed because the device was offline and was queued
- **WHEN** a background run starts after connectivity returns
- **THEN** the update is sent once and its pending entry is removed

#### Scenario: Validation error is not queued
- **WHEN** a create fails with HTTP 422
- **THEN** no pending entry is stored, the local insert is rolled back, and no background run ever sends it

#### Scenario: Unauthorised create is queued
- **GIVEN** a create failed with HTTP 401 and was queued
- **WHEN** a background run starts and the API now accepts the request with 201
- **THEN** the create is sent once, the user gets its server id, and its pending entry is removed

### Requirement: A queued write requests its own background sync
When a write is queued, the app SHALL at once ask the operating system to sync that user's pending entry in the
background. On Android the sync SHALL start as soon as the device is online, without waiting for the periodic
replay. On iOS, where the OS offers no per-write task, the request SHALL make sure a background refresh is pending.

#### Scenario: Android offline create, then back online
- **GIVEN** a create failed because the Android device was offline and the write was queued
- **WHEN** the device regains a network connection, with the app in the background and no periodic run due
- **THEN** that user's create is sent to the API without waiting for the periodic replay, and its pending entry is removed

#### Scenario: iOS write queued
- **GIVEN** no background refresh request is pending on iOS
- **WHEN** a write is queued
- **THEN** a background refresh request is pending

#### Scenario: Successful write requests nothing
- **WHEN** a create, update or finalised delete succeeds on its direct API call
- **THEN** no background sync is requested for that user

#### Scenario: Rejected write requests nothing
- **WHEN** a create fails with HTTP 422 and is not queued
- **THEN** no background sync is requested for that user

### Requirement: A per-user background sync sends only that user's entry
A background sync requested for one user SHALL send at most that user's pending entry and no other. It SHALL NOT
fetch the users page or replace the local cache. If that user has no pending entry when the sync starts, it SHALL send
nothing.

#### Scenario: Two users queued, one sync runs
- **GIVEN** users 41 and 42 each have a pending entry, and a background sync for user 42 starts
- **WHEN** the sync runs
- **THEN** only user 42's entry is sent, and user 41's entry stays queued

#### Scenario: Entry already sent by another sync
- **GIVEN** a per-user sync for user 42 is requested, and a foreground refresh sends user 42's entry first
- **WHEN** the per-user sync starts
- **THEN** no request is sent for user 42

#### Scenario: Created user re-keyed before the sync runs
- **GIVEN** a per-user sync was requested for a user under temporary id -1, and the create was then replayed and re-keyed to id 7012
- **WHEN** the per-user sync for id -1 starts
- **THEN** no request is sent

### Requirement: One user's sync never blocks another's
Per-user background syncs SHALL be independent: a failing or retryable entry for one user SHALL NOT prevent the sync of
another user's entry, and a slow one SHALL delay it by at most that one request. Requesting a sync for a user that
already has one pending SHALL NOT add a second one.

#### Scenario: One slow entry
- **GIVEN** user 41's and user 42's writes are queued, each with its own requested sync
- **WHEN** user 41's request times out
- **THEN** user 42's entry is still sent by its own sync, and user 41's entry stays queued

#### Scenario: Two writes for the same user
- **WHEN** two writes for user 42 are queued while offline
- **THEN** exactly one background sync is pending for user 42, and when it runs it sends the merged entry once

#### Scenario: Sync replaced while it is sending
- **GIVEN** user 42's sync is sending its entry
- **WHEN** a new request for user 42 replaces that sync
- **THEN** the request in flight still completes, and the entry is sent once in total
