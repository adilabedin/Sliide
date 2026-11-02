# Design

## Context

See proposal.md for the motivation. The relevant current state:

- `:core:data` `HttpClientExt.kt`: `post`/`patch`/`delete` go through `safeCall` + `toResult`, which maps a non-2xx
  status to `DataError.Remote` via `HttpStatusCode.toRemoteError()` and discards the response body.
- `:users:domain` `UserApiClient` returns `Result<_, DataError.Remote>`; `UserRepository` write methods return
  `Result<WriteStatus, DataError>` (the repository adds `DataError.Local.NOT_FOUND` for an uncached user).
- `DataError.Remote` is an enum; `DataError.Remote.VALIDATION` carries no detail.
- `OfflineFirstUserRepository` decides failure handling inline in `createUser`, `updateUser`, `finalizeDelete` and
  `replay`, each with its own `when` over `isRetryable` plus special cases for `NOT_FOUND`.
- `ProfileViewModel.onSaveFailure` treats every `VALIDATION` as "email may already be in use";
  `DeleteUndoController.DeleteMessage.FinalizeFailed` carries a `DataError`; `DataError.toUiText()` maps errors to strings.
- GoRest v2 (reference pasted during exploration): 422 bodies are `[{ "field", "message" }]`; a DELETE can return 422
  when fewer than 100 seeded records remain; 401 means missing, invalid or expired token; data resets every 3 hours, so
  404s on cached ids are routine.

## Goals / Non-Goals

**Goals:**
- A 422 carries what GoRest said (field and message) through the existing `DataError` type, with no parallel error
  hierarchy.
- One pure, exhaustively tested function that maps (operation, error) to an outbox decision, used by both direct writes
  and replay.
- Presentation reacts to typed errors without guessing.

**Non-Goals:**
- Changing the pending-sync table, merge rules, replay ordering, single-flight or Undo-window behaviour.
- Changing reads: `fetchLastPage`, `refresh` and `syncPending` keep `DataError` / `DataError.Remote`.
- Using `X-RateLimit-Reset` for backoff, moving to GoRest v3, or refreshing the token at runtime.

## Decisions

### D1. `DataError.Validation` is a sibling of the existing enums

```kotlin
sealed interface DataError : Error {
    enum class Local : DataError { DISK_FULL, NOT_FOUND, UNKNOWN }
    enum class Remote : DataError { NO_INTERNET, REQUEST_TIMEOUT, SERVER_ERROR, TOO_MANY_REQUESTS, UNAUTHORIZED,
        NOT_FOUND, SERIALIZATION, UNKNOWN }          // VALIDATION removed
    data class Validation(val fields: List<FieldError>) : DataError
}
data class FieldError(val field: String, val message: String)
```

An enum value cannot carry a field list, so the 422 case becomes its own `data class` next to the enums rather than
turning `Remote` into a sealed interface (which would rename every value to PascalCase under `ktlint_official` and lose
`entries`). `VALIDATION` is removed so a 422 has exactly one representation. 404 stays `Remote.NOT_FOUND`, an uncached
user stays `Local.NOT_FOUND`.

*Alternative:* a feature-level `UserError` sealed type wrapping `DataError`. Rejected by the project owner: it
duplicates the core mapping in a second hierarchy for one feature. *Alternative:* `Remote` as a sealed interface.
Rejected: ktlint renames and lost `entries` for no extra expressiveness.

### D2. The `:core:data` write helpers parse 422 bodies

`post`, `patch` and `delete` return `Result<T, DataError>`. For a 422 they read the body as bounded text and decode it
as `List<GoRestFieldErrorDto>` with `HttpClientFactory.json`; a body that is empty, not JSON, not a list, or fails to
read becomes `Validation(emptyList())`. Every other status maps through the unchanged `toRemoteError()`. Transport
exceptions and cancellation behave as before. `get` keeps `Result<_, DataError.Remote>` (GoRest reads never 422).

The 422 body format is GoRest-wide, and `:core:data` already owns GoRest knowledge (`GoRestApi`, credentials), so this
is API knowledge in core, not users-feature knowledge; the dependency rules hold.

### D3. Delete refused is a `Validation` on a DELETE

GoRest answers 422 to a DELETE when fewer than 100 seeded records remain; that body is undocumented. No separate type is
added: whoever handles a failed delete already knows it was a delete, so `Validation` on DELETE reads as "refused".
`KtorUserApiClient` passes the helpers' results through unchanged.

*Alternative:* a `DELETE_REFUSED` value set by the delete helper. Rejected: the policy and the UI already branch on the
operation, so a second value would carry no extra information.

### D4. `OutboxPolicy`: a pure decision table in `:users:data`

```kotlin
enum class OutboxDecision { KEEP, REJECT, GONE }
object OutboxPolicy { fun decide(op: PendingOperation, error: DataError): OutboxDecision }
```

| error | CREATE | UPDATE | DELETE |
|---|---|---|---|
| Remote: NO_INTERNET, REQUEST_TIMEOUT, SERVER_ERROR, TOO_MANY_REQUESTS, UNAUTHORIZED | KEEP | KEEP | KEEP |
| Remote: SERIALIZATION, UNKNOWN (incl. 400) | REJECT | REJECT | REJECT |
| Validation (422; on DELETE: refused) | REJECT | REJECT | REJECT |
| Remote: NOT_FOUND (404) | REJECT | GONE | GONE |
| Local | REJECT | REJECT | REJECT |

