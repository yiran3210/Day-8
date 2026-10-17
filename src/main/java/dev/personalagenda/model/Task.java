package dev.personalagenda.model;

import java.util.UUID;
import java.time.LocalDate;

public class Task {

    private final String id;
    private String title;
    private int estimatedMinutes;
    private boolean completed;
    private LocalDate plannedDate;

    public Task(String title, int estimatedMinutes) {
        this(UUID.randomUUID().toString(), title, estimatedMinutes, false);
    }

    public Task(String id, String title, int estimatedMinutes, boolean completed) {
        this.id = id;
        this.title = title;
        this.estimatedMinutes = estimatedMinutes;
        this.completed = completed;
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

    public void setTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Task title is required");
        }
        this.title = title.trim();
    }

    public void setEstimatedMinutes(int minutes) {
        if (minutes <= 0) {
            throw new IllegalArgumentException("Minutes must be positive");
        }
        this.estimatedMinutes = minutes;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public LocalDate getPlannedDate() {
        return plannedDate;
    }

    public void setPlannedDate(LocalDate plannedDate) {
        this.plannedDate = plannedDate;
    }

    @Override
    public String toString() {
        return getTitle();
    }
}