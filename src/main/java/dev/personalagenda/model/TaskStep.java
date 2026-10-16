
package dev.personalagenda.model;

import java.time.LocalDateTime;
import java.util.UUID;

public record TaskStep(
        String id,
        String taskId,
        String title,
        int estimatedMinutes,
        boolean completed,
        LocalDateTime completedAt) {

    public TaskStep {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Step title is required");
        }

        if (estimatedMinutes <= 0) {
            throw new IllegalArgumentException(
                    "Estimated minutes must be positive");
        }
    }

    public TaskStep(
            String taskId,
            String title,
            int estimatedMinutes) {

        this(
                UUID.randomUUID().toString(),
                taskId,
                title,
                estimatedMinutes,
                false,
                null);
    }
}
