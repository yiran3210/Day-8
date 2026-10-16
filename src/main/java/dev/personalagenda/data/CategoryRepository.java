package dev.personalagenda.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

public class CategoryRepository {

    public Map<String, String> findAll() throws SQLException {

        Map<String, String> colors = new LinkedHashMap<>();

        String sql = "SELECT name, color FROM categories ORDER BY name";

        try (Connection conn = Database.connect();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                colors.put(
                        rs.getString("name"),
                        rs.getString("color"));
            }
        }

        return colors;
    }

    public void save(String name, String color) throws SQLException {

        String sql = """
                INSERT INTO categories (name, color)
                VALUES (?, ?)
                ON CONFLICT(name)
                DO UPDATE SET color = excluded.color
                """;

        try (Connection conn = Database.connect();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, name);
            stmt.setString(2, color);
            stmt.executeUpdate();
        }
    }

    public String findForTask(String taskId) throws SQLException {

        String sql = """
                SELECT category_name
                FROM task_category_links
                WHERE task_id = ?
                """;

        try (Connection conn = Database.connect();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, taskId);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next()
                        ? rs.getString("category_name")
                        : "General";
            }
        }
    }

    public void assign(String taskId, String category)
            throws SQLException {

        String sql = """
                INSERT INTO task_category_links
                (task_id, category_name)
                VALUES (?, ?)
                ON CONFLICT(task_id)
                DO UPDATE SET category_name = excluded.category_name
                """;

        try (Connection conn = Database.connect();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, taskId);
            stmt.setString(2, category);
            stmt.executeUpdate();
        }
    }
}
