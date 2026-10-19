
package dev.personalagenda.service;

import dev.personalagenda.data.WorkSessionRepository;
import dev.personalagenda.model.Task;
import dev.personalagenda.model.TaskStep;
import dev.personalagenda.model.WorkSession;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class FocusSessionService {

    public enum State {
        IDLE, RUNNING, PAUSED
    }

    private final WorkSessionRepository repository;

    private WorkSession activeSession;
    private WorkSession pausedSession;

    private Duration accumulated = Duration.ZERO;

    public FocusSessionService(WorkSessionRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public State state() {
        if (activeSession != null)
            return State.RUNNING;
        if (pausedSession != null)
            return State.PAUSED;
        return State.IDLE;
    }

    public WorkSession activeSession() {
        return activeSession;
    }

    public WorkSession pausedSession() {
        return pausedSession;
    }

    // Start a new focus process.
    public WorkSession start(Task task, TaskStep step)
            throws SQLException {

        if (state() != State.IDLE) {
            throw new IllegalStateException(
                    "Finish the current focus process first.");
        }

        WorkSession session = repository.start(task, step);

        activeSession = session;
        accumulated = Duration.ZERO;

        return session;
    }

    // Pause the running segment.
    public void pause() throws SQLException {

        if (state() != State.RUNNING) {
            throw new IllegalStateException(
                    "No running session to pause.");
        }

        Instant endedAt = Instant.now();

        if (!repository.stop(activeSession.id(), endedAt)) {
            throw new SQLException("Unable to pause session.");
        }

        accumulated = accumulated.plus(
                Duration.between(
                        activeSession.startedAt(), endedAt));

        pausedSession = activeSession;
        activeSession = null;
    }

    // Continue the same Task/Step with a new database row.
    public WorkSession resume(Task task, TaskStep step)
            throws SQLException {

        if (state() != State.PAUSED) {
            throw new IllegalStateException(
                    "No paused session to resume.");
        }

        String stepId = step == null ? null : step.id();

        if (task == null
                || !Objects.equals(
                        task.getId(), pausedSession.taskId())
                || !Objects.equals(
                        stepId, pausedSession.stepId())) {

            throw new IllegalArgumentException(
                    "Resume requires the original Task/Step.");
        }

        WorkSession session = repository.start(task, step);

        activeSession = session;
        pausedSession = null;

        return session;
    }

    // End the focus process.
    public void stop() throws SQLException {

        if (activeSession != null) {

            if (!repository.stop(
                    activeSession.id(), Instant.now())) {

                throw new SQLException("Unable to stop session.");
            }
        }

        activeSession = null;
        pausedSession = null;
        accumulated = Duration.ZERO;
    }

    // Read elapsed work time without querying SQLite.
    public Duration elapsed(Instant now) {

        Duration result = accumulated;

        if (activeSession != null) {

            Duration current = Duration.between(
                    activeSession.startedAt(), now);

            if (!current.isNegative()) {
                result = result.plus(current);
            }
        }

        return result;
    }
}
