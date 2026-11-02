# Design

## Context

- **Android (`:androidApp`):** the manifest's application theme is the platform `@android:style/Theme.Material.Light.NoActionBar`.
  It has no night variant and no project `themes.xml`. `MainActivity` calls `enableEdgeToEdge()` and then `setContent { App() }`.
  The launcher icon is the template adaptive icon (Android robot foreground on a green grid). With minSdk 26, the
  `mipmap-anydpi-v26` adaptive icon always wins, so the density PNGs are dead weight.
- **iOS (`iosApp`):** `GENERATE_INFOPLIST_FILE = YES` with `INFOPLIST_KEY_UILaunchScreen_Generation = YES` in both
  build configurations, which produces an empty `UILaunchScreen` dictionary. `Info.plist` holds only the background-task
  keys. `AppIcon.appiconset` has a 1024 px template hexagon and empty dark/tinted slots.
- **Palette** (`:core:designsystem` `Color.kt`): Midnight `#1E2A4A`, Ink900 `#0D0E12` (dark background), Porcelain
  `#F6F6F3` (light background), ChampagneLight `#D9BF86`. Champagne on midnight measures 7.9:1 and on Ink900 10.8:1,
  both well above the 3:1 needed for a graphic.
- The challenge rules say to use `expect`/`actual` only for real platform differences. A launch screen is entirely
  platform-owned and is drawn before any Kotlin/Compose code runs.

## Goals / Non-Goals

**Goals:**
- Show a branded first frame on both platforms that follows the system light/dark mode and hands over to Compose with
  no colour flash.
- One mark geometry with reproducible assets.

**Non-Goals:**
- No animated splash icon (AVD) and no exit animation beyond the platform default.
- No Compose-drawn splash or extra "loading" screen after the native one.
- No holding the splash while data loads (see D2).
- No iPad- or Android-TV-specific launch assets.

## Decisions

### D1. Native splash only, no shared Compose splash
Use the platform mechanisms: the AndroidX SplashScreen compat library on Android and the `UILaunchScreen` Info.plist
dictionary on iOS. A Compose-drawn splash in `:composeApp` would appear only *after* the system window, so users would
see two splashes. It would also go against Android 12+'s mandatory system splash. Nothing in shared code changes,
so no `expect`/`actual` is needed.

### D2. Splash is dismissed on the first frame (no keep-on-screen condition)
Room is the single source of truth, so the first Compose frame already shows the cached feed or the shimmer. Holding
the splash until the network answers would slow every launch to fix a problem the shimmer already solves.
`installSplashScreen()` is called before `super.onCreate()` and gets no `setKeepOnScreenCondition`.

### D3. Android themes
- `Theme.Sliide.Starting` (parent `Theme.SplashScreen`) is the activity's launch theme in the manifest. It sets:
  `windowSplashScreenBackground = @color/splash_background`, `windowSplashScreenAnimatedIcon = @drawable/ic_logo_mark`,
  `postSplashScreenTheme = @style/Theme.Sliide`.
- `Theme.Sliide` (in `values`: parent `android:Theme.Material.Light.NoActionBar`; in `values-night`: parent
  `android:Theme.Material.NoActionBar`) replaces the raw platform theme as the application theme. Its
  `android:windowBackground` is the in-app background (Porcelain light / Ink900 dark). After the splash exits, the window
  therefore matches Compose's first frame, which meets the no-flash hand-off requirement. Today the window is always the
  light platform background, which flashes white before Compose draws in dark mode.
- Colours live in `values/colors.xml` and `values-night/colors.xml`: `splash_background` (Midnight / Ink900),
  `window_background` (Porcelain / Ink900), `logo_mark` (ChampagneLight), `icon_background` (Midnight). They copy the
  `Color.kt` hex values. Android XML resources cannot read Compose colours, so a comment in each file points to
  `Color.kt` as the source of truth.
- *Alternative considered:* setting `Theme.SplashScreen` on the `<application>` element. Rejected: the starting theme
  belongs on the launcher activity, and the application keeps a plain theme for any future activities.

