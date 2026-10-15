package dev.personalagenda.model;

import java.util.UUID;

public class Task {

    private final String id;
    private final String title;
    private final int estimatedMinutes;
    private boolean completed;

    public Task(String title, int estimatedMinutes) {
        this.id = UUID.randomUUID().toString();
        this.title = title;
        this.estimatedMinutes = estimatedMinutes;
        this.completed = false;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public int getEstimatedMinutes() {
        return estimatedMinutes;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }
}