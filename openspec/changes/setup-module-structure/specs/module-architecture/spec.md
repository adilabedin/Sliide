# Spec Delta

## Purpose

Defines how the codebase is split into modules, which dependency directions between them are allowed, how every module is built, and how dependency injection is assembled and started on Android and iOS.

## ADDED Requirements

### Requirement: Layered module layout
The project SHALL be organised into these modules:
- a shared app module
- a thin Android application module
- the iOS app
- a design-system core module
- a local-database core module
- a users feature split into separate domain, data and presentation modules

#### Scenario: Project modules are present
- **WHEN** the Gradle project is configured
- **THEN** it includes the shared app module, the Android app module, the design-system and database core modules, and the users feature's domain, data and presentation modules
- **AND** the template's single shared module no longer exists

### Requirement: Domain layer is dependency-free
The users domain module MUST NOT depend on any other project module, Android framework API or persistence library.

#### Scenario: Domain compiles in isolation
- **WHEN** the users domain module is built
- **THEN** it declares no project dependencies, and its common code references no database, UI or platform types

### Requirement: Presentation depends on domain, not data
The users presentation module SHALL depend only on the users domain module and the design-system module. It MUST NOT depend on the users data module or the database module.

#### Scenario: Presentation dependency check
- **WHEN** the project dependencies of the users presentation module are listed
- **THEN** they contain the users domain and design-system modules and nothing from the data or database layers

### Requirement: Data implements domain contracts
The users data module SHALL implement the repository contract declared in the users domain module. It SHALL be the only users module that depends on the database module.

#### Scenario: Repository contract is satisfied by data
- **WHEN** the dependency graph asks for the users repository contract
- **THEN** it resolves to an implementation that lives in the users data module

### Requirement: Core modules never depend on features
Core modules MUST NOT depend on any feature module, and feature modules MUST NOT depend on other feature modules.

#### Scenario: Core dependency check
- **WHEN** the project dependencies of the design-system and database modules are listed
- **THEN** none of them is a feature module

### Requirement: Thin platform entry points
The Android application module SHALL depend only on the shared app module. The iOS app SHALL consume a single framework produced by the shared app module.

#### Scenario: iOS framework links
- **WHEN** the shared app module's debug framework is linked for the iOS simulator (arm64)
- **THEN** the link succeeds and produces the framework that the iOS app imports

### Requirement: Uniform module build configuration
Every KMP module SHALL get its targets, SDK levels, JVM target and test source sets from shared build conventions, not from per-module configuration. Targets are Android, iOS arm64 and iOS simulator arm64. Test source sets are common tests, Android host tests and Android device tests.

#### Scenario: Host tests exist in every module
- **WHEN** `./gradlew testAndroidHostTest` runs
- **THEN** the task exists and runs for every KMP module

#### Scenario: Full build succeeds
- **WHEN** `./gradlew build` runs
- **THEN** every module compiles and every module's unit tests pass

### Requirement: Dependencies declared in the version catalog
Every library and plugin version SHALL be declared in the project's version catalog. Module build files MUST NOT hardcode versions.

#### Scenario: No inline versions
- **WHEN** module and convention build scripts are searched for dependency coordinates that include an inline version
- **THEN** none are found

### Requirement: Dependency graph resolves on every platform
Each layer module SHALL expose its own dependency-injection definitions. The shared app module SHALL assemble them into one graph, which SHALL be started once at app launch on Android and on iOS before the first screen is shown.

#### Scenario: Graph verification
- **WHEN** the assembled dependency graph is verified in a test
- **THEN** every declared definition resolves without missing dependencies

#### Scenario: App launches with the graph started
- **WHEN** the app is launched on Android or iOS
- **THEN** the users screen is shown with its view model provided by the dependency graph, and no injection error occurs

### Requirement: Local user storage
The database module SHALL provide on-device storage for users on Android and iOS. Stored users SHALL be observable as a stream that emits again whenever the stored set changes.

#### Scenario: Stored users are observed
- **GIVEN** an empty local database
- **WHEN** a user is inserted
- **THEN** observers of the stored users receive a list that contains that user

#### Scenario: Deleted users disappear
- **GIVEN** a local database that contains a user
- **WHEN** that user is deleted by id
- **THEN** observers receive a list without that user
