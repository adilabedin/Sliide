# Tasks

## 1. `:core:domain`: move the shared Result and error types (design D1)

- [x] 1.1 Create `:core:domain` with the `kmp.library` convention plugin and no dependencies, and register it in
  `settings.gradle.kts` (follow the kmp-module-structure skill). Verify: `./gradlew :core:domain:build` succeeds.
- [x] 1.2 Move `Error`, `Result`, `EmptyResult`, `DataError` (+ `isRetryable`), the helpers, `ResultTest` and
  `DataErrorTest` from `:users:domain` `…users.domain.util` to `:core:domain` `…core.domain.util`. Add
  `api(projects.core.domain)` to `:users:domain`, and fix every import in `:users:domain`, `:users:data` (main, commonTest,
  androidDeviceTest) and `:users:presentation` commonTest. Verify: `grep -r "users.domain.util" --include='*.kt'` finds
  nothing, and `./gradlew :core:domain:allTests :users:domain:allTests :users:data:allTests :users:presentation:allTests` passes.
- [x] 1.3 Add `:core:domain` to the CLAUDE.md module table (it holds `Result`/`DataError`; every module may depend on
  it). Remove `Result`/`DataError` from the `:users:domain` row. Verify: the table matches `settings.gradle.kts`.

## 2. Build setup: Ktor, BuildKonfig, `:core:data` (design D2)

- [x] 2.1 Using the android-version-catalog skill, look up the current stable versions. Add Ktor (`ktor-client-core`,
  `-content-negotiation`, `ktor-serialization-kotlinx-json`, `-okhttp`, `-darwin`, `-mock`), a `ktor-common` bundle, the
  BuildKonfig plugin plus its `buildkonfig-gradlePlugin` (compileOnly on build-logic), and the
  `convention-buildkonfig` plugin id. Verify: Gradle sync and `./gradlew help` succeed.
- [x] 2.2 Add `BuildKonfigConventionPlugin`:
  - package name from `pathToPackageName()`;
  - `BASE_URL = "https://gorest.co.in/public/v2/"`;
  - `API_TOKEN` read from `GOREST_API_TOKEN` in the root `local.properties` via `java.util.Properties`, else the
    environment variable of the same name, else `""` with a `logger.warn`.

  Register it in `build-logic/convention/build.gradle.kts`. Verify: the build-logic build passes strict
  `validatePlugins`.
- [x] 2.3 Create `:core:data` (`kmp.library` + `buildkonfig`) and register it in settings. Dependencies:
  - commonMain: `api(projects.core.domain)`, the Ktor bundle and Koin;
  - androidMain: `ktor-client-okhttp`; iosMain: `ktor-client-darwin`; commonTest: `ktor-client-mock`.

  Add an androidMain `AndroidManifest.xml` declaring `android.permission.INTERNET` (design D10).
  `GOREST_API_TOKEN` is already in the developer's git-ignored `local.properties` (added during planning); keep it out of every tracked file. Verify:
  - `./gradlew :core:data:build :composeApp:linkDebugFrameworkIosSimulatorArm64` succeeds and the generated
    `internal object BuildKonfig` exists;
  - `git status` shows no tracked file containing the token;
  - if BuildKonfig is incompatible with AGP 9 KMP, apply the generated-source fallback from design Risks, so the
    verification still passes.
- [x] 2.4 Add `:core:data` to the CLAUDE.md module table (HTTP client, `HttpClient` extensions, BuildKonfig
  credentials, the application-scope qualifier). Verify: the table matches `settings.gradle.kts`.

## 3. `:core:data`: HTTP client and safe-call helpers (design D3–D5, spec `http-client`)

- [x] 3.1 Implement `HttpClientFactory.create(engine, baseUrl, apiToken)` with `expectSuccess = false`, lenient JSON,
  `HttpTimeout` (20 s / 10 s / 20 s) and `defaultRequest` (base URL, `Authorization: Bearer`,
  `Accept: application/json`). MockEngine unit tests:
  - every method carries the bearer header and `Accept: application/json`;
  - POST and PATCH carry `Content-Type: application/json`;
  - relative route `"users"` resolves to `<base>/users` with `/public/v2` kept;
  - unknown response fields are ignored.

  Verify: `./gradlew :core:data:allTests` passes.
