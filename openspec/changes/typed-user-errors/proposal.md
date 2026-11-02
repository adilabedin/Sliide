# Proposal

## Why

Every GoRest 422 currently collapses into one `DataError.Remote.VALIDATION`, so the app cannot tell "email already
taken" from any other rejected field, or from a refused delete, even though GoRest says exactly which field failed and
why. The profile screen therefore guesses that every 422 means "This email may already be in use". On top of that, the rule for what happens to a write after a failure (keep it
queued, drop it, revert it) is written inline four times in `OfflineFirstUserRepository` (create, update, finalise
delete, replay) on top of one yes/no `isRetryable` flag, so the direct write path and the replay path can drift apart.

The pending-sync replay itself stays: it is the only way a queued write ever reaches the server. What changes is how
failures are classified and how the outcome is decided.

## What Changes

- Add `DataError.Validation(fields: List<FieldError>)` and `FieldError(field, message)` to `:core:domain`, next to the
  unchanged `DataError.Remote` and `DataError.Local` enums. `DataError.Remote.VALIDATION` is removed: a 422 is always
  `Validation`.
- The `:core:data` write helpers (`post`, `patch`, `delete`) parse a 422 body (`[{"field": ..., "message": ...}]`, the
  format every GoRest endpoint uses) into `Validation`, keeping GoRest's field names and messages. The app does not
  interpret the message text (no "taken" matching). A 422 body that cannot be parsed becomes `Validation(emptyList())`,
  never a serialization failure. Write helpers return `Result<_, DataError>`; `get` keeps `DataError.Remote`.
- A `Validation` failure on a DELETE is a refused delete (GoRest refuses deletes while the table holds fewer than 100
  seeded records); no separate type is needed because every consumer already knows the operation.
- One pure outbox policy decides, per operation and error, whether a failed write is kept queued, rejected, or treated as
  already gone. Direct writes and replay both use it.
- **BREAKING** (internal API): `UserApiClient` write methods return `DataError` (they may now fail with `Validation`).
  `UserRepository` write methods keep returning `DataError`. `isRetryable` is removed from `:core:domain`; its role
  moves into the outbox policy.
- **Behaviour change**: a write that fails with 401 or 403 is now kept queued instead of being dropped and rolled back.
  It is reported with a new `WriteStatus.QUEUED_AUTH_FAILED`, so the UI can say "Couldn't authenticate, will retry"
  rather than "Saved offline". Replay stops at a 401/403 like it does at a 5xx. Refresh (a read) is unaffected and
  still reports an unauthorised error.
- The profile shows server field errors in a snackbar as "<Field> <message>" (for example "Email has already been
  taken"). The ViewModel does not interpret or place them. Server messages are shown in English as GoRest returns
  them.
- A refused delete restores the user and says the server refused the delete.

## Capabilities

### New Capabilities
- `user-write-errors`: what a failed GoRest user write means (422 field errors, refused delete, not found, transport
  failures) and the single policy mapping each operation and error to an outbox outcome.

### Modified Capabilities
- `http-client`: a 422 is returned as a validation failure carrying GoRest's field errors instead of a bare
  `VALIDATION`.
- `offline-sync`: which failures are queued (now including 401/403), how rejected replays are handled, and that the
  decision comes from the shared policy.
- `user-remote-api`: a 422 on create, update or delete returns the field errors GoRest sent.
- `background-sync`: "permanent errors are never queued" is narrowed to rejections; an authentication failure is queued.
- `user-profile`: rejected saves show GoRest's field messages in a snackbar; an unauthorised save is kept as queued.
- `user-deletion`: a delete refused by the server restores the user with a specific message; an unauthorised delete is
  queued instead of restored.

These capabilities are currently defined only in the unarchived changes `add-offline-pending-sync`,
`add-ktor-api-client`, `add-background-sync` and `build-users-ui`. Those changes must be archived before this one, so
that the modified requirements exist in `openspec/specs/`.

## Impact

- `:core:domain`: `DataError.Validation` and `FieldError` added, `DataError.Remote.VALIDATION` removed; `isRetryable`
  and `DataErrorTest` removed.
- `:core:data`: `HttpClientExt.kt` write helpers parse 422 bodies and return `DataError`; `HttpClientFactory` exposes
  its `Json` so the parser decodes the same way as the client.
- `:users:domain`: new `WriteStatus.QUEUED_AUTH_FAILED`; `UserApiClient` write methods return `DataError`
  (`fetchLastPage`, `refresh` and `syncPending` keep theirs).
- `:users:data`: `KtorUserApiClient`, new outbox policy, `OfflineFirstUserRepository` (create/update/finalise/replay
  rewritten around the policy), fakes and tests.
- `:users:presentation`: `ProfileViewModel`, delete-undo handling, `DataError.toUiText()`, new string resources, tests.
- No new dependencies. No database schema change: the pending entry format is unchanged.
