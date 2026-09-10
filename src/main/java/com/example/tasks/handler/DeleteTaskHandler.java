package com.example.tasks.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.example.tasks.model.Task;
import com.example.tasks.repository.TaskRepository;
import com.example.tasks.util.ApiGatewayResponse;

import java.util.Map;

/**
 * Lambda handler for DELETE /tasks/{id}.
 *
 * Deletes a task by its ID from DynamoDB.
 * Returns 404 if the task does not exist (check before delete).
 *
 * IAM Permission: dynamodb:GetItem, dynamodb:DeleteItem on TasksTable
 */
public class DeleteTaskHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final TaskRepository taskRepository;

    public DeleteTaskHandler() {
        this.taskRepository = new TaskRepository();
    }

    public DeleteTaskHandler(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        context.getLogger().log("DeleteTaskHandler invoked");

        try {
            // Extract task ID from path parameters
            String taskId = input.getPathParameters() != null ? input.getPathParameters().get("id") : null;

            if (taskId == null || taskId.isBlank()) {
                return ApiGatewayResponse.error(400, "BAD_REQUEST", "Task ID is required");
            }

            // Check if task exists before deleting
            Task existingTask = taskRepository.findById(taskId);
            if (existingTask == null) {
                return ApiGatewayResponse.error(404, "NOT_FOUND",
                        "Task with id '" + taskId + "' not found");
            }

            // Delete the task
            taskRepository.delete(taskId);

            context.getLogger().log("Task deleted with id: " + taskId);

            return ApiGatewayResponse.success(204, Map.of("message", "Task deleted successfully"));

        } catch (Exception e) {
            context.getLogger().log("Error deleting task: " + e.getMessage());
            return ApiGatewayResponse.error(500, "INTERNAL_ERROR", "An unexpected error occurred");
        }
    }
}