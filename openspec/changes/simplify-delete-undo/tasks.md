# Tasks

## 1. Domain and data: a single delete call

- [x] 1.1 In `UserRepository`, replace `removeUserLocally`, `restoreUser` and `finalizeDelete` with
  `deleteUser(user: User): Result<WriteStatus, DataError>` and document it (local first; temp id cancels its CREATE;
  404 = success; kept failure queues; rejection drops the entry). Verify: `:users:domain` compiles.
- [x] 1.2 In `OfflineFirstUserRepository`, implement `deleteUser` from the old `finalizeDelete` body (design D4).
  Delete `awaitingFinalize`, the replay skip in `replayEntry` and the open-window guard in `refresh`. Update the KDoc.
  Verify: `:users:data` compiles.
- [x] 1.3 Record `tempId → serverId` in `rekey` and resolve it at the start of `deleteUser` (design D5). Verify: a new
  test where -1 syncs as 7012 and `deleteUser(User(-1))` removes 7012 locally and sends DELETE 7012.
- [x] 1.4 Update the data tests (`WriteTest`, `SyncTest`, `SingleUserSyncTest`, `RefreshTest`, `LifetimeTest`,
  `CrashSafetyTest`, `PolicyParityTest`, `SyncRequestTest`, `BackgroundUserSyncTest`). Remove the Undo-window
  remove/restore/skip cases, move the delete cases to `deleteUser`, and add the spec scenarios "Delete while offline"
  and "Delete of a never-synced user". Verify: `./gradlew :users:data:allTests` passes.

## 2. Presentation: the feed owns the window

- [x] 2.1 In `UsersViewModel`, replace `DeleteUndoController` with a `pendingDelete` state, a delete `Job` and an
  injectable `undoWindow: Duration = 5.seconds` (design D1, D2, D6). Confirm, Undo, swipe-commit and a second delete
  follow the spec. Confirming the chosen user also clears the selection (design D2). Hide the pending user (and, for a temp id, its email) in `buildState`, and map `deleteUser` results
  to `UsersEvent.ShowMessage`. Add `UsersAction.OnConfirmDelete(userId)` for the profile hand-off. Verify: module
  compiles.
- [x] 2.2 In `ProfileViewModel`, remove `DeleteUndoController`. `confirmDelete()` emits
  `ProfileEvent.DeleteConfirmed(userId)` and closes; `UsersListDetailRoot` forwards it as
  `UsersAction.OnConfirmDelete` (design D3). Verify: module compiles.
- [x] 2.3 Delete `DeleteUndoController.kt`, `DeleteUndoControllerTest.kt` and its Koin `single`. Update
  `UsersPresentationModule` and `FakeUserRepository` (`deleteUser` and a record of deleted users). Verify:
  `grep -r DeleteUndoController` finds nothing outside `build/` and the openspec archive.
- [x] 2.4 Rewrite the delete tests in `UsersViewModelTest` with a `StandardTestDispatcher` and `advanceTimeBy`: card
  hidden at once with no repository call; Undo within 5 s → shown again and no delete; expiry → `deleteUser` called once;
  swipe → called at once; second delete → first finalised at once; stale Undo ignored; refresh during the window keeps
  the card hidden; 422 / 400 / auth messages; confirming the chosen user clears the
  selection so the pane moves to the next visible user. In `ProfileViewModelTest`, assert that
  confirm emits `DeleteConfirmed` + close and never touches the repository. Verify:
  `./gradlew :users:presentation:allTests` passes.

## 3. Documentation and verification

- [x] 3.1 Update README.md's delete / Undo section to describe the ViewModel-owned window and the single repository
  call. Verify: README no longer mentions `DeleteUndoController`.
- [x] 3.2 Run `./gradlew build` and `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64`, and
  `./gradlew ktlintCheck`. Verify: all succeed.
- [ ] 3.3 Run the instrumented tests for the affected modules on an emulator (android-cli), and manually check delete →
  Undo, delete → expiry, and delete from profile on a phone and a two-pane layout. Verify: tests pass and the
  behaviour matches the user-deletion spec.

## 4. Simplify along the lines of SliideChallenge

- [x] 4.1 Add `ApplicationScope` to `:core:domain`, bind it in `appModules`, and move `:users:data`'s Koin modules off the
  `:core:data` qualifier (design D2). Verify: `./gradlew :composeApp:testAndroidHostTest` (Koin graph test) passes.
- [x] 4.2 Simplify `UsersViewModel` (design D1): `hiddenIds`, id-less `OnUndoDelete` / `OnCommitDelete`, `finishDelete`
  in `ApplicationScope`, `onCleared()` finishes the window; drop `finalizing`, email matching and per-id checks. Update
  `UsersScreen`'s snackbar effect. Verify: module compiles.
- [x] 4.3 Update `UsersViewModelTest`: id-less actions, remove the stale-Undo and re-keyed-email tests, and add "clearing
  the ViewModel sends the pending delete". Verify: `./gradlew :users:presentation:allTests` passes.
- [x] 4.4 Update README and re-run `./gradlew build`, the iOS link and ktlint. Verify: all succeed.

## Workflow follow-up

- Archive the change after review (`/opsx:archive`), which merges the deltas into `openspec/specs`.
