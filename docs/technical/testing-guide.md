# Testing Guide — Task Management API

> **Version:** 1.0
> **Last Updated:** 2026-09-17
> **Audience:** Developers, QA
>
> Step-by-step guide to running and testing the application locally, using
> Docker (DynamoDB Local) and Maven — **no AWS account, no SAM CLI required**.

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Quick Reference](#2-quick-reference)
3. [Level 1 — Build & Unit Tests (no Docker)](#3-level-1--build--unit-tests-no-docker)
4. [Level 2 — Start the Database (DynamoDB Local)](#4-level-2--start-the-database-dynamodb-local)
5. [Level 3 — Integration Test (real handlers + real database)](#5-level-3--integration-test-real-handlers--real-database)
6. [What the Integration Test Covers](#6-what-the-integration-test-covers)
7. [Troubleshooting](#7-troubleshooting)
8. [Cleanup](#8-cleanup)

---

## 1. Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| Java JDK | 21 | Compile & run (enforced by the build) |
| Apache Maven | 3.6+ | Build & test |
| Docker | 20+ | DynamoDB Local (the database) |

> **Note:** The build enforces Java 21 via the Maven Enforcer plugin. If you
> build with Java 8, the build **fails on purpose** with a clear message.

Verify your tools (open a **fresh** terminal):

```powershell
java -version   # -> openjdk 21.x
mvn -version    # -> Java version: 21.x
docker --version
```

---

## 2. Quick Reference

```powershell
# ─── Build + unit tests (no Docker) ───────────────
mvn clean test

# ─── Database lifecycle (Docker) ──────────────────
.\scripts\dynamodb.ps1 start     # start DynamoDB Local
.\scripts\dynamodb.ps1 status    # check if running
.\scripts\dynamodb.ps1 stop      # stop it
.\scripts\dynamodb.ps1 restart   # restart it

# (CMD / .bat equivalent)
run-dynamodb.bat start|stop|status|restart

# ─── Integration test (real DB) ───────────────────
mvn test -Dtest=TaskManagementIT
```

---

## 3. Level 1 — Build & Unit Tests (no Docker)

This runs the unit tests that mock the repository — no database, no Docker.
It's the fastest sanity check.

```powershell
mvn clean test
```

**Expected result:**

```
[INFO] maven-enforcer-plugin:3.4.1:enforce ... RequireJavaVersion passed
[INFO] Tests run: 40, Failures: 0, Errors: 0
```

> `40` includes the 2 integration tests, which **auto-skip** if DynamoDB Local
> is not running (see Level 3). To run *only* the unit tests:

```powershell
mvn test -Dtest='*Test'
```

---

## 4. Level 2 — Start the Database (DynamoDB Local)

The application stores tasks in DynamoDB. Locally, we emulate it with
**DynamoDB Local** running in Docker on port 8000.

**Step 1 — Start it:**

```powershell
.\scripts\dynamodb.ps1 start
```

or (CMD / double-click):

```cmd
run-dynamodb.bat start
```

**Step 2 — Confirm it's running:**

```powershell
.\scripts\dynamodb.ps1 status
```

**Expected output:**

```
Status: RUNNING  (container 'taskmgmt-dynamodb', port 8000)
```

The script reports one of three states:
- `RUNNING` — ready to use.
- `STOPPED` — container exists but is stopped.
- `MISSING` — not created yet (start will create it).

---

## 5. Level 3 — Integration Test (real handlers + real database)

This is the **most important** test: it runs the *real* Lambda handlers
(`CreateTaskHandler`, `GetTaskHandler`, etc.) against the *real* DynamoDB,
proving the application logic works end-to-end — no mocking.

**Prerequisite:** DynamoDB Local is running (Level 2).

```powershell
mvn test -Dtest=TaskManagementIT
```

**Expected result:**

```
Tests run: 2, Failures: 0, Errors: 0
```

The test does the heavy lifting for you:
1. Connects to DynamoDB Local at `http://localhost:8000`.
2. Creates the `TasksTable` + `StatusIndex` (GSI).
3. Waits until the table and index are `ACTIVE`.
4. Runs the full CRUD flow.
5. Deletes the table afterward (cleanup).

> If DynamoDB Local is **not** running, the test is **skipped** (not failed),
> with the message: `DynamoDB Local is not running on http://localhost:8000`.

---

## 6. What the Integration Test Covers

The test (`src/test/java/com/example/tasks/integration/TaskManagementIT.java`)
exercises the complete application flow against real DynamoDB:

| # | Operation | Expected |
|---|-----------|----------|
| 1 | Create task (`POST /tasks`) | `201`, auto UUID, status `TODO` |
| 2 | Get task (`GET /tasks/{id}`) | `200`, correct title |
| 3 | List all (`GET /tasks`) | `200`, contains the task |
| 4 | Update status `TODO → IN_PROGRESS` | `200` (valid transition) |
| 5 | List by status (`GET /tasks?status=IN_PROGRESS`) | `200`, uses `StatusIndex` GSI |
| 6 | Delete (`DELETE /tasks/{id}`) | `204` |
| 7 | Get after delete | `404` |
| 8 | Invalid transition `DONE → TODO` | `409 CONFLICT` |

---

## 7. Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| Build fails: "invalid target release: 21" | Maven using Java 8 | Set `JAVA_HOME` to JDK 21 and open a fresh terminal |
| Enforcer fails: "RequireJavaVersion" | Wrong JDK | Use Java 21 (see prerequisites) |
| IT skipped: "DynamoDB Local is not running" | Docker not running / container stopped | `.\scripts\dynamodb.ps1 start` |
| `docker: error during connect` | Docker Desktop not started | Start Docker Desktop |
| Port 8000 already in use | Another process on 8000 | Stop it, or change port in `docker-compose.yml` |
| Test fails with 500 on GSI query | `StatusIndex` annotations missing | Ensure `Task` has `@DynamoDbSecondaryPartitionKey` / `@DynamoDbSecondarySortKey` |

---

## 8. Cleanup

```powershell
# Stop & remove the DynamoDB Local container
.\scripts\dynamodb.ps1 stop

# Remove build artifacts
mvn clean
```

---

## Appendix: Files Involved

| File | Purpose |
|------|---------|
| `docker-compose.yml` | Defines the DynamoDB Local service |
| `scripts/dynamodb.ps1` | PowerShell start/stop/status/restart |
| `run-dynamodb.bat` | CMD equivalent of the script |
| `src/test/java/.../integration/TaskManagementIT.java` | End-to-end integration test |
| `pom.xml` | Enforces Java 21 via Maven Enforcer |

