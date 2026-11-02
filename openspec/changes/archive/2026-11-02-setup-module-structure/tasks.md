# Tasks

## 1. Version catalog and build-logic

- [x] 1.1 Using the `android-version-catalog` skill (and `android-cli` for lookups), add the versions, libraries and plugins listed below to `gradle/libs.versions.toml`, with a version compatible with Kotlin 2.4.21 / AGP 9.1.1 for each:
  - Koin BOM: core, compose, compose-viewmodel, android, test
  - Room: runtime, compiler, gradle plugin
  - KSP, matched to Kotlin 2.4.21
  - sqlite-bundled, coroutines core and test, kotlinx-serialization (json + plugin)
  - AssertK, Turbine
  - androidx compose ui-test-junit4/manifest
  - `-gradlePlugin` artifacts for AGP, Kotlin, the Compose compiler, KSP and Room
  - `convention-*` plugin aliases

  Verify: `./gradlew help` resolves the catalog.
- [x] 1.2 Create `build-logic/` with:
  - `settings.gradle.kts`, sharing `../gradle/libs.versions.toml`
  - `gradle.properties`
  - `convention/build.gradle.kts`, with `compileOnly` plugin artifacts, JVM 17 and `validatePlugins` strict

  Add `includeBuild("build-logic")` and `enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")` to `settings.gradle.kts`. Verify: `./gradlew -p build-logic validatePlugins` passes.
- [x] 1.3 Implement the helpers (`ProjectExt.libs`, `pathToPackageName`, `pathToResourcePrefix`, KMP/Android config functions) and the `kmp.library`, `cmp.library`, `cmp.feature`, `cmp.application`, `android.application.compose` and `room` plugins as described in design.md Decision 2:
  - targets `iosArm64`/`iosSimulatorArm64`
  - JVM 17
  - `withHostTest` + `withDeviceTestBuilder`
  - preview tooling via `androidMainImplementation`
  - Room KSP for android/iosArm64/iosSimulatorArm64

  Verify: `./gradlew -p build-logic build` passes.
- [x] 1.4 Add the new third-party plugins (KSP, Room, serialization) to the root `build.gradle.kts` with `apply false`. Extend the root ktlint block with the `/build/` exclude filter, and add a root `.editorconfig` with `ktlint_function_naming_ignore_when_annotated_with = Composable`. Verify: `./gradlew ktlintCheck` no longer reports files under `build/generated`.

## 2. `:core:designsystem`

- [x] 2.1 Create `core/designsystem` with `cmp.library`. Add `SliideTheme` (light/dark `ColorScheme`, `Typography`, `isSystemInDarkTheme()` default, no dynamic colour) in `commonMain`, plus a theme preview in light and in dark (`android-compose-components`). Register the module in settings. Verify: `./gradlew :core:designsystem:build` passes and both previews render (android-cli `render-compose-preview`).

## 3. `:core:database`

- [x] 3.1 Create `core/database` with `kmp.library` + `room`. Add:
  - `UserEntity` (id, name, email, gender, status, createdAtEpochMillis)
  - `UserDao` (`observeAll()` ordered by `createdAt DESC`, `upsert`, `deleteById`)
  - `SliideDatabase` v1 with `@ConstructedBy(SliideDatabaseConstructor)` and the `expect object` constructor

  Verify: `./gradlew :core:database:kspAndroidMain` (or the equivalent KSP task) generates the DAO implementation and the schema JSON under `core/database/schemas/`.
- [x] 3.2 Add `expect val platformDatabaseModule` with actuals:
  - Android: `Room.databaseBuilder(androidContext(), …)`
  - iOS: path in the `NSDocumentDirectory`

  Add the common `databaseModule`, which builds the DB with `BundledSQLiteDriver` + `Dispatchers.IO` and provides `UserDao`. Verify: `./gradlew :core:database:compileKotlinIosSimulatorArm64 :core:database:compileAndroidMain` passes.
- [x] 3.3 Write `androidDeviceTest` `UserDaoTest` against an in-memory Room DB (`android-integration-testing`, Turbine) covering the module-architecture "Local user storage" scenarios: insert is observed, delete-by-id is observed, and ordering by `createdAt` is newest first. Verify: `./gradlew :core:database:connectedAndroidDeviceTest` passes on an emulator started via `android-cli`.

## 4. `:users:domain`

- [x] 4.1 Create `users/domain` with `kmp.library` and no project deps. Add:
  - `User`, `Gender`, `UserStatus`, `UserRepository.observeUsers(): Flow<List<User>>`
  - `Error`, `Result<D, E>`, `EmptyResult`, `DataError` (Local) and the `map`/`onSuccess`/`onFailure`/`asEmptyResult` helpers (`android-error-handling`)

  Verify: `./gradlew :users:domain:build` passes. `./gradlew :users:domain:dependencies` shows no project dependencies.
- [x] 4.2 Add `commonTest` tests for the `Result` helpers (`android-unit-testing`, AssertK). Verify: `./gradlew :users:domain:testAndroidHostTest :users:domain:iosSimulatorArm64Test` passes.

## 5. `:users:data`

- [x] 5.1 Create `users/data` with `kmp.library`, depending on `users:domain` + `core:database`. Add:
  - the `UserEntity ↔ User` mappers (enum ↔ string, epoch millis ↔ `Instant`)
  - `RoomUserRepository : UserRepository` (`android-data-layer`)
  - `usersDataModule`

  Verify: `./gradlew :users:data:build` passes.
