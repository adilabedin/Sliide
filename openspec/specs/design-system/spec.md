# design-system Specification

## Purpose
TBD - created by archiving change build-users-ui. Update Purpose after archive.

## Requirements

### Requirement: Premium colour palette
The app theme SHALL use a refined palette in both light and dark modes. It SHALL have deep ink neutrals, one restrained
accent used for primary actions and selection, cards that lift off the background by tone rather than by heavy
shadows, and one refined red used only for destructive actions. Light and dark SHALL share the same hue identity.

#### Scenario: Light mode surfaces
- **WHEN** the feed is shown in light mode
- **THEN** cards are visibly separated from the background by tone and a hairline outline, without heavy drop shadows

#### Scenario: Dark mode surfaces
- **WHEN** the feed is shown in dark mode
- **THEN** the background is near-black ink and cards are a slightly lighter ink tone, with the same accent hue as light mode

### Requirement: Palette meets contrast requirements
Every text and icon colour pairing used in the app SHALL meet a WCAG 2.1 AA contrast ratio against its background, in
both light and dark modes. Body text requires at least 4.5:1, and large text and icons at least 3:1.

#### Scenario: Secondary text
- **WHEN** an email address is rendered in the secondary text colour on a card
- **THEN** its contrast ratio against the card colour is at least 4.5:1 in both modes

#### Scenario: Destructive button
- **WHEN** the Delete button label is rendered on the destructive red
- **THEN** its contrast ratio is at least 4.5:1 in both modes

### Requirement: Shared components give one visual style
The user card, shimmer placeholder, two-option toggle, floating add button, Undo snackbar, destructive button and error
banner SHALL each be defined once in the design system and reused by every screen. Their corner radii, spacing and
typography SHALL come from shared tokens.

#### Scenario: Consistent corners
- **WHEN** a card, a toggle and the Undo snackbar are shown together
- **THEN** they use corner radii from the same shared shape scale

### Requirement: Motion is subtle and consistent
Interactive components SHALL give touch feedback with a short, subtle animation, such as a press scale or a colour
transition. Shimmer SHALL be a slow, low-contrast sweep. Animations SHALL use shared duration and easing values.

#### Scenario: Card press
- **WHEN** the user presses and holds a card
- **THEN** the card scales down slightly and returns smoothly when released

#### Scenario: Shimmer
- **WHEN** placeholders are shown
- **THEN** a low-contrast highlight sweeps across them, repeating roughly every 1.2 seconds

### Requirement: App theme follows system appearance
The app SHALL render all screens inside one shared app theme. The theme SHALL use the dark colour scheme when the device is in dark mode and the light colour scheme otherwise.

#### Scenario: Light mode
- **WHEN** the app is launched while the device is in light mode
- **THEN** screens use the light colour scheme of the app theme

#### Scenario: Dark mode
- **WHEN** the app is launched while the device is in dark mode
- **THEN** screens use the dark colour scheme of the app theme

### Requirement: Theme is identical on Android and iOS
The app theme's colour schemes and typography SHALL be defined once in shared code. They SHALL produce the same values on Android and iOS.

#### Scenario: Shared definition
- **WHEN** the theme is applied on Android and on iOS in the same appearance mode
- **THEN** both platforms use the same colour and typography values

### Requirement: Previews render in the app theme
Every preview of a non-Root composable SHALL be wrapped in the app theme. It SHALL be provided in both a light and a dark variant.

#### Scenario: Placeholder users screen previews
- **WHEN** the placeholder users screen previews are rendered
- **THEN** a light and a dark preview exist, and each renders inside the app theme