- [x] 3.2 Implement `NetworkResponse<T>`, `safeCall`, `responseToResult`, and the `get` (body + headers), `post`,
  `patch` and `delete` (no body decode) extensions, with the exception order from D5. MockEngine unit tests:
  - every status row of the spec: 200 / 201 / 204 / 401 / 403 / 404 / 408 / 422 / 429 / 500 / 503 / 418 → `UNKNOWN`;
  - `get` returns the `X-Pagination-Pages` header value;
  - a 204 delete succeeds;
  - a malformed 200 body → `SERIALIZATION`;
  - a thrown `IOException` → `NO_INTERNET`;
  - a `HttpRequestTimeoutException` → `REQUEST_TIMEOUT`, and so does a `SocketTimeoutException`, despite being an
    `IOException`;
  - another exception → `UNKNOWN`;
  - cancellation propagates instead of returning a failure.

  Verify: `./gradlew :core:data:allTests` passes.
- [x] 3.3 Add `coreDataModule` (`single<HttpClient>`), `expect val platformHttpEngineModule` with OkHttp/Darwin
  actuals (D4), and the `ApplicationScope` qualifier (D8). Verify: `./gradlew :core:data:build` succeeds for Android and
  iOS targets.
- [x] 3.4 Real-engine transport mapping tests (android-integration-testing), each a GET to the refused port
  `https://127.0.0.1:1/` through `HttpClientFactory` with the real platform engine, expecting `NO_INTERNET`. Use https
  so cleartext policy (Android network security config, iOS ATS) can't produce a false pass.
  - an `androidDeviceTest` with OkHttp;
  - an `iosTest` with Darwin.

  Verify: `./gradlew :core:data:connectedAndroidDeviceTest` (emulator via android-cli) and
  `./gradlew :core:data:iosSimulatorArm64Test` both pass.
- [x] 3.5 Add an "HTTP stack" section to README.md: BuildKonfig credentials, bearer header, timeouts, the
  status/exception → `DataError.Remote` table, and why routes have no leading slash. Verify: it matches D2–D5.

## 4. `:users:data`: GoRest remote data source (design D6–D7, spec `user-remote-api`)

- [x] 4.0 Rename the domain interface `UserRemoteDataSource` to `UserApiClient` (file name too), and the test fake
  `FakeUserRemoteDataSource` to `FakeUserApiClient`. Update every reference: `OfflineFirstUserRepository` (its `remote`
  parameter becomes `apiClient`), `UsersDataModule`, `RepositoryHarness`, the repository tests and
  `OfflineFirstUserRepositoryIntegrationTest`. Verify: `grep -rn "UserRemoteDataSource" --include='*.kt'` matches only
  the soon-to-be-deleted `UnavailableUserRemoteDataSource`, and `./gradlew :users:domain:allTests :users:data:allTests` passes.
- [x] 4.1 With curl, confirm that `GET https://gorest.co.in/public/v2/users?page=1&per_page=20` returns the
  `X-Pagination-Pages` header and lowercase `gender`/`status` values (read-only, no token). Verify: the header name
  matches the constant used in code.
- [x] 4.2 Add `UserDto`, `UserRequestDto` and the mappers. Unknown gender or status → `null`. Unit tests: round-trip of
  every enum value, lowercase on the wire, unknown value → `null`. Verify: `./gradlew :users:data:allTests` passes.
- [x] 4.3 Implement `KtorUserApiClient` (`PAGE_SIZE = 20`, sequential `fetchLastPage` per D6, plus `create`,
  `update` and `delete`), depending on `:core:data`. MockEngine unit tests covering every `user-remote-api` scenario:
  - request order and query params (`page=1&per_page=20` then `page=N&per_page=20`);
  - pages = 1 → one request; empty collection → empty list;
  - first call fails → no second request; second call fails → the whole fetch fails;
  - missing or non-numeric header → `SERIALIZATION`;
  - empty last page with pages > 1 → success with an empty list, and no third request;
  - POST 201 / 422, `PATCH users/{id}`, DELETE 204 / 404;
  - unknown status in a response → `SERIALIZATION`.

  Verify: `./gradlew :users:data:allTests` passes.
