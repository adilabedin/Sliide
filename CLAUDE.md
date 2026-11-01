# Sliide KMP Challenge — Agent Instructions

## Project context

Build a high-fidelity, cross-platform **User Management System** for the Sliide “UX Innovator” challenge. The app talks to the **GoRest Public API** and must feel production-ready: polished UX, clean architecture, and shared logic on Kotlin Multiplatform (Android & iOS).

**Must-have features**

1. **Smart User Feed** — Fetch and display users from the **last page** of `/users`. Each row shows name, email, and a **relative timestamp** (e.g. “5 minutes ago”) computed in shared logic. Shimmer while loading; graceful errors (e.g. “No Internet”).
2. **Adaptive Add User** — FAB opens a validated form (real-time name and email validation). On **201**, the new user appears **at the top** of the list immediately.
3. **Delete with Undo** — Long-press → confirm delete. On **204**, remove with animation and show a Snackbar with **Undo** to restore local state before the action is finalized.
4. **Adaptive layout** — 100% shared Compose Multiplatform UI: portrait = single list; landscape/tablet = master-detail or two-column grid.

**Evaluation focus:** AI orchestration, UX polish (animations, dark mode, Material 3, adaptive layouts), KMP mastery (`expect`/`actual` only where needed), unit tests on shared ViewModels/logic, and offline-capable caching.

---

## Stack (fixed)

| Area | Choice |
|------|--------|
| Platform | Kotlin Multiplatform — Android & iOS |
| UI | Compose Multiplatform, Material 3, dark mode |
| Architecture | Clean Architecture + **MVI** (domain / data / presentation) |
| Networking | Ktor |
| Persistence | **Room KMP** — offline-first local cache |
| DI | Koin |

Use `expect`/`actual` only for genuine platform differences.

---

## Multi-module architecture (required)

Do **not** keep all code in a single `:shared` blob long term. Evolve toward **convention plugins** in `build-logic` plus **core** and **feature** modules. When creating or reshaping modules, read the **`kmp-module-structure`** skill and follow it.

**Target shape (starting point; refine per skill)**

- `:core:domain`, `:core:data`, `:core:database`, `:core:designsystem`, `:core:presentation`
- `:feature:users` (split into domain / data / presentation if the structure skill requires)
- Thin app entry: `:composeApp` / `:androidApp` and `iosApp`

**Dependency rules**

- Features do **not** depend on other features.
- Presentation depends on **domain** only (not on data implementations).
- Data implements interfaces defined in **domain**.

---

## Acceptance criteria (Definition of Done)

A task or feature is **done** only when **Gradle succeeds**. If the build fails, fix the cause and re-run; do **not** disable tests, weaken checks, or skip verification to get green.

**Verification gate**

1. `./gradlew build` — all modules compile; shared unit tests run.
2. `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` (or the equivalent iOS framework link task for this project) — iOS target compiles.

Until both pass (or the iOS task name is updated to match the actual module name), the work is not complete.

**Automated gates (hooks in `.claude/settings.json`)**

- **Stop → `.claude/hooks/stop-lint.sh`:** when a turn ends with Kotlin changes, `./gradlew ktlintCheck` runs and any new
  violations are sent back to you. Fix them (`./gradlew ktlintFormat` handles most) — do not add them to the baseline.
- **PreToolUse(Bash) → `.claude/hooks/pre-push-tests.sh`:** any `git push` first runs `./gradlew testAndroidHostTest`;
  a failure blocks the push. Fix the tests — never skip or weaken them to get through.
- Pre-existing violations live in each module's `ktlint-baseline.xml`. The baseline is position-based, so editing a
  file can resurface its old violations; fix them in that file as part of the change.

---

## Skill usage

