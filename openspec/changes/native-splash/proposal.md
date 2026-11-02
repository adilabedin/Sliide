# Proposal

## Why

On a cold start both apps show a blank system window. Android uses the plain `Theme.Material.Light` window, and iOS
uses the empty launch screen that Xcode generates. The only logos are the template ones: the Android robot launcher
icon and the default KMP hexagon on iOS. The brief is graded on UX polish, so the first frame should look branded and
match the app's midnight/champagne palette in light and dark mode.

## What Changes

- Add a Sliide logo mark: a rounded-stroke champagne "S" monogram. It is drawn once on a 108×108 adaptive-icon grid,
  and every platform asset is generated from that one geometry by a small checked-in script, so the assets stay in
  sync.
- **Android:** use the platform splash screen (the AndroidX SplashScreen compat API, so API 26–30 match the
  Android 12+ behaviour). The splash shows the mark centred on a midnight background in light mode and an ink
  background in dark mode, then hands over to the app theme. Today's raw `@android:style/Theme.Material.Light.NoActionBar`
  becomes a project theme that also follows night mode.
- **iOS:** replace the generated empty launch screen with a declared `UILaunchScreen` that uses an asset-catalog
  logo image and a background colour set with light and dark variants. No storyboard is added.
- Replace the template launcher icons with the mark. On Android that means the adaptive icon (background + foreground)
  plus a monochrome layer for themed icons; the unused legacy PNG mipmaps are removed (minSdk 26). On iOS the
  1024 px AppIcon is replaced.
- README: add a short "App branding & splash" note.

## Capabilities

### New Capabilities
- `app-branding`: The app's launch experience and identity assets. It covers the native splash shown on cold start
  on Android and iOS, its light/dark appearance, the hand-off to the first app frame, and the launcher icons
  that carry the same mark.

### Modified Capabilities
<!-- None: design-system covers in-app Compose theming; splash and icons are platform-level and do not change its requirements. -->

## Impact

- `:androidApp`: manifest theme, new `res/values` and `res/values-night` themes and colours, new drawables, launcher-icon
  resources (the PNG mipmaps are removed), and `MainActivity` (installs the splash). It gains one dependency,
  `androidx.core:core-splashscreen`, added through `libs.versions.toml`.
- `iosApp`: `Info.plist` (`UILaunchScreen`), `Assets.xcassets` (new `LaunchLogo` image set, `LaunchBackground` colour
  set, replaced AppIcon), and `project.pbxproj` (`INFOPLIST_KEY_UILaunchScreen_Generation` removed).
- New `tools/brand/` asset-generator script (Python + Pillow), used only by developers and not part of the build.
- No shared KMP code, Compose UI, or `expect`/`actual` changes.
