# Spec Delta

## MODIFIED Requirements

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

### Requirement: Background runs never retry
A background run that fails or is skipped SHALL NOT ask the operating system to retry it or back off. The queue
waits for the next periodic run or the next foreground refresh.

#### Scenario: Retryable failure in the background
- **WHEN** a background run gets a retryable error such as a timeout or HTTP 503 for one entry
- **THEN** that entry stays queued for the next run, the other entries are still sent, and the run ends as finished,
  not as a retry

#### Scenario: Rejected entry in the background
- **WHEN** a background run hits a permanent rejection for one entry
- **THEN** that entry is dropped exactly as in a foreground sync, and the run continues
