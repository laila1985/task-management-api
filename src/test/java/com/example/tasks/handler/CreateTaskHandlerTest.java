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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateTaskHandlerTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private Context context;

    private CreateTaskHandler handler;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        handler = new CreateTaskHandler(taskRepository);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        when(context.getLogger()).thenReturn(new com.amazonaws.services.lambda.runtime.LambdaLogger() {
            @Override public void log(String message) {}
            @Override public void log(byte[] message) {}
        });
    }

    @Test
    void shouldCreateTaskSuccessfully() throws Exception {
        // Arrange
        String requestBody = "{\"title\":\"My Task\",\"description\":\"Task description\"}";
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setBody(requestBody);

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(201, response.getStatusCode());
        assertNotNull(response.getBody());

        Task createdTask = objectMapper.readValue(response.getBody(), Task.class);
        assertEquals("My Task", createdTask.getTitle());
        assertEquals("Task description", createdTask.getDescription());
        assertEquals("TODO", createdTask.getStatus());
        assertNotNull(createdTask.getId());
        assertNotNull(createdTask.getCreatedAt());
        assertNotNull(createdTask.getUpdatedAt());

        verify(taskRepository, times(1)).save(any(Task.class));
    }

    @Test
    void shouldReturn400WhenBodyIsNull() {
        // Arrange
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setBody(null);

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("BAD_REQUEST"));
        verify(taskRepository, never()).save(any());
    }

    @Test
    void shouldReturn400WhenBodyIsEmpty() {
        // Arrange
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setBody("");

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("BAD_REQUEST"));
    }

    @Test
    void shouldReturn400WhenTitleIsMissing() {
        // Arrange
        String requestBody = "{\"description\":\"No title task\"}";
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setBody(requestBody);

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Title is required"));
    }

    @Test
    void shouldReturn400WhenTitleIsBlank() {
        // Arrange
        String requestBody = "{\"title\":\"   \",\"description\":\"Blank title\"}";
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setBody(requestBody);

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Title is required"));
    }

    @Test
    void shouldReturn400WhenTitleExceedsMaxLength() {
        // Arrange
        String longTitle = "x".repeat(201);
        String requestBody = "{\"title\":\"" + longTitle + "\"}";
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setBody(requestBody);

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Title must not exceed"));
    }

    @Test
    void shouldReturn400WhenDescriptionExceedsMaxLength() {
        // Arrange
        String longDescription = "x".repeat(2001);
        String requestBody = "{\"title\":\"Valid Title\",\"description\":\"" + longDescription + "\"}";
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setBody(requestBody);

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Description must not exceed"));
    }

    @Test
    void shouldReturn400WhenBodyIsInvalidJson() {
        // Arrange
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setBody("not valid json");

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("not valid JSON"));
    }

    @Test
    void shouldCreateTaskWithNullDescription() throws Exception {
        // Arrange
        String requestBody = "{\"title\":\"Task without description\"}";
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setBody(requestBody);

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(201, response.getStatusCode());
        Task createdTask = objectMapper.readValue(response.getBody(), Task.class);
        assertEquals("Task without description", createdTask.getTitle());
        assertNull(createdTask.getDescription());
    }

    @Test
    void shouldReturn500WhenRepositoryThrowsException() {
        // Arrange
        String requestBody = "{\"title\":\"My Task\"}";
        when(taskRepository.save(any(Task.class))).thenThrow(new RuntimeException("DynamoDB error"));

        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setBody(requestBody);

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(500, response.getStatusCode());
        assertTrue(response.getBody().contains("INTERNAL_ERROR"));
    }

    @Test
    void shouldSetStatusToTodoOnCreation() throws Exception {
        // Arrange
        String requestBody = "{\"title\":\"New Task\"}";
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setBody(requestBody);

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        Task createdTask = objectMapper.readValue(response.getBody(), Task.class);
        assertEquals("TODO", createdTask.getStatus());
    }
}