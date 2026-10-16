
package dev.personalagenda.data;

import dev.personalagenda.model.TaskStep;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class TaskStepRepository {

    public void insert(TaskStep step) throws SQLException {

        String sql = """
                INSERT INTO task_steps
                (id, task_id, title, estimated_minutes,
                 completed, completed_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = Database.connect();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, step.id());
            stmt.setString(2, step.taskId());
            stmt.setString(3, step.title());
            stmt.setInt(4, step.estimatedMinutes());
            stmt.setInt(5, step.completed() ? 1 : 0);
            stmt.setString(6,
                    step.completedAt() == null
                            ? null
                            : step.completedAt().toString());

            stmt.executeUpdate();
        }
    }

    public List<TaskStep> findByTaskId(String taskId)
            throws SQLException {

        List<TaskStep> steps = new ArrayList<>();

        String sql = """
                SELECT *
                FROM task_steps
                WHERE task_id = ?
                ORDER BY rowid
                """;

        try (Connection conn = Database.connect();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, taskId);

            try (ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {

                    String completedAt = rs.getString("completed_at");

                    steps.add(new TaskStep(
                            rs.getString("id"),
                            rs.getString("task_id"),
                            rs.getString("title"),
                            rs.getInt("estimated_minutes"),
                            rs.getInt("completed") == 1,
                            completedAt == null
                                    ? null
                                    : LocalDateTime.parse(completedAt)));
                }
            }
        }

        return steps;
    }

    public void updateCompleted(
            String id, boolean completed) throws SQLException {

        String sql = """
                UPDATE task_steps
                SET completed = ?, completed_at = ?
                WHERE id = ?
                """;

        try (Connection conn = Database.connect();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, completed ? 1 : 0);
            stmt.setString(2,
                    completed ? LocalDateTime.now().toString() : null);
            stmt.setString(3, id);

            stmt.executeUpdate();
        }
    }

    public void delete(String id) throws SQLException {

        String sql = "DELETE FROM task_steps WHERE id = ?";

        try (Connection conn = Database.connect();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, id);
            stmt.executeUpdate();
        }
    }
}
