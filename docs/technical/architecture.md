# Architecture — Task Management API

> **Version:** 1.0  
> **Last Updated:** 2026-09-10  
> **Audience:** Developers, SRE, Interviewers

---

## 1. System Overview

### 1.1 Architecture Diagram

```
                              ┌──────────────┐
                              │   Client     │
                              │  (curl/UI)   │
                              └──────┬───────┘
                                     │
                                     │ HTTPS
                                     ▼
                          ┌───────────────────────┐
                          │     API Gateway       │
                          │  (REST API, CORS)     │
                          └──────────┬────────────┘
                                     │
              ┌──────────┬───────────┼───────────┬──────────┐
              │          │           │           │          │
              ▼          ▼           ▼           ▼          ▼
        ┌──────────┐┌──────────┐┌──────────┐┌──────────┐┌──────────┐
        │ Create   ││ Get      ││ List     ││ Update   ││ Delete   │
        │ Task     ││ Task     ││ Tasks    ││ Task     ││ Task     │
        │ Lambda   ││ Lambda   ││ Lambda   ││ Lambda   ││ Lambda   │
        └────┬─────┘└────┬─────┘└────┬─────┘└────┬─────┘└────┬─────┘
             │           │           │           │           │
             │     ┌─────┴───────────┴───────────┴───────┐   │
             │     │           DynamoDB                  │   │
             │     │        (TasksTable)                 │   │
             │     │   PK: id  |  GSI: status            │   │
             │     └─────────────────┬───────────────────┘   │
             │                       │                       │
             │                       │ DynamoDB Stream       │
             │                       ▼                       │
             │               ┌────────────────┐              │
             │               │  EventBridge   │              │
             │               │  (Task Events) │              │
             │               └───────┬────────┘              │
             │                       │                       │
             │                       ▼                       │
             │               ┌────────────────┐              │
             │               │ Notification   │              │
             │               │ Lambda         │              │
             │               └────────────────┘              │
             │                                               │
             └───────────────────────────────────────────────┘
                  (Each Lambda has its own IAM execution role)
```

### 1.2 Request Flow (Create Task Example)

```
1. Client sends POST /tasks {"title": "Learn AWS"}
2. API Gateway receives the HTTPS request
3. API Gateway invokes CreateTaskHandler Lambda (proxy integration)
4. Lambda handler:
   a. Parses the request body (JSON → Java object)
   b. Validates input (title required, max 200 chars)
   c. Generates UUID, sets status=TODO, sets timestamps
   d. Calls DynamoDB Enhanced Client to persist the Task
   e. Constructs HTTP response (201 Created + JSON body)
5. API Gateway returns the response to the client
6. (Async) DynamoDB Stream captures the INSERT event
7. (Async) Stream publishes to EventBridge
8. (Async) Notification Lambda processes the event
```

---

## 2. Technology Stack

### 2.1 Runtime & Build

| Layer | Technology | Version | Rationale |
|-------|-----------|---------|-----------|
| Language | Java | 21 | LTS, Lambda runtime support |
| Build Tool | Apache Maven | 3.6+ | Dependency management, shade plugin |
| AWS SDK | AWS SDK for Java v2 | 2.x | Modern, non-blocking, enhanced DynamoDB client |
| JSON | Jackson | 2.x | Serialization/deserialization |
| Testing | JUnit 5 + Mockito | 5.x / 5.x | Standard Java testing |

### 2.2 AWS Services

| Service | Usage | Rationale |
|---------|-------|-----------|
| Lambda | Compute | Serverless, auto-scaling, pay-per-invocation |
| API Gateway | HTTP entry | REST API, CORS, Lambda proxy integration |
| DynamoDB | Database | NoSQL, single-digit ms latency, auto-scaling |
| EventBridge | Event bus | Decoupled async notifications (Phase 3) |
| CloudWatch | Observability | Logs, metrics, traces (Phase 5) |
| IAM | Security | Least-privilege execution roles |
| SAM | IaC | Infrastructure as Code, CloudFormation extension |

### 2.3 Dependency Graph

```
pom.xml
├── aws-lambda-java-core          (Lambda handler interface)
├── aws-lambda-java-events        (APIGatewayProxyRequestEvent, etc.)
├── aws-java-sdk-dynamodb         (DynamoDB Enhanced Client)
├── jackson-databind              (JSON serialization)
├── jackson-datatype-jsr310       (Java 8 date/time support)
├── junit-jupiter                 (Testing)
└── mockito-core                  (Mocking)
```