`UNAUTHORIZED` covers 401 and 403 (existing status mapping). The policy returns *what* to do; the repository decides
*how*, because the two paths hold different information:

| decision | direct write | replay |
|---|---|---|
| KEEP | queue the write; return `QUEUED` (`QUEUED_AUTH_FAILED` for `UNAUTHORIZED`) | keep the entry, carry on with the next one; the pass reports the first such error |
| REJECT | drop the entry; create also deletes its temp row (the form stays open for a corrected resubmit); return the error | drop the entry only; count `rejected` |
| GONE | delete: clear entry, return `SYNCED`; update: delete row + entry, return `NOT_FOUND` | drop the entry only; count `synced` for a delete, `rejected` for an update |

This keeps all error *classification* in one table (one parameterised test covers every cell). Neither path rolls the
cache back for a dropped entry (apart from a rejected direct create's temp row): the next refresh replaces the cached list with the server's, which
removes a rejected temp user, brings back a refused delete and drops a user that no longer exists. Sync stays one
loop over the queue with no per-entry outcome type: a success clears the entry (re-keying a created user, because
GoRest's last page usually won't contain it), a KEEP leaves the entry for the next sync and the pass
carries on (it reports the first such `DataError.Remote`, so `syncPending`'s signature is unchanged), and anything else
deletes the entry. Each queued write is sent once per sync; there is no retry inside a pass and no per-write retry job.

Every write (create, edit, delete) is queued only after its request fails with a KEEP error. A successful write
therefore never touches the queue except to clear an older entry it supersedes; the accepted cost is that a process
killed mid-request loses that write, and the next refresh shows the server's data. `writeMutex` is held for the whole
write, so sync can never send an older entry for the same user while the request is in flight.

*Alternative:* encode the rollback into richer decisions (`DROP_AND_RESTORE_PREVIOUS`, `DROP_AND_KEEP_LOCAL`, ...).
Rejected: the decision would depend on the path as well as the error, which brings back the per-path branching the
policy is meant to remove.

### D5. `isRetryable` is removed

Its only consumers are the repository and `DataErrorTest`. Leaving it would keep a second, diverging source of truth
(it says 401 is not retryable; the policy says KEEP). The KEEP row of D4 replaces it.

### D6. `WriteStatus.QUEUED_AUTH_FAILED`

`WriteStatus` gains a third value. A plain `QUEUED` would make the UI say "Saved offline" for an auth failure, and a
`Failure` would make it roll back the form, contradicting the KEEP decision. An enum value keeps every existing
`== WriteStatus.QUEUED` check meaningful; call sites that mean "queued for any reason" switch to a small
`WriteStatus.isQueued` extension.

### D7. Presentation mapping

- `DataError.toUiText()` gains the `Validation` case: with fields, each renders as
  `"${field.replaceFirstChar(Char::titlecase)} $message"` (e.g. "Email has already been taken"), one per line, as
  `UiText.Dynamic`; with no fields it is the existing `error_validation` ("The server rejected these details"). GoRest
  messages are English sentence fragments; they are shown as sent, untranslated. New string resources:
  `delete_refused` ("The server refused to delete %1$s"), `write_queued_auth_failed` ("Couldn't authenticate, will
  retry"). `validation_email_in_use` is removed.
- `ProfileViewModel.onSaveFailure` no longer branches on the error: every failure is
  `send(ProfileEvent.ShowMessage(error.toUiText()))`, and the form keeps its input. `serverEmailError` and its
  clearing logic are removed; the email field only shows local validation.
- `DeleteUndoController.DeleteMessage.FinalizeFailed` keeps carrying a `DataError`; a `Validation` failure picks the
  "refused" string. A `QUEUED_AUTH_FAILED` finalise emits a new `DeleteMessage.QueuedAuthFailed`.

## Risks / Trade-offs

- [Server messages are English fragments and untranslated] → acceptable for this challenge; the field name is
  prefixed so they read as sentences, and only placement (not wording) is decided by the app.
- [GoRest changes a message's wording] → the new wording is shown as-is; nothing in the app depends on it.
- [Bad or expired build token pauses the queue indefinitely] → nothing is lost and every write reports
  `QUEUED_AUTH_FAILED`, so the user sees why; a new build with a valid token drains the queue.
- [Reading error bodies adds cost to every failure] → only non-2xx responses are read, and the text is bounded.
- [This change rewrites code the unarchived `add-background-sync` and `build-users-ui` changes still own] → archive those
  first (also required for the MODIFIED spec deltas to apply; see Migration Plan).

## Migration Plan

1. Finish and archive `add-offline-pending-sync`, `add-ktor-api-client`, `add-background-sync` and `build-users-ui`, so
   their capabilities exist in `openspec/specs/` and this change's MODIFIED deltas can archive.
2. Implement bottom-up (core:domain → core:data → users:domain → users:data → users:presentation).
3. No data migration: the pending-sync table and entries are unchanged. Entries queued by an older build are replayed
   and classified under the new policy.
4. Rollback is a code revert; no persisted state depends on the new types.
