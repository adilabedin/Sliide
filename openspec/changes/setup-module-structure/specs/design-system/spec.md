# Spec Delta

## Purpose

Provides the single Material 3 app theme, with light and dark colour schemes and typography, that every screen and preview is rendered in. This keeps the app visually consistent on Android and iOS.

## ADDED Requirements

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
