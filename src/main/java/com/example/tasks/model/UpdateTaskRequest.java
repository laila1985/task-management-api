package com.example.tasks.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for PUT /tasks/{id}.
 *
 * Fields:
 *   title       - Optional, max 200 characters
 *   description - Optional, max 2000 characters (pass null to clear)
 *   status      - Optional, must follow state machine: TODO → IN_PROGRESS → DONE
 */
public class UpdateTaskRequest {

    @JsonProperty("title")
    private String title;

    @JsonProperty("description")
    private String description;

    @JsonProperty("status")
    private String status;

    public UpdateTaskRequest() {
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

    /**
     * Check if at least one field is provided for update.
     */
    public boolean hasUpdates() {
        return title != null || description != null || status != null;
    }
}