---

## 3. Component Design

### 3.1 Project Structure

```
task-management-api/
├── template.yaml                        # SAM IaC (all AWS resources)
├── pom.xml                              # Maven build configuration
├── src/
│   ├── main/java/com/example/tasks/
│   │   ├── model/
│   │   │   └── Task.java                # POJO: id, title, description, status, timestamps
│   │   ├── handler/
│   │   │   ├── CreateTaskHandler.java    # POST /tasks
│   │   │   ├── GetTaskHandler.java       # GET /tasks/{id}
│   │   │   ├── ListTasksHandler.java     # GET /tasks
│   │   │   ├── UpdateTaskHandler.java    # PUT /tasks/{id}
│   │   │   ├── DeleteTaskHandler.java    # DELETE /tasks/{id}
│   │   │   └── NotificationHandler.java # EventBridge consumer (Phase 3)
│   │   ├── repository/
│   │   │   └── TaskRepository.java      # DynamoDB Enhanced Client wrapper
│   │   └── util/
│   │       └── ApiGatewayResponse.java  # Response builder utility
│   └── test/java/com/example/tasks/
│       ├── handler/
│       │   ├── CreateTaskHandlerTest.java
│       │   ├── GetTaskHandlerTest.java
│       │   ├── ListTasksHandlerTest.java
│       │   ├── UpdateTaskHandlerTest.java
│       │   └── DeleteTaskHandlerTest.java
│       └── repository/
│           └── TaskRepositoryTest.java
├── events/                              # Sample Lambda test events
│   ├── create-task.json
│   ├── get-task.json
│   └── list-tasks.json
└── docs/                                # Documentation (this folder)
```

### 3.2 Lambda Handlers

Each handler implements `RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent>`.

#### CreateTaskHandler

```java
public class CreateTaskHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    
    private final TaskRepository taskRepository;
    
    public CreateTaskHandler() {
        this.taskRepository = new TaskRepository();  // DynamoDB client initialized here
    }
    
    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        // 1. Parse request body → CreateTaskRequest
        // 2. Validate: title required, max 200 chars
        // 3. Create Task with UUID, status=TODO, timestamps
        // 4. Save to DynamoDB via TaskRepository
        // 5. Return 201 Created with Task JSON
    }
}
```

#### Handler Responsibilities

| Handler | HTTP Method | Input | DynamoDB Operation | HTTP Response |
|---------|-------------|-------|--------------------|---------------|
| CreateTaskHandler | POST | JSON body | PutItem | 201 + Task JSON |
| GetTaskHandler | GET | Path param `id` | GetItem | 200 + Task JSON |
| ListTasksHandler | GET | Query param `status` | Query (GSI) or Scan | 200 + Task[] |
| UpdateTaskHandler | PUT | Path param `id` + JSON body | UpdateItem | 200 + Task JSON |
| DeleteTaskHandler | DELETE | Path param `id` | DeleteItem | 204 (no body) |
| NotificationHandler | EventBridge | Event JSON | (read-only) | 200 (ack) |

### 3.3 Task Model

```java
@DynamoDbBean
public class Task {
    
    @DynamoDbPartitionKey
    @DynamoDbAutoGeneratedKey
    private String id;
    
    private String title;
    private String description;
    private String status;      // TODO | IN_PROGRESS | DONE
    private Instant createdAt;
    private Instant updatedAt;
    
    // Getters, setters, validation methods
}
```

### 3.4 Task Repository

```java
public class TaskRepository {
    
    private final DynamoDbTable<Task> taskTable;
    
    public TaskRepository() {
        DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.create();
        this.taskTable = enhancedClient.table("TasksTable", TableSchema.fromBean(Task.class));
    }
    
    public Task save(Task task) { /* PutItem */ }
    public Task findById(String id) { /* GetItem */ }
    public List<Task> findAll() { /* Scan */ }
    public List<Task> findByStatus(String status) { /* Query on GSI */ }
    public Task update(Task task) { /* UpdateItem */ }
    public void delete(String id) { /* DeleteItem */ }
}
```

### 3.5 API Gateway Response Builder

