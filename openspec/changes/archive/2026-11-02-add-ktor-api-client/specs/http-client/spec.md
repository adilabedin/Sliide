# Spec Delta

## Purpose

The shared HTTP stack every remote data source uses to talk to the GoRest API. It covers authentication, timeouts,
response headers, and turning every HTTP or transport outcome into a typed remote error, identically on Android and iOS.

## ADDED Requirements

### Requirement: API credentials are supplied at build time and never committed
The GoRest base URL and bearer token SHALL be supplied at build time. The token SHALL be read from a git-ignored local
source (the local properties file or an environment variable) and SHALL NOT appear in any tracked file. When no token is
configured, the build and all tests SHALL still succeed, with the token treated as empty.

#### Scenario: Token configured locally
- **WHEN** the developer has set the GoRest token in their local, git-ignored configuration and builds the app
- **THEN** every API request carries that token, and no tracked file in the repository contains it

#### Scenario: Token missing
- **WHEN** the project is built on a machine with no token configured
- **THEN** the build and the unit tests succeed, and API calls that need authorisation fail with `UNAUTHORIZED`

### Requirement: Every request is authenticated and JSON-encoded
Every request SHALL target the configured GoRest base URL, SHALL carry an `Authorization: Bearer <token>` header, and
SHALL send and accept JSON. Unknown JSON fields in responses SHALL be ignored.

#### Scenario: Authorization header on every method
- **WHEN** any GET, POST, PATCH or DELETE request is sent
- **THEN** it carries `Authorization: Bearer <token>` and is addressed relative to the configured base URL

#### Scenario: Extra response fields
- **WHEN** a response body contains fields the client does not model
- **THEN** decoding succeeds and the extra fields are ignored

### Requirement: GET exposes response headers alongside the body
A GET request SHALL return both the decoded body and the response headers on success, so callers can read pagination
metadata such as `X-Pagination-Pages`.

#### Scenario: Reading a pagination header
- **WHEN** a GET succeeds with a header `X-Pagination-Pages: 7`
- **THEN** the caller receives the decoded body and can read the value `7` from the returned headers

### Requirement: HTTP status codes map to typed remote errors
Every non-2xx response SHALL be returned as a typed failure, never thrown: 401 and 403 → `UNAUTHORIZED`, 404 → `NOT_FOUND`,
408 → `REQUEST_TIMEOUT`, 422 → `VALIDATION`, 429 → `TOO_MANY_REQUESTS`, 5xx → `SERVER_ERROR`, any other → `UNKNOWN`.

#### Scenario: Validation error on create
- **WHEN** a POST returns 422
- **THEN** the call returns a `VALIDATION` failure

#### Scenario: Rate limited
- **WHEN** any request returns 429
- **THEN** the call returns a `TOO_MANY_REQUESTS` failure

#### Scenario: Server error
- **WHEN** any request returns 503
- **THEN** the call returns a `SERVER_ERROR` failure

### Requirement: Transport failures map to typed remote errors on every platform
Exceptions SHALL map to a typed failure the same way on Android and iOS. Any failure to reach the server (no
connectivity, DNS failure, connection refused or reset) → `NO_INTERNET`. A request, connect or socket timeout →
`REQUEST_TIMEOUT`. A body that cannot be decoded → `SERIALIZATION`. Anything else → `UNKNOWN`. Cancellation SHALL
propagate and never become a failure.

#### Scenario: Device offline
- **WHEN** a request is sent while the device has no connectivity, on Android or on iOS
- **THEN** the call returns `NO_INTERNET`, which is retryable, so offline writes are queued and not discarded

#### Scenario: Request exceeds the timeout
- **WHEN** the server does not answer within the configured request timeout
- **THEN** the call returns `REQUEST_TIMEOUT`

#### Scenario: Malformed body
- **WHEN** a 2xx response body does not match the expected shape
- **THEN** the call returns `SERIALIZATION`

#### Scenario: Caller cancelled
- **WHEN** the coroutine running a request is cancelled
- **THEN** the cancellation propagates to the caller instead of being returned as a failure

### Requirement: Requests are bounded by a timeout
Every request SHALL be bounded by a request timeout of at most 30 seconds, so no caller, including a
background sync on iOS, waits longer than one timeout for a single request.

#### Scenario: Hung connection
- **WHEN** the server accepts the connection but never responds
- **THEN** the request ends with `REQUEST_TIMEOUT` once the timeout elapses

### Requirement: Empty-body success responses
A successful response with no body (for example `204 No Content`) SHALL succeed without decoding a body.

#### Scenario: Delete returns 204
- **WHEN** a DELETE returns 204 with an empty body
- **THEN** the call returns success