Skills live at `.claude/skills/<name>/SKILL.md`. Most are copied from [PL-Coding-GmbH/Skills](https://github.com/PL-Coding-GmbH/Skills)
(`skills/*`); to update, re-copy from a fresh clone of that repo. The `openspec-*` skills come from OpenSpec.

- Read a skill **only when** the current task matches its description.
- Do **not** load all skills up front.
- When two skills overlap, prefer the **KMP-specific** one (e.g. `kmp-module-structure` over `android-module-structure`).

---

## Skill index

One- to two-line summaries so you can choose the right skill when needed.

### Project setup and build

- **`kmp-module-structure`** — KMP module layout, dependency rules, Gradle 9 convention plugins, and Compose Multiplatform setup. Use when creating modules, placing code, or configuring `build-logic`. **Primary structure skill for this project.**
- **`android-module-structure`** — Android-only module layout and convention plugins. Use for Android-specific modules; otherwise prefer `kmp-module-structure`.
- **`android-version-catalog`** — Add or update libraries, plugins, bundles, and versions in `libs.versions.toml`; create convention plugins for libraries with non-trivial setup (KSP, Room, etc.).
- **`android-cli`** — `android` CLI for project creation, deploy, SDK management, and environment diagnostics.

### Architecture and layers

- **`android-data-layer`** — Data sources, repositories, DTOs, mappers, Room entities/DAOs, Ktor `HttpClient`, safe-call helpers, offline-first. Use for networking or persistence.
- **`android-error-handling`** — Typed `Result<T, E>`, `DataError`, and helpers (`map`, `onSuccess`, `onFailure`). Use when defining or propagating errors (including validation).
- **`android-di-koin`** — Koin modules per layer, ViewModel injection, assembling modules in the app entry point.
- **`android-presentation-mvi`** — State, Action, Event, ViewModel, Root/Screen split, `UiText`, `SavedStateHandle`. Use for every screen and ViewModel.
- **`android-navigation`** — Type-safe Compose Navigation, feature nav graphs, wiring in the app module.

### Concurrency

- **`kotlin-coroutines`** — Suspend functions, scopes, cancellation, dispatchers, synchronization. Use for async code that is not primarily Flow-based.
- **`kotlin-flows`** — StateFlow, SharedFlow, Channel, `stateIn`/`shareIn`, operators, offline-first reactive streams.

### UI

- **`android-compose-architecture`** — Screen-level structure, stability/recomposition, state ownership, side effects, lazy-list performance.
- **`android-compose-components`** — Reusable non-screen composables (cards, list items, FAB, fields, dialogs), previews, Material 3, strings, animations.
- **`compose-adaptive-layouts`** — Structural layout changes by device configuration (e.g. portrait list vs landscape/tablet two-pane). Not for simple responsive sizing only.
- **`figma-to-android-vector`** — SVG/Figma → VectorDrawable or `ImageVector`. Use when adding real icon assets from design exports.

### Testing and quality

- **`android-unit-testing`** — JUnit5, AssertK, Turbine, fakes over mocks, test dispatchers. Required for ViewModels, validators, mappers, formatters, use cases.
- **`android-integration-testing`** — Real classes wired together; swap boundaries only (MockEngine, in-memory Room). Repository and offline-first tests.
- **`android-ui-testing`** — Instrumented Compose UI / E2E (Robot pattern). Use when UI tests are requested.
- **`android-jvm-test-coverage`** — JVM unit-test coverage report for a module without modifying build files.
- **`android-jacoco-setup`** — Permanent JaCoCo in Gradle. Use only when asked.
- **`kotlin-mutation-testing`** — Mutation-testing audit of a test class. Use **only** when the user explicitly asks for mutation testing.

### Optional / rarely needed

- **`android-background`** — WorkManager and foreground services (Android-only). Only if background sync is explicitly added.
- **`android-ui-verify`**, **`android-ui-screenshot-verify`**, **`android-preview-figma-verify`** — Compare UI to Figma or mockups. Only when a design reference exists.
- **`kmp-migration-analyzer`** — Plan Android→KMP migration (analytical only). Not applicable to this greenfield project; listed for completeness.

---

## Working conventions

- Add dependencies via the **version catalog** (`gradle/libs.versions.toml`).
- Avoid unrelated refactors in the same change as a feature fix.
- Keep **README.md** updated with architectural choices and how AI accelerated the build (challenge deliverable).
