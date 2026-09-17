
# Task Management API

> A **serverless** Task Management (CRUD) API built with **Java 21**, **AWS Lambda**, **API Gateway**, **DynamoDB**, and **AWS SAM**.
>
> This README is written for **beginners** — it explains *what* the application is, *how* it works, and walks you through running and testing it step by step.

---

## Table of Contents

1. [What is this application?](#what-is-this-application)
2. [The big idea: there is no "server to start"](#the-big-idea-there-is-no-server-to-start)
3. [What the application does](#what-the-application-does)
4. [Architecture at a glance](#architecture-at-a-glance)
5. [The code structure](#the-code-structure)
6. [How a request flows through the code](#how-a-request-flows-through-the-code)
7. [What template.yaml does (the wiring)](#what-templateyaml-does-the-wiring)
8. [Prerequisites](#prerequisites)
9. [Getting started](#getting-started)
10. [Testing the application (step by step)](#testing-the-application-step-by-step)
11. [Three ways to run this app](#three-ways-to-run-this-app)
12. [Deploying to AWS](#deploying-to-aws)
13. [Understanding DynamoDB (partition key, GSI)](#understanding-dynamodb-partition-key-gsi)
14. [Troubleshooting](#troubleshooting)
15. [Documentation](#documentation)

---

## What is this application?

It's a **Task Management API** — a backend service (like a mini "Todo list" backend) that lets clients create, read, update, and delete "tasks". Each task looks like this:

```json
{
  "id": "auto-generated-uuid",
  "title": "Learn AWS Lambda",
  "description": "Build my first serverless application",
  "status": "TODO",
  "createdAt": "2026-09-17T10:00:00Z",
  "updatedAt": "2026-09-17T10:00:00Z"
}
```

The API exposes five operations:

| HTTP call | What it does |
|-----------|--------------|
| `POST /tasks` | Create a new task |
| `GET /tasks` | List all tasks (optionally filtered by `?status=`) |
| `GET /tasks/{id}` | Get a single task |
| `PUT /tasks/{id}` | Update a task |
| `DELETE /tasks/{id}` | Delete a task |

---

## The big idea: there is no "server to start"

If you've used Spring Boot or similar frameworks before, this is the most important thing to understand: **this application has no `main()` method and no always-running server.**

- A **traditional app** runs continuously on a server and handles all requests.
- A **serverless app** is a collection of *small, independent functions* that AWS runs **only when someone calls them**. You never start a server — AWS starts and stops your code on demand.

```
Traditional app:  [Server always running]  ← handles every route
This app:         [5 separate functions]   ← each wakes up only for its own route
```

This is why you can't simply "run" the application like a normal program. Instead you either **test the functions locally** (against a database in Docker) or **deploy them to AWS** (where API Gateway, Lambda, and DynamoDB exist for real).

---

## What the application does

### Business rules

1. **Create** — a new task is assigned a random UUID, starts with `status = "TODO"`, and gets `createdAt`/`updatedAt` timestamps.
2. **Status state machine** — a task's status moves one way only:

   ```
   TODO  →  IN_PROGRESS  →  DONE
   ```

   You cannot skip a step or go backwards (e.g., `DONE → TODO` is rejected with `409 Conflict`).

3. **Partial updates** — `PUT` only changes the fields you provide; others stay untouched.

### Error codes

| Code | Meaning |
|------|---------|
| `400` | Bad request (missing/invalid input) |
| `404` | Task not found |
| `409` | Invalid status transition |
| `500` | Unexpected error |

---

## Architecture at a glance

The application spans three AWS services, coordinated by a single `template.yaml` file:

```
Client (curl / browser)
   │  HTTPS
   ▼
API Gateway        ← the "front door" that receives HTTP requests
   │
   ▼
Lambda functions   ← your Java code, one function per endpoint
   │
   ▼
DynamoDB           ← the database (a NoSQL table called "TasksTable")
```

- **API Gateway** — receives HTTP requests and routes them to the right Lambda.
- **Lambda** — runs your Java handler code on demand.
- **DynamoDB** — stores the tasks (a fast, serverless NoSQL database).

You describe all of this in **`template.yaml`** (see [What template.yaml does](#what-templateyaml-does-the-wiring)).

---

## The code structure

```
src/main/java/com/example/tasks/
├── handler/          ← "controllers" (like Spring @Controller)
│   ├── CreateTaskHandler.java    handles POST /tasks
│   ├── GetTaskHandler.java       handles GET /tasks/{id}
│   ├── ListTasksHandler.java     handles GET /tasks
│   ├── UpdateTaskHandler.java    handles PUT /tasks/{id}
│   └── DeleteTaskHandler.java    handles DELETE /tasks/{id}
│
├── model/            ← "entities" / DTOs (like Spring @Entity)
│   ├── Task.java                 the task stored in the database
│   ├── CreateTaskRequest.java    the body of POST /tasks
│   └── UpdateTaskRequest.java    the body of PUT /tasks/{id}
│
├── repository/       ← "DAO" (like Spring @Repository)
│   └── TaskRepository.java       all DynamoDB reads/writes
│
└── util/
    └── ApiGatewayResponse.java   builds HTTP responses
```

**Translation table** (if you know Spring):

| This project | Spring equivalent |
|--------------|-------------------|
| `handler/` | `@Controller` / `@RestController` |
| `model/` | `@Entity` / DTOs |
| `repository/` | `@Repository` |
| `util/ApiGatewayResponse` | a response helper |

---

## How a request flows through the code

Let's trace **`POST /tasks`** (create a task), the clearest example.

1. **Client** sends: `POST /tasks` with body `{"title": "Learn AWS Lambda"}`.
2. **API Gateway** receives it and hands it to `CreateTaskHandler` as an `APIGatewayProxyRequestEvent` (a Java object holding the HTTP request).
3. **`CreateTaskHandler.handleRequest()`** runs (this is the "main method" of the function). It:
   1. Checks the body isn't empty → else `400`.
   2. Parses JSON into a `CreateTaskRequest` → else `400`.
   3. Validates the title (required, ≤ 200 chars) → else `400`.
   4. Creates a `Task`: generates a UUID, sets `status = "TODO"`, sets timestamps.
   5. Calls `taskRepository.save(task)` → writes to DynamoDB.
   6. Returns `201 Created` with the task as JSON.
4. **`TaskRepository.save()`** uses the DynamoDB Enhanced Client to write the row into `TasksTable`.
5. **API Gateway** sends the `201` response back to the client.

Every handler follows the same shape:

```
PUT /tasks/{id}  {"status": "IN_PROGRESS"}
        │
        ▼
1. Extract id from URL               → missing? 400
2. Parse & validate body             → invalid? 400
3. Fetch existing task from DB       → not found? 404
4. Check status transition rules     → illegal? 409
5. Apply changes + updatedAt
6. Save to DB, return 200 + JSON
        │
        ▼
   (any unexpected error → 500)
```

---

## What template.yaml does (the wiring)

`template.yaml` is a **SAM template** — a recipe that says: *"when deployed, create these AWS resources and connect them together."* It has three parts: **Globals → Resources → Outputs**.

### Globals — defaults for every Lambda

```yaml
Globals:
  Function:
    Runtime: java21
    MemorySize: 512
    Timeout: 30
    Environment:
      Variables:
        TABLE_NAME: !Ref TasksTable
```

The key line is `TABLE_NAME: !Ref TasksTable`. It injects the DynamoDB table's name as an environment variable into every function. Your `TaskRepository` reads exactly that:

```java
String tableName = System.getenv("TABLE_NAME");   // ← injected by template.yaml
```

### Resources — the actual AWS things

**1. The DynamoDB table** (`TasksTable`) — primary key on `id`, plus a `StatusIndex` GSI for querying by status.

**2. One Lambda per endpoint.** Here's `UpdateTaskFunction`:

```yaml
UpdateTaskFunction:
  Type: AWS::Serverless::Function
  Properties:
    CodeUri: .                                          # code is in this folder
    Handler: com.example.tasks.handler.UpdateTaskHandler::handleRequest   # THE WIRING
    Policies:
      - DynamoDBCrudPolicy:
          TableName: !Ref TasksTable                     # grant DB permission
    Events:
      UpdateTask:
        Type: Api
        Properties:
          Path: /tasks/{id}                              # THE URL
          Method: put
          Cors: true
```

The **two connections** that matter:

| Line | What it connects |
|------|------------------|
| `Handler: ...UpdateTaskHandler::handleRequest` | Tells AWS *which* class/method to run |
| `Events → Api → Path: /tasks/{id}, Method: put` | Tells API Gateway *which URL* triggers it |

The `{id}` in the path becomes `input.getPathParameters().get("id")` in the Java code.

The full endpoint mapping:

| Function | Path | Method | Java handler |
|----------|------|--------|--------------|
| `CreateTaskFunction` | `/tasks` | `post` | `CreateTaskHandler` |
| `GetTaskFunction` | `/tasks/{id}` | `get` | `GetTaskHandler` |
| `ListTasksFunction` | `/tasks` | `get` | `ListTasksHandler` |
| `UpdateTaskFunction` | `/tasks/{id}` | `put` | `UpdateTaskHandler` |
| `DeleteTaskFunction` | `/tasks/{id}` | `delete` | `DeleteTaskHandler` |

> Notice `CreateTaskFunction` and `ListTasksFunction` share `/tasks` but differ by HTTP method (`post` vs `get`) — API Gateway uses the method to tell them apart.

### Outputs — what AWS prints back after deploy

```yaml
Outputs:
  TaskApi:
    Value: !Sub "https://${ServerlessRestApi}.execute-api.${AWS::Region}.amazonaws.com/prod/"
```

This is the URL you use to call your API after deployment.

---

## Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| Java JDK | 21 | Compile & run (enforced by the build) |
| Apache Maven | 3.6+ | Build & test |
| Docker | 20+ | DynamoDB Local (the database for local testing) |
| AWS CLI + SAM CLI | latest | *Only needed to deploy to AWS* (not for local testing) |

> **Java 21 is mandatory.** The build uses the Maven Enforcer plugin, which **fails the build on purpose** if you try to compile with Java 8.

Verify your tools (open a **fresh** terminal):

```powershell
java -version    # → openjdk 21.x
mvn -version     # → Java version: 21.x
docker --version
```

---

## Getting started

```powershell
# 1. Clone the repository
git clone <your-repo-url>
cd task-management-api

# 2. Build + run unit tests (no Docker needed)
mvn clean test
```

---

## Testing the application (step by step)

There are **three levels** of testing. They all use the *same* handler code — only "where it runs" changes.

### Level 1 — Unit tests (no Docker, no database)

```powershell
mvn clean test
```

Runs **38 unit tests** that mock the repository (no real database). Fastest sanity check.

Expected: `Tests run: 40, Failures: 0, Errors: 0` (the `40` includes 2 integration tests that auto-skip without Docker).

### Level 2 — Start the database (DynamoDB Local in Docker)

```powershell
.\scripts\dynamodb.ps1 start     # start
.\scripts\dynamodb.ps1 status    # check (RUNNING / STOPPED / MISSING)
.\scripts\dynamodb.ps1 stop      # stop
.\scripts\dynamodb.ps1 restart   # restart
```

Or the CMD equivalent: `run-dynamodb.bat start|stop|status|restart`.

### Level 3 — Integration test (real handlers + real database)

```powershell
mvn test -Dtest=TaskManagementIT
```

This runs the **real Lambda handlers against a real DynamoDB** (DynamoDB Local in Docker) — proving the full create → get → list → update → list-by-status → delete flow works end-to-end, with no mocking.

Expected: `Tests run: 2, Failures: 0, Errors: 0`.

For a detailed walkthrough, see [`docs/technical/testing-guide.md`](docs/technical/testing-guide.md).

---

## Three ways to run this app

| Path | Database | Routing | Code execution |
|------|----------|---------|----------------|
| **1. Unit tests** (`*Test`) | Mocked (fake) | None | Handler + mock repository |
| **2. Integration test** (`TaskManagementIT`) | DynamoDB Local (Docker) | Simulated via `APIGatewayProxyRequestEvent` | Real handlers + real repository |
| **3. Production** (`sam deploy`) | Real DynamoDB (AWS) | Real API Gateway | Real Lambdas (AWS) |

The integration test mirrors `template.yaml` locally: it programmatically creates the **same table and `StatusIndex` GSI**, then invokes the **same handlers** with request objects identical to what API Gateway would build. The only "trick" is redirecting the AWS SDK to Docker via `endpointOverride("http://localhost:8000")`.

---

## Deploying to AWS

This requires SAM CLI, AWS CLI, and an AWS account. It's a two-phase process:

```powershell
# Phase 1 — build the fat JAR
sam build

# Phase 2 — create the cloud resources
sam deploy --guided    # first time (interactive)
sam deploy             # subsequent times
```

What happens:
1. `sam build` runs `mvn clean package` and stages the fat JAR in `.aws-sam/build/`.
2. `sam deploy` uploads the JAR to S3 and hands the template to **CloudFormation**, which creates (in order): the DynamoDB table → the 5 Lambda functions → API Gateway → IAM policies.
3. AWS prints the `Outputs`, including the `TaskApi` URL.

Test it after deploy:

```powershell
curl -X POST https://abc123.execute-api.eu-west-1.amazonaws.com/prod/tasks \
  -H "Content-Type: application/json" \
  -d '{"title":"My first deployed task"}'
```

---

## Understanding DynamoDB (partition key, GSI)

This is the "why" behind a real bug that was found and fixed in this project.

### Partition key (primary key)

Every DynamoDB table has a **partition key** — a field that uniquely identifies a row. Here it's `id` (a UUID). You can fetch a task *instantly* by its `id`, but you **cannot** efficiently query by any other field (like `status`).

### Global Secondary Index (GSI)

To also query by `status` (for `GET /tasks?status=TODO`), the project defines a **Global Secondary Index** called `StatusIndex`, keyed on `status` (and sorted by `createdAt`).

### The three-way connection (and the bug)

A GSI query only works if **three things agree**:

1. **`template.yaml`** defines `StatusIndex` (status + createdAt).
2. **`Task.java`** declares the matching annotations:
   ```java
   @DynamoDbSecondaryPartitionKey(indexNames = "StatusIndex")  // on getStatus()
   @DynamoDbSecondarySortKey(indexNames = "StatusIndex")       // on getCreatedAt()
   ```
3. **`TaskRepository`** queries it: `taskTable.index("StatusIndex").query(...)`.

Originally, `Task.java` was **missing** those two annotations. The unit tests never caught it (they mock the repository), but the integration test — which creates the *real* index — surfaced this error:

```
Attempt to execute an operation that requires a secondary index without
defining the index attributes in the table metadata. Index name: StatusIndex
```

Adding the two annotations fixed it. This is a great example of why integration tests matter: they exercise the real database, not a fake one.

---

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| `invalid target release: 21` | Maven using Java 8 | Set `JAVA_HOME` to JDK 21, open a fresh terminal |
| Enforcer fails: `RequireJavaVersion` | Wrong JDK | Use Java 21 |
| IT skipped: "DynamoDB Local is not running" | Docker/container not running | `.\scripts\dynamodb.ps1 start` |
| `docker: error during connect` | Docker Desktop not started | Start Docker Desktop |
| Port 8000 in use | Another process on 8000 | Stop it or change port in `docker-compose.yml` |
| 500 on GSI query | Missing `@DynamoDbSecondary*Key` | Add the annotations to `Task.java` |

---

## Documentation

| Document | Audience | Description |
|----------|----------|-------------|
| [Functional Specification](docs/functional/functional-specification.md) | Product/QA | Business requirements & use cases |
| [Architecture](docs/technical/architecture.md) | Developers/SRE | System design & trade-offs |
| [API Reference](docs/technical/api-reference.md) | API consumers | Endpoint-by-endpoint spec |
| [Deployment Guide](docs/technical/deployment-guide.md) | DevOps | Build, deploy, troubleshoot |
| [Testing Guide](docs/technical/testing-guide.md) | Developers/QA | Local testing walkthrough |

---

## Tech stack

| Layer | Technology |
|-------|-----------|
| Runtime | Java 21 (AWS Lambda) |
| Build | Maven 3.6+ |
| AWS SDK | AWS SDK for Java v2 (DynamoDB Enhanced Client) |
| Database | Amazon DynamoDB |
| API | Amazon API Gateway (REST) |
| IaC | AWS SAM (`template.yaml`) |
| Testing | JUnit 5 + Mockito |





