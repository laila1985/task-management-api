# Functional Specification — Task Management API

> **Version:** 1.0  
> **Last Updated:** 2026-09-10  
> **Status:** Phase 1–2 complete, Phase 3+ planned

---

## 1. Overview

### 1.1 Purpose

The Task Management API is a serverless REST API that allows users to create, read, update, and delete tasks. It is designed as a portfolio project to demonstrate AWS serverless architecture, Infrastructure as Code, and event-driven design patterns — skills expected of a Senior Backend Engineer.

### 1.2 Problem Statement

Teams need a simple, scalable task tracker without managing servers. Traditional solutions require provisioning EC2 instances, managing databases, and handling scaling — all operational overhead that distracts from business logic.

### 1.3 Solution

A fully serverless architecture:

- **No servers to manage** — AWS Lambda executes code on demand
- **Auto-scaling** — DynamoDB and Lambda scale automatically
- **Pay-per-use** — Only pay for actual API calls, not idle time
- **Event-driven** — Task changes trigger notifications via EventBridge

### 1.4 Target Users

| User                  | Description                                           |
|-----------------------|-------------------------------------------------------|
| End User              | Creates and manages tasks via the REST API            |
| Developer             | Integrates the API into a front-end application       |
| Admin                 | Monitors system health, reviews CloudWatch logs       |
| Notification Consumer | Receives event-driven notifications when tasks change |

---

## 2. Actors & Use Cases

### 2.1 Primary Actor: End User

```
┌──────────────────────────────────────────────────┐
│                  End User                        │
├──────────────────────────────────────────────────┤
│  UC-1: Create a new task                         │
│  UC-2: View all tasks (optionally filtered)      │
│  UC-3: View a specific task by ID                │
│  UC-4: Update a task (change title, status, etc.)│
│  UC-5: Delete a task                             │
└──────────────────────────────────────────────────┘
```

### 2.2 Secondary Actor: Notification Consumer

```
┌───────────────────────────────────────────────────┐
│            Notification Consumer                  │
├───────────────────────────────────────────────────┤
│  UC-6: Receive notification when a task is created│
│  UC-7: Receive notification when a task changes   │
└───────────────────────────────────────────────────┘
```

### 2.3 Use Case Diagram

```
    End User                    System                     Notification Consumer
       │                         │                              │
       │── UC-1: Create Task ──▶ │                              │
       │                         │── Publish event ────────────▶│
       │                         │                              │
       │── UC-2: List Tasks ───▶ │                              │
       │◀── Task list ─────────  │                              │
       │                         │                              │
       │── UC-3: Get Task ─────▶ │                              │
       │◀── Task details ──────  │                              │
       │                         │                              │
       │── UC-4: Update Task ──▶ │                              │
       │                         │── Publish event ────────────▶│
       │                         │                              │
       │── UC-5: Delete Task ──▶ │                              │
       │                         │── Publish event ────────────▶│
```

---

## 3. Functional Requirements

### FR-1: Create Task

| Attribute | Value |
|-----------|-------|
| **ID** | FR-1 |
| **Name** | Create a new task |
| **Actor** | End User |
| **Description** | The user submits a task with a title and optional description. The system generates a unique ID, sets the status to `TODO`, records creation timestamp, and persists the task. |
| **Preconditions** | None |
| **Postconditions** | Task is stored in DynamoDB; the API returns the created task with HTTP 201. |
| **Business Rules** | BR-1, BR-2 |

### FR-2: List Tasks

| Attribute | Value |
|-----------|-------|
| **ID** | FR-2 |
| **Name** | List all tasks |
| **Actor** | End User |
| **Description** | The user retrieves a list of all tasks. Optionally, tasks can be filtered by status. |
| **Preconditions** | None |
| **Postconditions** | The API returns an array of tasks with HTTP 200. Returns empty array if no tasks exist. |
| **Business Rules** | BR-3 |

### FR-3: Get Task by ID

| Attribute | Value |
|-----------|-------|
| **ID** | FR-3 |
| **Name** | Get a specific task |
| **Actor** | End User |
| **Description** | The user retrieves a single task by its unique identifier. |
| **Preconditions** | A task with the given ID must exist. |
| **Postconditions** | The API returns the task details with HTTP 200. |
| **Business Rules** | BR-4 |

### FR-4: Update Task

| Attribute | Value |
|-----------|-------|
| **ID** | FR-4 |
| **Name** | Update an existing task |
| **Actor** | End User |
| **Description** | The user modifies one or more attributes of an existing task (title, description, status). The system updates the task and records the modification timestamp. |
| **Preconditions** | A task with the given ID must exist. |
| **Postconditions** | Task is updated in DynamoDB; the API returns the updated task with HTTP 200. An event is published to EventBridge. |
| **Business Rules** | BR-1, BR-5 |

