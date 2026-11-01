# Design

## Context

- `:users:domain` owns `Error` / `Result` / `EmptyResult` / `DataError` (with `DataError.Remote` + `isRetryable`) under
  `…users.domain.util`, and the `UserRemoteDataSource` interface, renamed `UserApiClient` by this change (`fetchLastPage`, `create`, `update`, `delete`).
- `:users:data` has `OfflineFirstUserRepository` (Room-first, outbox, single-flight `syncPending` running in an optional
  `syncScope` that defaults to `CoroutineScope(SupervisorJob())`) and binds the `UnavailableUserRemoteDataSource` stub,
  which always returns `NO_INTERNET`. See `openspec/changes/add-offline-pending-sync/design.md` D5–D9.
- The repository already relies on the network layer for three things:
  - it treats only `NO_INTERNET`, `REQUEST_TIMEOUT`, `SERVER_ERROR` and `TOO_MANY_REQUESTS` as retryable;
  - it treats `NOT_FOUND` on delete as success;
  - it assumes a request can't wait longer than one timeout.
  A wrong mapping here is a data-loss bug. For example, a non-retryable error deletes an offline-created user instead
  of queueing it.
- Build: Gradle 9 + AGP 9 KMP library plugin, with convention plugins in `build-logic`. No Ktor or BuildKonfig in the
  catalog yet. `local.properties` is git-ignored.
- Several `:users:data` tests simulate a process kill by **cancelling the caller** of `updateUser` / `finalizeDelete`
  (`OfflineFirstUserRepositoryCrashSafetyTest`). After this change, cancelling the caller no longer stops the work
  (see `specs/operation-lifetime`), so those tests must model the kill differently (D9).

See `proposal.md` for motivation and `specs/` for the behaviour contract.

## Goals / Non-Goals

**Goals:**

- One place (`:core:data`) maps HTTP and transport outcomes to `DataError.Remote`, identically on both platforms, and is
  unit-tested with Ktor `MockEngine`.
- `KtorUserApiClient` is a thin, fully MockEngine-tested translation of the domain interface to GoRest.
- Repository operations outlive their callers without changing any existing result semantics.

**Non-Goals:**

- Request or response logging (it would risk printing the token). Retry and backoff. Certificate pinning.
- Any ViewModel or UI change. The application scope is in place for the presentation change that follows.
- Calling the live GoRest API from automated tests. Live checks are a manual step in tasks.md.

## Decisions

### D1. `Result`/`DataError` move to a new `:core:domain`

`:core:data` must return `Result<T, DataError.Remote>`, and core can't depend on features. The whole `util/` package
(`Error`, `Result`, `EmptyResult`, `DataError`, `isRetryable`, the helpers) moves together with `ResultTest` /
`DataErrorTest` to `:core:domain`, package `com.androidlearning.sliide.core.domain.util`.
- `DataError` is a sealed interface, so `Remote` can't be split out from `Local`.
- `:users:domain` declares `api(projects.core.domain)`, because its public signatures expose these types.
- `:core:domain` has no dependencies.

