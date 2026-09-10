package com.example.tasks.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.example.tasks.model.Task;
import com.example.tasks.repository.TaskRepository;
import com.example.tasks.util.ApiGatewayResponse;

/**
 * Lambda handler for GET /tasks/{id}.
 *
 * Retrieves a single task by its ID from DynamoDB.
 * Returns 404 if the task is not found.
 *
 * IAM Permission: dynamodb:GetItem on TasksTable
 */
public class GetTaskHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final TaskRepository taskRepository;

    public GetTaskHandler() {
        this.taskRepository = new TaskRepository();
    }

    public GetTaskHandler(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        context.getLogger().log("GetTaskHandler invoked");

        try {
            // Extract task ID from path parameters
            String taskId = input.getPathParameters() != null ? input.getPathParameters().get("id") : null;

            if (taskId == null || taskId.isBlank()) {
                return ApiGatewayResponse.error(400, "BAD_REQUEST", "Task ID is required");
            }

            // Fetch task from DynamoDB
            Task task = taskRepository.findById(taskId);

            if (task == null) {
                return ApiGatewayResponse.error(404, "NOT_FOUND",
                        "Task with id '" + taskId + "' not found");
            }

            context.getLogger().log("Task retrieved with id: " + task.getId());

            return ApiGatewayResponse.success(200, task);

        } catch (Exception e) {
            context.getLogger().log("Error retrieving task: " + e.getMessage());
            return ApiGatewayResponse.error(500, "INTERNAL_ERROR", "An unexpected error occurred");
        }
    }
}