# Task Management API — Documentation

> Serverless Java application on AWS: Lambda, API Gateway, DynamoDB, EventBridge

---

## 📂 Documentation Structure
this is a __Java 21 serverless application__ built with:

- __Language:__ Java 21

- __Build Tool:__ Maven (pom.xml)

- __AWS SDK:__ Java SDK v2 (DynamoDB Enhanced Client)

- __Runtime:__ AWS Lambda (Java 21 runtime)

- __Framework:__ AWS Serverless Application Model (SAM)

- __Dependencies:__

    - `aws-lambda-java-core` — Lambda runtime interface
    - `aws-lambda-java-events` — API Gateway event types
    - `aws-sdk-java-dynamodb` & `aws-sdk-java-dynamodb-enhanced` — DynamoDB access
    - `jackson-databind` & `jackson-datatype-jsr310` — JSON serialization
    - `JUnit 5` + `Mockito` — Testing

The application is not a traditional Spring Boot or standalone Java server. It's a collection of __AWS Lambda handler classes__ (each implementing `RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent>`) deployed behind __API Gateway__ with __DynamoDB__ as the database.


It compiles into a fat JAR (`target/task-management-api-1.0.jar`) that gets deployed to AWS Lambda via SAM/CloudFormation.


```
docs/
├── README.md                           ← You are here
├── functional/
│   └── functional-specification.md     What the system does (business view)
└── technical/
    ├── architecture.md                 System design & AWS architecture
    ├── api-reference.md                Endpoint-by-endpoint technical spec
    └── deployment-guide.md             Build, test & deploy instructions
```

---

## 🔍 Quick Navigation

| Document | Audience | Description |
|----------|----------|-------------|
| [Functional Specification](functional/functional-specification.md) | Product owners, QA, stakeholders | Business requirements, use cases, acceptance criteria |
| [Architecture](technical/architecture.md) | Developers, SRE, interviewers | AWS component design, data model, security, trade-offs |
| [API Reference](technical/api-reference.md) | Frontend devs, API consumers | Request/response schemas, status codes, examples |
| [Deployment Guide](technical/deployment-guide.md) | DevOps, developers | Setup, local dev, deploy, CI/CD, troubleshooting |

---

## 🎯 Project Phases

| Phase | Scope | Status |
|-------|-------|--------|
| 1 | Java Lambda + API Gateway + DynamoDB (CRUD) | Documented |
| 2 | Full CRUD implementation | Documented |
| 3 | EventBridge — event-driven notifications | Planned |
| 4 | IAM / Cognito — authentication & authorization | Planned |
| 5 | CloudWatch — monitoring, logging, tracing | Planned |
| 6 | SAM — Infrastructure as Code refinement | In progress |
| 7 | GitHub Actions — CI/CD pipeline | Planned |
| 8 | Interview preparation — architecture deep-dive | Planned |

---

## 📐 Architecture at a Glance

```
Client
  │
  │ HTTPS
  ▼
API Gateway
  │
  ├── POST   /tasks        → CreateTaskHandler
  ├── GET    /tasks        → ListTasksHandler
  ├── GET    /tasks/{id}   → GetTaskHandler
  ├── PUT    /tasks/{id}   → UpdateTaskHandler
  └── DELETE /tasks/{id}   → DeleteTaskHandler
        │
        │ AWS SDK v2
        ▼
    DynamoDB (TasksTable)
        │
        │ DynamoDB Streams
        ▼
    EventBridge
        │
        ▼
  NotificationHandler
```

---

## 🛠 Tech Stack

| Layer | Technology |
|-------|-----------|
| Runtime | Java 21 (AWS Lambda) |
| Build | Maven 3.6+ |
| AWS SDK | AWS SDK for Java v2 |
| Database | Amazon DynamoDB |
| API | Amazon API Gateway (REST) |
| Events | Amazon EventBridge |
| IaC | AWS SAM (template.yaml) |
| CI/CD | GitHub Actions (planned) |

---

*For detailed information, navigate to the relevant document above.*