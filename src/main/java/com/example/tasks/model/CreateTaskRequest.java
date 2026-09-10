package com.example.tasks.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for POST /tasks.
 *
 * Fields:
 *   title       - Required, max 200 characters
 *   description - Optional, max 2000 characters
 */
public class CreateTaskRequest {

    @JsonProperty("title")
    private String title;

    @JsonProperty("description")
    private String description;

    public CreateTaskRequest() {
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
}