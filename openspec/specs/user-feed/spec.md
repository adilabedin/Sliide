# user-feed Specification

## Purpose
The main users screen: a lazily built feed of user cards read from the local cache. It covers how the cards look,
the relative "added" time, loading placeholders, refresh and the error states.

## Requirements

### Requirement: Feed shows every cached user as a card
The users screen SHALL show every user currently in the local cache, in the order the cache provides, newest first.
Each user is shown as a card with an initials avatar, the full name, the email address and a relative "added" time.
Rows SHALL be composed lazily, so only the rows on screen are built.

#### Scenario: Cache with users
- **WHEN** the cache holds 3 users
- **THEN** the feed shows 3 cards, each with that user's initials, name, email and relative added time

#### Scenario: Long text is truncated
- **WHEN** a user's name or email is wider than the card
- **THEN** it is shown on one line, ending with an ellipsis, and the card height does not change

### Requirement: Feed reflects cache changes immediately
The feed SHALL update whenever the cache changes, without any user action. Cards that are added SHALL animate in,
cards that are removed SHALL animate out, and cards that move SHALL animate to their new position.

#### Scenario: User added elsewhere
- **WHEN** a user is inserted into the cache while the feed is visible
- **THEN** a card for that user animates into its position without a manual refresh

#### Scenario: User removed
- **WHEN** a user is removed from the cache while the feed is visible
- **THEN** that card animates out and the cards below it move up smoothly

### Requirement: Relative added time is computed in shared code
Each card SHALL show how long ago the user was added to the cache, using the timestamp recorded locally when the user
first entered it. The text SHALL be computed in shared code for both platforms. It SHALL refresh at least once a
minute while the feed is visible.

#### Scenario: Under a minute
- **WHEN** a user was added 30 seconds ago
- **THEN** the card reads "Added just now"

#### Scenario: Minutes, hours and days
- **WHEN** a user was added 5 minutes, 3 hours or 2 days ago
- **THEN** the card reads "Added 5 minutes ago", "Added 3 hours ago" or "Added 2 days ago", using the singular form when the count is 1

#### Scenario: Time passes while viewing
- **WHEN** the feed stays open and a user's age crosses from 4 minutes to 5 minutes
- **THEN** that card updates to "Added 5 minutes ago" within one minute, with no user action

#### Scenario: Clock earlier than the timestamp
- **WHEN** a user's added timestamp is later than the current time
- **THEN** the card reads "Added just now"

### Requirement: Unsynced users are marked
A user created locally whose creation has not yet reached the server SHALL show a subtle "Pending sync" marker on its
card. The marker SHALL disappear once the server confirms the user.

#### Scenario: Created offline
- **WHEN** a user is created while offline
- **THEN** its card is at the top of the feed and shows "Pending sync"

#### Scenario: Sync completes
- **WHEN** that user's queued create later succeeds
- **THEN** its card no longer shows "Pending sync"

### Requirement: Feed refreshes from the network on launch
When the users screen first appears, the app SHALL request a refresh of the cache from the network. The cache SHALL
stay the only source of what is displayed: refresh results reach the screen only through cache updates.

#### Scenario: Launch with network
- **WHEN** the app launches with network access
- **THEN** a refresh runs, and when it changes the cache, the feed shows the updated users

#### Scenario: Configuration change
- **WHEN** the device is rotated after launch
- **THEN** no additional refresh is triggered by the rotation

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

### Requirement: Shimmer placeholders while the first load is running
While the cache has no users and the first load is still running, the feed SHALL show 20 placeholder cards shaped like
real cards, with an animated shimmer. They SHALL be composed lazily, like real rows. The placeholders SHALL be hidden
from accessibility services as individual items and announced once as "Loading users".

#### Scenario: First launch
- **WHEN** the app starts with an empty cache and the refresh is still running
- **THEN** 20 shimmering placeholder cards are shown

#### Scenario: Cached data available
- **WHEN** the app starts and the cache already holds users
- **THEN** the cached users are shown immediately and no placeholders appear, even while the refresh runs

#### Scenario: Load finishes
- **WHEN** the refresh completes and the cache now holds users
- **THEN** the placeholders are replaced by the user cards

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

### Requirement: Full-screen error when nothing is cached
When a refresh fails and the cache has no users, the feed SHALL show a full-screen error state. It SHALL contain a
human-readable message for the failure and a Retry action that starts a new refresh.

#### Scenario: No internet on first launch
- **WHEN** the first refresh fails with no connectivity and the cache is empty
- **THEN** a full-screen state reads "No internet connection" and offers Retry

#### Scenario: Retry succeeds
- **WHEN** the user taps Retry and the refresh succeeds with users
- **THEN** the error state is replaced by the user cards

### Requirement: Inline error banner when data is cached
When a refresh fails while the cache holds users, the feed SHALL keep showing the cached users. A slim, dismissible
banner with a human-readable message SHALL appear above the list. A later successful refresh SHALL clear the banner.

#### Scenario: Offline with a cache
- **WHEN** a refresh fails with no connectivity and the cache holds users
- **THEN** the cached cards stay visible and a banner reads "No internet connection"

#### Scenario: Recovery
- **WHEN** a later refresh succeeds
- **THEN** the banner disappears

### Requirement: Failure messages are specific
Each refresh or write failure SHALL be shown with a message that matches its cause. Distinct messages SHALL exist for
no connectivity, timeout, server error, rate limiting, unauthorised, and a generic fallback for any other failure.

#### Scenario: Rate limited
- **WHEN** a refresh fails because the server rate-limited the app
- **THEN** the message tells the user there were too many requests and to try again shortly

#### Scenario: Unauthorised
- **WHEN** a refresh fails as unauthorised
- **THEN** the message says the app could not authenticate with the server

### Requirement: Feed app bar stays pinned
The users screen SHALL show a fixed-height app bar titled "Users" at the top. It SHALL NOT collapse, resize or scroll
away while the feed scrolls. Only the content beneath it SHALL scroll: the list of cards, or the shimmer
placeholders. That content SHALL be composed lazily, so only the visible cards (plus a small buffer) are built.

#### Scenario: Scroll a long feed
- **WHEN** the feed holds more users than fit on screen and the user scrolls down
- **THEN** the cards scroll under the app bar, and the app bar keeps the same position, height and title

#### Scenario: Scroll back to the top
- **WHEN** the user scrolls back to the first card
- **THEN** the app bar is still the same height, with no expanding title

#### Scenario: Loading state
- **WHEN** the shimmer placeholders are shown and the user scrolls them
- **THEN** the app bar stays fixed at the top
