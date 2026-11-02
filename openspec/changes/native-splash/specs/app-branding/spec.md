# Spec Delta

## Purpose

Defines the app's launch experience and identity assets: the native splash shown on cold start on Android and iOS,
and the launcher icons, all carrying the one Sliide logo mark.

## ADDED Requirements

### Requirement: Native splash on cold start
On a cold start, each platform SHALL show its native launch screen with the Sliide logo mark centred on a solid brand
background, from the first visible frame until the app's first screen is drawn. No blank or white window SHALL be
shown before it.

#### Scenario: Android cold start
- **WHEN** the app is launched on Android with no existing process
- **THEN** the system splash shows the Sliide mark centred on the brand background, and no blank window precedes it

#### Scenario: Android before API 31
- **WHEN** the app is cold-started on a device running Android 8.0–11
- **THEN** the same centred mark on the brand background is shown, matching the Android 12+ splash

#### Scenario: iOS cold start
- **WHEN** the app is launched on iOS with no existing process
- **THEN** the launch screen shows the Sliide mark centred on the brand background instead of an empty screen

### Requirement: Splash follows light and dark mode
The splash background SHALL be midnight in light mode and near-black ink in dark mode on both platforms, and the mark
SHALL be champagne in both. The mark SHALL keep a contrast ratio of at least 3:1 against the background in both
modes.

#### Scenario: Dark mode launch
- **WHEN** the app is cold-started while the device is in dark mode
- **THEN** the splash background is the dark ink colour and the champagne mark is shown on it

#### Scenario: Mark contrast
- **WHEN** the champagne mark is measured against either splash background
- **THEN** its contrast ratio is at least 3:1

### Requirement: Splash hands over without a flash
When the first app screen is ready, the splash SHALL be replaced by it without a frame of a different background
colour between the two. After hand-off, the app's window SHALL use the app theme for the current light/dark mode.

#### Scenario: Hand-off on Android
- **WHEN** the splash is dismissed on Android
- **THEN** the user feed appears directly, with no white or mismatched window frame in between

#### Scenario: Warm start
- **WHEN** the app is brought back from the background while its activity still exists
- **THEN** no splash is shown and the previous screen is restored

### Requirement: Launcher icons carry the mark
The app's launcher icon on both platforms SHALL be the Sliide mark on the midnight background (on iOS, the dark
appearance variant uses the dark ink background and the tinted variant is the bare mark). On Android, the mark
SHALL stay inside the adaptive-icon safe zone for every launcher mask shape, and a monochrome layer SHALL be provided
for themed icons.

#### Scenario: Android adaptive icon
- **WHEN** the launcher draws the icon with a circle, squircle or rounded-square mask
- **THEN** the full "S" is visible and is not clipped by the mask

#### Scenario: Android themed icon
- **WHEN** themed icons are enabled on Android 13 or later
- **THEN** the launcher shows the "S" mark tinted to the system theme instead of a generic fallback

#### Scenario: iOS home screen
- **WHEN** the app is installed on iOS
- **THEN** the home-screen icon shows the champagne "S" on midnight, not the template hexagon

### Requirement: One source for the mark
Every platform asset that shows the mark, including splash images and launcher icons on both platforms, SHALL be
produced from the same mark geometry, so the mark is identical across platforms and can be regenerated after a change.

#### Scenario: Regenerating assets
- **WHEN** a developer changes the mark geometry and runs the asset generator
- **THEN** the Android vector path and all iOS PNG assets are regenerated and stay consistent with each other
