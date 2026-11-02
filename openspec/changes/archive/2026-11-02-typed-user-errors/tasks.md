# Tasks

## 0. Prerequisites

- [x] 0.1 Confirm `add-offline-pending-sync`, `add-ktor-api-client`, `add-background-sync` and `build-users-ui` are
  archived, so `openspec list --specs` lists `offline-sync`, `user-remote-api`, `background-sync`, `user-profile` and
  `user-deletion`. Verify: `openspec validate typed-user-errors --strict` reports no "target spec does not exist" notes.

## 1. Core: typed 422 (design D1, D2, D5)

- [x] 1.1 In `:core:domain` `DataError.kt`, add `DataError.Validation(fields)` and `FieldError(field, message)`, and
  remove `DataError.Remote.VALIDATION`. Verify: `:core:domain` compiles and `ResultTest` passes.
- [x] 1.2 Remove `isRetryable` from `:core:domain` `DataError.kt` and delete `DataErrorTest`; update the
  `toRemoteError` KDoc that references it. Verify: `./gradlew :core:domain:allTests :core:data:allTests` passes (the
  users modules are fixed in groups 2-4).
- [x] 1.3 In `:core:data`, expose `HttpClientFactory.json`; make `post`/`patch`/`delete` return `Result<_, DataError>`
  and parse a 422 body (bounded read) into `Validation` with `GoRestFieldErrorDto`; an empty, non-JSON or non-list
  body (or a failed read) gives `Validation(emptyList())`; remove `422` from `toRemoteError`. Delete `HttpFailure` and
  the `*ForFailure` helpers. Verify: `HttpClientExtTest` covers, for POST, PATCH and DELETE, a 422 with one field, with
  several fields in order, with an empty body and with `{"message":"rejected"}`, plus the existing status, transport
  and cancellation tests.

## 2. Domain (design D6)

- [x] 2.1 Add `WriteStatus.QUEUED_AUTH_FAILED` and a `WriteStatus.isQueued` extension. Verify: `:users:domain`
  compiles.
- [x] 2.2 Change `UserApiClient.create`/`update`/`delete` to return `DataError` and update the `UserApiClient` and
  `UserRepository` KDoc (422 → `Validation`, 401 now queued). Delete `UserError`. Verify: `:users:domain` compiles.

## 3. Data: policy and repository (design D3, D4)

- [x] 3.1 Make `KtorUserApiClient` create/update/delete pass the helpers' `DataError` through; delete
  `UserWriteFailureClassifier` and its test. Verify: `KtorUserApiClientTest` (MockEngine) covers 201/200/204, 422
  field errors on create and update, 422 on delete (`Validation`), 404, 401 and 503, and the existing tests still pass.
- [x] 3.2 `OutboxPolicy.decide(operation, error: DataError): OutboxDecision` (KEEP / REJECT / GONE) per design D4's
  table, with exhaustive `when`s and no `else`. Verify: one parameterised test asserts every (operation × error) cell,
  including every `DataError.Remote` and `DataError.Local` value and `Validation` with and without fields.
- [x] 3.3 Failure handling in `OfflineFirstUserRepository.createUser`, `updateUser` and `finalizeDelete` calls
  `OutboxPolicy` and applies design D4's direct-write column (KEEP → `QUEUED`, or `QUEUED_AUTH_FAILED` for
  `UNAUTHORIZED`; REJECT → drop the pending entry, and for a create also its temp row, with no other rollback; GONE →
  per operation). Verify: `OfflineFirstUserRepositoryWriteTest`
  covers, for each operation, 401 → kept and `QUEUED_AUTH_FAILED`, 503 → kept and `QUEUED`, `Validation` → entry
  dropped with the error (cache corrected by the next refresh), 404 → per operation; tests that asserted 401 rollback are updated to the new spec, not deleted.
