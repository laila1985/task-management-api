package com.example.tasks.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.example.tasks.model.CreateTaskRequest;
import com.example.tasks.model.Task;
import com.example.tasks.repository.TaskRepository;
import com.example.tasks.util.ApiGatewayResponse;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.time.Instant;
import java.util.UUID;

/**
 * Lambda handler for POST /tasks.
 *
 * Creates a new task with auto-generated ID, status=TODO, and timestamps.
 * Validates that title is present and within length limits.
 *
 * IAM Permission: dynamodb:PutItem on TasksTable
 */
public class CreateTaskHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_DESCRIPTION_LENGTH = 2000;

    private final TaskRepository taskRepository;

    /**
     * Default constructor - initializes TaskRepository with DynamoDB client.
     */
    public CreateTaskHandler() {
        this.taskRepository = new TaskRepository();
    }

    /**
     * Constructor for testing - accepts a TaskRepository mock.
     */
    public CreateTaskHandler(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        context.getLogger().log("CreateTaskHandler invoked");

        try {
            // Validate request body
            if (input.getBody() == null || input.getBody().isBlank()) {
                return ApiGatewayResponse.error(400, "BAD_REQUEST", "Request body is required");
            }

            // Parse request body
            CreateTaskRequest request;
            try {
                request = ApiGatewayResponse.parseBody(input.getBody(), CreateTaskRequest.class);
            } catch (JsonProcessingException e) {
                return ApiGatewayResponse.error(400, "BAD_REQUEST", "Request body is not valid JSON");
            }

            // Validate title
            if (request.getTitle() == null || request.getTitle().isBlank()) {
                return ApiGatewayResponse.error(400, "BAD_REQUEST", "Title is required and must not be blank");
            }
            if (request.getTitle().length() > MAX_TITLE_LENGTH) {
                return ApiGatewayResponse.error(400, "BAD_REQUEST",
                        "Title must not exceed " + MAX_TITLE_LENGTH + " characters");
            }

            // Validate description (if provided)
            if (request.getDescription() != null && request.getDescription().length() > MAX_DESCRIPTION_LENGTH) {
                return ApiGatewayResponse.error(400, "BAD_REQUEST",
                        "Description must not exceed " + MAX_DESCRIPTION_LENGTH + " characters");
            }

            // Create task
            Instant now = Instant.now();
            Task task = new Task();
            task.setId(UUID.randomUUID().toString());
            task.setTitle(request.getTitle());
            task.setDescription(request.getDescription());
            task.setStatus("TODO");
            task.setCreatedAt(now);
            task.setUpdatedAt(now);

            // Save to DynamoDB
            Task savedTask = taskRepository.save(task);

            context.getLogger().log("Task created with id: " + savedTask.getId());

            return ApiGatewayResponse.success(201, savedTask);

        } catch (Exception e) {
            context.getLogger().log("Error creating task: " + e.getMessage());
            return ApiGatewayResponse.error(500, "INTERNAL_ERROR", "An unexpected error occurred");
        }
    }
}