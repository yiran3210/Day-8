package dev.personalagenda.model;

import java.time.LocalDateTime;
import java.util.UUID;

public record TimeBlock(
        String id,
        String title,
        LocalDateTime start,
        LocalDateTime end,
        Type type,
        String taskId,
        String stepId) {

    public enum Type {
        TASK, FIXED, REST, FREE
    }

    public TimeBlock {
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException(
                    "End must be after start");
        }
    }

    // Compatible with existing saved blocks and old callers
    public TimeBlock(
            String id, String title,
            LocalDateTime start, LocalDateTime end,
            Type type, String taskId) {

        this(id, title, start, end, type, taskId, null);
    }

    // Create a block linked to a Task
    public TimeBlock(
            String title, LocalDateTime start,
            LocalDateTime end, Type type, String taskId) {

        this(title, start, end, type, taskId, null);
    }

    // Create a block linked to a Task and an optional Step
    public TimeBlock(
            String title, LocalDateTime start,
            LocalDateTime end, Type type,
            String taskId, String stepId) {

        this(UUID.randomUUID().toString(),
                title, start, end, type, taskId, stepId);
    }
}