```java
public class ApiGatewayResponse {
    
    public static APIGatewayProxyResponseEvent success(int statusCode, Object body) {
        return new APIGatewayProxyResponseEvent()
            .withStatusCode(statusCode)
            .withBody(toJson(body))
            .withHeaders(corsHeaders());
    }
    
    public static APIGatewayProxyResponseEvent error(int statusCode, String errorCode, String message) {
        Map<String, Object> errorBody = Map.of(
            "error", errorCode,
            "message", message,
            "statusCode", statusCode
        );
        return new APIGatewayProxyResponseEvent()
            .withStatusCode(statusCode)
            .withBody(toJson(errorBody))
            .withHeaders(corsHeaders());
    }
    
    private static Map<String, String> corsHeaders() {
        return Map.of(
            "Content-Type", "application/json",
            "Access-Control-Allow-Origin", "*",
            "Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS",
            "Access-Control-Allow-Headers", "Content-Type"
        );
    }
}
```

---

## 4. Data Storage Design

### 4.1 DynamoDB Table Schema

#### Primary Table: `TasksTable`

| Attribute | Type | Key Type | Description |
|-----------|------|----------|-------------|
| `id` | String (UUID) | Partition Key | Unique task identifier |
| `title` | String | — | Task title |
| `description` | String | — | Optional description |
| `status` | String | — | `TODO`, `IN_PROGRESS`, `DONE` |
| `createdAt` | String (ISO 8601) | — | Creation timestamp |
| `updatedAt` | String (ISO 8601) | — | Last update timestamp |

#### Global Secondary Index: `StatusIndex`

| Attribute | Type | Key Type | Description |
|-----------|------|----------|-------------|
| `status` | String | Partition Key | Enables querying by status |
| `createdAt` | String (ISO 8601) | Sort Key | Orders tasks by creation time within status |

### 4.2 Access Patterns

| Access Pattern | Operation | Key Condition |
|----------------|-----------|---------------|
| Get task by ID | GetItem | PK = `id` |
| List all tasks | Scan | (full table scan) |
| List tasks by status | Query (GSI) | GSI PK = `status` |
| Create task | PutItem | PK = auto-generated UUID |
| Update task | UpdateItem | PK = `id` |
| Delete task | DeleteItem | PK = `id` |

### 4.3 Why DynamoDB Over RDS?

| Factor | DynamoDB | RDS (PostgreSQL) |
|--------|-----------|-------------------|
| Serverless-native | ✅ Fully managed, auto-scaling | ❌ Requires provisioning or Aurora Serverless |
| Cold start | ✅ Single-digit ms | ⚠️ Connection pooling challenges in Lambda |
| Scaling | ✅ Horizontal, on-demand | ⚠️ Vertical scaling limits |
| Schema flexibility | ✅ Schemaless | ❌ Requires migrations |
| Pay-per-use | ✅ Pay per read/write | ❌ Pay for running instance |
| Interview relevance | ✅ Very common in serverless | ⚠️ Common but not serverless-native |

### 4.4 Capacity Mode

- **On-Demand Mode** — Pay per request, ideal for unpredictable workloads
- Future: Switch to **Provisioned** with auto-scaling if traffic patterns stabilize

---

## 5. AWS SAM Template (`template.yaml`)

### 5.1 Resource Overview

```yaml
Resources:
  # DynamoDB Table
  TasksTable:
    Type: AWS::DynamoDB::Table
    
  # Lambda Functions (each with its own IAM role)
  CreateTaskFunction:
    Type: AWS::Serverless::Function
    
  GetTaskFunction:
    Type: AWS::Serverless::Function
    
  ListTasksFunction:
    Type: AWS::Serverless::Function
    
  UpdateTaskFunction:
    Type: AWS::Serverless::Function
    
  DeleteTaskFunction:
    Type: AWS::Serverless::Function
    
  # API Gateway (implicit, created by SAM)
  # SAM auto-creates API when Events are defined on functions
```

### 5.2 Key SAM Concepts

```yaml
# Each Lambda function in SAM:
CreateTaskFunction:
  Type: AWS::Serverless::Function
  Properties:
    CodeUri: .                          # Points to Maven-built JAR
    Handler: com.example.tasks.handler.CreateTaskHandler::handleRequest
    Runtime: java21                     # Java 21 Lambda runtime
    MemorySize: 512                     # MB (Java needs more than Python/Node)
    Timeout: 30                         # seconds
    Environment:
      Variables:
        TABLE_NAME: !Ref TasksTable     # Injected table name
    Policies:
      - DynamoDBCrudPolicy:             # SAM policy template
          TableName: !Ref TasksTable
    Events:
      CreateTask:                       # API Gateway event
        Type: Api
        Properties:
          Path: /tasks
          Method: post
          Cors: true                    # Enable CORS
```

