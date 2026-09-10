package com.example.tasks.repository;

import com.example.tasks.model.Task;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Repository for Task CRUD operations using DynamoDB Enhanced Client.
 *
 * Uses the Enhanced Client for type-safe mapping between Java objects
 * and DynamoDB items via the @DynamoDbBean annotations on Task.
 *
 * Environment Variables:
 *   TABLE_NAME    - DynamoDB table name (injected by SAM)
 *   AWS_ENDPOINT   - Optional: DynamoDB Local endpoint for local testing
 *   AWS_REGION     - Optional: AWS region (defaults to eu-west-1)
 */
public class TaskRepository {

    private final DynamoDbTable<Task> taskTable;

    /**
     * Default constructor - initializes DynamoDB client from environment.
     * Uses TABLE_NAME environment variable (injected by SAM via template.yaml).
     */
    public TaskRepository() {
        String tableName = System.getenv("TABLE_NAME");
        String endpointOverride = System.getenv("AWS_ENDPOINT");
        String region = System.getenv().getOrDefault("AWS_REGION", "eu-west-1");

        DynamoDbClientBuilder clientBuilder = DynamoDbClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create());

        if (endpointOverride != null && !endpointOverride.isEmpty()) {
            // For local testing with DynamoDB Local
            clientBuilder.endpointOverride(java.net.URI.create(endpointOverride));
        }

        DynamoDbClient dynamoDbClient = clientBuilder.build();

        DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();

        this.taskTable = enhancedClient.table(tableName, TableSchema.fromBean(Task.class));
    }

    /**
     * Constructor for testing - accepts a pre-configured DynamoDbTable.
     */
    public TaskRepository(DynamoDbTable<Task> taskTable) {
        this.taskTable = taskTable;
    }

    /**
     * Save a new task to DynamoDB.
     * Uses PutItem which will overwrite if the ID already exists.
     *
     * @param task the task to save
     * @return the saved task (with auto-generated ID if applicable)
     */
    public Task save(Task task) {
        taskTable.putItem(task);
        return task;
    }

    /**
     * Find a task by its ID.
     *
     * @param id the task ID (partition key)
     * @return the task, or null if not found
     */
    public Task findById(String id) {
        Key key = Key.builder().partitionValue(id).build();
        return taskTable.getItem(key);
    }

    /**
     * List all tasks from DynamoDB.
     * Uses Scan operation - note: for large tables, pagination should be added.
     *
     * @return list of all tasks
     */
    public List<Task> findAll() {
        List<Task> tasks = new ArrayList<>();
        taskTable.scan().stream().forEach(page -> tasks.addAll(page.items()));
        return tasks;
    }

    /**
     * Find tasks by status using the StatusIndex GSI.
     *
     * @param status the status to filter by (TODO, IN_PROGRESS, DONE)
     * @return list of tasks matching the given status
     */
    public List<Task> findByStatus(String status) {
        List<Task> tasks = new ArrayList<>();
        QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(status).build()
        );
        taskTable.index("StatusIndex").query(queryConditional)
                .stream()
                .forEach(page -> tasks.addAll(page.items()));
        return tasks;
    }

    /**
     * Update an existing task in DynamoDB.
     * Uses PutItem which will overwrite the entire item.
     *
     * @param task the task with updated fields
     * @return the updated task
     */
    public Task update(Task task) {
        taskTable.putItem(task);
        return task;
    }

    /**
     * Delete a task by its ID.
     *
     * @param id the task ID to delete
     */
    public void delete(String id) {
        Key key = Key.builder().partitionValue(id).build();
        taskTable.deleteItem(key);
    }
}