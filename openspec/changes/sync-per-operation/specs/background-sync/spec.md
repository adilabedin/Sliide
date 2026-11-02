# Spec Delta

## ADDED Requirements

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

## MODIFIED Requirements

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