### 5.3 Why Separate Functions?

| Approach | Pros | Cons |
|----------|------|------|
| **One function per endpoint** (our choice) | Least-privilege IAM, independent scaling, smaller blast radius | More deployment units, slightly more cold starts |
| **One function for all endpoints** | Simpler deployment, fewer cold starts | Overly broad IAM permissions, larger deployment package, coupled scaling |

We choose **one function per endpoint** because:
1. **Security** — Each function gets only the DynamoDB permissions it needs
2. **Interview talking point** — Demonstrates understanding of least-privilege
3. **Independent scaling** — Create can scale differently from List
4. **Smaller blast radius** — A bug in Delete doesn't affect Create

---

## 6. Security Design

### 6.1 IAM Least-Privilege Roles

| Function | DynamoDB Permissions | Rationale |
|----------|----------------------|-----------|
| CreateTaskHandler | `dynamodb:PutItem` | Only needs to create items |
| GetTaskHandler | `dynamodb:GetItem` | Only needs to read single items |
| ListTasksHandler | `dynamodb:Query`, `dynamodb:Scan` | Needs to query GSI and scan |
| UpdateTaskHandler | `dynamodb:GetItem`, `dynamodb:UpdateItem` | Must verify existence before updating |
| DeleteTaskHandler | `dynamodb:GetItem`, `dynamodb:DeleteItem` | Must verify existence before deleting |
| NotificationHandler | `dynamodb:DescribeStream`, `dynamodb:ListStreams` | Only reads stream metadata (Phase 3) |

### 6.2 Permission Matrix (Visualization)

```
                    DynamoDB Permissions
                    PutItem  GetItem  UpdateItem  DeleteItem  Query  Scan
                    ───────  ────────  ──────────  ──────────  ─────  ────
CreateTaskHandler:    ✅
GetTaskHandler:                 ✅
ListTasksHandler:                                                  ✅     ✅
UpdateTaskHandler:              ✅       ✅
DeleteTaskHandler:              ✅                    ✅
NotificationHandler:             (no DynamoDB access)
```

### 6.3 Network Security

- API Gateway enforces HTTPS (no HTTP)
- CORS headers configured for browser access
- Lambda functions run in AWS-owned VPC (no VPC configuration needed for DynamoDB)
- Future: Add Cognito authorizer for authenticated access (Phase 4)

### 6.4 Data Validation

All input is validated at the Lambda handler level:

| Field | Validation |
|-------|-----------|
| `title` | Required, non-blank, max 200 chars |
| `description` | Optional, max 2000 chars |
| `status` | Must be one of: `TODO`, `IN_PROGRESS`, `DONE` |
| `id` (path param) | Must be valid UUID format |
| `status` transition | Must follow state machine rules |

---

## 7. Event-Driven Design (Phase 3)

### 7.1 Event Flow

```
DynamoDB Table (TasksTable)
       │
       │ EnableStream: true
       │ StreamViewType: NEW_AND_OLD_IMAGES
       ▼
DynamoDB Stream
       │
       │ Filter: eventName = INSERT | MODIFY | REMOVE
       ▼
EventBridge Bus (TaskEventsBus)
       │
       │ Rules:
       │   - event.type = "TaskCreated"
       │   - event.type = "TaskUpdated"
       │   - event.type = "TaskDeleted"
       ▼
NotificationHandler Lambda
       │
       │ Logs event, sends notification
       ▼
CloudWatch Logs
```

