package dev.personalagenda.model;

import java.time.Instant;
import java.time.LocalDate;

public record CompletionObservation(
        LocalDate date,
        Instant observedAt,
        int plannedMinutes,
        int completedMinutes) {

    public Double completionRate() {
        if (plannedMinutes == 0) {
            return null;
        }

        return 100.0 * completedMinutes / plannedMinutes;
    }
}