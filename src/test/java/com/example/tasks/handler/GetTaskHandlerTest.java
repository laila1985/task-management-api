package com.example.tasks.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.example.tasks.model.Task;
import com.example.tasks.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetTaskHandlerTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private Context context;

    private GetTaskHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetTaskHandler(taskRepository);
        when(context.getLogger()).thenReturn(new com.amazonaws.services.lambda.runtime.LambdaLogger() {
            @Override public void log(String message) {}
            @Override public void log(byte[] message) {}
        });
    }

    @Test
    void shouldReturnTaskWhenFound() {
        // Arrange
        Task existingTask = new Task("task-123", "My Task", "Description", "TODO",
                Instant.parse("2024-01-15T10:30:00Z"), Instant.parse("2024-01-15T10:30:00Z"));
        when(taskRepository.findById("task-123")).thenReturn(existingTask);

        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPathParameters(Map.of("id", "task-123"));

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains("My Task"));
        assertTrue(response.getBody().contains("task-123"));
        verify(taskRepository, times(1)).findById("task-123");
    }

    @Test
    void shouldReturn404WhenTaskNotFound() {
        // Arrange
        when(taskRepository.findById("non-existent")).thenReturn(null);

        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPathParameters(Map.of("id", "non-existent"));

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(404, response.getStatusCode());
        assertTrue(response.getBody().contains("NOT_FOUND"));
    }

    @Test
    void shouldReturn400WhenIdIsMissing() {
        // Arrange
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPathParameters(null);

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("BAD_REQUEST"));
        verify(taskRepository, never()).findById(any());
    }

    @Test
    void shouldReturn400WhenIdIsBlank() {
        // Arrange
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPathParameters(Map.of("id", "  "));

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("BAD_REQUEST"));
    }

    @Test
    void shouldReturn500WhenRepositoryThrowsException() {
        // Arrange
        when(taskRepository.findById("task-123")).thenThrow(new RuntimeException("DynamoDB error"));

        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPathParameters(Map.of("id", "task-123"));

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(500, response.getStatusCode());
        assertTrue(response.getBody().contains("INTERNAL_ERROR"));
    }
}