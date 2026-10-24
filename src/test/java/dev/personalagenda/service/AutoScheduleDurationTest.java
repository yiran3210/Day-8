
package dev.personalagenda.service;

import dev.personalagenda.model.Task;
import dev.personalagenda.model.TaskStep;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AutoScheduleDurationTest {

    private Task task(int minutes) {
        return new Task("task-1", "Research", minutes, false);
    }

    private TaskStep step(
            String id, int minutes, boolean completed) {

        return new TaskStep(
                id, "task-1", "Experiment",
                minutes, completed, null);
    }

    @Test
    void unfinishedTaskWithoutStepsUsesEstimate() {
        assertEquals(90L,
                AutoScheduleDuration.suggestedMinutes(
                        task(90), null, List.of()));
    }

    @Test
    void unfinishedStepCanBeScheduledAgain() {
        Task t = task(120);
        TaskStep s = step("s1", 120, false);

        assertEquals(120L,
                AutoScheduleDuration.suggestedMinutes(
                        t, s, List.of(s)));
    }

    @Test
    void wholeTaskCountsOnlyUnfinishedSteps() {
        Task t = task(150);

        TaskStep done = step("s1", 30, true);
        TaskStep open1 = step("s2", 45, false);
        TaskStep open2 = step("s3", 60, false);

        assertEquals(105L,
                AutoScheduleDuration.suggestedMinutes(
                        t, null, List.of(done, open1, open2)));
    }

    @Test
    void completedStepHasZeroSuggestion() {
        Task t = task(90);
        TaskStep done = step("s1", 90, true);

        assertEquals(0L,
                AutoScheduleDuration.suggestedMinutes(
                        t, done, List.of(done)));
    }

    @Test
    void completedTaskHasZeroSuggestion() {
        Task t = task(90);
        t.setCompleted(true);

        assertEquals(0L,
                AutoScheduleDuration.suggestedMinutes(
                        t, null, List.of()));
    }

    @Test
    void staleOrDeletedStepIsNotScheduled() {
        Task t = task(90);
        TaskStep oldStep = step("deleted", 90, false);

        assertEquals(0L,
                AutoScheduleDuration.suggestedMinutes(
                        t, oldStep, List.of()));
    }

    @Test
    void allStepsCompletedMeansNoSuggestedTime() {
        Task t = task(90);

        assertEquals(0L,
                AutoScheduleDuration.suggestedMinutes(
                        t, null, List.of(
                                step("s1", 30, true),
                                step("s2", 60, true))));
    }
}
