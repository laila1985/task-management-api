# Deployment Guide — Task Management API

> **Version:** 1.0  
> **Last Updated:** 2026-09-10  
> **Audience:** Developers, DevOps

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Project Setup](#2-project-setup)
3. [Local Development](#3-local-development)
4. [Building](#4-building)
5. [Local Testing](#5-local-testing)
6. [Deploying to AWS](#6-deploying-to-aws)
7. [Post-Deployment Verification](#7-post-deployment-verification)
8. [CI/CD Pipeline (Phase 7)](#8-cicd-pipeline-phase-7)
9. [Environment Management](#9-environment-management)
10. [Monitoring & Observability](#10-monitoring--observability)
11. [Troubleshooting](#11-troubleshooting)
12. [Cleanup](#12-cleanup)

---

## 1. Prerequisites

### 1.1 Required Tools

| Tool | Version | Purpose | Install |
|------|---------|---------|---------|
| Java JDK | 21 | Runtime & build | `sudo apt install openjdk-21-jdk` |
| Apache Maven | 3.6+ | Build & dependency management | `sudo apt install maven` |
| Docker | 20+ | Local Lambda emulation | [docker.com](https://docs.docker.com/engine/install/) |
| AWS CLI | v2 | AWS interaction | [docs.aws.amazon.com](https://docs.aws.amazon.com/cli/latest/userguide/install-cliv2.html) |
| SAM CLI | Latest | Build, test & deploy serverless | [docs.aws.amazon.com/serverless](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/install-sam-cli.html) |
| Git | 2.x | Version control | `sudo apt install git` |

### 1.2 Verify Installations

```bash
# Check all required tools
java -version          # openjdk version "21.x"
mvn -version           # Apache Maven 3.6.x
docker --version       # Docker version 20.x+
aws --version           # aws-cli/2.x
sam --version           # SAM CLI, 1.x
git --version           # git version 2.x
```

### 1.3 AWS Account Setup

1. **Create an AWS account** (if you don't have one): [aws.amazon.com](https://aws.amazon.com/)
2. **Create an IAM user** with programmatic access:
   ```bash
   # In AWS Console → IAM → Users → Create User
   # Attach policies: AdministratorAccess (for development only)
   # Generate Access Key
   ```
3. **Configure AWS CLI:**
   ```bash
   aws configure
   # AWS Access Key ID: [your-access-key]
   # AWS Secret Access Key: [your-secret-key]
   # Default region name: eu-west-1
   # Default output format: json
   ```
4. **Verify credentials:**
   ```bash
   aws sts get-caller-identity
   # Should return your account ID, user ARN, and user ID
   ```

### 1.4 Docker Setup (for local testing)

```bash
# Ensure Docker daemon is running
sudo systemctl start docker
sudo systemctl enable docker

# Add your user to docker group (avoids sudo)
sudo usermod -aG docker $USER
newgrp docker

# Verify
docker run hello-world
```

---

## 2. Project Setup

### 2.1 Clone the Repository

```bash
cd ~/Documents/project/practic/
git clone https://github.com/laila1985/task-management-api.git
cd task-management-api
```

### 2.2 Project Structure

```
task-management-api/
├── template.yaml                    # SAM infrastructure definition
├── pom.xml                          # Maven build configuration
├── src/
│   ├── main/java/com/example/tasks/
│   │   ├── model/Task.java
│   │   ├── handler/
│   │   │   ├── CreateTaskHandler.java
│   │   │   ├── GetTaskHandler.java
│   │   │   ├── ListTasksHandler.java
│   │   │   ├── UpdateTaskHandler.java
│   │   │   ├── DeleteTaskHandler.java
│   │   │   └── NotificationHandler.java
│   │   ├── repository/TaskRepository.java
│   │   └── util/ApiGatewayResponse.java
│   └── test/java/com/example/tasks/
│       ├── handler/
│       │   └── CreateTaskHandlerTest.java
│       └── repository/
│           └── TaskRepositoryTest.java
├── events/                          # Sample Lambda test events
│   ├── create-task.json
│   ├── get-task.json
│   ├── list-tasks.json
│   └── update-task.json
└── docs/                            # Documentation
```

### 2.3 Maven Dependencies

Key dependencies in `pom.xml`:

```xml
<!-- AWS Lambda Core -->
<dependency>
    <groupId>com.amazonaws</groupId>
    <artifactId>aws-lambda-java-core</artifactId>
    <version>1.2.3</version>
</dependency>

<!-- AWS Lambda Events (API Gateway event types) -->
<dependency>
    <groupId>com.amazonaws</groupId>
    <artifactId>aws-lambda-java-events</artifactId>
    <version>3.11.4</version>
</dependency>

<!-- AWS SDK v2 - DynamoDB Enhanced Client -->
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>dynamodb-enhanced</artifactId>
    <version>2.25.70</version>
</dependency>

<!-- Jackson for JSON -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>2.17.0</version>
</dependency>

<!-- Jackson Java 8 Date/Time -->
<dependency>
    <groupId>com.fasterxml.jackson.datatype</groupId>
    <artifactId>jackson-datatype-jsr310</artifactId>
    <version>2.17.0</version>
</dependency>
```

Build plugins:

```xml
<!-- Maven Shade Plugin - creates fat JAR -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-shade-plugin</artifactId>
    <version>3.5.1</version>
</plugin>
```

---

## 3. Local Development

### 3.1 Build the Project

```bash
# Compile and package
mvn clean package

# Output: target/task-management-api-1.0.jar (shaded fat JAR)
```

### 3.2 SAM Template Configuration

The `template.yaml` defines all AWS resources:

```yaml
AWSTemplateFormatVersion: '2010-09-09'
Transform: AWS::Serverless-2016-10-31
Description: Task Management API - Serverless Java Application

Globals:
  Function:
    Runtime: java21
    MemorySize: 512
    Timeout: 30
    Environment:
      Variables:
        TABLE_NAME: !Ref TasksTable

Resources:
  # DynamoDB Table
  TasksTable:
    Type: AWS::DynamoDB::Table
    Properties:
      TableName: TasksTable
      BillingMode: PAY_PER_REQUEST
      AttributeDefinitions:
        - AttributeName: id
          AttributeType: S
        - AttributeName: status
          AttributeType: S
        - AttributeName: createdAt
          AttributeType: S
      KeySchema:
        - AttributeName: id
          KeyType: HASH
      GlobalSecondaryIndexes:
        - IndexName: StatusIndex
          KeySchema:
            - AttributeName: status
              KeyType: HASH
            - AttributeName: createdAt
              KeyType: RANGE
          Projection:
            ProjectionType: ALL

  # Lambda Functions
  CreateTaskFunction:
    Type: AWS::Serverless::Function
    Properties:
      CodeUri: .
      Handler: com.example.tasks.handler.CreateTaskHandler::handleRequest
      Policies:
        - DynamoDBCrudPolicy:
            TableName: !Ref TasksTable
      Events:
        CreateTask:
          Type: Api
          Properties:
            Path: /tasks
            Method: post
            Cors: true

  GetTaskFunction:
    Type: AWS::Serverless::Function
    Properties:
      CodeUri: .
      Handler: com.example.tasks.handler.GetTaskHandler::handleRequest
      Policies:
        - DynamoDBReadPolicy:
            TableName: !Ref TasksTable
      Events:
        GetTask:
          Type: Api
          Properties:
            Path: /tasks/{id}
            Method: get
            Cors: true

  ListTasksFunction:
    Type: AWS::Serverless::Function
    Properties:
      CodeUri: .
      Handler: com.example.tasks.handler.ListTasksHandler::handleRequest
      Policies:
        - DynamoDBReadPolicy:
            TableName: !Ref TasksTable
      Events:
        ListTasks:
          Type: Api
          Properties:
            Path: /tasks
            Method: get
            Cors: true

  UpdateTaskFunction:
    Type: AWS::Serverless::Function
    Properties:
      CodeUri: .
      Handler: com.example.tasks.handler.UpdateTaskHandler::handleRequest
      Policies:
        - DynamoDBCrudPolicy:
            TableName: !Ref TasksTable
      Events:
        UpdateTask:
          Type: Api
          Properties:
            Path: /tasks/{id}
            Method: put
            Cors: true

  DeleteTaskFunction:
    Type: AWS::Serverless::Function
    Properties:
      CodeUri: .
      Handler: com.example.tasks.handler.DeleteTaskHandler::handleRequest
      Policies:
        - DynamoDBCrudPolicy:
            TableName: !Ref TasksTable
      Events:
        DeleteTask:
          Type: Api
          Properties:
            Path: /tasks/{id}
            Method: delete
            Cors: true

Outputs:
  TaskApi:
    Description: API Gateway endpoint URL
    Value: !Sub "https://${ServerlessRestApi}.execute-api.${AWS::Region}.amazonaws.com/prod/"
  TasksTable:
    Description: DynamoDB Table Name
    Value: !Ref TasksTable
```

### 3.3 Environment Variables

The Lambda functions use one environment variable injected by SAM:

| Variable | Source | Description |
|----------|--------|-------------|
| `TABLE_NAME` | `!Ref TasksTable` | DynamoDB table name (injected at deploy time) |

Locally, SAM sets this automatically via the template.

---

## 4. Building

### 4.1 Maven Build

```bash
# Clean, compile, test, package
mvn clean package

# Skip tests (faster, for quick iteration)
mvn clean package -DskipTests

# Verbose output
mvn clean package -X
```

The Maven Shade plugin produces a single fat JAR at `target/task-management-api-1.0.jar` containing all dependencies.

### 4.2 SAM Build

```bash
# Build the SAM application (wraps Maven build)
sam build

# Output: .aws-sam/build/ directory with deployment artifacts
```

**What `sam build` does:**

1. Reads `template.yaml`
2. Runs `mvn clean package` for each Lambda function
3. Copies JARs to `.aws-sam/build/`
4. Prepares the deployment package

### 4.3 Build Troubleshooting

| Issue | Cause | Solution |
|-------|-------|---------|
| `ClassNotFoundException` | Missing shade plugin | Ensure `maven-shade-plugin` is in `pom.xml` |
| `NoClassDefFoundError: software/amazon/awssdk/...` | AWS SDK not in fat JAR | Check shade plugin configuration |
| `Handler not found` | Wrong handler path | Verify `Handler` in `template.yaml` matches package structure |
| `Build failed` | Java version mismatch | Ensure `java.version` in `pom.xml` matches installed JDK |
| `Permission denied` | Docker not running | Start Docker: `sudo systemctl start docker` |

---

## 5. Local Testing

### 5.1 Invoke a Single Lambda Function

```bash
# Create a task
sam local invoke CreateTaskFunction --event events/create-task.json

# Get a task (use an ID from the create response)
sam local invoke GetTaskFunction --event events/get-task.json

# List all tasks
sam local invoke ListTasksFunction --event events/list-tasks.json

# Update a task
sam local invoke UpdateTaskFunction --event events/update-task.json

# Delete a task
sam local invoke DeleteTaskFunction --event events/delete-task.json
```

### 5.2 Sample Event Files

#### `events/create-task.json`

```json
{
  "httpMethod": "POST",
  "path": "/tasks",
  "body": "{\"title\": \"Learn AWS Lambda\", \"description\": \"Build my first serverless application\"}",
  "headers": {
    "Content-Type": "application/json"
  }
}
```

#### `events/get-task.json`

```json
{
  "httpMethod": "GET",
  "path": "/tasks/550e8400-e29b-41d4-a716-446655440000",
  "pathParameters": {
    "id": "550e8400-e29b-41d4-a716-446655440000"
  }
}
```

#### `events/list-tasks.json`

```json
{
  "httpMethod": "GET",
  "path": "/tasks",
  "queryStringParameters": {
    "status": "TODO"
  }
}
```

#### `events/update-task.json`

```json
{
  "httpMethod": "PUT",
  "path": "/tasks/550e8400-e29b-41d4-a716-446655440000",
  "pathParameters": {
    "id": "550e8400-e29b-41d4-a716-446655440000"
  },
  "body": "{\"title\": \"Learn AWS Lambda - Updated\", \"status\": \"IN_PROGRESS\"}",
  "headers": {
    "Content-Type": "application/json"
  }
}
```

#### `events/delete-task.json`

```json
{
  "httpMethod": "DELETE",
  "path": "/tasks/550e8400-e29b-41d4-a716-446655440000",
  "pathParameters": {
    "id": "550e8400-e29b-41d4-a716-446655440000"
  }
}
```

### 5.3 Start Local API Gateway

```bash
# Start a local API Gateway emulator on port 3000
sam local start-api --port 3000

# Now you can test with curl:
curl -X POST http://localhost:3000/tasks \
  -H "Content-Type: application/json" \
  -d '{"title": "Local test task"}'

curl http://localhost:3000/tasks

curl http://localhost:3000/tasks/{id}

curl -X PUT http://localhost:3000/tasks/{id} \
  -H "Content-Type: application/json" \
  -d '{"status": "IN_PROGRESS"}'

curl -X DELETE http://localhost:3000/tasks/{id}
```

> **Note:** `sam local start-api` requires Docker to be running. DynamoDB operations will fail unless you also run DynamoDB Local (see Section 5.4).

### 5.4 DynamoDB Local (for local API testing)

For local API Gateway testing that includes DynamoDB operations, run DynamoDB Local:

```bash
# Pull and run DynamoDB Local
docker run -p 8000:8000 -d amazon/dynamodb-local

# Create the table locally
aws dynamodb create-table \
  --table-name TasksTable \
  --attribute-definitions \
    AttributeName=id,AttributeType=S \
    AttributeName=status,AttributeType=S \
    AttributeName=createdAt,AttributeType=S \
  --key-schema AttributeName=id,KeyType=HASH \
  --global-secondary-indexes \
    '[
      {
        "IndexName": "StatusIndex",
        "KeySchema": [
          {"AttributeName": "status", "KeyType": "HASH"},
          {"AttributeName": "createdAt", "KeyType": "RANGE"}
        ],
        "Projection": {"ProjectionType": "ALL"},
        "ProvisionedThroughput": {"ReadCapacityUnits": 5, "WriteCapacityUnits": 5}
      }
    ]' \
  --billing-mode PAY_PER_REQUEST \
  --endpoint-url http://localhost:8000 \
  --region eu-west-1

# List tables to verify
aws dynamodb list-tables --endpoint-url http://localhost:8000 --region eu-west-1
```

Then start SAM with DynamoDB Local endpoint override:

```bash
sam local start-api --port 3000 \
  --env-vars env.json \
  --docker-network host
```

Where `env.json` points the DynamoDB client to localhost:

```json
{
  "CreateTaskFunction": {
    "TABLE_NAME": "TasksTable",
    "AWS_ENDPOINT": "http://host.docker.internal:8000"
  },
  "GetTaskFunction": {
    "TABLE_NAME": "TasksTable",
    "AWS_ENDPOINT": "http://host.docker.internal:8000"
  },
  "ListTasksFunction": {
    "TABLE_NAME": "TasksTable",
    "AWS_ENDPOINT": "http://host.docker.internal:8000"
  },
  "UpdateTaskFunction": {
    "TABLE_NAME": "TasksTable",
    "AWS_ENDPOINT": "http://host.docker.internal:8000"
  },
  "DeleteTaskFunction": {
    "TABLE_NAME": "TasksTable",
    "AWS_ENDPOINT": "http://host.docker.internal:8000"
  }
}
```

### 5.5 Unit Tests

```bash
# Run all unit tests
mvn test

# Run a specific test class
mvn test -Dtest=CreateTaskHandlerTest

# Run with verbose output
mvn test -X
```

Unit tests use **Mockito** to mock `TaskRepository` and test handler logic in isolation.

---

## 6. Deploying to AWS

### 6.1 First-Time Deployment

```bash
# Step 1: Build the application
sam build

# Step 2: Deploy with guided prompts (first time only)
sam deploy --guided
```

**Guided deployment prompts:**

```
Configuring SAM deploy
======================

    Looking for config file [samconfig.toml] :  Not found

    Setting default arguments for 'sam deploy'
    =========================================
    Stack Name [sam-app]: task-management-api
    AWS Region [eu-west-1]: eu-west-1
    #Parameter "TableName" [TasksTable]: TasksTable
    Confirm changes before deploy [y/N]: y
    Allow SAM CLI IAM role creation [Y/n]: Y
    Save arguments to configuration file [Y/n]: Y
    SAM configuration file [samconfig.toml]: samconfig.toml
    SAM configuration environment [default]: default
```

**What happens during deployment:**

1. SAM transforms `template.yaml` → CloudFormation template
2. Uploads deployment artifacts to S3
3. Creates/updates CloudFormation stack
4. Provisions: API Gateway, 5 Lambda functions, DynamoDB table, IAM roles
5. Outputs the API Gateway URL

### 6.2 Subsequent Deployments

After the first guided deployment, `samconfig.toml` is created. Future deployments are simpler:

```bash
# Build and deploy
sam build && sam deploy
```

### 6.3 Deployment Options

```bash
# Deploy with confirmation of changes
sam deploy --no-confirm-changeset

# Deploy to a different region
sam deploy --region us-east-1

# Deploy with a specific S3 bucket
sam deploy --s3-bucket my-deployment-bucket

# Deploy with specific parameter values
sam deploy --parameter-overrides TableName=MyTasksTable

# View the CloudFormation template SAM generates
sam package --output-template-file packaged.yaml
```

### 6.4 CloudFormation Outputs

After deployment, SAM outputs:

```
-------------------------------------------------------------------------------------------------------------------------
Outputs                                                                                                                 
-------------------------------------------------------------------------------------------------------------------------
Key                 TaskApi                                                                                            
Description          API Gateway endpoint URL                                                                            
Value                https://abc123def.execute-api.eu-west-1.amazonaws.com/prod/                                          
                                                                                                                         
Key                 TasksTable                                                                                          
Description          DynamoDB Table Name                                                                                 
Value                TasksTable                                                                                          
-------------------------------------------------------------------------------------------------------------------------
```

**Save the API URL!** You'll need it for testing.

---

## 7. Post-Deployment Verification

### 7.1 Test the Deployed API

Replace `$API_URL` with the URL from CloudFormation outputs.

```bash
API_URL="https://abc123def.execute-api.eu-west-1.amazonaws.com/prod"

# Create a task
curl -X POST $API_URL/tasks \
  -H "Content-Type: application/json" \
  -d '{"title": "Learn AWS Lambda", "description": "Build my first serverless application"}'
# → Returns 201 with created task (save the id)

# List all tasks
curl $API_URL/tasks

# Get a specific task (replace {id} with actual ID)
curl $API_URL/tasks/{id}

# Update a task
curl -X PUT $API_URL/tasks/{id} \
  -H "Content-Type: application/json" \
  -d '{"status": "IN_PROGRESS"}'

# Delete a task
curl -X DELETE $API_URL/tasks/{id}
```

### 7.2 Verify AWS Resources

```bash
# List Lambda functions
aws lambda list-functions --query 'Functions[*].[FunctionName,Runtime,MemorySize,Timeout]' --output table

# Check DynamoDB table
aws dynamodb describe-table --table-name TasksTable --output json

# List API Gateway APIs
aws apigateway get-rest-apis --output table

# Check CloudFormation stack
aws cloudformation describe-stacks --stack-name task-management-api --output json
```

### 7.3 Check CloudWatch Logs

```bash
# List log groups
aws logs describe-log-groups --output table

# Get recent logs for a specific function
aws logs tail /aws/lambda/task-management-api-CreateTaskFunction --follow

# Search for errors
aws logs filter-log-events \
  --log-group-name /aws/lambda/task-management-api-CreateTaskFunction \
  --filter-pattern "ERROR" \
  --limit 10
```

---

## 8. CI/CD Pipeline (Phase 7)

### 8.1 GitHub Actions Workflow

Create `.github/workflows/deploy.yml`:

```yaml
name: Deploy Task Management API

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven
      
      - name: Run tests
        run: mvn test

  deploy:
    needs: test
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    steps:
      - uses: actions/checkout@v4
      
      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven
      
      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-region: eu-west-1
      
      - name: SAM Build
        run: sam build
      
      - name: SAM Deploy
        run: sam deploy --no-confirm-changeset --no-fail-on-empty-changeset
```

### 8.2 Required GitHub Secrets

Set these in **Settings → Secrets and variables → Actions**:

| Secret | Description |
|--------|-------------|
| `AWS_ACCESS_KEY_ID` | IAM access key with deployment permissions |
| `AWS_SECRET_ACCESS_KEY` | IAM secret key |

### 8.3 Branch Strategy

```
main ──────► Production (auto-deploy via GitHub Actions)
  │
  └── develop ──► Staging (manual deploy for testing)
        │
        └── feature/xxx ──► Feature branches (CI test only)
```

---

## 9. Environment Management

### 9.1 Multiple Environments

SAM supports multiple configuration environments:

```bash
# Deploy to staging
sam deploy --config-env staging

# Deploy to production
sam deploy --config-env production
```

`samconfig.toml` with multiple environments:

```toml
[default]
[default.deploy]
[default.deploy.parameters]
stack_name = "task-management-api"
region = "eu-west-1"

[staging]
[staging.deploy]
[staging.deploy.parameters]
stack_name = "task-management-api-staging"
region = "eu-west-1"

[production]
[production.deploy]
[production.deploy.parameters]
stack_name = "task-management-api-prod"
region = "eu-west-1"
```

### 9.2 Environment-Specific Configuration

| Environment | Stack Name | DynamoDB | API Gateway |
|-------------|-----------|----------|-------------|
| dev (default) | `task-management-api` | `TasksTable` | Dev API |
| staging | `task-management-api-staging` | `TasksTable-staging` | Staging API |
| production | `task-management-api-prod` | `TasksTable-prod` | Prod API |

---

## 10. Monitoring & Observability

### 10.1 CloudWatch Dashboards

```bash
# View Lambda metrics
aws cloudwatch get-metric-statistics \
  --namespace AWS/Lambda \
  --metric-name Invocations \
  --dimensions Name=FunctionName,Value=task-management-api-CreateTaskFunction \
  --start-time $(date -u -d '1 hour ago' +%Y-%m-%dT%H:%M:%S) \
  --end-time $(date -u +%Y-%m-%dT%H:%M:%S) \
  --period 300 \
  --statistics Sum
```

### 10.2 Key Metrics to Monitor

| Metric | Service | Alert Threshold |
|--------|---------|----------------|
| `Invocations` | Lambda | Sudden spikes may indicate abuse |
| `Errors` | Lambda | > 1% error rate |
| `Duration` | Lambda | p99 > 3000ms (cold starts) |
| `Throttles` | Lambda | > 0 (need more concurrency) |
| `5XXError` | API Gateway | > 0 |
| `Latency` | API Gateway | p99 > 5000ms |
| `ConsumedReadCapacityUnits` | DynamoDB | Approaching table limit |
| `ConsumedWriteCapacityUnits` | DynamoDB | Approaching table limit |

### 10.3 X-Ray Tracing (Phase 5)

Enable in `template.yaml`:

```yaml
Globals:
  Function:
    Tracing: Active
```

Then view traces in the X-Ray console to see:
- API Gateway → Lambda → DynamoDB request path
- Time spent in each service
- Cold start vs. warm invocation
- Error points

---

## 11. Troubleshooting

### 11.1 Common Build Issues

| Problem | Cause | Solution |
|---------|-------|---------|
| `mvn package` fails | Java version mismatch | Check `java -version` matches `pom.xml` |
| `ClassNotFoundException` at runtime | Missing shade plugin | Ensure `maven-shade-plugin` configured |
| `sam build` fails | SAM CLI outdated | Update: `sam --update` or reinstall |
| `SAM CLI not found` | Not on PATH | Install SAM CLI and add to PATH |
| `Docker not running` | Docker daemon stopped | Start: `sudo systemctl start docker` |

### 11.2 Common Deployment Issues

| Problem | Cause | Solution |
|---------|-------|---------|
| `Stack creation failed` | IAM permission denied | Ensure your AWS user has `AdministratorAccess` or required permissions |
| `S3 bucket not found` | Deployment bucket missing | SAM creates it automatically, or specify with `--s3-bucket` |
| `Function already exists` | Stack name conflict | Use unique stack names or delete old stack: `aws cloudformation delete-stack --stack-name <name>` |
| `Table already exists` | DynamoDB table name conflict | Use a different table name or delete existing table |
| `Timeout` | Large JAR upload | Increase `--timeout` or reduce JAR size |

### 11.3 Common Runtime Issues

| Problem | Cause | Solution |
|---------|-------|---------|
| `500 Internal Server Error` | Unhandled exception in Lambda | Check CloudWatch Logs for the specific function |
| `Task timed out` | Lambda timeout (default 30s) | Increase `Timeout` in `template.yaml` or optimize code |
| `AccessDeniedException` | IAM role too restrictive | Check function's IAM policy includes required DynamoDB actions |
| `ResourceNotFoundException` | Wrong table name | Verify `TABLE_NAME` environment variable matches actual table |
| `Cold start > 10s` | Large JAR, too many dependencies | Reduce dependencies, use provisioned concurrency, or consider GraalVM |
| `CORS error` | Missing CORS headers | Ensure `Cors: true` in `template.yaml` and headers in response |

### 11.4 Checking Logs

```bash
# View logs for a specific function (last 10 minutes)
sam logs -n CreateTaskFunction --stack-name task-management-api --tail

# View logs for a specific function (specific time range)
sam logs -n CreateTaskFunction --stack-name task-management-api \
  --start-time 2026-09-10T10:00:00 --end-time 2026-09-10T11:00:00

# Search for errors across all functions
aws logs filter-log-events \
  --log-group-name /aws/lambda/task-management-api-CreateTaskFunction \
  --filter-pattern "ERROR"
```

### 11.5 Debugging Lambda Locally

```bash
# Invoke with environment variables
sam local invoke CreateTaskFunction \
  --event events/create-task.json \
  --env-vars env.json

# Enable Java remote debugging (port 5858)
sam local invoke CreateTaskFunction \
  --event events/create-task.json \
  --debug-port 5858

# Then attach IntelliJ debugger to localhost:5858
```

**IntelliJ IDEA Debug Configuration:**
1. Run → Edit Configurations → Add New → Remote JVM Debug
2. Host: `localhost`, Port: `5858`
3. Start `sam local invoke` with `--debug-port 5858`
4. Set breakpoints in your handler code
5. Run the debug configuration in IntelliJ

---

## 12. Cleanup

### 12.1 Delete the AWS Stack

```bash
# Delete all AWS resources created by SAM
sam delete --stack-name task-management-api

# Or via CloudFormation directly
aws cloudformation delete-stack --stack-name task-management-api
```

### 12.2 Delete the S3 Deployment Bucket

```bash
# Find the deployment bucket
aws cloudformation list-stack-resources \
  --stack-name task-management-api \
  --query "StackResources[?ResourceType=='AWS::S3::Bucket'].PhysicalResourceId" \
  --output text

# Empty and delete the bucket (replace with your bucket name)
aws s3 rm s3://your-deployment-bucket --recursive
aws s3 rb s3://your-deployment-bucket
```

### 12.3 Delete DynamoDB Table (if not deleted by stack)

```bash
aws dynamodb delete-table --table-name TasksTable
```

### 12.4 Delete CloudWatch Log Groups

```bash
# List and delete log groups for this application
aws logs describe-log-groups \
  --log-group-name-prefix /aws/lambda/task-management-api \
  --query 'logGroups[*].logGroupName' \
  --output text | xargs -I {} aws logs delete-log-group --log-group-name {}
```

### 12.5 Remove Local Build Artifacts

```bash
# Clean Maven build
mvn clean

# Remove SAM build artifacts
rm -rf .aws-sam/

# Remove packaged template
rm -f packaged.yaml
```

---

## Appendix: Quick Reference Commands

```bash
# ─── Build ────────────────────────────────────────────
mvn clean package              # Build JAR
sam build                      # Build SAM deployment artifacts

# ─── Local Testing ───────────────────────────────────
sam local invoke CreateTaskFunction --event events/create-task.json
sam local invoke GetTaskFunction --event events/get-task.json
sam local start-api --port 3000
mvn test                        # Run unit tests

# ─── Deploy ───────────────────────────────────────────
sam deploy --guided             # First deployment (interactive)
sam deploy                      # Subsequent deployments
sam deploy --no-confirm-changeset   # Deploy without confirmation

# ─── Monitor ─────────────────────────────────────────
sam logs -n CreateTaskFunction --stack-name task-management-api --tail
aws cloudwatch describe-alarms  # Check CloudWatch alarms
aws lambda list-functions        # List deployed functions

# ─── Cleanup ─────────────────────────────────────────
sam delete --stack-name task-management-api
mvn clean
rm -rf .aws-sam/
```

---

## Appendix: SAM CLI Cheat Sheet

| Command | Description |
|---------|-------------|
| `sam init` | Initialize a new SAM project |
| `sam build` | Build the application |
| `sam local invoke` | Invoke a function locally |
| `sam local start-api` | Start local API Gateway |
| `sam local start-lambda` | Start local Lambda runtime |
| `sam deploy --guided` | First deployment with prompts |
| `sam deploy` | Deploy using saved configuration |
| `sam logs` | View Lambda function logs |
| `sam delete` | Delete the stack |
| `sam validate` | Validate the template |
| `sam package` | Package the application |
| `sam list endpoints` | List API endpoints |
| `sam list resources` | List stack resources |