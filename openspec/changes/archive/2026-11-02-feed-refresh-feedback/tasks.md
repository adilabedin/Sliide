# Tasks

## 1. Indicator only for user pulls

- [x] 1.1 In `UsersViewModel`, replace `RefreshState.isRunning` with `running: RefreshTrigger?` (private enum `Launch`, `Pull`, `Retry`). Make `startRefresh(trigger)` record it: `onStart` passes `Launch`, `OnRefresh` passes `Pull`, `OnRetry` passes `Retry`. A `Pull` while a job is active upgrades `running` to `Pull` without starting a second refresh (design D1, D2). `buildState` uses `running != null` for `Loading` and `running == Pull && cards.isNotEmpty()` for `isRefreshing`. Verify `./gradlew :users:presentation:compileKotlinMetadata` (or the module's `testAndroidHostTest` compile) succeeds
- [x] 1.2 Update `UsersViewModelTest`:
  - Rewrite `pullWhileRefreshingDoesNotStartAnotherRefresh` so that `isRefreshing` is false after launch with cached users, true after a pull during the gated launch refresh, `refreshCount` stays 1, and it is false after the gate completes.
  - Add tests for: a gated launch refresh with cached users shows no indicator; users arriving during the first launch refresh give `Users` content with no indicator; a pull when idle shows the indicator while running and clears it after.

  Verify `./gradlew :users:presentation:testAndroidHostTest` passes

## 2. Retry on the empty state

- [x] 2.1 In `UsersScreen`'s `FeedContent.Empty` branch, pass `actionLabel = stringResource(Res.string.users_retry)` and `onAction = { onAction(UsersAction.OnRetry) }` to `FullScreenMessage` (design D3). Verify the four `UsersScreenEmpty*` previews render the Retry button (Android Studio preview, or `render-compose-preview` through android-cli)
- [x] 2.2 Add `UsersViewModelTest` cases:
  - Retry from `Empty` shows `Loading` with `isRefreshing` false while gated, then `Users` when the cache gets users.
  - Retry from `Empty` returns to `Empty` when the cache stays empty.

  Verify `./gradlew :users:presentation:testAndroidHostTest` passes
- [x] 2.3 Add a `UsersScreenTest` case: with `FeedContent.Empty`, a "Retry" button is displayed and clicking it dispatches `UsersAction.OnRetry`. Verify `./gradlew :users:presentation:connectedAndroidDeviceTest` passes on an emulator

## 3. Documentation

- [x] 3.1 In the README "UI: feed, profile, delete with Undo" section, note that the pull indicator appears only for user pulls (the launch refresh is silent: shimmer or cached cards), and that the empty state offers Retry. Verify the wording matches the code and the updated `user-feed` spec

## 4. Verification

- [x] 4.1 Run `./gradlew ktlintCheck` and verify no violations
- [x] 4.2 Run `./gradlew build` and `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` and verify both succeed
- [x] 4.3 For the affected module `:users:presentation` (and `:androidApp`, whose smoke test launches the feed), run all unit tests (`testAndroidHostTest`, `iosSimulatorArm64Test`) and all instrumented tests (`:users:presentation:connectedAndroidDeviceTest`, `:androidApp:connectedDebugAndroidTest` on an emulator). Verify all pass, with none skipped or weakened
- [x] 4.4 Manual check on an emulator:
  - A cold start with a cached feed shows no spinner.
  - With the app's data cleared, a cold start shows the shimmer and then cards with no spinner.
  - A pull shows the spinner.
  - With networking off and the data cleared, the empty or error state appears; tap Retry and confirm the shimmer appears.

  Verify each by screen recording or screenshots

## Workflow follow-up

- After merge, archive with `/opsx:archive`, which merges the MODIFIED requirements into `openspec/specs/user-feed/spec.md`.
