package dev.personalagenda.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDate;

import dev.personalagenda.model.Task;

public class TaskRepository {

    public void insert(Task task) throws SQLException {

        String sql = """
                    INSERT INTO tasks (id, title, estimated_minutes, completed, planned_date)
                    VALUES (?, ?, ?, ?, ?)
                """;

        try (Connection conn = Database.connect();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, task.getId());
            stmt.setString(2, task.getTitle());
            stmt.setInt(3, task.getEstimatedMinutes());
            stmt.setInt(4, task.isCompleted() ? 1 : 0);
            stmt.setString(5, task.getPlannedDate() == null ? null : task.getPlannedDate().toString());
            stmt.executeUpdate();
        }
    }

    public List<Task> findAll() throws SQLException {

        List<Task> tasks = new ArrayList<>();

        String sql = """
                SELECT id, title, estimated_minutes, completed, planned_date
                FROM tasks
                ORDER BY rowid
                """;

        try (Connection conn = Database.connect();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {

                Task task = new Task(
                        rs.getString("id"),
                        rs.getString("title"),
                        rs.getInt("estimated_minutes"),
                        rs.getInt("completed") != 0);

                String dateText = rs.getString("planned_date");

                if (dateText != null) {
                    task.setPlannedDate(LocalDate.parse(dateText));
                }

                tasks.add(task);
            }
        }

        return tasks;
    }

    public void updateCompleted(String id, boolean completed) throws SQLException {

        String sql = """
                UPDATE tasks
                SET completed = ?
                WHERE id = ?
                """;

        try (Connection conn = Database.connect();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, completed ? 1 : 0);
            stmt.setString(2, id);

            stmt.executeUpdate();
        }
    }

    public void updatePlannedDate(String id, LocalDate date) throws SQLException {

        String sql = """
                UPDATE tasks
                SET planned_date = ?
                WHERE id = ?
                """;

        try (Connection conn = Database.connect();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, date == null ? null : date.toString());
            stmt.setString(2, id);

            stmt.executeUpdate();
        }
    }

    public void delete(String id) throws SQLException {

        String sql = """
                DELETE FROM tasks
                WHERE id = ?
                """;

        try (Connection conn = Database.connect();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, id);
            stmt.executeUpdate();
        }
    }
}