### FR-5: Delete Task

| Attribute | Value |
|-----------|-------|
| **ID** | FR-5 |
| **Name** | Delete a task |
| **Actor** | End User |
| **Description** | The user removes a task permanently. |
| **Preconditions** | A task with the given ID must exist. |
| **Postconditions** | Task is removed from DynamoDB; the API returns HTTP 204. An event is published to EventBridge. |
| **Business Rules** | BR-4 |

### FR-6: Receive Task Notifications (Phase 3)

| Attribute | Value |
|-----------|-------|
| **ID** | FR-6 |
| **Name** | Receive task change notifications |
| **Actor** | Notification Consumer |
| **Description** | When a task is created, updated, or deleted, the system publishes an event to EventBridge. A notification Lambda consumes these events and logs/processes them. |
| **Preconditions** | A task change operation occurred. |
| **Postconditions** | Event is published and consumed; notification is logged. |
| **Business Rules** | BR-6 |

---

## 4. Business Rules

| ID | Rule | Description |
|----|------|-------------|
| BR-1 | Title required | Task title must not be null or blank. Maximum 200 characters. |
| BR-2 | Auto-generated ID | The system generates a UUID for each task. The client must not provide an ID on creation. |
| BR-3 | Default list order | Tasks are returned ordered by creation date (newest first). |
| BR-4 | Task must exist | Operations on a non-existent task ID return HTTP 404 with an error message. |
| BR-5 | Status lifecycle | Valid statuses: `TODO`, `IN_PROGRESS`, `DONE`. Transitions must follow: `TODO → IN_PROGRESS → DONE`. A `DONE` task cannot be moved back. |
| BR-6 | At-least-once delivery | Events are delivered at least once. Consumers must be idempotent. |

### Status State Machine

```
    ┌──────┐
    │ TODO │
    └──┬───┘
       │
       ▼
┌──────────────┐
│  IN_PROGRESS │
└──────┬───────┘
       │
       ▼
   ┌──────┐
   │ DONE │  (terminal state)
   └──────┘
```

---

## 5. Data Model (Conceptual)

### 5.1 Task Entity

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `id` | String (UUID) | Auto-generated | Unique identifier |
| `title` | String | Yes | Task title, max 200 characters |
| `description` | String | No | Detailed description, max 2000 characters |
| `status` | Enum | Auto-set | One of: `TODO`, `IN_PROGRESS`, `DONE` |
| `createdAt` | ISO 8601 datetime | Auto-generated | Timestamp of creation |
| `updatedAt` | ISO 8601 datetime | Auto-generated | Timestamp of last update |

### 5.2 Status Enum Values

| Value | Description |
|-------|-------------|
| `TODO` | Task has been created but not yet started |
| `IN_PROGRESS` | Task is actively being worked on |
| `DONE` | Task has been completed |

### 5.3 Entity Relationships

```
Task (1) ──────────► (0..N) TaskEvent
  │                         │
  │ id                      │ taskId (same as Task.id)
  │                         │ eventType: CREATED | UPDATED | DELETED
  │                         │ timestamp
  │                         │ payload (diff or full snapshot)
```

> **Note:** In Phase 1–2, events are not persisted. They flow through DynamoDB Streams → EventBridge → Notification Lambda without storage.

---

## 6. API Contract (Functional)

### 6.1 Create Task

```
POST /tasks

Request:
{
  "title": "Learn AWS Lambda",
  "description": "Build my first serverless application"
}

Response (201 Created):
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "title": "Learn AWS Lambda",
  "description": "Build my first serverless application",
  "status": "TODO",
  "createdAt": "2026-09-10T10:30:00Z",
  "updatedAt": "2026-09-10T10:30:00Z"
}
```

### 6.2 List Tasks

```
GET /tasks?status=TODO

Response (200 OK):
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "title": "Learn AWS Lambda",
    "description": "Build my first serverless application",
    "status": "TODO",
    "createdAt": "2026-09-10T10:30:00Z",
    "updatedAt": "2026-09-10T10:30:00Z"
  }
]
```

### 6.3 Get Task

```
GET /tasks/550e8400-e29b-41d4-a716-446655440000

Response (200 OK):
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "title": "Learn AWS Lambda",
  "description": "Build my first serverless application",
  "status": "TODO",
  "createdAt": "2026-09-10T10:30:00Z",
  "updatedAt": "2026-09-10T10:30:00Z"
}
```

### 6.4 Update Task

```
PUT /tasks/550e8400-e29b-41d4-a716-446655440000

Request:
{
  "title": "Learn AWS Lambda - Updated",
  "status": "IN_PROGRESS"
}

Response (200 OK):
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "title": "Learn AWS Lambda - Updated",
  "description": "Build my first serverless application",
  "status": "IN_PROGRESS",
  "createdAt": "2026-09-10T10:30:00Z",
  "updatedAt": "2026-09-10T11:00:00Z"
}
```

