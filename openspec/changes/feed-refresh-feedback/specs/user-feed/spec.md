# Spec Delta

## MODIFIED Requirements

### Requirement: Pull to refresh
The user SHALL be able to pull the feed down to request a refresh. A refresh indicator SHALL be shown while it runs.
A pull while a refresh is already running SHALL NOT start a second refresh. The refresh indicator SHALL be shown only
for a refresh the user asked for by pulling; the automatic refresh on launch SHALL NOT show it.

#### Scenario: Pull
- **WHEN** the user pulls the feed down and releases
- **THEN** a refresh indicator appears and disappears when the refresh finishes

#### Scenario: Pull during a refresh
- **WHEN** the user pulls again while a refresh is running
- **THEN** only the running refresh continues

#### Scenario: Launch refresh with cached users
- **WHEN** the app launches with users in the cache and the launch refresh is running
- **THEN** the cached users are shown with no refresh indicator

#### Scenario: Users arrive during the first launch refresh
- **WHEN** the app launches with an empty cache and users reach the cache before the launch refresh finishes
- **THEN** the placeholders are replaced by the user cards and no refresh indicator appears

#### Scenario: Pull during the launch refresh
- **WHEN** the user pulls the feed down while the launch refresh is still running
- **THEN** the refresh indicator appears until that refresh finishes, and no second refresh starts

### Requirement: Empty state
When the cache has no users and no load is running, the feed SHALL show an empty state inviting the user to add one.
It SHALL also offer a Retry action that starts a new refresh.

#### Scenario: Server returns no users
- **WHEN** the refresh succeeds and the cache is still empty
- **THEN** an empty state with a short title, body and a Retry action is shown, and the add-user button stays available

#### Scenario: Retry from the empty state
- **WHEN** the user taps Retry on the empty state
- **THEN** a refresh starts, the shimmer placeholders are shown while it runs, and no pull refresh indicator appears

#### Scenario: Retry finds users
- **WHEN** a Retry from the empty state completes and the cache now holds users
- **THEN** the user cards are shown

#### Scenario: Retry still finds no users
- **WHEN** a Retry from the empty state completes and the cache is still empty
- **THEN** the empty state with its Retry action is shown again
