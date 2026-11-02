# Spec Delta

## MODIFIED Requirements

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
