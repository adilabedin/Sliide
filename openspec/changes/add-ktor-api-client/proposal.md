# Proposal

## Why

The offline-first repository is ready, but the app still has no network. `UnavailableUserRemoteDataSource` answers
every call with `NO_INTERNET`, so the feed never loads from GoRest and every write stays queued forever. We need a
real Ktor client for the GoRest `/users` API. We also need the repository's network work to outlive the screen that
started it. Otherwise a create, delete or refresh is cancelled half-way when its ViewModel is cleared.

## What Changes

- **New `:core:domain` module.** `Error`, `Result`, `EmptyResult`, `DataError` and the `map` / `onSuccess` /
  `onFailure` / `asEmptyResult` helpers move here from `:users:domain`, together with their tests. A core module
  (`:core:data`) now needs them, and core can never depend on a feature. `:users:domain` re-exports it as `api`.
  **BREAKING (internal):** the package changes from `…users.domain.util` to `…core.domain.util`, so about 16 files
  get new imports. Behaviour is unchanged.
- **New `:core:data` module** that owns the shared HTTP stack:
  - The GoRest **base URL** and **API token** come from **BuildKonfig**, through a new `buildkonfig` convention plugin.
    The token is read from the git-ignored `local.properties` (or an environment variable) and is never committed.
    It defaults to an empty string, so builds and tests run without it.
  - An `HttpClientFactory`: JSON, a bearer `Authorization` header on every request, request timeouts, and a per-platform
    engine (OkHttp on Android, Darwin on iOS).
  - `HttpClient` extension functions `get`, `post`, `patch` and `delete`, each returning `Result<…, DataError.Remote>`.
    **`get` also returns the response headers** along with the decoded body.
  - One mapping from status codes and transport exceptions to `DataError.Remote`. It is the same on both platforms;
    in particular, every connectivity failure becomes `NO_INTERNET`.
- **The domain interface `UserRemoteDataSource` is renamed `UserApiClient`**, and the test fake becomes
  `FakeUserApiClient`. This is a pure rename; the method signatures don't change.
- **New `KtorUserApiClient` (implements `UserApiClient`) in `:users:data`**, replacing the `UnavailableUserRemoteDataSource` stub, which is
  deleted:
  - `fetchLastPage()` makes **two dependent calls inside one method**. The first is `GET /users?page=1&per_page=20`; its
    `X-Pagination-Pages` header gives the last page number. The second is `GET /users?page=<last>&per_page=20`. The
    second call waits for the first. If either one fails, the whole fetch fails with a single error and no partial
    list.
  - `create` → `POST /users` (201), `update` → `PATCH /users/{id}` (200), `delete` → `DELETE /users/{id}` (204).
  - `UserDto` with lowercase `gender` / `status`, plus the DTO↔domain mappers.
- **Repository work runs in an application `CoroutineScope`.** The application entry points create a single process-wide
  scope: `SliideApplication` on Android and `iOSApp` (through a Kotlin `initKoinIos()` helper) on iOS. They pass it
  into `initKoin`, which binds it under a qualifier. Every suspending `OfflineFirstUserRepository` operation runs its body
  in that scope (create, update, remove, restore, finalize delete, sync, refresh). If the caller (a future ViewModel)
  is cancelled, the operation still completes, including its rollback paths. The existing `syncScope` parameter
  becomes this injected scope.
- The docs are updated: CLAUDE.md's module table and README.md's architecture section.

## Capabilities

### New Capabilities

- `http-client`: the shared GoRest HTTP stack. Covers the build-time base URL and token (kept out of git), bearer
  auth, timeouts, header-exposing GET and POST/PATCH/DELETE helpers, and the status-code and exception mapping to
  `DataError.Remote`.
- `user-remote-api`: the GoRest `/users` remote data source. Covers the two-step last-page fetch with its
  fail-together rule, create/update/delete, and the DTO format.
- `operation-lifetime`: repository operations run in an application-wide scope and are not cancelled when their
  caller is cancelled.

### Modified Capabilities

<!-- None: openspec/specs/ is empty (earlier changes are not archived yet), so nothing there can be modified. -->

## Impact

- **New modules:** `:core:domain` (`kmp.library`) and `:core:data` (`kmp.library` + new `buildkonfig` convention
  plugin). Both are registered in `settings.gradle.kts`.
- **`:users:domain`:** loses `util/` and its tests to `:core:domain` and depends on it as `api`.
- **`:users:data`:** depends on `:core:data`. Adds `KtorUserApiClient`, `UserDto` and its mappers. The Koin binding
  changes, the stub is deleted, and the repository constructor takes the application scope. Tests: new MockEngine
  tests, and the repository harness passes the scope.
- **`:users:presentation`:** only import changes (in `FakeUserRepository`).
- **`:composeApp`:** depends on `:core:data`. `initKoin`/`appModules` take the application scope, an `iosMain`
  `initKoinIos()` is added, and the network modules join the graph. `KoinGraphTest` must still resolve.
- **`:androidApp` / `iosApp`:** `SliideApplication` creates and owns the scope. `iOSApp.swift` calls `initKoinIos()`.
  The Android `INTERNET` permission is merged in from `:core:data`.
- **`build-logic` / version catalog:** add Ktor (core, content-negotiation, serialization-kotlinx-json, okhttp,
  darwin, mock), the BuildKonfig plugin, and the `convention-buildkonfig` plugin id.
- **Out of scope:** UI changes and ViewModel wiring, request logging, retry or backoff, connectivity monitoring, and
  background sync scheduling.