### D4. One drawable for the mark on Android
`ic_logo_mark.xml` is a 108×108 dp vector. It is a single path with a `strokeWidth` and round caps in `@color/logo_mark`,
and it is used in three places: as the adaptive icon foreground, as the monochrome layer (the launcher tints it), and as
the splash icon. The geometry spans viewport units 24–84 (radius ≈ 30 from the centre). That fits the 66 dp adaptive
safe zone (radius 33) and the Android 12 splash mask (a 192 dp circle inside a 288 dp icon, i.e. radius 36 of 54). So
one drawable satisfies every placement. The adaptive icon background is `@color/icon_background`.

The adaptive-icon XMLs move from `mipmap-anydpi-v26` to `mipmap-anydpi` (minSdk 26 makes the qualifier redundant).
The density PNGs and the template `drawable-v24/ic_launcher_foreground.xml` and `drawable/ic_launcher_background.xml`
are deleted.

### D5. iOS launch screen through Info.plist, not a storyboard
Add to `Info.plist`:
`UILaunchScreen = { UIImageName: LaunchLogo, UIColorName: LaunchBackground, UIImageRespectsSafeAreaInsets: false }`.
Then remove `INFOPLIST_KEY_UILaunchScreen_Generation` from both build configurations in `project.pbxproj`, so the
generated empty dictionary cannot compete with the declared one.
- `LaunchBackground.colorset`: Midnight with a dark-appearance variant of Ink900.
- `LaunchLogo.imageset`: a transparent PNG of the mark at @1x/@2x/@3x. iOS draws the launch image at its point size
  without scaling, so the canvas is 120 pt (120/240/360 px). The mark then occupies about the same share of the screen
  as on Android.
- *Alternative considered:* `LaunchScreen.storyboard`. Rejected: it adds an Interface Builder file for a centred image
  on a colour, which the plist form expresses directly and which Xcode's own templates now use.

### D6. iOS AppIcon
Replace `app-icon-1024.png` with the champagne mark on Midnight, and fill the existing empty slots: dark (the mark on
Ink900) and tinted (the mark in white on a transparent background, so iOS can tint it). Keeping the 1024 px
single-size format is enough because Xcode derives the other sizes.

### D7. Asset generator `tools/brand/generate_brand_assets.py`
A dev-only Python 3 script that depends only on Pillow. It defines the mark once as cubic Bézier segments plus a stroke
width in the 108 viewport. It then:
1. writes `androidApp/src/main/res/drawable/ic_logo_mark.xml` (path data and stroke from the same constants);
2. rasterises the iOS PNGs at 4× supersampling, stamping a round brush along the sampled curve so the joins and caps
   match Android's round caps, then downsampling with Lanczos;
3. asserts that the champagne-to-background contrast is ≥ 3:1 for both splash backgrounds. This is the testable form of
   the contrast scenario.

The generated files are committed, so the build never runs the script. Adding the script to Gradle was rejected: it
would make every build depend on Python and Pillow to produce assets that change only when the logo changes.

### D8. Verification of the hand-off
Add a check to the existing instrumented smoke test, or a new `SplashThemeTest`. After `MainActivity` launches, it
asserts that the activity's resolved theme is `Theme.Sliide`, by reading the theme's `android:windowBackground`
against `@color/window_background`. That proves `postSplashScreenTheme` was applied, which is what prevents the
mismatched frame. The visual check of the splash itself (icon, colours, light/dark, API < 31) is manual through
android-cli on two emulator API levels and on the iOS simulator.

## Risks / Trade-offs

- [iOS caches launch screens, so changes may not show on a simulator that already has the app] → Delete the app (or
  `xcrun simctl erase`) before the manual check; this is noted in the README.
- [Android 12+ splash icon may not appear when the app is started from Android Studio's Run] → Do the manual check by
  tapping the launcher icon or using `adb shell am start` after a force-stop.
- [The XML colours copy `Color.kt` and could drift from it] → A comment in each `colors.xml` points to `Color.kt`.
  The generator's contrast assertion uses the same hex values.
- [The rasterised iOS PNGs differ by sub-pixel antialiasing from Android's vector rendering] → Accepted. Both come
  from the same Bézier geometry at 4× supersampling, and the difference is not visible at icon sizes.
- [Deleting the legacy PNG mipmaps] → They are unreachable at minSdk 26. If minSdk were ever lowered below 26, AAPT
  would fail the build on a missing icon rather than ship silently without one.
