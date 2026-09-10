package com.example.tasks.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.time.Instant;

/**
 * Represents a Task entity stored in DynamoDB.
 *
 * DynamoDB Table Schema:
 *   PK: id (String, UUID)
 *   GSI (StatusIndex): PK=status, SK=createdAt
 *
 * Fields:
 *   id          - Auto-generated UUID (partition key)
 *   title       - Required, max 200 characters
 *   description - Optional, max 2000 characters
 *   status      - TODO | IN_PROGRESS | DONE (follows state machine)
 *   createdAt   - ISO 8601 timestamp, set on creation
 *   updatedAt   - ISO 8601 timestamp, set on creation and updates
 */
@DynamoDbBean
public class Task {

    private String id;
    private String title;
    private String description;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;

    public Task() {
    }

    public Task(String id, String title, String description, String status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    @DynamoDbPartitionKey
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "Task{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", description='" + description + '\'' +
                ", status='" + status + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}