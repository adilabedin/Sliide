# user-remote-api Specification

## Purpose
The GoRest `/users` remote data source. It fetches the users on the last page of the collection and creates, updates
and deletes users. Its results feed the offline-first repository.

## Requirements

### Requirement: The last page is found with two dependent requests
Fetching the last page SHALL first request `GET /users?page=1&per_page=20`, read the total page count from its
`X-Pagination-Pages` header, then request `GET /users?page=<count>&per_page=20` and return that page's users. The second
request SHALL start only after the first has succeeded. Both run within one operation.

#### Scenario: Several pages
- **WHEN** the first request succeeds with `X-Pagination-Pages: 412`
- **THEN** a second request is sent for `page=412&per_page=20` and its users are returned

#### Scenario: A single page
- **WHEN** the first request succeeds with `X-Pagination-Pages: 1`
- **THEN** no second request is sent and the first response's users are returned

#### Scenario: No users at all
- **WHEN** the first request succeeds with an empty list and a page count of 0 or 1
- **THEN** an empty list is returned successfully

### Requirement: The two page requests fail together
If either request fails, the whole fetch SHALL fail with that single error and SHALL return no users. A partial result
is never returned.

#### Scenario: First request fails
- **WHEN** the first request fails with `NO_INTERNET`
- **THEN** the fetch fails with `NO_INTERNET` and the second request is never sent

#### Scenario: Second request fails
- **WHEN** the first request succeeds but the last-page request returns 503
- **THEN** the fetch fails with `SERVER_ERROR` and the first page's users are discarded

#### Scenario: Cancelled between requests
- **WHEN** the operation is cancelled after the first request completes
- **THEN** the second request is not sent and cancellation propagates

### Requirement: Invalid pagination metadata fails the fetch
A missing or non-numeric `X-Pagination-Pages` header SHALL fail the fetch with `SERIALIZATION`.

#### Scenario: Header absent
- **WHEN** the first response succeeds but has no `X-Pagination-Pages` header
- **THEN** the fetch fails with `SERIALIZATION` and no second request is sent

### Requirement: An empty last page is a valid result
GoRest is public and changes constantly, so the computed last page can disappear between the two requests. When the
last-page response is an empty list, the fetch SHALL succeed with an empty list. The feed then shows its empty state,
and the user can refresh to load the new last page.

#### Scenario: Users deleted between the two requests
- **WHEN** the first response reports 5 pages and the request for page 5 returns an empty list
- **THEN** the fetch succeeds with an empty list, and no further request is sent

### Requirement: Create, update and delete map to the GoRest endpoints
Create SHALL send `POST /users` and expects 201 with the created user. Update SHALL send `PATCH /users/{id}` and expects
200 with the updated user. Delete SHALL send `DELETE /users/{id}` and expects 204. Create and update send the
name, email, gender and status; the returned user carries the server-assigned id. A failed create, update or delete
SHALL return the typed error from the HTTP client unchanged; for 422 that is a validation failure carrying GoRest's
field errors.

#### Scenario: User created
- **WHEN** a create is sent and GoRest answers 201 with `{"id": 7001, ...}`
- **THEN** the result is a user with id 7001 and the submitted details

#### Scenario: Email already taken
- **WHEN** a create is sent and GoRest answers 422 with `[{"field":"email","message":"has already been taken"}]`
- **THEN** the result is a validation failure naming `email` with the message "has already been taken"

#### Scenario: User updated
- **WHEN** an update for id 42 is sent
- **THEN** the request is `PATCH /users/42` and a 200 response yields the updated user

#### Scenario: User deleted
- **WHEN** a delete for id 42 is sent and GoRest answers 204
- **THEN** the result is success

#### Scenario: Deleting a user that no longer exists
- **WHEN** a delete for id 42 is sent and GoRest answers 404
- **THEN** the result is a `NOT_FOUND` failure (which the repository treats as a completed delete)

#### Scenario: Delete refused
- **WHEN** a delete for id 42 is sent and GoRest answers 422
- **THEN** the result is a validation failure, which the repository and UI treat as a refused delete

### Requirement: User wire format
Users SHALL be exchanged as JSON objects with `id`, `name`, `email`, `gender` and `status`. `gender` is `"male"` or
`"female"` and `status` is `"active"` or `"inactive"`, all lowercase. A response with any other gender or status value
SHALL fail with `SERIALIZATION`.

#### Scenario: Lowercase enums on the wire
- **WHEN** a user with gender FEMALE and status ACTIVE is created
- **THEN** the request body contains `"gender":"female"` and `"status":"active"`

#### Scenario: Unknown enum value
- **WHEN** a response contains `"status":"pending"`
- **THEN** the call fails with `SERIALIZATION`