### 7.2 Event Schema

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "type": "TaskCreated",
  "detail": {
    "taskId": "550e8400-e29b-41d4-a716-446655440000",
    "title": "Learn AWS Lambda",
    "status": "TODO",
    "timestamp": "2026-09-10T10:30:00Z"
  },
  "detail-type": "TaskEvent",
  "source": "com.example.tasks"
}
```

### 7.3 Why DynamoDB Streams → EventBridge?

| Option | Pros | Cons |
|--------|------|------|
| **DynamoDB Streams → Lambda (direct)** | Simple, low latency | Tightly coupled, 1:1 mapping |
| **DynamoDB Streams → EventBridge** ✅ | Decoupled, fan-out, filtering, multiple consumers | Slightly higher latency |
| **Lambda → SNS/SQS** | Decoupled, proven | Requires manual publishing in Lambda code |
| **Lambda → EventBridge (direct publish)** | Decoupled, event bus | Requires publishing code in every Lambda |

**Our choice:** DynamoDB Streams → EventBridge because:
1. **Decoupled** — No event publishing code in CRUD Lambdas
2. **Fan-out** — Multiple consumers can subscribe (logging, analytics, notifications)
3. **Filtering** — EventBridge rules filter events by type
4. **Interview gold** — Shows understanding of event-driven architecture

---

## 8. Error Handling & Resilience

### 8.1 Lambda Error Handling

```java
// Standardized error response in every handler
try {
    // Business logic
} catch (ValidationException e) {
    return ApiGatewayResponse.error(400, "BAD_REQUEST", e.getMessage());
} catch (NotFoundException e) {
    return ApiGatewayResponse.error(404, "NOT_FOUND", e.getMessage());
} catch (ConflictException e) {
    return ApiGatewayResponse.error(409, "CONFLICT", e.getMessage());
} catch (Exception e) {
    context.getLogger().log("Unexpected error: " + e.getMessage());
    return ApiGatewayResponse.error(500, "INTERNAL_ERROR", "An unexpected error occurred");
}
```

### 8.2 DynamoDB Conditional Writes

```java
// Prevent status transitions that violate the state machine
table.updateItem(item -> item
    .key(key)
    .updateExpression("SET #status = :newStatus, #updatedAt = :timestamp")
    .conditionExpression("#status = :expectedStatus")  // Optimistic locking
    .expressionAttributeNames(Map.of("#status", "status", "#updatedAt", "updatedAt"))
    .expressionAttributeValues(Map.of(
        ":newStatus", AttributeValue.builder().s(newStatus).build(),
        ":expectedStatus", AttributeValue.builder().s(currentStatus).build(),
        ":timestamp", AttributeValue.builder().s(Instant.now().toString()).build()
    ))
);
// If condition fails → ConditionalCheckFailedException → 409 Conflict
```

### 8.3 Retry & DLQ Strategy

| Component | Retry | DLQ | Notes |
|-----------|-------|-----|-------|
| API Gateway → Lambda | 0 retries | N/A | Synchronous, client handles retry |
| DynamoDB Streams → Lambda | 2 retries (default) | SQS DLQ | Failed after 2 → DLQ for investigation |
| EventBridge → Lambda | 2 retries, 300s max | SQS DLQ | Exponential backoff |

### 8.4 Idempotency

| Concern | Mitigation |
|---------|-----------|
| Duplicate task creation | Each request generates a new UUID; client can send idempotency key (Phase 4) |
| Duplicate updates | Conditional writes with status check |
| Duplicate deletions | Return 404 if task doesn't exist (idempotent) |
| Duplicate events | Notification Lambda must be idempotent (process by eventId) |

---

## 9. Observability (Phase 5)

### 9.1 Logging

```java
// Structured JSON logging via Lambda Logger
context.getLogger().log(Json.toJson(Map.of(
    "event", "TaskCreated",
    "taskId", task.getId(),
    "timestamp", Instant.now(),
    "requestId", context.getAwsRequestId()
)));
```

All logs go to **CloudWatch Logs** with:
- Log group per Lambda function
- JSON-structured entries for easy querying
- Request IDs for distributed tracing

### 9.2 Metrics

| Metric | Source | Type | Description |
|--------|--------|------|-------------|
| Invocations | CloudWatch | Count | Number of Lambda invocations |
| Duration | CloudWatch | Timer | Lambda execution time |
| Errors | CloudWatch | Count | Lambda error count |
| Throttles | CloudWatch | Count | Lambda concurrent execution limit |
| DynamoDB Read/Write | CloudWatch | Count | Consumed capacity units |

### 9.3 Tracing (Phase 5)

- **AWS X-Ray** enabled on all Lambda functions
- Trace requests from API Gateway → Lambda → DynamoDB
- SAM configuration:

```yaml
Tracing: Active
```

---

## 10. Design Decisions & Trade-offs

### 10.1 Why Serverless?

| Factor | Serverless (Lambda) | Traditional (EC2/ECS) |
|--------|---------------------|----------------------|
| Operational overhead | ✅ Zero | ❌ Patching, scaling, monitoring |
| Cost for low traffic | ✅ Pay per invocation | ❌ Pay for running instances |
| Auto-scaling | ✅ Built-in | ⚠️ Requires configuration |
| Cold starts | ⚠️ ~1–3s (Java) | ✅ None (always running) |
| Execution time limit | ⚠️ 15 min max | ✅ Unlimited |
| Interview relevance | ✅ Highly relevant | ⚠️ Standard |

### 10.2 Why Java on Lambda Despite Cold Starts?

1. **Team expertise** — Java is the primary language
2. **Enterprise relevance** — Most large companies run Java
3. **AWS SDK quality** — Best-in-class DynamoDB Enhanced Client
4. **Type safety** — Compile-time checking reduces runtime errors
5. **Cold start mitigations** — Provisioned concurrency, GraalVM native (future)
6. **Interview depth** — Being able to explain cold-start trade-offs is valuable

### 10.3 Why SAM (Not CDK or Terraform)?

| Tool | Pros | Cons |
|------|------|------|
| **SAM** ✅ | Purpose-built for serverless, simpler YAML, `sam local` for testing | Less flexible for non-serverless resources |
| **CDK** | More powerful, TypeScript/Java, reusable constructs | Steeper learning curve, overkill for Lambda-only |
| **Terraform** | Multi-cloud, huge ecosystem | No local testing, verbose Lambda configurations |

**Our choice:** SAM because:
1. **Serverless-first** — Designed specifically for Lambda + API Gateway + DynamoDB
2. **Learning path** — SAM teaches CloudFormation concepts that transfer to CDK
3. **`sam local`** — Local testing without deploying to AWS
4. **Interview relevance** — SAM is commonly used in AWS serverless teams

### 10.4 Why DynamoDB Enhanced Client?

| Approach | Pros | Cons |
|----------|------|------|
| **Enhanced Client** ✅ | Type-safe, annotation-based, fluent API | More boilerplate |
| **Low-level Client** | Full control, direct attribute mapping | Verbose, error-prone |
| **Spring Data DynamoDB** | Familiar Spring patterns | Heavy dependency, cold start penalty |

### 10.5 Why One Lambda Per Endpoint?

See [Section 5.3](#53-why-separate-functions) above for the detailed analysis.

**Summary:** Least-privilege IAM, independent scaling, smaller blast radius, better interview signal.

---

## 11. Cold Start Analysis

### 11.1 What Is a Cold Start?

When Lambda receives a request and no existing execution environment is available:

```
Request arrives
  → Lambda creates execution environment
  → Downloads deployment package
  → Initializes runtime (JVM start)
  → Initialize handler class (constructor)
  → Call handleRequest()
  → Return response
