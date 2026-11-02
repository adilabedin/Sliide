# Spec Delta

## ADDED Requirements

### Requirement: Feed app bar stays pinned
The users screen SHALL show a fixed-height app bar titled "Users" at the top. It SHALL NOT collapse, resize or scroll
away while the feed scrolls. Only the content beneath it SHALL scroll: the list or grid of cards, or the shimmer
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