*Alternative:* `:core:data` defines its own `NetworkError`, and `:users:data` maps it onto `DataError.Remote`.
Rejected (the user's choice): it creates a second error vocabulary with a 1:1 mapping, and the mapping is exactly
where a retryability bug could hide.

### D2. `:core:data` = `kmp.library` + a new `buildkonfig` convention plugin

- `BuildKonfigConventionPlugin` (id `com.androidlearning.sliide.convention.buildkonfig`):
  - applies `com.codingfeline.buildkonfig` and sets `packageName = pathToPackageName()`;
  - declares two `STRING` fields: `BASE_URL = "https://gorest.co.in/public/v2/"` and `API_TOKEN`.
- `API_TOKEN` is read in this order:
  1. `GOREST_API_TOKEN` in the root `local.properties`, read with `java.util.Properties`. We don't use AGP's internal
     `gradleLocalProperties`, which can break between AGP versions.
  2. The `GOREST_API_TOKEN` environment variable, for CI.
  3. `""`, with a Gradle `logger.warn`.
- The skill's sample throws when the key is missing. We deliberately don't, so a clean checkout, CI and the
  MockEngine tests all build without the secret (spec: token missing).
- The generated `BuildKonfig` object stays `internal` to `:core:data`. No other module ever sees the token.
- The plugin reads the property at configuration time, so changing `local.properties` needs a re-sync. That's
  acceptable.

*Alternative:* put the BuildKonfig block in `core/data/build.gradle.kts`. Rejected: CLAUDE.md says module build files
only pick plugins and list dependencies.

### D3. `HttpClientFactory`: the engine and credentials are injected; config is fixed

```kotlin
object HttpClientFactory {
    fun create(
        engine: HttpClientEngine,
        baseUrl: String = BuildKonfig.BASE_URL,
        apiToken: String = BuildKonfig.API_TOKEN,
    ): HttpClient
}
```

- `expectSuccess = false`: status codes are mapped by us (D5), not thrown.
- `ContentNegotiation { json(Json { ignoreUnknownKeys = true; explicitNulls = false }) }`.
- `HttpTimeout { requestTimeoutMillis = 20_000; connectTimeoutMillis = 10_000; socketTimeoutMillis = 20_000 }`. This is
  within the 30 s bound in `specs/http-client` and the iOS background budget.
- `defaultRequest`: `url(baseUrl)`, the `Authorization: Bearer $apiToken` header, and `accept(Application.Json)`.
  `Content-Type: application/json` is set only on requests that have a body (POST/PATCH, in the D5 extensions), because
  GET and DELETE have no body to describe.
  The plain header is used instead of the `Auth` bearer plugin, which only sends the token after a 401 challenge
  unless configured otherwise, and adds refresh machinery that GoRest's static token doesn't need.
- **Routes are relative and have no leading slash** (`"users"`, `"users/42"`). With `defaultRequest`, a leading `/`
  replaces the base path and would drop `/public/v2`. A unit test pins this.
- Tests call `create(MockEngine { … }, baseUrl = "https://test/", apiToken = "t")`.

### D4. Engine per platform through `expect val platformHttpEngineModule` *(justified expect/actual)*

- `androidMain` binds `single<HttpClientEngine> { OkHttp.create() }`.
- `iosMain` binds `single<HttpClientEngine> { Darwin.create() }`.

This is a genuine platform difference: each OS has its own native HTTP stack. It mirrors the existing
`platformDatabaseModule`.
- `commonMain` `coreDataModule` binds `single<HttpClient> { HttpClientFactory.create(get()) }`.

*Alternative:* call `HttpClient()` with no engine and rely on engine auto-discovery. Rejected: discovery is implicit,
especially on native, and the engine couldn't be swapped through DI.

### D5. One `safeCall` + `responseToResult`, and the `HttpClient` extensions

In `:core:data`, package `…core.data.networking`:

```kotlin
data class NetworkResponse<T>(val body: T, val headers: Headers)

suspend inline fun <reified T> HttpClient.get(route: String, queryParameters: Map<String, Any?> = emptyMap()):
    Result<NetworkResponse<T>, DataError.Remote>
suspend inline fun <reified B, reified T> HttpClient.post(route: String, body: B): Result<T, DataError.Remote>
suspend inline fun <reified B, reified T> HttpClient.patch(route: String, body: B): Result<T, DataError.Remote>
suspend fun HttpClient.delete(route: String): EmptyResult<DataError.Remote>
```

- `get` always returns headers, as the user asked; callers that don't need them use `.map { it.body }`.
- `delete` never decodes a body, which covers 204 (spec: empty-body success).
- The body is decoded **inside** `safeCall`'s `try`, so a decoding failure maps to `SERIALIZATION` instead of escaping.

Status mapping (spec: http-client): 2xx success; 401/403 `UNAUTHORIZED`; 404 `NOT_FOUND`; 408 `REQUEST_TIMEOUT`;
422 `VALIDATION`; 429 `TOO_MANY_REQUESTS`; 5xx `SERVER_ERROR`; any other `UNKNOWN`.

Exception mapping. **Order matters**, because Ktor's timeout exceptions are themselves `IOException`s:
1. `CancellationException` → rethrow.
2. `HttpRequestTimeoutException`, `ConnectTimeoutException`, `SocketTimeoutException` → `REQUEST_TIMEOUT`.
3. `kotlinx.io.IOException` → `NO_INTERNET`. On Android, OkHttp's `UnknownHostException` / `ConnectException` are
   `IOException`s. On iOS, Darwin's `DarwinHttpRequestException`, which wraps `NSURLErrorNotConnectedToInternet` and
   similar errors, is an `IOException` too.
4. `SerializationException`, `ContentConvertException`, `NoTransformationFoundException` → `SERIALIZATION`.
5. `Exception` → `UNKNOWN`.

Rule 3 is what keeps offline creates queued on iOS (spec: device offline). It is verified on the simulator (tasks.md).
If Darwin turns out to throw something else, the fix belongs in this one function.

### D6. `KtorUserApiClient.fetchLastPage`: sequential calls, one result

```kotlin
override suspend fun fetchLastPage(): Result<List<RemoteUser>, DataError.Remote> {
    val first = client.get<List<UserDto>>("users", mapOf("page" to 1, "per_page" to PAGE_SIZE))
    // first failed → return it; header missing/NaN → SERIALIZATION; pages <= 1 → map first.body
    val last = client.get<List<UserDto>>("users", mapOf("page" to pages, "per_page" to PAGE_SIZE))
    // last failed → return it; else map last.body (an empty list is a valid result)
}
```

- **String constants live in their core module** (the user's rule). Every GoRest API string is in `GoRestApi` in
  `:core:data`: the `users` route, `user(id)`, the `page`/`per_page` query names, the `X-Pagination-Pages` header, the
  bearer prefix and `PAGE_SIZE = 20` (the user's choice). Database names (file, tables, columns used in queries) are
  in `DatabaseConstants` in `:core:database`. Room's exported schema is unchanged by that move.
- **Fail-together** comes for free from sequencing. The second call depends on the first's header, so it can't run in
  parallel. Any failure returns early as one `Result.Failure`, and cancellation between the calls propagates normally.
  `async`/`coroutineScope` would add nothing.
- **Pages ≤ 1** reuses the first body and saves a request.
- **The vanished last page** (the user's decision): if the computed last page has disappeared by the time it's
  requested and comes back empty, the fetch succeeds with an empty list. `refresh` then replaces the cache with nothing,
  the feed shows its empty state, and the user pulls to refresh, which loads the new last page.
  - *Alternatives:* treat it as a retryable `SERVER_ERROR` so the cache is kept, or re-request using the newer page
    count. Both were rejected as extra machinery for a rare race that a manual refresh already fixes.
- **Mapping is all-or-nothing:** one DTO with an unknown gender or status fails the whole call with `SERIALIZATION`
  (spec: user wire format).

### D7. DTOs and mappers in `:users:data`

- `@Serializable data class UserDto(val id: Long, val name: String, val email: String, val gender: String, val status: String)`.
- `@Serializable data class UserRequestDto(name, email, gender, status)`, used for POST and PATCH.
- Gender and status stay `String`s on the wire and are mapped explicitly (`"male"`↔`MALE`, …). An unknown value makes
  the mapper return `null`, which the data source turns into `SERIALIZATION`. Mapping inside the data source keeps the
  wire format visible and testable.
  - *Alternative:* `@SerialName` on the domain enums. Rejected: it would put serialization annotations on domain types.
- Endpoints:
  - `create` → `post<UserRequestDto, UserDto>("users", …)`;
  - `update` → `patch("users/$id", …)`;
  - `delete` → `delete("users/$id")`.
- **Naming** (the user's choice): the domain interface is `UserApiClient` (renamed from `UserRemoteDataSource`) and the
  Ktor implementation is `KtorUserApiClient`. The prefix names the technology and the interface names the role,
  matching `OfflineFirstUserRepository : UserRepository`.

### D8. The application scope: created by the application, handed to Koin

- The app entry point creates the scope as `CoroutineScope(SupervisorJob() + Dispatchers.Default)` and passes it to
  `initKoin`. This was the user's decision.
  - **Android:** `SliideApplication` owns it as a property, `val applicationScope = CoroutineScope(...)`, and calls
    `initKoin(applicationScope) { androidContext(...) }`. The `Application` object lives as long as the process.
  - **iOS:** there is no `Application` object, and Swift can't build a Kotlin `CoroutineScope`. So `:composeApp`
    `iosMain` exposes `fun initKoinIos()`, which creates the scope the same way and calls `initKoin`. `iOSApp.init()`
    calls it in place of `KoinKt.doInitKoin(config:)`. This is a platform entry-point function, not `expect`/`actual`.
- `initKoin(applicationScope: CoroutineScope, config: KoinAppDeclaration? = null)` registers the scope as
  `single(ApplicationScope) { applicationScope }`. Because `appModules` now depends on that scope, it becomes
  `appModules(applicationScope: CoroutineScope): List<Module>`, and `KoinGraphTest` passes a test scope.
- `:core:data` declares `val ApplicationScope: Qualifier = named("ApplicationScope")` in its `di` package, so
  `:composeApp`, which provides the scope, and `:users:data`, which consumes it, share one qualifier.
- The scope is never cancelled, because its lifetime is the process.
- *Alternative:* create the scope inside a Koin `single` in `:composeApp`. Rejected because the user wants the
  application to own the scope's lifetime explicitly.
- `OfflineFirstUserRepository`'s `syncScope` parameter is renamed `applicationScope`, and it becomes **required** (no
  default). The hidden default scope is how the scope could silently diverge from the app's.
- Each suspending public method wraps its existing body:
  `private suspend fun <T> outlivingCaller(block: suspend () -> T): T = applicationScope.async { block() }.await()`.
  - Cancelling the caller cancels only its `await()`. Because the scope has a `SupervisorJob`, an exception reaches
    the awaiting caller and doesn't cancel sibling operations.
  - `observeUsers()` stays a plain cold `Flow` (spec: observing stays bound to the observer).
  - `syncPending` already uses `applicationScope.async` for single-flight. `refresh` → `syncPending` nests inside the
    same scope; no lock is held across that call, so the non-reentrant `Mutex` is safe.
- *Alternative:* `withContext(NonCancellable)` around each body. Rejected: it still runs on the caller's dispatcher,
  ignores the user's request for an application-owned scope, and tests can't cancel it to simulate process death.

### D9. Reworking the crash-safety tests

The tests in `OfflineFirstUserRepositoryCrashSafetyTest` that cancel the **caller** to simulate a kill now model the
kill as cancelling the **application scope**. They use the pattern `ConcurrencyTest` already uses: a `killedScope`
child of `backgroundScope`, passed to `h.newRepository(applicationScope = killedScope)`, then
`killedScope.cancel()`. The test intent (a process dies mid-request and the outbox keeps the change) and the
assertions stay the same; only the way the kill is modelled changes. This keeps them faithful. Leaving the caller
cancellation in place would now let the request complete, and the test would no longer exercise a kill.

New tests then cover the new behaviour (spec: operation-lifetime): cancelling a gated caller of `createUser`,
`updateUser` and `finalizeDelete`, then releasing the gate, still re-keys, applies or deletes. A further test checks
that a failing operation doesn't cancel a concurrent one.

### D10. Koin assembly

- `appModules(applicationScope)` = a module binding the passed scope under `ApplicationScope`, `platformDatabaseModule`, `databaseModule`, `platformHttpEngineModule`,
  `coreDataModule`, `usersDataModule`, `usersPresentationModule`.
- `usersDataModule` binds `singleOf(::KtorUserApiClient) bind UserApiClient::class` and passes
  `applicationScope = get(ApplicationScope)`. `UnavailableUserRemoteDataSource` is deleted.
- `KoinGraphTest` (`verify`) must still pass. Koin's `verify` inspects constructor parameter types. If it flags a
  Ktor-owned type it can't see as a definition (for example `HttpClientConfig`), adding that type to `extraTypes` is the
  correct fix and does not weaken the test.
- **Android `INTERNET` permission.** `androidApp/src/main/AndroidManifest.xml` doesn't declare it today. It is added
  to `:core:data`'s `androidMain` manifest, so it merges into any app that uses the HTTP stack. Without it every call
  fails, either as a `SecurityException` (→ `UNKNOWN`, permanent, so offline creates are deleted) or as `EPERM`
  (→ `NO_INTERNET`, so the app silently looks offline forever).

## Risks / Trade-offs

- [Darwin may throw a non-`IOException` for some offline case, which would map to `UNKNOWN` and delete an offline create
  instead of queueing it] → A manual iOS simulator check with the network off is a task. The mapping lives in one
  function, so a fix is one line.
- [BuildKonfig's compatibility with the AGP 9 `com.android.kotlin.multiplatform.library` plugin and Kotlin 2.4] → Pick
  the latest BuildKonfig release and confirm `./gradlew build` plus the iOS link early (task 2). If it's incompatible,
  the fallback is a tiny `generateGoRestConfig` Gradle task in the same convention plugin that writes the same
  `internal object` into a generated `commonMain` source dir. The specs don't change.
- [A process-wide scope keeps working after the UI has gone] → This is intended. Work is bounded by one request timeout
  per HTTP call and by the existing per-entry locking. The OS can still kill the process, which the outbox already
  survives.
- [The token was shared in plain text while planning] → It only ever goes into the git-ignored `local.properties`.
  GoRest tokens can be regenerated if the value is considered exposed.
- [The token is a plain string constant in the shipped binary, and anyone can extract it from the APK or IPA] →
  Accepted, with no in-app encryption. Encrypting a build-time constant gives no real protection, because the
  decryption key has to ship in the same binary. R8 doesn't hide string constants either. Keychain or Android Keystore
  only help for secrets the app *receives* at runtime, not ones it ships with. The only real fix is to keep the token on
  a backend proxy, which is out of scope for a client-only challenge against a free, regenerable test API. Keeping it
  out of git is what actually matters here.
- [Running the app with no token configured] → The build succeeds by design, but every write gets a permanent 401
  (`UNAUTHORIZED`). Direct writes then roll back and queued replays are dropped. The developer must set
  `GOREST_API_TOKEN` before running the app. The README setup section says so, and the Gradle warning flags it.
- [The last page can be small (1–20 users)] → This is inherent to "show the last page". It was accepted with
  `per_page=20`.

## Migration Plan

- There are no data or schema migrations; Room stays at version 1.
- The package move from `…users.domain.util` to `…core.domain.util` is mechanical: move the files, then fix the
  imports.
- Rollback is reverting the change. Before apply, the developer adds `GOREST_API_TOKEN=<token>` to `local.properties`.
