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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListTasksHandlerTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private Context context;

    private ListTasksHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ListTasksHandler(taskRepository);
        when(context.getLogger()).thenReturn(new com.amazonaws.services.lambda.runtime.LambdaLogger() {
            @Override public void log(String message) {}
            @Override public void log(byte[] message) {}
        });
    }

    @Test
    void shouldReturnAllTasks() {
        // Arrange
        List<Task> tasks = Arrays.asList(
                new Task("task-1", "Task 1", "Desc 1", "TODO",
                        Instant.parse("2024-01-15T10:30:00Z"), Instant.parse("2024-01-15T10:30:00Z")),
                new Task("task-2", "Task 2", "Desc 2", "IN_PROGRESS",
                        Instant.parse("2024-01-16T10:30:00Z"), Instant.parse("2024-01-16T10:30:00Z"))
        );
        when(taskRepository.findAll()).thenReturn(tasks);

        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setQueryStringParameters(null);

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains("task-1"));
        assertTrue(response.getBody().contains("task-2"));
        verify(taskRepository, times(1)).findAll();
        verify(taskRepository, never()).findByStatus(any());
    }

    @Test
    void shouldReturnEmptyListWhenNoTasks() {
        // Arrange
        when(taskRepository.findAll()).thenReturn(Collections.emptyList());

        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setQueryStringParameters(null);

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains("[]"));
    }

    @Test
    void shouldFilterByStatus() {
        // Arrange
        List<Task> todoTasks = List.of(
                new Task("task-1", "Task 1", "Desc 1", "TODO",
                        Instant.parse("2024-01-15T10:30:00Z"), Instant.parse("2024-01-15T10:30:00Z"))
        );
        when(taskRepository.findByStatus("TODO")).thenReturn(todoTasks);

        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setQueryStringParameters(java.util.Map.of("status", "TODO"));

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains("TODO"));
        verify(taskRepository, times(1)).findByStatus("TODO");
        verify(taskRepository, never()).findAll();
    }

    @Test
    void shouldReturn400ForInvalidStatusFilter() {
        // Arrange
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setQueryStringParameters(java.util.Map.of("status", "INVALID"));

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Invalid status filter"));
        verify(taskRepository, never()).findAll();
        verify(taskRepository, never()).findByStatus(any());
    }

    @Test
    void shouldReturn500WhenRepositoryThrowsException() {
        // Arrange
        when(taskRepository.findAll()).thenThrow(new RuntimeException("DynamoDB error"));

        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setQueryStringParameters(null);

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(500, response.getStatusCode());
        assertTrue(response.getBody().contains("INTERNAL_ERROR"));
    }

    @Test
    void shouldFilterByInProgressStatus() {
        // Arrange
        List<Task> inProgressTasks = List.of(
                new Task("task-2", "Task 2", "Desc 2", "IN_PROGRESS",
                        Instant.parse("2024-01-16T10:30:00Z"), Instant.parse("2024-01-16T10:30:00Z"))
        );
        when(taskRepository.findByStatus("IN_PROGRESS")).thenReturn(inProgressTasks);

        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setQueryStringParameters(java.util.Map.of("status", "IN_PROGRESS"));

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains("IN_PROGRESS"));
    }

    @Test
    void shouldFilterByDoneStatus() {
        // Arrange
        List<Task> doneTasks = List.of(
                new Task("task-3", "Task 3", "Desc 3", "DONE",
                        Instant.parse("2024-01-17T10:30:00Z"), Instant.parse("2024-01-17T10:30:00Z"))
        );
        when(taskRepository.findByStatus("DONE")).thenReturn(doneTasks);

        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setQueryStringParameters(java.util.Map.of("status", "DONE"));

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains("DONE"));
    }
}