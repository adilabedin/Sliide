# Design

## Context

Current state, as observed in the repo:
- Two modules exist: `:shared`, a KMP library (`com.android.kotlin.multiplatform.library`, Compose MP, framework `Shared`), and `:androidApp`, the application module. Both have hand-written Gradle config with JVM target 11.
- Targets are `iosArm64` and `iosSimulatorArm64`, with no desktop target and no `iosX64`.
- Gradle 9.5.1, AGP 9.1.1, Kotlin 2.4.21, Compose MP 1.12.2.
- `:shared` already declares `withHostTest { isIncludeAndroidResources = true }` and `withDeviceTestBuilder { sourceSetTreeName = "test" }` with `AndroidJUnitRunner`. These provide the `testAndroidHostTest` and device-test tasks that the pre-push hook and `config.yaml` rely on.
- The root `build.gradle.kts` applies ktlint to every subproject, with a per-module `ktlint-baseline.xml`. The current `shared` baseline holds entries for files under `build/generated/...` and for `@Composable` `function-naming`. So ktlint currently lints generated code and does not understand Compose naming.
- The Xcode build phase runs `./gradlew :shared:embedAndSignAppleFrameworkForXcode`, and Swift does `import Shared`.
- `openspec/specs/` is empty.

The agreed module shape (see proposal.md) deliberately differs from the `:core:domain` / `:core:data` / `:core:presentation` modules listed in CLAUDE.md and `config.yaml`. See Decision 1.

## Goals / Non-Goals

**Goals:**
- Convention plugins carry every shared Gradle decision: targets, SDKs, JVM target, test source sets, Compose and preview tooling, Koin, Room/KSP. A module's `build.gradle.kts` holds only its plugin alias and its project dependencies.
- The specs' dependency rules hold by construction. A layer cannot reach a module it isn't supposed to, because it is never given that dependency.
- Each layer is exercised end to end by a thin vertical slice:
  1. DAO
  2. repository
  3. ViewModel
  4. placeholder screen
  5. app shell

  This proves the structure compiles, links and resolves DI on both platforms.

**Non-Goals:**
- Navigation. With a single screen, `:composeApp` renders `UsersRoot` directly.
- Navigation and SavedState dependencies in the feature convention. They are added in the change that introduces a second screen.
- A desktop target, `iosX64`, BuildKonfig, Ktor and DataStore.
- Final user-feed UI, shimmer, error states. The `User` schema may still be extended by the feed change, with a Room migration if released. Pre-release, `fallbackToDestructiveMigration` is acceptable.

## Decisions

### 1. Module layout: feature-first, with only two core modules

| Module | Plugin(s) | Project deps | Contents |
|---|---|---|---|
| `:users:domain` | `kmp.library` | none | `User`, `Gender`, `UserStatus`, `UserRepository`, `Result`/`Error`/`DataError`/`EmptyResult` + helpers |
| `:users:data` | `kmp.library` | users:domain, core:database | `RoomUserRepository`, `UserEntity↔User` mappers, `usersDataModule` |
| `:users:presentation` | `cmp.feature` | users:domain, core:designsystem (via plugin) | `UsersState`/`UsersAction`/`UsersEvent`, `UsersViewModel`, `UsersRoot`/`UsersScreen`, strings, `usersPresentationModule` |
| `:core:database` | `kmp.library` + `room` | none | `SliideDatabase`, `UserEntity`, `UserDao`, constructor, platform builder, `databaseModule` |
| `:core:designsystem` | `cmp.library` | none | `SliideTheme`, colour schemes, typography |
| `:composeApp` | `cmp.application` | everything above | `App()`, `initKoin()`, `MainViewController`, iOS framework `ComposeApp` |
| `:androidApp` | `android.application.compose` | composeApp | `SliideApplication`, `MainActivity`, manifest |

Why this layout:
- With one feature, the `kmp-module-structure` rule "code stays in the feature until a second feature needs it" outweighs the speculative `:core:domain` / `:core:data` / `:core:presentation` modules.
- `Result` and `DataError` therefore live in `users:domain`. `UiText` and `ObserveAsEvents` live in `users:presentation`.
- The user explicitly wanted the database and the design system in core:
  - The database: so a later feature can share the cache without depending on the users feature.
  - The design system: so the app shell and every feature use one theme.
- Feature modules sit at the root, named after the feature (`:users:*`, directory `users/`), not under a `:feature:` group. This was the user's choice. A future feature follows the same `:<name>:{domain,data,presentation}` pattern. The convention-derived namespace is `com.androidlearning.sliide.users.<layer>`.

Alternatives considered:
- The CLAUDE.md five-core-module layout: rejected as over-engineering for one feature.
- Room inside `users:data`: rejected by the user.

CLAUDE.md and `config.yaml` are updated to the new shape so reviewers don't flag it as drift.

