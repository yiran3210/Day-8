package dev.personalagenda.model;

import java.time.Duration;
import java.time.Instant;

public record WorkSession(
        String id,
        String taskId,
        String stepId,
        String taskTitle,
        String stepTitle,
        Instant startedAt,
        Instant endedAt) {

    public boolean isActive() {
        return endedAt == null;
    }

    public Duration finishedDuration() {
        if (isActive()) {
            throw new IllegalStateException(
                    "Session is still active.");
        }

        return Duration.between(startedAt, endedAt);
    }
}