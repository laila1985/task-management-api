package com.example.tasks.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.example.tasks.model.Task;
import com.example.tasks.repository.TaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdateTaskHandlerTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private Context context;

    private UpdateTaskHandler handler;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        handler = new UpdateTaskHandler(taskRepository);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        when(context.getLogger()).thenReturn(new com.amazonaws.services.lambda.runtime.LambdaLogger() {
            @Override public void log(String message) {}
            @Override public void log(byte[] message) {}
        });
    }

    @Test
    void shouldUpdateTitleSuccessfully() throws Exception {
        // Arrange
        Task existingTask = new Task("task-123", "Original Title", "Description", "TODO",
                Instant.parse("2024-01-15T10:30:00Z"), Instant.parse("2024-01-15T10:30:00Z"));
        when(taskRepository.findById("task-123")).thenReturn(existingTask);
        when(taskRepository.update(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String requestBody = "{\"title\":\"Updated Title\"}";
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setBody(requestBody);
        request.setPathParameters(Map.of("id", "task-123"));

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(200, response.getStatusCode());
        Task updatedTask = objectMapper.readValue(response.getBody(), Task.class);
        assertEquals("Updated Title", updatedTask.getTitle());
        assertEquals("Description", updatedTask.getDescription());
        assertEquals("TODO", updatedTask.getStatus());
        verify(taskRepository, times(1)).update(any(Task.class));
    }

    @Test
    void shouldUpdateStatusFromTodoToInProgress() throws Exception {
        // Arrange
        Task existingTask = new Task("task-123", "Title", "Description", "TODO",
                Instant.parse("2024-01-15T10:30:00Z"), Instant.parse("2024-01-15T10:30:00Z"));
        when(taskRepository.findById("task-123")).thenReturn(existingTask);
        when(taskRepository.update(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String requestBody = "{\"status\":\"IN_PROGRESS\"}";
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setBody(requestBody);
        request.setPathParameters(Map.of("id", "task-123"));

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(200, response.getStatusCode());
        Task updatedTask = objectMapper.readValue(response.getBody(), Task.class);
        assertEquals("IN_PROGRESS", updatedTask.getStatus());
    }

    @Test
    void shouldUpdateStatusFromInProgressToDone() throws Exception {
        // Arrange
        Task existingTask = new Task("task-123", "Title", "Description", "IN_PROGRESS",
                Instant.parse("2024-01-15T10:30:00Z"), Instant.parse("2024-01-15T10:30:00Z"));
        when(taskRepository.findById("task-123")).thenReturn(existingTask);
        when(taskRepository.update(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String requestBody = "{\"status\":\"DONE\"}";
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setBody(requestBody);
        request.setPathParameters(Map.of("id", "task-123"));

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(200, response.getStatusCode());
        Task updatedTask = objectMapper.readValue(response.getBody(), Task.class);
        assertEquals("DONE", updatedTask.getStatus());
    }

    @Test
    void shouldReturn409WhenInvalidTransitionFromTodoToDone() {
        // Arrange
        Task existingTask = new Task("task-123", "Title", "Description", "TODO",
                Instant.parse("2024-01-15T10:30:00Z"), Instant.parse("2024-01-15T10:30:00Z"));
        when(taskRepository.findById("task-123")).thenReturn(existingTask);

        String requestBody = "{\"status\":\"DONE\"}";
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setBody(requestBody);
        request.setPathParameters(Map.of("id", "task-123"));

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(409, response.getStatusCode());
        assertTrue(response.getBody().contains("CONFLICT"));
        verify(taskRepository, never()).update(any());
    }

    @Test
    void shouldReturn409WhenTransitionFromDone() {
        // Arrange
        Task existingTask = new Task("task-123", "Title", "Description", "DONE",
                Instant.parse("2024-01-15T10:30:00Z"), Instant.parse("2024-01-15T10:30:00Z"));
        when(taskRepository.findById("task-123")).thenReturn(existingTask);

        String requestBody = "{\"status\":\"TODO\"}";
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setBody(requestBody);
        request.setPathParameters(Map.of("id", "task-123"));

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(409, response.getStatusCode());
        assertTrue(response.getBody().contains("CONFLICT"));
    }

    @Test
    void shouldReturn404WhenTaskNotFound() {
        // Arrange
        when(taskRepository.findById("non-existent")).thenReturn(null);

        String requestBody = "{\"title\":\"Updated\"}";
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setBody(requestBody);
        request.setPathParameters(Map.of("id", "non-existent"));

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(404, response.getStatusCode());
        assertTrue(response.getBody().contains("NOT_FOUND"));
    }

    @Test
    void shouldReturn400WhenNoFieldsProvided() {
        // Arrange
        String requestBody = "{}";
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setBody(requestBody);
        request.setPathParameters(Map.of("id", "task-123"));

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("At least one field"));
    }

    @Test
    void shouldReturn400WhenInvalidStatusValue() {
        // Arrange - no need to mock findById since invalid status is caught before DB lookup
        String requestBody = "{\"status\":\"INVALID\"}";
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setBody(requestBody);
        request.setPathParameters(Map.of("id", "task-123"));

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Invalid status"));
    }

    @Test
    void shouldReturn400WhenIdIsMissing() {
        // Arrange
        String requestBody = "{\"title\":\"Updated\"}";
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setBody(requestBody);
        request.setPathParameters(null);

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Task ID is required"));
    }

    @Test
    void shouldAllowSameStatusTransition() throws Exception {
        // Arrange - setting same status should be allowed (idempotent)
        Task existingTask = new Task("task-123", "Title", "Description", "TODO",
                Instant.parse("2024-01-15T10:30:00Z"), Instant.parse("2024-01-15T10:30:00Z"));
        when(taskRepository.findById("task-123")).thenReturn(existingTask);
        when(taskRepository.update(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String requestBody = "{\"status\":\"TODO\"}";
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setBody(requestBody);
        request.setPathParameters(Map.of("id", "task-123"));

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(200, response.getStatusCode());
    }
}