**Entity ownership:** `UserEntity` is in `:core:database`, the owner of the `@Database`. The mapping to the domain `User` is in `users:data`, so neither core nor domain knows the other's types.

### 2. Convention plugins adapted to this repo, not copied from the skill

`build-logic/convention` uses package `com.androidlearning.sliide.convention` and plugin IDs `com.androidlearning.sliide.convention.<name>`. The plugin IDs are registered in the catalog as `convention-*` with `version = "unspecified"`.

| Plugin | Applies / configures |
|---|---|
| `kmp.library` | AGP KMP library + Kotlin MP + serialization. Android target: `compileSdk`/`minSdk` from the existing catalog keys `android-compileSdk`/`android-minSdk`, namespace derived from the module path (`com.androidlearning.sliide.users.data`), `withHostTest { isIncludeAndroidResources = true }`, `withDeviceTestBuilder { sourceSetTreeName = "test" }` with `AndroidJUnitRunner`. Targets: `iosArm64` and `iosSimulatorArm64`, without framework binaries. Default hierarchy template, JVM target 17. Common deps: coroutines-core. Test deps: kotlin-test, AssertK, Turbine, coroutines-test (commonTest); androidx test runner/ext (androidDeviceTest). |
| `cmp.library` | `kmp.library` + Compose compiler + Compose MP. Deps: runtime, foundation, material3, ui, components-resources, ui-tooling-preview. `androidMainImplementation(compose-uiTooling)` so previews render in KMP modules. Compose resources package: `sliide.<module path>.generated.resources`. |
| `cmp.feature` | `cmp.library` + `:core:designsystem` + Koin (bom, core, compose, compose-viewmodel) + lifecycle viewmodel-compose/runtime-compose. |
| `cmp.application` | Same base as `cmp.library` (for `:composeApp`) + iOS framework `baseName = "ComposeApp"`, `isStatic = true` + Koin. |
| `android.application.compose` | `com.android.application` + Compose compiler, SDKs from the catalog, JVM 17, packaging excludes, `debugImplementation` tooling. |
| `room` | KSP + `androidx.room`, `schemaDirectory("$projectDir/schemas")`. `commonMainApi(room-runtime, sqlite-bundled)`. `kspAndroid`, `kspIosArm64` and `kspIosSimulatorArm64` with `room-compiler`. |

Why: the skill's sample assumes catalog keys that don't exist here, such as `projectCompileSdkVersion`, `android-desugarJdkLibs` and the `jetbrains-compose-*` aliases. It also assumes desktop and `iosX64` targets. We reuse the existing catalog keys instead of renaming them, which keeps the diff smaller.

Other choices:
- No core-library desugaring. `minSdk` is 26 and `kotlin.time` replaces `java.time`.
- Framework binaries only in `:composeApp`. Library modules don't need them, and this avoids extra link work.
- **JVM 17 everywhere.** It is required by AGP 9 and used by the skill. The current 11 is only a template default.
- `TYPESAFE_PROJECT_ACCESSORS` is enabled, so deps read `projects.users.domain`.

### 3. Test stack

Unit tests:
- Unit tests live in `commonTest`. They run as `testAndroidHostTest` (JVM) and `iosSimulatorArm64Test`, using kotlin-test as the runner with AssertK, Turbine and coroutines-test.
- JUnit5 (from `android-unit-testing`) is not used. It is JVM-only and cannot run `commonTest`. Recorded as a deviation from the skill.
- Fakes are used over mocks: `FakeUserDao` and `FakeUserRepository`.

Instrumented tests:
- `androidDeviceTest` in `:core:database` uses an in-memory Room database with `BundledSQLiteDriver`.
- `:androidApp/src/androidTest` holds one Compose smoke test. It launches `MainActivity` and asserts that the placeholder users screen is shown. This proves Koin started and the ViewModel resolved.

DI check:
- The Koin graph is checked with `koin-test`'s `Module.verify()` in `:composeApp` `androidHostTest`. `verify` is JVM-only.
- `Context`, `RoomDatabase.Builder` and `SavedStateHandle` are passed as `extraTypes`.

### 4. expect/actual: only for Room, with each use justified

1. `expect object SliideDatabaseConstructor : RoomDatabaseConstructor<SliideDatabase>`
   - Room KMP *requires* this.
   - KSP generates the `actual` per target.
   - It is not hand-written platform code.
