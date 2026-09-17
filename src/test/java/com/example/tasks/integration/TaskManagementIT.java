package com.example.tasks.integration;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.example.tasks.handler.CreateTaskHandler;
import com.example.tasks.handler.DeleteTaskHandler;
import com.example.tasks.handler.GetTaskHandler;
import com.example.tasks.handler.ListTasksHandler;
import com.example.tasks.handler.UpdateTaskHandler;
import com.example.tasks.model.Task;
import com.example.tasks.repository.TaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * End-to-end integration test that runs the REAL Lambda handlers against a
 * REAL DynamoDB (DynamoDB Local running in Docker on http://localhost:8000).
 *
 * This is the closest thing to "running the application" without SAM CLI:
 *   - DynamoDB Local = the database (real, in Docker)
 *   - CreateTaskHandler/GetTaskHandler/... = the real application code
 *
 * It exercises the full CRUD flow, the status state machine, and the GSI
 * (StatusIndex) used for filtering by status.
 *
 * Prerequisite: DynamoDB Local must be running:
 *   docker run -p 8000:8000 -d amazon/dynamodb-local
 *
 * Run it explicitly (not part of the default `mvn test`):
 *   mvn test -Dtest=TaskManagementIT
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TaskManagementIT {

    private static final String TABLE_NAME = "TasksTable";
    private static final String ENDPOINT = "http://localhost:8000";
    private static final Region REGION = Region.EU_WEST_1;

    private static DynamoDbClient client;
    private static TaskRepository repository;

    private CreateTaskHandler createHandler;
    private GetTaskHandler getHandler;
    private ListTasksHandler listHandler;
    private UpdateTaskHandler updateHandler;
    private DeleteTaskHandler deleteHandler;

    private ObjectMapper objectMapper;
    private Context context;

    @BeforeAll
    static void setUpDatabase() {
        // Connect to DynamoDB Local with dummy credentials (local doesn't validate them).
        client = DynamoDbClient.builder()
                .endpointOverride(URI.create(ENDPOINT))
                .region(REGION)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("dummy", "dummy")))
                .build();

        // Skip the test suite (don't fail) if DynamoDB Local isn't reachable.
        assumeTrue(isDynamoDbLocalAvailable(), "DynamoDB Local is not running on " + ENDPOINT
                + ". Start it with: docker run -p 8000:8000 -d amazon/dynamodb-local");

        createTable();

        // Build the Enhanced Client table wrapper and inject it into the real repository.
        DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(client)
                .build();
        DynamoDbTable<Task> taskTable = enhancedClient.table(TABLE_NAME, TableSchema.fromBean(Task.class));
        repository = new TaskRepository(taskTable);
    }

    @AfterAll
    static void tearDownDatabase() {
        if (client != null) {
            try {
                client.deleteTable(DeleteTableRequest.builder().tableName(TABLE_NAME).build());
            } catch (Exception ignored) {
                // Table might already be gone.
            }
            client.close();
        }
    }

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        context = new TestContext();

        // Wire the real handlers to the real repository (pointing at DynamoDB Local).
        createHandler = new CreateTaskHandler(repository);
        getHandler = new GetTaskHandler(repository);
        listHandler = new ListTasksHandler(repository);
        updateHandler = new UpdateTaskHandler(repository);
        deleteHandler = new DeleteTaskHandler(repository);
    }

    private static boolean isDynamoDbLocalAvailable() {
        try {
            client.listTables();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void createTable() {
        client.createTable(CreateTableRequest.builder()
                .tableName(TABLE_NAME)
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .attributeDefinitions(
                        AttributeDefinition.builder().attributeName("id").attributeType(ScalarAttributeType.S).build(),
                        AttributeDefinition.builder().attributeName("status").attributeType(ScalarAttributeType.S).build(),
                        AttributeDefinition.builder().attributeName("createdAt").attributeType(ScalarAttributeType.S).build())
                .keySchema(KeySchemaElement.builder().attributeName("id").keyType(KeyType.HASH).build())
                .globalSecondaryIndexes(GlobalSecondaryIndex.builder()
                        .indexName("StatusIndex")
                        .keySchema(
                                KeySchemaElement.builder().attributeName("status").keyType(KeyType.HASH).build(),
                                KeySchemaElement.builder().attributeName("createdAt").keyType(KeyType.RANGE).build())
                        .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                        .build())
                .build());

        // Wait until the table (and its GSI) are ACTIVE before using them.
        waitForTableActive();
    }

    private static void waitForTableActive() {
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(30).toMillis();
        while (System.currentTimeMillis() < deadline) {
            var table = client.describeTable(
                    DescribeTableRequest.builder().tableName(TABLE_NAME).build()).table();
            boolean tableActive = "ACTIVE".equals(table.tableStatusAsString());
            boolean gsiActive = true;
            if (table.hasGlobalSecondaryIndexes()) {
                for (var gsi : table.globalSecondaryIndexes()) {
                    if (!"ACTIVE".equals(gsi.indexStatusAsString())) {
                        gsiActive = false;
                    }
                }
            }
            if (tableActive && gsiActive) {
                return;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        throw new IllegalStateException("DynamoDB table/GSI did not become ACTIVE in time");
    }

    @Test
    @Order(1)
    void fullCrudFlow() throws Exception {
        // 1. CREATE
        APIGatewayProxyRequestEvent createRequest = new APIGatewayProxyRequestEvent()
                .withBody("{\"title\":\"Integration test task\",\"description\":\"Created by TaskManagementIT\"}");
        APIGatewayProxyResponseEvent createResponse = createHandler.handleRequest(createRequest, context);
        assertEquals(201, createResponse.getStatusCode(), "Create should return 201");
        Task created = objectMapper.readValue(createResponse.getBody(), Task.class);
        assertNotNull(created.getId(), "Created task must have an id");
        assertEquals("TODO", created.getStatus(), "New task must default to TODO");
        String id = created.getId();

        // 2. GET by id
        APIGatewayProxyRequestEvent getRequest = new APIGatewayProxyRequestEvent()
                .withPathParameters(Map.of("id", id));
        APIGatewayProxyResponseEvent getResponse = getHandler.handleRequest(getRequest, context);
        assertEquals(200, getResponse.getStatusCode(), "Get should return 200");
        Task fetched = objectMapper.readValue(getResponse.getBody(), Task.class);
        assertEquals("Integration test task", fetched.getTitle());

        // 3. LIST (all)
        APIGatewayProxyResponseEvent listResponse = listHandler.handleRequest(new APIGatewayProxyRequestEvent(), context);
        assertEquals(200, listResponse.getStatusCode());
        Task[] all = objectMapper.readValue(listResponse.getBody(), Task[].class);
        assertTrue(all.length >= 1, "List should contain at least the created task");

        // 4. UPDATE status: TODO -> IN_PROGRESS (valid transition)
        APIGatewayProxyRequestEvent updateRequest = new APIGatewayProxyRequestEvent()
                .withPathParameters(Map.of("id", id))
                .withBody("{\"status\":\"IN_PROGRESS\"}");
        APIGatewayProxyResponseEvent updateResponse = updateHandler.handleRequest(updateRequest, context);
        assertEquals(200, updateResponse.getStatusCode(), "Valid status transition should return 200");
        Task updated = objectMapper.readValue(updateResponse.getBody(), Task.class);
        assertEquals("IN_PROGRESS", updated.getStatus());

        // 5. LIST by status (exercises the StatusIndex GSI)
        APIGatewayProxyRequestEvent listByStatusRequest = new APIGatewayProxyRequestEvent()
                .withQueryStringParameters(Map.of("status", "IN_PROGRESS"));
        APIGatewayProxyResponseEvent listByStatusResponse = listHandler.handleRequest(listByStatusRequest, context);
        assertEquals(200, listByStatusResponse.getStatusCode());
        Task[] inProgress = objectMapper.readValue(listByStatusResponse.getBody(), Task[].class);
        assertTrue(inProgress.length >= 1, "GSI query should return the IN_PROGRESS task");

        // 6. DELETE
        APIGatewayProxyRequestEvent deleteRequest = new APIGatewayProxyRequestEvent()
                .withPathParameters(Map.of("id", id));
        APIGatewayProxyResponseEvent deleteResponse = deleteHandler.handleRequest(deleteRequest, context);
        assertEquals(204, deleteResponse.getStatusCode(), "Delete should return 204");

        // 7. GET after delete -> 404
        APIGatewayProxyResponseEvent getAfterDelete = getHandler.handleRequest(getRequest, context);
        assertEquals(404, getAfterDelete.getStatusCode(), "Get after delete should return 404");
    }

    @Test
    @Order(2)
    void updateStatusConflict() throws Exception {
        // Create a task, move it to DONE, then attempt an invalid DONE -> TODO transition.
        APIGatewayProxyResponseEvent createResponse = createHandler.handleRequest(
                new APIGatewayProxyRequestEvent().withBody("{\"title\":\"Conflict task\"}"), context);
        String id = objectMapper.readValue(createResponse.getBody(), Task.class).getId();

        // TODO -> IN_PROGRESS -> DONE (both valid)
        updateHandler.handleRequest(new APIGatewayProxyRequestEvent()
                .withPathParameters(Map.of("id", id)).withBody("{\"status\":\"IN_PROGRESS\"}"), context);
        updateHandler.handleRequest(new APIGatewayProxyRequestEvent()
                .withPathParameters(Map.of("id", id)).withBody("{\"status\":\"DONE\"}"), context);

        // DONE -> TODO is invalid -> 409 CONFLICT
        APIGatewayProxyResponseEvent conflictResponse = updateHandler.handleRequest(
                new APIGatewayProxyRequestEvent()
                        .withPathParameters(Map.of("id", id))
                        .withBody("{\"status\":\"TODO\"}"), context);
        assertEquals(409, conflictResponse.getStatusCode(), "DONE -> TODO must return 409");
        assertTrue(conflictResponse.getBody().contains("CONFLICT"));
    }

    /**
     * Minimal in-memory {@link Context} stub — avoids pulling Mockito into the
     * integration test just for logging.
     */
    private static final class TestContext implements Context {
        @Override public String getAwsRequestId() { return "it-request"; }
        @Override public String getLogGroupName() { return "/aws/lambda/TaskManagementIT"; }
        @Override public String getLogStreamName() { return "it-stream"; }
        @Override public String getFunctionName() { return "TaskManagementIT"; }
        @Override public String getFunctionVersion() { return "$LATEST"; }
        @Override public String getInvokedFunctionArn() { return "arn:aws:lambda:eu-west-1:000000000000:function:TaskManagementIT"; }
        @Override public com.amazonaws.services.lambda.runtime.CognitoIdentity getIdentity() { return null; }
        @Override public com.amazonaws.services.lambda.runtime.ClientContext getClientContext() { return null; }
        @Override public int getRemainingTimeInMillis() { return 30000; }
        @Override public int getMemoryLimitInMB() { return 512; }
        @Override public LambdaLogger getLogger() {
            return new LambdaLogger() {
                @Override public void log(String message) {
                    try {
                        java.nio.file.Files.writeString(
                                java.nio.file.Path.of("it-handler-log.txt"),
                                message + System.lineSeparator(),
                                java.nio.file.StandardOpenOption.CREATE,
                                java.nio.file.StandardOpenOption.APPEND);
                    } catch (java.io.IOException ignored) { }
                }
                @Override public void log(byte[] message) { log(new String(message)); }
            };
        }
    }
}

