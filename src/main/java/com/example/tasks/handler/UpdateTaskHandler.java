package com.example.tasks.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.example.tasks.model.Task;
import com.example.tasks.model.UpdateTaskRequest;
import com.example.tasks.repository.TaskRepository;
import com.example.tasks.util.ApiGatewayResponse;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * Lambda handler for PUT /tasks/{id}.
 *
 * Updates an existing task. Supports partial updates - only provided fields are changed.
 * Enforces the status state machine: TODO → IN_PROGRESS → DONE
 *
 * IAM Permission: dynamodb:GetItem, dynamodb:PutItem on TasksTable
 */
public class UpdateTaskHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_DESCRIPTION_LENGTH = 2000;

    private static final Set<String> VALID_STATUSES = Set.of("TODO", "IN_PROGRESS", "DONE");

    // Status transition rules: from → allowed targets
    private static final Map<String, Set<String>> STATUS_TRANSITIONS = Map.of(
            "TODO", Set.of("IN_PROGRESS"),
            "IN_PROGRESS", Set.of("DONE")
    );

    private final TaskRepository taskRepository;

    public UpdateTaskHandler() {
        this.taskRepository = new TaskRepository();
    }

    public UpdateTaskHandler(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        context.getLogger().log("UpdateTaskHandler invoked");

        try {
            // Extract task ID from path parameters
            String taskId = input.getPathParameters() != null ? input.getPathParameters().get("id") : null;

            if (taskId == null || taskId.isBlank()) {
                return ApiGatewayResponse.error(400, "BAD_REQUEST", "Task ID is required");
            }

            // Validate request body
            if (input.getBody() == null || input.getBody().isBlank()) {
                return ApiGatewayResponse.error(400, "BAD_REQUEST", "Request body is required");
            }

            // Parse request body
            UpdateTaskRequest request;
            try {
                request = ApiGatewayResponse.parseBody(input.getBody(), UpdateTaskRequest.class);
            } catch (JsonProcessingException e) {
                return ApiGatewayResponse.error(400, "BAD_REQUEST", "Request body is not valid JSON");
            }

            // Check at least one field is provided
            if (!request.hasUpdates()) {
                return ApiGatewayResponse.error(400, "BAD_REQUEST",
                        "At least one field (title, description, or status) must be provided");
            }

            // Validate title (if provided)
            if (request.getTitle() != null) {
                if (request.getTitle().isBlank()) {
                    return ApiGatewayResponse.error(400, "BAD_REQUEST", "Title must not be blank");
                }
                if (request.getTitle().length() > MAX_TITLE_LENGTH) {
                    return ApiGatewayResponse.error(400, "BAD_REQUEST",
                            "Title must not exceed " + MAX_TITLE_LENGTH + " characters");
                }
            }

            // Validate description (if provided)
            if (request.getDescription() != null && request.getDescription().length() > MAX_DESCRIPTION_LENGTH) {
                return ApiGatewayResponse.error(400, "BAD_REQUEST",
                        "Description must not exceed " + MAX_DESCRIPTION_LENGTH + " characters");
            }

            // Validate status (if provided)
            if (request.getStatus() != null && !VALID_STATUSES.contains(request.getStatus())) {
                return ApiGatewayResponse.error(400, "BAD_REQUEST",
                        "Invalid status: '" + request.getStatus() + "'. Must be one of: TODO, IN_PROGRESS, DONE");
            }

            // Fetch existing task
            Task existingTask = taskRepository.findById(taskId);
            if (existingTask == null) {
                return ApiGatewayResponse.error(404, "NOT_FOUND",
                        "Task with id '" + taskId + "' not found");
            }

            // Validate status transition (if status is being changed)
            if (request.getStatus() != null && !request.getStatus().equals(existingTask.getStatus())) {
                Set<String> allowedTransitions = STATUS_TRANSITIONS.get(existingTask.getStatus());
                if (allowedTransitions == null || !allowedTransitions.contains(request.getStatus())) {
                    return ApiGatewayResponse.error(409, "CONFLICT",
                            "Cannot transition task status from '" + existingTask.getStatus()
                                    + "' to '" + request.getStatus()
                                    + "'. Allowed transitions: TODO → IN_PROGRESS → DONE");
                }
            }

            // Apply updates
            if (request.getTitle() != null) {
                existingTask.setTitle(request.getTitle());
            }
            if (request.getDescription() != null) {
                existingTask.setDescription(request.getDescription());
            }
            if (request.getStatus() != null) {
                existingTask.setStatus(request.getStatus());
            }
            existingTask.setUpdatedAt(Instant.now());

            // Save updated task
            Task updatedTask = taskRepository.update(existingTask);

            context.getLogger().log("Task updated with id: " + updatedTask.getId());

            return ApiGatewayResponse.success(200, updatedTask);

        } catch (Exception e) {
            context.getLogger().log("Error updating task: " + e.getMessage());
            return ApiGatewayResponse.error(500, "INTERNAL_ERROR", "An unexpected error occurred");
        }
    }
}