2. The database builder is provided per platform as `expect val platformDatabaseModule: Module`, with an `actual` in `androidMain` and one in `iosMain`.
   - **Justification:** on Android, `Room.databaseBuilder` needs an Android `Context` (from Koin's `androidContext`). On iOS, the file path comes from `NSFileManager`'s documents directory. Neither can be expressed in common code.
   - The common `databaseModule` then builds `SliideDatabase` from the injected `RoomDatabase.Builder` with `BundledSQLiteDriver` and `Dispatchers.IO`, so the configuration is identical on both platforms.
   - **Alternative:** a `DatabaseFactory` `expect class`. Rejected because the Koin-module form needs no extra type, and Android gets `Context` without passing it through constructors.

No other expect/actual is added. The template `Platform`/`Greeting` expect/actual is deleted.

### 5. Koin assembly and startup

- Each module exposes one `val ...Module: Module`:
  - `databaseModule`
  - `platformDatabaseModule`
  - `usersDataModule` (`singleOf(::RoomUserRepository) bind UserRepository::class`)
  - `usersPresentationModule` (`viewModelOf(::UsersViewModel)`)
- `:composeApp` has `fun initKoin(config: KoinAppDeclaration? = null)`, which calls `startKoin { config?.invoke(this); modules(appModules) }`.
- Android: `SliideApplication.onCreate()` calls `initKoin { androidContext(this@SliideApplication) }`. The `Application` is registered with `android:name` in the manifest.
- iOS: `iOSApp.init()` calls `KoinKt.doInitKoin()`. Kotlin/Native exports functions starting with `init` with a `do` prefix. This runs before `ContentView` creates `MainViewController`.
- `App()` is not a `KoinApplication` composable. Starting Koin in the platform entry points means a configuration change or a recreated view controller never restarts Koin.

### 6. Users vertical slice (placeholder)

Domain:
- `User(id: Long, name, email, gender: Gender, status: UserStatus, createdAt: Instant)`. `createdAt` is set locally, because GoRest returns no timestamp and the feed needs relative times.
- `UserRepository.observeUsers(): Flow<List<User>>`. Write methods come with the feed and add/delete changes.

Database:
- `UserDao`:
  - `observeAll(): Flow<List<UserEntity>>`, ordered by `createdAt DESC`
  - `upsert`
  - `deleteById`
- Stored enums are written as strings, through mappers rather than a TypeConverter.

Presentation (MVI, per `android-presentation-mvi`):
- `UsersState(users: List<UserUi>, isLoading: Boolean)`
- `UsersAction` is empty for now.
- `UsersViewModel` collects `observeUsers()` into state with `stateIn(WhileSubscribed(5_000))`.
- `UsersRoot` does `koinViewModel()` and `collectAsStateWithLifecycle()`. `UsersScreen` is stateless.
- `UsersScreen` shows a top app bar title plus either an empty-state message or a plain `LazyColumn` of name and email. There are no other extracted composables, which keeps the number of previews small.
- Previews follow `config.yaml`: empty, loading and populated states, each in light and dark, all wrapped in `SliideTheme`.
- Strings are Compose resources in `users:presentation`.

### 7. Theme

- `SliideTheme(darkTheme: Boolean = isSystemInDarkTheme(), content)` wraps `MaterialTheme` with hand-defined `lightColorScheme`/`darkColorScheme` and a `Typography`, all in `commonMain`.
- **Dynamic colour is not used.** It is Android 12+ only, and would break the requirement that both platforms get identical values.

### 8. ktlint

The root `subprojects { ktlint }` block stays and gains two settings:
1. `filter { exclude { it.file.path.contains("/build/") } }`, so generated Compose-resources and Room/KSP sources are never linted.
2. A root `.editorconfig` with `ktlint_function_naming_ignore_when_annotated_with = Composable`.

The `shared` and `androidApp` baselines only cover template files that are deleted or rewritten, so both baselines are deleted and every module starts clean. This follows CLAUDE.md: fix the code, don't baseline new violations.

## Risks / Trade-offs

- **KSP has to match Kotlin 2.4.21, and Room KMP has to support AGP 9's KMP-library plugin.** → The version-catalog task looks up current compatible versions (android-version-catalog skill, android-cli). The Room plugin is validated first on `:core:database` before the other modules are wired.
- **Koin and Compose MP 1.12 / lifecycle 2.11 version skew.** → Use the Koin BOM. Check that `koin-compose-viewmodel` resolves the JetBrains lifecycle artifacts already in the catalog.
- **Renaming `:shared` breaks the iOS build** if any reference is missed. → Grep for `shared`/`Shared` across `iosApp/`, Gradle files and README. The verification gate includes the iOS link and a simulator build.
- **Deviating from CLAUDE.md's core layout** could be flagged by reviewers. → CLAUDE.md, `config.yaml` and README are updated in the same change.
- **`Module.verify()` misses runtime-only issues**, such as a missing `androidContext`. → The androidApp instrumented smoke test covers real startup.
- **The `User` schema may change** in the feed change. → The database is at version 1 and pre-release, so changing it later is cheap.

## Migration Plan

This is all on one branch, `chore/sets_up_module_structure`:
1. build-logic and catalog
2. core modules
3. feature modules
4. `:shared` renamed to `:composeApp`
5. app/iOS wiring
6. docs

Rollback means reverting the branch. No user data exists yet.
