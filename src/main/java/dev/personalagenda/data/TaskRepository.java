package dev.personalagenda.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.ArrayList;
import java.util.List;

import dev.personalagenda.model.Task;

public class TaskRepository {

    public void insert(Task task) throws SQLException {

        String sql = """
                    INSERT INTO tasks (id, title, estimated_minutes, completed)
                    VALUES (?, ?, ?, ?)
                """;

        try (Connection conn = Database.connect();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, task.getId());
            stmt.setString(2, task.getTitle());
            stmt.setInt(3, task.getEstimatedMinutes());
            stmt.setInt(4, task.isCompleted() ? 1 : 0);

            stmt.executeUpdate();
        }
    }

    public List<Task> findAll() throws SQLException {

        List<Task> tasks = new ArrayList<>();

        String sql = """
                SELECT id, title, estimated_minutes, completed
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

                tasks.add(task);
            }
        }

        return tasks;
    }
}