- [x] 4.4 Document the two-step last-page fetch in README.md: both requests, the fail-together rule, pages ≤ 1, the
  empty-last-page rule (an empty feed the user can refresh), and `per_page=20`. Verify: it matches D6.

## 5. Application scope and repository lifetime (design D8–D10, spec `operation-lifetime`)

- [x] 5.1 Rename the `OfflineFirstUserRepository` parameter `syncScope` to a required `applicationScope`, and wrap every
  suspending public method body in `outlivingCaller { … }` (`applicationScope.async { }.await()`). `observeUsers` is
  left as is; update the KDoc. Update `RepositoryHarness` and the androidDeviceTest
  `OfflineFirstUserRepositoryIntegrationTest` to pass the scope. Verify: `./gradlew :users:data:allTests` compiles and
  runs.
- [x] 5.2 Rework the caller-cancellation tests in `OfflineFirstUserRepositoryCrashSafetyTest` (and any similar ones
  found by `grep -n "caller.cancel\|cancel()" users/data/src`) to simulate the kill by cancelling a `killedScope` passed
  as `applicationScope` (D9). The assertions stay unchanged. Verify: the reworked tests pass and still fail if the
  outbox-first enqueue is removed (check temporarily, then restore).
- [x] 5.3 Add `operation-lifetime` tests:
  - with a gated fake remote, cancel the caller of `createUser`, `updateUser` and `finalizeDelete`, release the gate,
    and assert re-key / server update / pending entry removed;
  - an un-cancelled caller still receives the same result;
  - an operation that throws doesn't cancel a concurrent one.

  Verify: `./gradlew :users:data:allTests` passes.
- [x] 5.4 Wire Koin, with the scope created by the application (D8):
  - `initKoin(applicationScope, config)` and `appModules(applicationScope)` bind the passed scope under
    `ApplicationScope`;
  - `SliideApplication` owns `val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)` and passes
    it in;
  - add `initKoinIos()` in `:composeApp` `iosMain` (it creates the scope the same way) and call it from
    `iosApp/iosApp/iOSApp.swift` in place of `KoinKt.doInitKoin(config:)`;
  - `KoinGraphTest` passes a test scope to `appModules`;
  - add `platformHttpEngineModule` and `coreDataModule` to `appModules`;
  - `usersDataModule` binds `KtorUserApiClient` and passes `get(ApplicationScope)`;
  - delete `UnavailableUserRemoteDataSource`;
  - add the `:core:data` dependency to `:composeApp`.

  Verify: `KoinGraphTest` passes (`./gradlew :composeApp:testAndroidHostTest`). Extend `extraTypes` only for
  Ktor-owned types, per D10. `grep -r UnavailableUserRemoteDataSource` finds nothing outside openspec/, and
  `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` succeeds with `initKoinIos` exported.
- [x] 5.5 Install the app on an emulator via android-cli and confirm it launches with no Koin or Ktor crash
  (`AppLaunchSmokeTest` also covers this). Check that the merged manifest contains `android.permission.INTERNET`
  (`androidApp/build/intermediates/merged_manifest*/**/AndroidManifest.xml`). Verify: the app reaches the users screen,
  and the grep finds the permission.
- [x] 5.6 Document the application scope in README.md: the application creates it (`SliideApplication` / `iOSApp`) and hands it to Koin, every repository operation outlives
  its caller, and crash-safety tests model a process kill by cancelling the scope. Add `GOREST_API_TOKEN` setup to the
  README's getting-started section: the key name only, never the value, and note that the app needs the token to
  write. Verify: it matches D8–D9, and `grep` for the token value across tracked files finds nothing.

## 6. Verification gate

- [x] 6.1 Run `./gradlew ktlintCheck`. Verify: no violations (fix them with `ktlintFormat`, never with a baseline).
- [x] 6.2 Run `./gradlew build` and `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64`. Verify: both succeed,
  with no tests disabled or weakened.
- [x] 6.3 For every affected module (`:core:domain`, `:core:data`, `:users:domain`, `:users:data`, `:users:presentation`,
  `:composeApp`, `:androidApp`), run all unit tests (`allTests` / `testAndroidHostTest`) AND all instrumented tests
  (`connectedAndroidDeviceTest` / `connectedAndroidTest`) on an emulator managed via android-cli. Verify: every run passes.
