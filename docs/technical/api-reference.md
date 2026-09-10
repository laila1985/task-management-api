# API Reference — Task Management API

> **Version:** 1.0  
> **Last Updated:** 2026-09-10  
> **Base URL:** `https://{api-id}.execute-api.{region}.amazonaws.com/prod`  
> **Content-Type:** `application/json`

---

## Table of Contents

1. [Overview](#1-overview)
2. [Authentication](#2-authentication)
3. [Common Headers](#3-common-headers)
4. [Error Responses](#4-error-responses)
5. [Endpoints](#5-endpoints)
   - [POST /tasks](#51-create-task)
   - [GET /tasks](#52-list-tasks)
   - [GET /tasks/{id}](#53-get-task)
   - [PUT /tasks/{id}](#54-update-task)
   - [DELETE /tasks/{id}](#55-delete-task)
6. [Data Types](#6-data-types)
7. [Status Codes](#7-status-codes)
8. [CORS](#8-cors)

---

## 1. Overview

The Task Management API provides CRUD operations for task resources. All endpoints accept and return JSON.

```
POST   /tasks          Create a new task
GET    /tasks          List all tasks (optionally filtered by status)
GET    /tasks/{id}     Get a specific task by ID
PUT    /tasks/{id}     Update an existing task
DELETE /tasks/{id}     Delete a task
```

### Lambda Handler Mapping

| Endpoint | Method | Lambda Handler |
|----------|--------|---------------|
| `/tasks` | POST | `CreateTaskHandler` |
| `/tasks` | GET | `ListTasksHandler` |
| `/tasks/{id}` | GET | `GetTaskHandler` |
| `/tasks/{id}` | PUT | `UpdateTaskHandler` |
| `/tasks/{id}` | DELETE | `DeleteTaskHandler` |

---

## 2. Authentication

**Phase 1–2:** No authentication required. API is publicly accessible.

**Phase 4 (planned):** Amazon Cognito User Pool authorizer will be added. Requests must include an `Authorization` header with a JWT token:

```
Authorization: Bearer <id_token>
```

---

## 3. Common Headers

### Request Headers

| Header | Value | Required | Description |
|--------|-------|----------|-------------|
| `Content-Type` | `application/json` | Yes (for POST/PUT) | Request body format |
| `Accept` | `application/json` | No | Response body format (default: JSON) |

### Response Headers

| Header | Value | Description |
|--------|-------|-------------|
| `Content-Type` | `application/json` | Response body format |
| `Access-Control-Allow-Origin` | `*` | CORS header |
| `Access-Control-Allow-Methods` | `GET,POST,PUT,DELETE,OPTIONS` | Allowed HTTP methods |
| `Access-Control-Allow-Headers` | `Content-Type,Authorization` | Allowed request headers |

---

## 4. Error Responses

All errors return a consistent JSON structure:

```json
{
  "error": "ERROR_CODE",
  "message": "Human-readable description of the error",
  "statusCode": 400
}
```

### Error Codes

| Error Code | HTTP Status | When |
|------------|-------------|------|
| `BAD_REQUEST` | 400 | Invalid input, missing required fields, malformed JSON |
| `NOT_FOUND` | 404 | Task with given ID does not exist |
| `CONFLICT` | 409 | Invalid state transition (e.g., DONE → TODO) |
| `INTERNAL_ERROR` | 500 | Unexpected server error |

---

## 5. Endpoints

---

### 5.1 Create Task

Creates a new task.

```
POST /tasks
```

#### Request

**Headers:**

```
Content-Type: application/json
```

**Body:**

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `title` | string | Yes | 1–200 chars, non-blank | Task title |
| `description` | string | No | 0–2000 chars | Task description |

**Example Request:**

```bash
curl -X POST https://{api-id}.execute-api.{region}.amazonaws.com/prod/tasks \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Learn AWS Lambda",
    "description": "Build my first serverless application"
  }'
```

**Minimal Request (description omitted):**

```bash
curl -X POST https://{api-id}.execute-api.{region}.amazonaws.com/prod/tasks \
  -H "Content-Type: application/json" \
  -d '{"title": "Learn AWS Lambda"}'
```

#### Response

**Status:** `201 Created`

**Body:**

| Field | Type | Description |
|-------|------|-------------|
| `id` | string (UUID) | Auto-generated unique identifier |
| `title` | string | Task title |
| `description` | string \| null | Task description (null if not provided) |
| `status` | string | Initial status: always `TODO` |
| `createdAt` | string (ISO 8601) | Creation timestamp (UTC) |
| `updatedAt` | string (ISO 8601) | Last update timestamp (same as createdAt on creation) |

**Example Response:**

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "title": "Learn AWS Lambda",
  "description": "Build my first serverless application",
  "status": "TODO",
  "createdAt": "2026-09-10T10:30:00Z",
  "updatedAt": "2026-09-10T10:30:00Z"
}
```

#### Error Cases

| Scenario | Status | Error Code | Example Message |
|----------|--------|------------|-----------------|
| Missing `title` | 400 | `BAD_REQUEST` | "Title is required and must not be blank" |
| `title` exceeds 200 chars | 400 | `BAD_REQUEST` | "Title must not exceed 200 characters" |
| `description` exceeds 2000 chars | 400 | `BAD_REQUEST` | "Description must not exceed 2000 characters" |
| Malformed JSON | 400 | `BAD_REQUEST` | "Request body is not valid JSON" |
| Unexpected server error | 500 | `INTERNAL_ERROR` | "An unexpected error occurred" |

#### Lambda Handler

```java
com.example.tasks.handler.CreateTaskHandler
```

**DynamoDB Operation:** `PutItem`  
**IAM Permission:** `dynamodb:PutItem` on `TasksTable`

---

### 5.2 List Tasks

Returns a list of tasks, optionally filtered by status.

```
GET /tasks?status={status}
```

#### Request

**Query Parameters:**

| Parameter | Type | Required | Values | Description |
|-----------|------|----------|--------|-------------|
| `status` | string | No | `TODO`, `IN_PROGRESS`, `DONE` | Filter tasks by status |

**Example Requests:**

```bash
# List all tasks
curl https://{api-id}.execute-api.{region}.amazonaws.com/prod/tasks

# List only TODO tasks
curl https://{api-id}.execute-api.{region}.amazonaws.com/prod/tasks?status=TODO

# List only IN_PROGRESS tasks
curl https://{api-id}.execute-api.{region}.amazonaws.com/prod/tasks?status=IN_PROGRESS
```

#### Response

**Status:** `200 OK`

**Body:** Array of task objects (may be empty)

| Field | Type | Description |
|-------|------|-------------|
| `[]` | array | List of task objects. Empty array `[]` if no tasks exist. |

Each task object has the same structure as the Create Task response.

**Example Response (with tasks):**

```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "title": "Learn AWS Lambda",
    "description": "Build my first serverless application",
    "status": "TODO",
    "createdAt": "2026-09-10T10:30:00Z",
    "updatedAt": "2026-09-10T10:30:00Z"
  },
  {
    "id": "660e8400-e29b-41d4-a716-446655440001",
    "title": "Deploy to production",
    "description": "Set up CI/CD pipeline",
    "status": "IN_PROGRESS",
    "createdAt": "2026-09-10T11:00:00Z",
    "updatedAt": "2026-09-10T12:00:00Z"
  }
]
```

**Example Response (no tasks):**

```json
[]
```

#### Error Cases

| Scenario | Status | Error Code | Example Message |
|----------|--------|------------|-----------------|
| Invalid `status` value | 400 | `BAD_REQUEST` | "Invalid status filter: 'INVALID'. Must be one of: TODO, IN_PROGRESS, DONE" |
| Unexpected server error | 500 | `INTERNAL_ERROR` | "An unexpected error occurred" |

#### Lambda Handler

```java
com.example.tasks.handler.ListTasksHandler
```

**DynamoDB Operation:** `Scan` (no filter) or `Query` on `StatusIndex` (with filter)  
**IAM Permissions:** `dynamodb:Scan`, `dynamodb:Query` on `TasksTable` and `StatusIndex`

---

### 5.3 Get Task

Returns a single task by its unique identifier.

```
GET /tasks/{id}
```

#### Request

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | string (UUID) | Yes | The unique identifier of the task |

**Example Request:**

```bash
curl https://{api-id}.execute-api.{region}.amazonaws.com/prod/tasks/550e8400-e29b-41d4-a716-446655440000
```

#### Response

**Status:** `200 OK`

**Body:** Single task object

| Field | Type | Description |
|-------|------|-------------|
| `id` | string (UUID) | Unique identifier |
| `title` | string | Task title |
| `description` | string \| null | Task description |
| `status` | string | Current status |
| `createdAt` | string (ISO 8601) | Creation timestamp |
| `updatedAt` | string (ISO 8601) | Last update timestamp |

**Example Response:**

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "title": "Learn AWS Lambda",
  "description": "Build my first serverless application",
  "status": "TODO",
  "createdAt": "2026-09-10T10:30:00Z",
  "updatedAt": "2026-09-10T10:30:00Z"
}
```

#### Error Cases

| Scenario | Status | Error Code | Example Message |
|----------|--------|------------|-----------------|
| Task not found | 404 | `NOT_FOUND` | "Task with id '550e8400-e29b-41d4-a716-446655440000' not found" |
| Unexpected server error | 500 | `INTERNAL_ERROR` | "An unexpected error occurred" |

#### Lambda Handler

```java
com.example.tasks.handler.GetTaskHandler
```

**DynamoDB Operation:** `GetItem`  
**IAM Permission:** `dynamodb:GetItem` on `TasksTable`

---

### 5.4 Update Task

Updates an existing task. Only provided fields are updated (partial update).

```
PUT /tasks/{id}
```

#### Request

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | string (UUID) | Yes | The unique identifier of the task |

**Headers:**

```
Content-Type: application/json
```

**Body:**

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `title` | string | No | 1–200 chars, non-blank | New task title |
| `description` | string | No | 0–2000 chars | New description (pass `null` to clear) |
| `status` | string | No | `TODO`, `IN_PROGRESS`, `DONE` | New status (must follow state machine) |

> **Note:** At least one field must be provided. Status transitions must follow: `TODO → IN_PROGRESS → DONE`.

**Example Requests:**

```bash
# Update title and status
curl -X PUT https://{api-id}.execute-api.{region}.amazonaws.com/prod/tasks/550e8400-e29b-41d4-a716-446655440000 \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Learn AWS Lambda - Updated",
    "status": "IN_PROGRESS"
  }'

# Update description only
curl -X PUT https://{api-id}.execute-api.{region}.amazonaws.com/prod/tasks/550e8400-e29b-41d4-a716-446655440000 \
  -H "Content-Type: application/json" \
  -d '{
    "description": "Updated description"
  }'

# Move task to DONE
curl -X PUT https://{api-id}.execute-api.{region}.amazonaws.com/prod/tasks/550e8400-e29b-41d4-a716-446655440000 \
  -H "Content-Type: application/json" \
  -d '{"status": "DONE"}'
```

#### Response

**Status:** `200 OK`

**Body:** Updated task object

| Field | Type | Description |
|-------|------|-------------|
| `id` | string (UUID) | Unchanged |
| `title` | string | Updated value (or unchanged if not provided) |
| `description` | string \| null | Updated value (or unchanged if not provided) |
| `status` | string | Updated value (or unchanged if not provided) |
| `createdAt` | string (ISO 8601) | Unchanged (set on creation) |
| `updatedAt` | string (ISO 8601) | Updated to current timestamp |

**Example Response:**

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "title": "Learn AWS Lambda - Updated",
  "description": "Build my first serverless application",
  "status": "IN_PROGRESS",
  "createdAt": "2026-09-10T10:30:00Z",
  "updatedAt": "2026-09-10T14:00:00Z"
}
```

#### Status State Machine

The `status` field must follow this transition:

```
TODO ──→ IN_PROGRESS ──→ DONE
```

| Current Status | Allowed Transitions |
|---------------|---------------------|
| `TODO` | → `IN_PROGRESS` |
| `IN_PROGRESS` | → `DONE` |
| `DONE` | (none — terminal state) |

**Invalid transitions return 409 Conflict.**

#### Error Cases

| Scenario | Status | Error Code | Example Message |
|----------|--------|------------|-----------------|
| Task not found | 404 | `NOT_FOUND` | "Task with id '...' not found" |
| Invalid status transition (DONE → TODO) | 409 | `CONFLICT` | "Cannot transition task status from DONE to TODO" |
| Invalid status transition (DONE → IN_PROGRESS) | 409 | `CONFLICT` | "Cannot transition task status from DONE to IN_PROGRESS" |
| Invalid status transition (TODO → DONE) | 409 | `CONFLICT` | "Cannot transition task status from TODO to DONE" |
| Empty request body | 400 | `BAD_REQUEST` | "Request body must contain at least one updatable field" |
| `title` exceeds 200 chars | 400 | `BAD_REQUEST` | "Title must not exceed 200 characters" |
| Malformed JSON | 400 | `BAD_REQUEST` | "Request body is not valid JSON" |
| Unexpected server error | 500 | `INTERNAL_ERROR` | "An unexpected error occurred" |

#### Lambda Handler

```java
com.example.tasks.handler.UpdateTaskHandler
```

**DynamoDB Operation:** `GetItem` (verify existence) + `UpdateItem` (with condition expression)  
**IAM Permissions:** `dynamodb:GetItem`, `dynamodb:UpdateItem` on `TasksTable`

---

### 5.5 Delete Task

Deletes a task permanently.

```
DELETE /tasks/{id}
```

#### Request

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | string (UUID) | Yes | The unique identifier of the task |

**Example Request:**

```bash
curl -X DELETE https://{api-id}.execute-api.{region}.amazonaws.com/prod/tasks/550e8400-e29b-41d4-a716-446655440000
```

#### Response

**Status:** `204 No Content`

**Body:** (empty)

> **Note:** This is the only endpoint that does not return a JSON body.

#### Error Cases

| Scenario | Status | Error Code | Example Message |
|----------|--------|------------|-----------------|
| Task not found | 404 | `NOT_FOUND` | "Task with id '550e8400-e29b-41d4-a716-446655440000' not found" |
| Unexpected server error | 500 | `INTERNAL_ERROR` | "An unexpected error occurred" |

#### Idempotency

Calling DELETE on an already-deleted task returns `404 Not Found`. This is the standard behavior — DELETE is idempotent in the sense that the side effect (task not existing) is the same, but the response differs.

#### Lambda Handler

```java
com.example.tasks.handler.DeleteTaskHandler
```

**DynamoDB Operation:** `GetItem` (verify existence) + `DeleteItem`  
**IAM Permissions:** `dynamodb:GetItem`, `dynamodb:DeleteItem` on `TasksTable`

---

## 6. Data Types

### Task

The primary resource in the API.

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "title": "Learn AWS Lambda",
  "description": "Build my first serverless application",
  "status": "TODO",
  "createdAt": "2026-09-10T10:30:00Z",
  "updatedAt": "2026-09-10T10:30:00Z"
}
```

| Field | Type | Read/Write | Description |
|-------|------|------------|-------------|
| `id` | string (UUID) | Read-only | Auto-generated on creation. Used as the partition key in DynamoDB. |
| `title` | string | Read/Write | Required on creation. Max 200 characters. |
| `description` | string \| null | Read/Write | Optional on creation. Max 2000 characters. Can be updated or cleared. |
| `status` | string (enum) | Read/Write | Auto-set to `TODO` on creation. Must follow state machine on update. |
| `createdAt` | string (ISO 8601) | Read-only | Set on creation. Never changes. |
| `updatedAt` | string (ISO 8601) | Read-only | Set on creation. Updated on every PUT request. |

### TaskStatus Enum

| Value | Description | Transition From |
|-------|-------------|----------------|
| `TODO` | Initial state, task created but not started | (initial) |
| `IN_PROGRESS` | Task is being actively worked on | `TODO` |
| `DONE` | Task is completed (terminal state) | `IN_PROGRESS` |

### CreateTaskRequest

```json
{
  "title": "string (1-200 chars, required)",
  "description": "string (0-2000 chars, optional)"
}
```

### UpdateTaskRequest

```json
{
  "title": "string (1-200 chars, optional)",
  "description": "string | null (0-2000 chars, optional)",
  "status": "string (TODO | IN_PROGRESS | DONE, optional)"
}
```

> At least one field must be provided in the update request.

### ErrorResponse

```json
{
  "error": "string (error code)",
  "message": "string (human-readable description)",
  "statusCode": 400 | 404 | 409 | 500
}
```

---

## 7. Status Codes

### Summary

| Status Code | Meaning | When |
|-------------|---------|------|
| `200 OK` | Success | GET, PUT operations completed successfully |
| `201 Created` | Resource created | POST operation created a new task |
| `204 No Content` | Success (no body) | DELETE operation completed successfully |
| `400 Bad Request` | Client error | Invalid input, validation failure |
| `404 Not Found` | Resource not found | Task ID does not exist |
| `409 Conflict` | State conflict | Invalid status transition |
| `500 Internal Server Error` | Server error | Unexpected failure |

### Status Code Decision Flow

```
Client Request
       │
       ▼
  Valid Input? ──── No ────▶ 400 Bad Request
       │
       Yes
       │
       ▼
  Resource Exists? ──── No ────▶ 404 Not Found
       │
       Yes
       │
       ▼
  State Valid? ──── No ────▶ 409 Conflict
  (for status transitions)
       │
       Yes
       │
       ▼
  Success: 200 / 201 / 204
```

---

## 8. CORS

### Preflight Requests

All endpoints support CORS preflight (OPTIONS) requests:

```
OPTIONS /tasks
OPTIONS /tasks/{id}
```

**Response Headers:**

```
Access-Control-Allow-Origin: *
Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS
Access-Control-Allow-Headers: Content-Type, Authorization
Access-Control-Max-Age: 86400
```

### CORS on All Responses

Every response (success and error) includes:

```
Access-Control-Allow-Origin: *
Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS
Access-Control-Allow-Headers: Content-Type, Authorization
```

> **Note:** In Phase 4, `Access-Control-Allow-Origin` will be restricted to specific domains when Cognito authentication is added.

---

## Appendix: Complete cURL Examples

### Create a Task

```bash
curl -X POST https://abc123.execute-api.eu-west-1.amazonaws.com/prod/tasks \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Learn AWS Lambda",
    "description": "Build my first serverless application"
  }'
```

### List All Tasks

```bash
curl https://abc123.execute-api.eu-west-1.amazonaws.com/prod/tasks
```

### List Tasks by Status

```bash
curl https://abc123.execute-api.eu-west-1.amazonaws.com/prod/tasks?status=TODO
```

### Get a Task

```bash
curl https://abc123.execute-api.eu-west-1.amazonaws.com/prod/tasks/550e8400-e29b-41d4-a716-446655440000
```

### Update a Task

```bash
curl -X PUT https://abc123.execute-api.eu-west-1.amazonaws.com/prod/tasks/550e8400-e29b-41d4-a716-446655440000 \
  -H "Content-Type: application/json" \
  -d '{
    "status": "IN_PROGRESS"
  }'
```

### Delete a Task

```bash
curl -X DELETE https://abc123.execute-api.eu-west-1.amazonaws.com/prod/tasks/550e8400-e29b-41d4-a716-446655440000
```

### Error Response Example (404)

```bash
curl https://abc123.execute-api.eu-west-1.amazonaws.com/prod/tasks/nonexistent-id
```

Response:

```json
{
  "error": "NOT_FOUND",
  "message": "Task with id 'nonexistent-id' not found",
  "statusCode": 404
}
```

### Error Response Example (400)

```bash
curl -X POST https://abc123.execute-api.eu-west-1.amazonaws.com/prod/tasks \
  -H "Content-Type: application/json" \
  -d '{"title": ""}'
```

Response:

```json
{
  "error": "BAD_REQUEST",
  "message": "Title is required and must not be blank",
  "statusCode": 400
}
```

### Error Response Example (409 — Invalid Transition)

```bash
curl -X PUT https://abc123.execute-api.eu-west-1.amazonaws.com/prod/tasks/550e8400-e29b-41d4-a716-446655440000 \
  -H "Content-Type: application/json" \
  -d '{"status": "TODO"}'
```

(When task is already `DONE`)

Response:

```json
{
  "error": "CONFLICT",
  "message": "Cannot transition task status from DONE to TODO",
  "statusCode": 409
}