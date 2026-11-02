# Spec Delta

## MODIFIED Requirements

### Requirement: HTTP status codes map to typed remote errors
Every non-2xx response SHALL be returned as a typed failure, never thrown: 401 and 403 → `UNAUTHORIZED`, 404 → `NOT_FOUND`,
408 → `REQUEST_TIMEOUT`, 429 → `TOO_MANY_REQUESTS`, 5xx → `SERVER_ERROR`, any other → `UNKNOWN`. A 422 to a POST, PATCH
or DELETE SHALL be returned as a validation failure carrying each field name and message from GoRest's
`[{"field": ..., "message": ...}]` body, unchanged and in order; a 422 body that is empty, not JSON or not a list of
field errors SHALL give a validation failure with no fields, never a serialization failure.

#### Scenario: Validation error on create
- **WHEN** a POST returns 422 with `[{"field":"email","message":"has already been taken"}]`
- **THEN** the call returns a validation failure naming `email` with the message "has already been taken"

#### Scenario: Several field errors
- **WHEN** a PATCH returns 422 with an `email` error followed by a `name` error
- **THEN** the call returns a validation failure naming `email` then `name`, each with its own message

#### Scenario: Unreadable validation body
- **WHEN** a POST returns 422 with an empty body, or with `{"message":"rejected"}`
- **THEN** the call returns a validation failure with no fields

#### Scenario: Rate limited
- **WHEN** any request returns 429
- **THEN** the call returns a `TOO_MANY_REQUESTS` failure

#### Scenario: Server error
- **WHEN** any request returns 503
- **THEN** the call returns a `SERVER_ERROR` failure
