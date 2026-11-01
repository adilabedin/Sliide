# Spec Delta

## Purpose

Background sync replays the offline write queue while the app is not in the foreground. A change saved offline
then reaches the GoRest API even if the user never reopens the app.

## ADDED Requirements

### Requirement: Periodic background replay on both platforms
The app SHALL ask the operating system to replay the pending-sync queue in the background on Android and on iOS,
no more often than once every 30 minutes. The operating system decides when each run actually starts.

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
A background run that fails or is skipped SHALL NOT ask the operating system to retry it or back off. The queue
waits for the next periodic run or the next foreground refresh.

#### Scenario: Retryable failure in the background
- **WHEN** a background replay stops on a retryable error such as a timeout or HTTP 503
- **THEN** the run ends as finished, not as a retry, and the remaining entries stay queued for the next run

#### Scenario: Rejected entry in the background
- **WHEN** a background replay hits a permanent rejection for one entry
- **THEN** that entry is dropped and its local effect resolved exactly as in a foreground replay, and the run continues

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
later background run sends nothing for it. A write SHALL be queued only when its call fails with a retryable error.
Permanent errors are rolled back and never queued.

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
