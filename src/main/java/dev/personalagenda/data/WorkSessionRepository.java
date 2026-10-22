package dev.personalagenda.data;

import dev.personalagenda.model.Task;
import dev.personalagenda.model.TaskStep;
import dev.personalagenda.model.WorkSession;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

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

                return insertSession(session);
        }

        public WorkSession startUnlinked(String title)
                        throws SQLException {

                if (title == null || title.isBlank()) {
                        throw new IllegalArgumentException(
                                        "Enter a title for the unlinked Focus Session.");
                }

                WorkSession session = new WorkSession(
                                UUID.randomUUID().toString(),
                                null,
                                null,
                                title.trim(),
                                null,
                                Instant.now(),
                                null);

                return insertSession(session);
        }

        private WorkSession insertSession(WorkSession session) throws SQLException {
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

        public long finishedMillisForDate(
                        LocalDate date, ZoneId zone) throws SQLException {

                long dayStart = date.atStartOfDay(zone)
                                .toInstant().toEpochMilli();

                long dayEnd = date.plusDays(1).atStartOfDay(zone)
                                .toInstant().toEpochMilli();

                String sql = """
                                SELECT COALESCE(
                                    SUM(
                                        MIN(ended_at_ms, ?) -
                                        MAX(started_at_ms, ?)
                                    ), 0
                                ) AS total_ms
                                FROM work_sessions
                                WHERE ended_at_ms IS NOT NULL
                                  AND started_at_ms < ?
                                  AND ended_at_ms > ?
                                """;

                try (Connection conn = Database.connect();
                                PreparedStatement stmt = conn.prepareStatement(sql)) {

                        stmt.setLong(1, dayEnd);
                        stmt.setLong(2, dayStart);
                        stmt.setLong(3, dayEnd);
                        stmt.setLong(4, dayStart);

                        try (ResultSet rs = stmt.executeQuery()) {
                                return rs.next() ? rs.getLong("total_ms") : 0L;
                        }
                }
        }

        public List<WorkSession> findRecentFinishedForDate(
                        LocalDate date, ZoneId zone, int limit)
                        throws SQLException {

                long dayStart = date.atStartOfDay(zone)
                                .toInstant().toEpochMilli();

                long dayEnd = date.plusDays(1).atStartOfDay(zone)
                                .toInstant().toEpochMilli();

                String sql = """
                                SELECT *
                                FROM work_sessions
                                WHERE ended_at_ms IS NOT NULL
                                  AND started_at_ms < ?
                                  AND ended_at_ms > ?
                                ORDER BY ended_at_ms DESC
                                LIMIT ?
                                """;

                List<WorkSession> result = new ArrayList<>();

                try (Connection conn = Database.connect();
                                PreparedStatement stmt = conn.prepareStatement(sql)) {

                        stmt.setLong(1, dayEnd);
                        stmt.setLong(2, dayStart);
                        stmt.setInt(3, Math.max(1, limit));

                        try (ResultSet rs = stmt.executeQuery()) {

                                while (rs.next()) {
                                        result.add(new WorkSession(
                                                        rs.getString("id"),
                                                        rs.getString("task_id"),
                                                        rs.getString("step_id"),
                                                        rs.getString("task_title"),
                                                        rs.getString("step_title"),
                                                        Instant.ofEpochMilli(
                                                                        rs.getLong("started_at_ms")),
                                                        Instant.ofEpochMilli(
                                                                        rs.getLong("ended_at_ms"))));
                                }
                        }
                }

                return result;
        }

        public List<WorkSession> findFinishedOverlapping(
                        Instant rangeStart, Instant rangeEnd) throws SQLException {

                String sql = """
                                SELECT *
                                FROM work_sessions
                                WHERE ended_at_ms IS NOT NULL
                                  AND started_at_ms < ?
                                  AND ended_at_ms > ?
                                ORDER BY started_at_ms
                                """;

                List<WorkSession> result = new ArrayList<>();

                try (Connection conn = Database.connect();
                                PreparedStatement stmt = conn.prepareStatement(sql)) {

                        stmt.setLong(1, rangeEnd.toEpochMilli());
                        stmt.setLong(2, rangeStart.toEpochMilli());

                        try (ResultSet rs = stmt.executeQuery()) {
                                while (rs.next()) {
                                        result.add(new WorkSession(
                                                        rs.getString("id"),
                                                        rs.getString("task_id"),
                                                        rs.getString("step_id"),
                                                        rs.getString("task_title"),
                                                        rs.getString("step_title"),
                                                        Instant.ofEpochMilli(rs.getLong("started_at_ms")),
                                                        Instant.ofEpochMilli(rs.getLong("ended_at_ms"))));
                                }
                        }
                }

                return result;
        }
}