- [x] 3.4 `syncPending` is one loop over the queue (no `ReplayOutcome`): success clears the entry (re-keying a create);
  `OutboxPolicy` KEEP keeps the entry and the pass carries on (reporting the first kept error); REJECT/GONE only delete the entry, leaving the cached users to the next refresh.
  Verify: `OfflineFirstUserRepositorySyncTest` covers replayed 401 (entry kept, the rest still sent), replayed 422 on each
  operation (entry dropped, cache untouched, counted rejected), replayed 400 on CREATE (dropped, next entry still
  sent), replayed 404 on UPDATE (rejected) and DELETE (synced), and that a refresh after a rejected create removes the
  temp user, matching `specs/offline-sync`.
- [x] 3.5 A test runs the same (operation, error) pairs through a direct write and through replay and asserts the same
  kept/rejected/done outcome (spec: "Direct writes and replay decide the same way"). Verify: it passes for 500 and
  `Validation` on CREATE at minimum.
- [x] 3.6 Update `FakeUserApiClient`, `RepositoryHarness` and the `androidDeviceTest` integration tests to the new
  types and to sync leaving the cache to refresh; one integration test queues a 401 create and a later `syncPending` with a 201 re-keys it (spec:
  background-sync "Unauthorised create is queued"). Verify: `./gradlew :users:data:testAndroidHostTest
  :users:data:iosSimulatorArm64Test` passes.

## 4. Presentation: precise messages (design D7)

- [x] 4.1 `DataError.toUiText()` handles `Validation` ("<Field> <message>" per field, one per line; no fields →
  `error_validation`); add `delete_refused` and `write_queued_auth_failed`, remove `validation_email_in_use`; delete
  `UserErrorUiText`. Verify: `DataErrorUiTextTest` asserts that `email`/"has already been taken" renders "Email has
  already been taken", that several field errors render together in order, and that no fields renders
  `error_validation`.
- [x] 4.2 `ProfileViewModel`: every save failure shows `error.toUiText()` as a snackbar and keeps the input;
  `serverEmailError` and its clearing logic are removed; `QUEUED_AUTH_FAILED` → "Couldn't authenticate, will retry" for
  both edit and create. Verify: `ProfileViewModelTest` covers every scenario in `specs/user-profile`.
- [x] 4.3 `DeleteUndoController`: `FinalizeFailed` carries the `DataError`; `QUEUED_AUTH_FAILED` emits
  `DeleteMessage.QueuedAuthFailed`; `UsersViewModel` shows "The server refused to delete <name>" for `Validation` and
  "Couldn't delete <name>" otherwise. Verify: `DeleteUndoControllerTest` and `UsersViewModelTest` cover the
  `specs/user-deletion` scenarios (422 refused, 400 generic, 401 queued with message).
- [x] 4.4 Update `FakeUserRepository` and any previews or device tests that build write failures. Verify:
  `./gradlew :users:presentation:testAndroidHostTest :users:presentation:iosSimulatorArm64Test` passes.

## 5. Documentation

- [x] 5.1 Update README.md: the HTTP stack's 422 handling, the outbox policy table (including 401 → kept), and the AI
  contribution for this change. Verify: the README table matches design D4.

## 6. Verification gate

- [x] 6.1 Run `./gradlew ktlintCheck` and fix violations without adding a baseline. Verify: it passes.
- [x] 6.2 Run all unit tests and all instrumented tests for every affected module: `:core:domain`, `:core:data`,
  `:users:domain`, `:users:data`, `:users:presentation` (`testAndroidHostTest` plus `iosSimulatorArm64Test` /
  `allTests` where present), `:core:data`, `:users:data` and `:users:presentation` `connectedAndroidDeviceTest`, and
  `:androidApp` `connectedDebugAndroidTest`, on an emulator started with the android-cli skill. Verify: every test
  passes, none skipped or disabled.
- [x] 6.3 Run `./gradlew build` and `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64`. Verify: both succeed.
- [x] 6.4 Against the real GoRest API, create a user with an email already used by another user on the last page.
  Verify: on the emulator (android-cli skill) the profile stays open with the input kept and a snackbar reads "Email
  has already been taken".

## Workflow follow-up

- Archive the change after review; it must be archived after the four prerequisite changes.
