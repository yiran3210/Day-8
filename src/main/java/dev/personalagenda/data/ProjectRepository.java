
package dev.personalagenda.data;

import dev.personalagenda.model.Project;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ProjectRepository {

    public void insert(Project project) throws SQLException {

        String sql = """
                INSERT INTO projects
                (id, name, category_name, start_date, due_date)
                VALUES (?, ?, ?, ?, ?)
                """;

        try (Connection conn = Database.connect();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, project.id());
            stmt.setString(2, project.name());
            stmt.setString(3, project.categoryName());
            stmt.setString(4, project.startDate().toString());
            stmt.setString(5, project.dueDate().toString());

            stmt.executeUpdate();
        }
    }

    public List<Project> findAll() throws SQLException {

        List<Project> projects = new ArrayList<>();

        String sql = """
                SELECT * FROM projects
                ORDER BY due_date, name
                """;

        try (Connection conn = Database.connect();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                projects.add(new Project(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("category_name"),
                        LocalDate.parse(rs.getString("start_date")),
                        LocalDate.parse(rs.getString("due_date"))));
            }
        }

        return projects;
    }

    public void assignTask(
            String taskId, String projectId) throws SQLException {

        if (projectId == null) {
            try (Connection conn = Database.connect();
                    PreparedStatement stmt = conn.prepareStatement(
                            "DELETE FROM task_project_links WHERE task_id = ?")) {

                stmt.setString(1, taskId);
                stmt.executeUpdate();
            }
            return;
        }

        String sql = """
                INSERT INTO task_project_links (task_id, project_id)
                VALUES (?, ?)
                ON CONFLICT(task_id)
                DO UPDATE SET project_id = excluded.project_id
                """;

        try (Connection conn = Database.connect();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, taskId);
            stmt.setString(2, projectId);
            stmt.executeUpdate();
        }
    }

    public List<String> findTaskIds(String projectId)
            throws SQLException {

        List<String> taskIds = new ArrayList<>();

        String sql = """
                SELECT task_id
                FROM task_project_links
                WHERE project_id = ?
                """;

        try (Connection conn = Database.connect();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, projectId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    taskIds.add(rs.getString("task_id"));
                }
            }
        }

        return taskIds;
    }

    public String findProjectIdForTask(String taskId)
            throws SQLException {

        if (taskId == null) {
            return null;
        }

        String sql = """
                SELECT project_id
                FROM task_project_links
                WHERE task_id = ?
                """;

        try (Connection conn = Database.connect();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, taskId);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getString("project_id") : null;
            }
        }
    }

    public void update(Project project) throws SQLException {

        String sql = """
                UPDATE projects
                SET name = ?, category_name = ?,
                    start_date = ?, due_date = ?
                WHERE id = ?
                """;

        try (Connection conn = Database.connect();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, project.name());
            stmt.setString(2, project.categoryName());
            stmt.setString(3, project.startDate().toString());
            stmt.setString(4, project.dueDate().toString());
            stmt.setString(5, project.id());

            stmt.executeUpdate();
        }
    }

    public void delete(String projectId) throws SQLException {

        try (Connection conn = Database.connect()) {

            conn.setAutoCommit(false);

            try {
                // 1. Remove task-project relationships.
                try (PreparedStatement stmt = conn.prepareStatement(
                        "DELETE FROM task_project_links WHERE project_id = ?")) {

                    stmt.setString(1, projectId);
                    stmt.executeUpdate();
                }

                // 2. Delete the project itself.
                try (PreparedStatement stmt = conn.prepareStatement(
                        "DELETE FROM projects WHERE id = ?")) {

                    stmt.setString(1, projectId);
                    stmt.executeUpdate();
                }

                conn.commit();

            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            }
        }
    }
}
