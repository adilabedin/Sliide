# Tasks

## 1. Mark and asset generator

- [x] 1.1 Add `tools/brand/generate_brand_assets.py` (Python 3 + Pillow only) with the mark geometry as constants (design D7). It writes `androidApp/src/main/res/drawable/ic_logo_mark.xml` and the iOS PNGs (LaunchLogo @1x/@2x/@3x at 120 pt; AppIcon 1024 light, dark and tinted), and asserts champagne contrast ≥ 3:1 against Midnight and Ink900. Verify `python3 -I tools/brand/generate_brand_assets.py` exits 0, prints both contrast ratios, and the rendered 1024 PNG shows a clean "S" with no joint artefacts
- [x] 1.2 Add a short `tools/brand/README.md` (what it generates, the `pip install pillow` prerequisite, how to regenerate) and verify the documented command runs as written

## 2. Android splash and themes

- [x] 2.1 Add `androidx-core-splashscreen` to `gradle/libs.versions.toml` (latest stable, looked up through android-version-catalog / android-cli) and `implementation(libs.androidx.core.splashscreen)` to `androidApp/build.gradle.kts`; verify `./gradlew :androidApp:assembleDebug` succeeds
- [x] 2.2 Add `values/colors.xml` and `values-night/colors.xml` (`splash_background`, `window_background`, `logo_mark`, `icon_background`, with a comment pointing to `Color.kt`) and `values/themes.xml` + `values-night/themes.xml` with `Theme.Sliide` and `Theme.Sliide.Starting` (design D3). Set the application theme to `@style/Theme.Sliide` and the `MainActivity` theme to `@style/Theme.Sliide.Starting` in the manifest; verify `./gradlew :androidApp:lintDebug` reports no new errors
- [x] 2.3 Call `installSplashScreen()` before `super.onCreate()` in `MainActivity` (no keep-on-screen condition, design D2) and verify `./gradlew :androidApp:assembleDebug` succeeds
- [x] 2.4 Add `SplashThemeTest` (instrumented): launch `MainActivity` and assert that the resolved `android:windowBackground` colour equals `@color/window_background`, which proves `postSplashScreenTheme` was applied (design D8). Verify it passes with `./gradlew :androidApp:connectedAndroidTest` on an emulator started through android-cli

## 3. Android launcher icon

- [x] 3.1 Move the adaptive icons to `mipmap-anydpi/ic_launcher.xml` and `ic_launcher_round.xml`, with background `@color/icon_background`, foreground `@drawable/ic_logo_mark` and monochrome `@drawable/ic_logo_mark`. Delete the density PNG mipmaps, `drawable-v24/ic_launcher_foreground.xml` and `drawable/ic_launcher_background.xml` (design D4). Verify `./gradlew :androidApp:assembleDebug` succeeds and `grep -r ic_launcher_background androidApp/src` finds nothing
- [x] 3.2 Manual check through android-cli on an API 34+ emulator: tap the launcher icon after a force-stop and capture the screen. Verify the splash shows the centred champagne "S" on midnight (light) and on ink (dark, after `adb shell cmd uimode night yes`), that the feed follows with no white frame, that the circle-masked launcher icon shows the whole "S", and that the themed icon shows the tinted "S"
- [x] 3.3 Repeat the splash part of 3.2 on an API 30 emulator and verify the compat splash matches (centred mark, brand background, no blank window)

## 4. iOS launch screen and app icon

- [x] 4.1 Add `Assets.xcassets/LaunchBackground.colorset` (Midnight, dark appearance Ink900) and `LaunchLogo.imageset` (the generated @1x/@2x/@3x PNGs), and replace the AppIcon images and `Contents.json` filenames for the light, dark and tinted slots (design D5, D6). Verify that `xcrun actool` (or the Xcode build in 4.3) reports no asset-catalog warnings
- [x] 4.2 Add the `UILaunchScreen` dictionary (`UIImageName` LaunchLogo, `UIColorName` LaunchBackground, `UIImageRespectsSafeAreaInsets` false) to `iosApp/iosApp/Info.plist`, and remove `INFOPLIST_KEY_UILaunchScreen_Generation` from both configurations in `project.pbxproj`. Verify `plutil -lint iosApp/iosApp/Info.plist` passes
- [x] 4.3 Build and launch on an iOS simulator (`xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -destination 'platform=iOS Simulator,name=iPhone 16' build`, then install/launch with `xcrun simctl`, after deleting any previous install so the launch screen is not cached). Verify via `xcrun simctl io booted screenshot` that the launch screen shows the mark on midnight, and on ink in dark mode (`xcrun simctl ui booted appearance dark`), and that the home-screen icon is the new mark

## 5. Documentation

- [x] 5.1 Add an "App branding & splash" subsection to the README Architecture section (native splash per platform and why no Compose splash, dismissal on first frame, the single-source mark and generator, the iOS launch-screen cache caveat), plus a short "Example: native splash" entry under "How AI is used in this project". Verify every file, theme and asset name mentioned exists

## 6. Verification

- [x] 6.1 Run `./gradlew ktlintCheck` and verify no violations
- [x] 6.2 Run `./gradlew build` and `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` and verify both succeed
- [x] 6.3 For the affected module `:androidApp`, run all unit tests and all instrumented tests (`./gradlew :androidApp:connectedAndroidTest` on an emulator started through android-cli). Verify all pass, including `AppLaunchSmokeTest`, `BackgroundSyncScheduledTest` and the new `SplashThemeTest`, with none skipped or weakened
- [x] 6.4 Commit on `feature/native-splash`, push (the pre-push hook runs `testAndroidHostTest`), and open a PR against `main` that lists the before/after splash and icon screenshots from 3.2–4.3

## Workflow follow-up

- After the PR merges, archive the change with `/opsx:archive`, which creates `openspec/specs/app-branding/spec.md`.
