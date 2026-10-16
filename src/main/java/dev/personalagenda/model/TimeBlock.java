package dev.personalagenda.model;

import java.time.LocalDateTime;
import java.util.UUID;

public record TimeBlock(
        String id, String title, LocalDateTime start, LocalDateTime end, Type type, String taskId) {

    public enum Type {
        TASK, FIXED, REST, FREE
    }

    public TimeBlock {
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException(
                    "End must be after start");
        }
    }

    public TimeBlock(
            String title, LocalDateTime start, LocalDateTime end, Type type, String taskId) {
        this(
                UUID.randomUUID().toString(),
                title, start, end, type, taskId);
    }
}