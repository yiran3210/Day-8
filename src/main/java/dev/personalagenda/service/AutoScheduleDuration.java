
package dev.personalagenda.service;

import dev.personalagenda.model.Task;
import dev.personalagenda.model.TaskStep;

import java.util.List;
import java.util.Objects;

public final class AutoScheduleDuration {

    private AutoScheduleDuration() {
    }

    public static long suggestedMinutes(
            Task task,
            TaskStep selectedStep,
            List<TaskStep> steps) {

        Objects.requireNonNull(task);
        Objects.requireNonNull(steps);

        // A completed Task has no unfinished work to suggest.
        if (task.isCompleted()) {
            return 0L;
        }

        // Scheduling one specific Step.
        if (selectedStep != null) {
            return steps.stream()
                    .filter(s -> s.id().equals(selectedStep.id()))
                    .filter(s -> !s.completed())
                    .mapToLong(TaskStep::estimatedMinutes)
                    .findFirst()
                    .orElse(0L);
        }

        // Whole Task without Steps.
        if (steps.isEmpty()) {
            return Math.max(0L, task.getEstimatedMinutes());
        }

        // Whole Task with Steps: unfinished Steps only.
        return steps.stream()
                .filter(s -> !s.completed())
                .mapToLong(TaskStep::estimatedMinutes)
                .sum();
    }
}
