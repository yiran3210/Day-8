package dev.personalagenda.data;

import dev.personalagenda.model.Task;
import dev.personalagenda.model.TaskStep;
import dev.personalagenda.model.WorkSession;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public class WorkSessionRepository {

    public WorkSession start(Task task, TaskStep step)
            throws SQLException {

        if (task == null) {
            throw new IllegalArgumentException(
                    "Select a task before starting.");
        }

        if (step != null
                && !task.getId().equals(step.taskId())) {
            throw new IllegalArgumentException(
                    "Step does not belong to the selected task.");
        }

        WorkSession session = new WorkSession(
                UUID.randomUUID().toString(),
                task.getId(),
                step == null ? null : step.id(),
                task.getTitle(),
                step == null ? null : step.title(),
                Instant.now(),
                null);

        String sql = """
                INSERT INTO work_sessions
                (id, task_id, step_id, task_title, step_title,
                 started_at_ms)
                SELECT ?, ?, ?, ?, ?, ?
                WHERE NOT EXISTS (
                    SELECT 1 FROM work_sessions
                    WHERE ended_at_ms IS NULL
                )
                """;

        try (Connection conn = Database.connect();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, session.id());
            stmt.setString(2, session.taskId());
            stmt.setString(3, session.stepId());
            stmt.setString(4, session.taskTitle());
            stmt.setString(5, session.stepTitle());
            stmt.setLong(6, session.startedAt().toEpochMilli());

            if (stmt.executeUpdate() != 1) {
                throw new SQLException(
                        "Another work session is already active.");
            }
        }

        return session;
    }

    public boolean stop(String sessionId, Instant endedAt)
            throws SQLException {

        String sql = """
                UPDATE work_sessions
                SET ended_at_ms = ?
                WHERE id = ?
                  AND ended_at_ms IS NULL
                """;

        try (Connection conn = Database.connect();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, endedAt.toEpochMilli());
            stmt.setString(2, sessionId);

            return stmt.executeUpdate() == 1;
        }
    }

    public Optional<WorkSession> findActive()
            throws SQLException {

        String sql = """
                SELECT *
                FROM work_sessions
                WHERE ended_at_ms IS NULL
                ORDER BY started_at_ms DESC
                LIMIT 1
                """;

        try (Connection conn = Database.connect();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {

            if (!rs.next()) {
                return Optional.empty();
            }

            long endMs = rs.getLong("ended_at_ms");

            Instant endedAt = rs.wasNull()
                    ? null
                    : Instant.ofEpochMilli(endMs);

            return Optional.of(new WorkSession(
                    rs.getString("id"),
                    rs.getString("task_id"),
                    rs.getString("step_id"),
                    rs.getString("task_title"),
                    rs.getString("step_title"),
                    Instant.ofEpochMilli(
                            rs.getLong("started_at_ms")),
                    endedAt));
        }
    }
}