- [x] 5.2 Add `commonTest` tests:
  - mapper round-trip, including every enum value
  - `RoomUserRepository` with a `FakeUserDao` and Turbine, checking that it emits mapped users and re-emits on change

  Verify: `./gradlew :users:data:testAndroidHostTest :users:data:iosSimulatorArm64Test` passes.

## 6. `:users:presentation`

- [x] 6.1 Create `users/presentation` with `cmp.feature`, depending only on `users:domain` (plus designsystem via the plugin). Add:
  - `UserUi`, `UsersState`, `UsersAction`, `UsersViewModel` (`stateIn(WhileSubscribed(5_000))`), per `android-presentation-mvi`
  - `UsersRoot` (`koinViewModel()`, `collectAsStateWithLifecycle()`)
  - stateless `UsersScreen` (title, empty state or `LazyColumn` of name/email), with strings as Compose resources
  - `usersPresentationModule`

  Verify: `./gradlew :users:presentation:build` passes, and its `dependencies` report shows no `data`/`database` modules.
- [x] 6.2 Add `UsersScreen` previews (`android-compose-components`): empty, loading and populated states, each in light and dark, wrapped in `SliideTheme`, with realistic sample users. `UsersRoot` gets no preview. Verify: each preview renders with android-cli `render-compose-preview`.
- [x] 6.3 Add `commonTest` `UsersViewModelTest` with a `FakeUserRepository`, `Dispatchers.setMain` (test dispatcher) and Turbine. It covers: the initial loading state, the populated state mapped from domain users, and the empty list. Verify: `./gradlew :users:presentation:testAndroidHostTest :users:presentation:iosSimulatorArm64Test` passes.

## 7. Rename `:shared` → `:composeApp` and wire the app shell

- [x] 7.1 Move `shared/` to `composeApp/` and apply `cmp.application` (framework `ComposeApp`, static). Then:
  - Delete the template `Greeting`, `GreetingUtil`, `Platform*`, template tests, the `compose-multiplatform.xml` drawable and `shared/ktlint-baseline.xml`.
  - Update `settings.gradle.kts` includes for all new modules.

  Verify: `./gradlew projects` lists exactly the modules in design.md Decision 1, and no `:shared` module.
- [x] 7.2 Add `initKoin(config)`, which assembles `databaseModule`, `platformDatabaseModule`, `usersDataModule` and `usersPresentationModule`. Rewrite `App()` as `SliideTheme { UsersRoot() }` and keep `MainViewController()` in `iosMain`. Verify: `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` passes.
- [x] 7.3 Add `androidHostTest` `KoinGraphTest` using `koin-test` `Module.verify(extraTypes = …)` over the assembled modules (`android-di-koin`). Verify: `./gradlew :composeApp:testAndroidHostTest` passes.
- [x] 7.4 Rework `:androidApp` to the `android.application.compose` convention, depending only on `:composeApp`:
  - add `SliideApplication` (`initKoin { androidContext(...) }`) and register it in the manifest
  - simplify `MainActivity` to `setContent { App() }` with no preview
  - delete `androidApp/ktlint-baseline.xml` and fix any violations it hid

  Verify: `./gradlew :androidApp:assembleDebug` passes.
- [x] 7.5 Add `androidApp/src/androidTest` `AppLaunchSmokeTest` (`android-ui-testing`, `createAndroidComposeRule<MainActivity>()`), which asserts that the users screen title is displayed. This proves Koin started and the ViewModel resolved. Verify: `./gradlew :androidApp:connectedDebugAndroidTest` passes on the emulator.
- [x] 7.6 Update iOS:
  - the Xcode build phase becomes `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode`
  - `ContentView.swift` uses `import ComposeApp`
  - `iOSApp.swift` calls `KoinKt.doInitKoin()` in `init()`

  Grep `iosApp/` for any remaining `Shared`/`:shared`. Verify: `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator -destination 'platform=iOS Simulator,name=<available iPhone>' build` succeeds, and the app launches in the simulator showing the users screen, in light and in dark appearance.
- [x] 7.7 Launch the Android app on the emulator via `android-cli`. Confirm the users screen renders in light and in dark mode (`android screen capture`) and that logcat shows no Koin errors.

## 8. Documentation

- [x] 8.1 Update the docs:
  - **CLAUDE.md:** module target shape (Decision 1 table), dependency rules, iOS gate task `:composeApp:linkDebugFrameworkIosSimulatorArm64`, the note that ktlint baselines are removed
  - **`openspec/config.yaml`:** the "Module architecture" section, with the same shape
  - **README.md:** replace the `/shared` template section with the module map, convention plugins, test commands (`testAndroidHostTest`, `iosSimulatorArm64Test`, device tests), and a short "how AI accelerated this" note for the module setup

  Verify: grep finds no stale `:shared` references or `:core:domain`/`:core:data`/`:core:presentation` mentions outside `openspec/changes/`.

## 9. Final verification (all affected modules)

- [x] 9.1 Run `./gradlew build`. Verify: every module compiles, and all unit tests pass (host and iOS simulator) for `:core:designsystem`, `:core:database`, `:users:domain`, `:users:data`, `:users:presentation`, `:composeApp` and `:androidApp`.
- [x] 9.2 Run `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64`. Verify: it succeeds.
- [x] 9.3 Run `./gradlew ktlintCheck`. Verify: it passes with no baseline files present.
- [x] 9.4 With an emulator started via `android-cli`, run every instrumented test for the affected modules: `./gradlew :core:database:connectedAndroidDeviceTest :androidApp:connectedDebugAndroidTest`, plus `connectedAndroidDeviceTest` for any other module that has device tests. Verify: all pass, with none skipped or disabled.

## Workflow follow-up

- Run `/opsx:verify` against the specs, then archive the change once reviewed.