### 6.5 Delete Task

```
DELETE /tasks/550e8400-e29b-41d4-a716-446655440000

Response (204 No Content)
(empty body)
```

---

## 7. Error Handling

### 7.1 Error Response Format

All errors follow a consistent JSON structure:

```json
{
  "error": "NOT_FOUND",
  "message": "Task with id '550e8400-e29b-41d4-a716-446655440000' not found",
  "statusCode": 404
}
```

### 7.2 Error Scenarios

| Scenario | HTTP Status | Error Code | Description |
|----------|-------------|------------|-------------|
| Missing required field | 400 | `BAD_REQUEST` | Title is null or blank |
| Invalid status value | 400 | `BAD_REQUEST` | Status not in enum |
| Invalid status transition | 409 | `CONFLICT` | e.g., moving from `DONE` back to `TODO` |
| Task not found | 404 | `NOT_FOUND` | No task with given ID |
| Malformed JSON body | 400 | `BAD_REQUEST` | Request body is not valid JSON |
| Internal server error | 500 | `INTERNAL_ERROR` | Unexpected failure |

### 7.3 Idempotency Considerations

| Operation | Idempotent? | Notes |
|-----------|-------------|-------|
| POST /tasks | No | Each call creates a new task with a new UUID |
| GET /tasks | Yes | Read-only, no side effects |
| GET /tasks/{id} | Yes | Read-only, no side effects |
| PUT /tasks/{id} | Yes | Same update applied multiple times = same result |
| DELETE /tasks/{id} | Yes | Deleting an already-deleted task returns 404 (acceptable) |

---

## 8. Non-Functional Requirements

### 8.1 Performance

| Metric | Target | Notes |
|--------|--------|-------|
| API response time (p50) | < 200ms | Warm Lambda |
| API response time (p99) | < 2s | Includes cold start |
| Cold start time | < 3s | Java 21 runtime, shaded JAR |

### 8.2 Availability & Scalability

| Metric | Target | Notes |
|--------|--------|-------|
| Availability | 99.9% | Managed by AWS (API Gateway + Lambda + DynamoDB) |
| Concurrent users | Unlimited | Lambda auto-scales; DynamoDB on-demand mode |
| Max tasks | Unlimited | DynamoDB scales horizontally |

### 8.3 Security (Phase 4)

- HTTPS enforced by API Gateway
- IAM least-privilege for each Lambda function
- Input validation on all endpoints
- CORS configured for web browser access
- Future: Cognito user pool authentication

---

## 9. Acceptance Criteria

### AC-1: Create Task

- [ ] Given a valid JSON body with `title`, when POST /tasks is called, then a new task is created with status `TODO`, a UUID, and timestamps
- [ ] Given a body without `title`, when POST /tasks is called, then a 400 error is returned
- [ ] Given a body with `title` exceeding 200 characters, when POST /tasks is called, then a 400 error is returned

### AC-2: List Tasks

- [ ] Given existing tasks, when GET /tasks is called, then all tasks are returned as an array
- [ ] Given no tasks, when GET /tasks is called, then an empty array is returned with 200
- [ ] Given a `status` query parameter, when GET /tasks?status=TODO is called, then only tasks with that status are returned

### AC-3: Get Task

- [ ] Given a valid task ID, when GET /tasks/{id} is called, then the task is returned with 200
- [ ] Given a non-existent task ID, when GET /tasks/{id} is called, then a 404 error is returned

### AC-4: Update Task

- [ ] Given a valid task ID and update body, when PUT /tasks/{id} is called, then the task is updated and returned with 200
- [ ] Given an invalid status transition (e.g., DONE → TODO), when PUT /tasks/{id} is called, then a 409 error is returned
- [ ] Given a non-existent task ID, when PUT /tasks/{id} is called, then a 404 error is returned

### AC-5: Delete Task

- [ ] Given a valid task ID, when DELETE /tasks/{id} is called, then 204 is returned
- [ ] Given a non-existent task ID, when DELETE /tasks/{id} is called, then a 404 error is returned

### AC-6: Event Notifications (Phase 3)

- [ ] Given a task is created, when the Lambda completes, then an event is published to EventBridge
- [ ] Given a task is updated, when the Lambda completes, then an update event is published
- [ ] Given a task is deleted, when the Lambda completes, then a delete event is published

---

## 10. Out of Scope (Phase 1–2)

The following are intentionally excluded from the initial phases:

- User authentication and authorization (Phase 4)
- Multi-tenancy / user-scoped tasks
- Pagination for list endpoints
- Task attachments or file uploads
- Real-time notifications (WebSocket)
- Rate limiting
- Caching layer
- Audit log persistence