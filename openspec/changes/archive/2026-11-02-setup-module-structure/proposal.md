# Proposal

## Why

All code currently sits in the template's single `:shared` module, with hand-written Gradle config and placeholder `Greeting`/`Platform` code. Every upcoming feature (user feed, add user, delete with undo, adaptive layout) needs to know where its domain, data and presentation code lives. It also needs Koin and Room KMP already wired. Setting up the module structure first stops each feature change from re-arguing the layout or re-doing the build configuration.

## What Changes

- Add an included `build-logic` build with Gradle 9 convention plugins:
  - KMP library
  - CMP library
  - CMP feature
  - CMP application
  - Android application
  - Room

  Module build files become a few lines each.
- **BREAKING (build/iOS):** Rename `:shared` to `:composeApp`. This is the shared KMP library that holds the app shell and DI assembly. The iOS framework becomes `ComposeApp`, and the Xcode build script, the Swift `import` and the Compose resources package change with it. Remove the template `Greeting`/`Platform` code.
- Add `:core:designsystem`, the app's Material 3 theme with light and dark colour schemes and typography. The app shell and the feature previews use it.
- Add `:core:database`, the Room KMP database: `@Database`, `UserEntity`, `UserDao`, and the per-platform database builder.
- Add the users feature as three modules:
  - `:users:domain`: the `User` model, the `UserRepository` interface, and the `Result`/`DataError` types.
  - `:users:data`: the repository implementation backed by `UserDao`, the entity↔domain mappers, and a Koin module.
  - `:users:presentation`: a stub MVI `UsersViewModel` and a placeholder users screen, plus a Koin module.
- Wire Koin end to end:
  - each module exposes its own Koin `Module`
  - `:composeApp` assembles them
  - Android starts Koin from a new `Application` subclass
  - iOS starts Koin before `MainViewController`
- Move all module dependencies and plugins into `gradle/libs.versions.toml`: Koin, Room, KSP, sqlite-bundled, coroutines, kotlinx-serialization, and the test libraries (AssertK, Turbine, coroutines-test).
- Update CLAUDE.md, `openspec/config.yaml` and README.md to describe the agreed module shape and the `:composeApp` iOS link task.

**Assumptions** (agreed with the user):
- There is only one feature for now, so there are no `:core:domain`, `:core:data` or `:core:presentation` modules. Shared code moves to `core` only when a second feature needs it.
- Room lives in `:core:database`, not inside the feature data module.

**Non-goals:**
- Ktor, the GoRest client and bearer-token handling. These come in the feed change.
- The real user feed, add-user form, delete/undo, adaptive layout and navigation.

## Capabilities

### New Capabilities

- `module-architecture`: the project's module layout, the allowed dependency directions between modules, the shared convention-plugin build setup, and how the Koin DI graph is assembled and started on Android and iOS.
- `design-system`: the shared Material 3 app theme (light and dark colour schemes and typography) that every screen and preview is rendered in.

### Modified Capabilities

<!-- None: openspec/specs/ is empty. -->

## Impact

- **Build:**
  - `settings.gradle.kts` (adds `includeBuild("build-logic")`, type-safe project accessors and the new modules)
  - the root `build.gradle.kts` (new plugins declared with `apply false`)
  - `gradle/libs.versions.toml`
  - new `build-logic/` directory
  - every module's `build.gradle.kts`
- **Modules:** `:shared` is removed and replaced by `:composeApp`. New modules: `:core:designsystem`, `:core:database`, `:users:domain`, `:users:data`, `:users:presentation`. `:androidApp` gains an `Application` class and depends on `:composeApp`.
- **iOS:**
  - `iosApp.xcodeproj` build phase (`:composeApp:embedAndSignAppleFrameworkForXcode`)
  - `ContentView.swift` (`import ComposeApp`)
  - `iOSApp.swift` (Koin start)
- **Tooling:**
  - The ktlint baseline moves with the module.
  - `testAndroidHostTest` (used by the pre-push hook) must exist in every new module.
  - The CLAUDE.md verification gate becomes `:composeApp:linkDebugFrameworkIosSimulatorArm64`.
- **JVM target:** goes from 11 to 17 in every module, matching the skill and AGP 9.
