package com.example.tasks.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.example.tasks.model.Task;
import com.example.tasks.repository.TaskRepository;
import com.example.tasks.util.ApiGatewayResponse;

import java.util.List;
import java.util.Set;

/**
 * Lambda handler for GET /tasks.
 *
 * Lists all tasks, optionally filtered by status query parameter.
 * - GET /tasks          → returns all tasks
 * - GET /tasks?status=TODO → returns only tasks with status TODO
 *
 * IAM Permissions: dynamodb:Scan, dynamodb:Query on TasksTable and StatusIndex
 */
public class ListTasksHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Set<String> VALID_STATUSES = Set.of("TODO", "IN_PROGRESS", "DONE");

    private final TaskRepository taskRepository;

    public ListTasksHandler() {
        this.taskRepository = new TaskRepository();
    }

    public ListTasksHandler(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        context.getLogger().log("ListTasksHandler invoked");

        try {
            // Check for status query parameter
            String statusFilter = null;
            if (input.getQueryStringParameters() != null) {
                statusFilter = input.getQueryStringParameters().get("status");
            }

            List<Task> tasks;

            if (statusFilter != null && !statusFilter.isBlank()) {
                // Validate status value
                if (!VALID_STATUSES.contains(statusFilter)) {
                    return ApiGatewayResponse.error(400, "BAD_REQUEST",
                            "Invalid status filter: '" + statusFilter + "'. Must be one of: TODO, IN_PROGRESS, DONE");
                }
                // Query by status using GSI
                tasks = taskRepository.findByStatus(statusFilter);
                context.getLogger().log("Listed tasks with status filter: " + statusFilter + ", count: " + tasks.size());
            } else {
                // Return all tasks (scan)
                tasks = taskRepository.findAll();
                context.getLogger().log("Listed all tasks, count: " + tasks.size());
            }

            return ApiGatewayResponse.success(200, tasks);

        } catch (Exception e) {
            context.getLogger().log("Error listing tasks: " + e.getMessage());
            return ApiGatewayResponse.error(500, "INTERNAL_ERROR", "An unexpected error occurred");
        }
    }
}