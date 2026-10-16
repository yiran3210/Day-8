
package dev.personalagenda.model;

import java.time.LocalDate;
import java.util.UUID;

public record Project(
        String id,
        String name,
        String categoryName,
        LocalDate startDate,
        LocalDate dueDate) {

    public Project {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Project name is required");
        }

        if (startDate == null || dueDate == null
                || !dueDate.isAfter(startDate)) {
            throw new IllegalArgumentException(
                    "Due date must be after start date");
        }
    }

    public Project(
            String name,
            String categoryName,
            LocalDate startDate,
            LocalDate dueDate) {

        this(
                UUID.randomUUID().toString(),
                name,
                categoryName,
                startDate,
                dueDate);
    }
}
