package com.example.tasks.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.Map;

/**
 * Utility class for building API Gateway proxy responses.
 *
 * Provides static methods to create success and error responses
 * with proper HTTP status codes, CORS headers, and JSON serialization.
 */
public class ApiGatewayResponse {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private static final Map<String, String> CORS_HEADERS = Map.of(
            "Content-Type", "application/json",
            "Access-Control-Allow-Origin", "*",
            "Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS",
            "Access-Control-Allow-Headers", "Content-Type,Authorization,X-Amz-Date,X-Api-Key,X-Amz-Security-Token"
    );

    private ApiGatewayResponse() {
        // Utility class - no instantiation
    }

    /**
     * Build a success response with a JSON body.
     *
     * @param statusCode HTTP status code (e.g., 200, 201)
     * @param body       the object to serialize as JSON
     * @return API Gateway proxy response event
     */
    public static com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent success(int statusCode, Object body) {
        try {
            return new com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent()
                    .withStatusCode(statusCode)
                    .withBody(OBJECT_MAPPER.writeValueAsString(body))
                    .withHeaders(CORS_HEADERS);
        } catch (JsonProcessingException e) {
            return error(500, "INTERNAL_ERROR", "Failed to serialize response: " + e.getMessage());
        }
    }

    /**
     * Build a success response with no body (e.g., for 204 No Content).
     *
     * @param statusCode HTTP status code (e.g., 204)
     * @return API Gateway proxy response event
     */
    public static com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent successNoBody(int statusCode) {
        return new com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(CORS_HEADERS);
    }

    /**
     * Build an error response with a structured JSON error body.
     *
     * @param statusCode HTTP status code (e.g., 400, 404, 409, 500)
     * @param errorCode  error code string (e.g., "BAD_REQUEST", "NOT_FOUND")
     * @param message    human-readable error description
     * @return API Gateway proxy response event
     */
    public static com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent error(int statusCode, String errorCode, String message) {
        Map<String, Object> errorBody = Map.of(
                "error", errorCode,
                "message", message,
                "statusCode", statusCode
        );
        try {
            return new com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent()
                    .withStatusCode(statusCode)
                    .withBody(OBJECT_MAPPER.writeValueAsString(errorBody))
                    .withHeaders(CORS_HEADERS);
        } catch (JsonProcessingException e) {
            // Fallback for serialization errors
            return new com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("{\"error\":\"INTERNAL_ERROR\",\"message\":\"Failed to serialize error response\"}")
                    .withHeaders(CORS_HEADERS);
        }
    }

    /**
     * Parse a JSON string into a Java object.
     *
     * @param json  the JSON string
     * @param clazz the target class
     * @param <T>   the target type
     * @return the deserialized object
     * @throws JsonProcessingException if parsing fails
     */
    public static <T> T parseBody(String json, Class<T> clazz) throws JsonProcessingException {
        return OBJECT_MAPPER.readValue(json, clazz);
    }
}