```

### 11.2 Cold Start Mitigations

| Strategy | Description | Trade-off |
|----------|-------------|-----------|
| **Shaded JAR** | Single fat JAR with `maven-shade-plugin` | Larger package, but avoids classpath issues |
| **Provisioned Concurrency** | Pre-warmed instances | Costs money even when idle |
| **GraalVM Native Image** | Compile to native binary (future) | Complex build, limited reflection |
| **Minimal dependencies** | Only include what's needed | More manual code, less convenience |
| **Lazy initialization** | Move heavy init from constructor to first request | Slightly slower first request, but constructor is fast |

### 11.3 Expected Cold Start Times

| Configuration | Cold Start | Warm Invocation |
|---------------|------------|-----------------|
| Java 21, 512MB, shaded JAR | ~2–3s | ~50–200ms |
| Java 21, 1024MB, shaded JAR | ~1–2s | ~30–100ms |
| Java 21, Provisioned Concurrency | N/A (pre-warmed) | ~30–100ms |

> **Interview tip:** Always mention that cold starts are a **known trade-off** that can be mitigated with provisioned concurrency, and that Java 21's improvements (ZGC, generational ZGC) help.

---

## 12. Cost Estimation

### 12.1 Monthly Cost (Low Traffic: ~10K requests/month)

| Service | Free Tier | Estimated Cost |
|---------|-----------|---------------|
| Lambda | 1M requests + 400K GB-sec free | $0.00 |
| API Gateway | 1M requests free | $0.00 |
| DynamoDB (On-Demand) | 25GB storage free | ~$0.50 (storage) |
| CloudWatch Logs | 5GB ingestion free | ~$0.10 |
| **Total** | | **~$0.60/month** |

### 12.2 Monthly Cost (Medium Traffic: ~1M requests/month)

| Service | Estimated Cost |
|---------|---------------|
| Lambda | ~$1.00 |
| API Gateway | ~$3.50 |
| DynamoDB (On-Demand) | ~$5.00 |
| CloudWatch Logs | ~$2.00 |
| **Total** | **~$11.50/month** |

> These estimates assume no provisioned concurrency and on-demand DynamoDB billing.