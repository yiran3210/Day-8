package dev.personalagenda.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.time.LocalDate;

import dev.personalagenda.model.Task;

public class TaskRepository {

    public void insert(Task task, String categoryName)
            throws SQLException {

        String sql = """
                    INSERT INTO tasks (id, title, estimated_minutes, completed, planned_date)
                    VALUES (?, ?, ?, ?, ?)
                """;

        try (Connection conn = Database.connect()) {
            conn.setAutoCommit(false);

            try {
                // 1. Insert Task
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, task.getId());
                    stmt.setString(2, task.getTitle());
                    stmt.setInt(3, task.getEstimatedMinutes());
                    stmt.setInt(4, task.isCompleted() ? 1 : 0);
                    stmt.setString(5, task.getPlannedDate() == null
                            ? null
                            : task.getPlannedDate().toString());
                    stmt.executeUpdate();
                }

                // 2. Assign Category
                try (PreparedStatement stmt = conn.prepareStatement("""
                        INSERT INTO task_category_links
                            (task_id, category_name)
                        VALUES (?, ?)
                        """)) {

                    stmt.setString(1, task.getId());
                    stmt.setString(2,
                            categoryName == null ? "General" : categoryName);
                    stmt.executeUpdate();
                }

                // 3. Save today's planned commitment
                captureTodayPlan(conn, task.getId());

                // 4. Record the initial schedule, if one exists.
                if (task.getPlannedDate() != null) {
                    recordPlanDateChange(
                            conn,
                            task.getId(),
                            null,
                            task.getPlannedDate().toString());
                }

                conn.commit();

            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            }
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

    public void updatePlannedDate(String id, LocalDate date)
            throws SQLException {

        String sql = """
                UPDATE tasks
                SET planned_date = ?
                WHERE id = ?
                """;

        try (Connection conn = Database.connect()) {
            conn.setAutoCommit(false);

            try {
                // Preserve today's original commitment before moving it.

                String oldDate = findPlannedDate(conn, id);
                String newDate = date == null ? null : date.toString();

                if (Objects.equals(oldDate, newDate)) {
                    conn.rollback();
                    return;
                }

                captureTodayPlan(conn, id);

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, newDate);
                    stmt.setString(2, id);
                    stmt.executeUpdate();
                }

                // Capture a task newly scheduled for today.
                captureTodayPlan(conn, id);

                recordPlanDateChange(conn, id, oldDate, newDate);

                conn.commit();

            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            }
        }
    }

    private void captureTodayPlan(Connection conn, String taskId)
            throws SQLException {

        String sql = """
                INSERT OR IGNORE INTO daily_plan_items (
                    local_date, item_key, task_id, step_id,
                    task_title, step_title, category_name,
                    planned_minutes, completed,
                    recorded_at_ms, completed_at_ms
                )
                SELECT
                    date('now', 'localtime'),
                    CASE WHEN s.id IS NULL
                         THEN 'TASK:' || t.id
                         ELSE 'STEP:' || s.id END,
                    t.id,
                    s.id,
                    t.title,
                    s.title,
                    COALESCE(c.category_name, 'General'),
                    CASE WHEN s.id IS NULL
                         THEN t.estimated_minutes
                         ELSE s.estimated_minutes END,
                    CASE WHEN s.id IS NULL
                         THEN t.completed
                         ELSE s.completed END,
                    CAST(strftime('%s', 'now') AS INTEGER) * 1000,
                    NULL
                FROM tasks t
                LEFT JOIN task_steps s ON s.task_id = t.id
                LEFT JOIN task_category_links c ON c.task_id = t.id
                WHERE t.id = ?
                  AND t.planned_date = date('now', 'localtime')
                """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, taskId);
            stmt.executeUpdate();
        }
    }

    private String findPlannedDate(Connection conn, String taskId)
            throws SQLException {

        String sql = "SELECT planned_date FROM tasks WHERE id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, taskId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Task not found: " + taskId);
                }
                return rs.getString("planned_date");
            }
        }
    }

    private void recordPlanDateChange(
            Connection conn,
            String taskId,
            String oldDate,
            String newDate) throws SQLException {

        String eventType = newDate == null
                ? "TO_BACKLOG"
                : (oldDate == null ? "SCHEDULE" : "RESCHEDULE");

        String sql = """
                INSERT INTO plan_change_events (
                    occurred_at_ms,
                    task_id,
                    event_type,
                    old_date,
                    new_date,
                    affected_date
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, System.currentTimeMillis());
            stmt.setString(2, taskId);
            stmt.setString(3, eventType);
            stmt.setString(4, oldDate);
            stmt.setString(5, newDate);
            stmt.setString(6,
                    oldDate != null ? oldDate : newDate);

            stmt.executeUpdate();
        }
    }

    public void updateDetails(
            String id, String title, int minutes) throws SQLException {

        String sql = """
                UPDATE tasks
                SET title = ?, estimated_minutes = ?
                WHERE id = ?
                """;

        try (Connection conn = Database.connect();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, title);
            stmt.setInt(2, minutes);
            stmt.setString(3, id);

            stmt.executeUpdate();
        }
    }

    public boolean exists(String id) throws SQLException {

        String sql = "SELECT 1 FROM tasks WHERE id = ?";

        try (Connection conn = Database.connect();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    public void delete(String id) throws SQLException {

        try (Connection conn = Database.connect()) {

            conn.setAutoCommit(false);

            try {

                try (PreparedStatement check = conn.prepareStatement("""
                        SELECT 1
                        FROM work_sessions
                        WHERE task_id = ?
                          AND ended_at_ms IS NULL
                        LIMIT 1
                        """)) {

                    check.setString(1, id);

                    try (ResultSet rs = check.executeQuery()) {
                        if (rs.next()) {
                            throw new SQLException(
                                    "Stop the active Focus Session before deleting this Task.");
                        }
                    }
                }

                executeForTask(conn,
                        "UPDATE time_blocks SET task_id = NULL, step_id = NULL WHERE task_id = ?", id);

                executeForTask(conn,
                        "DELETE FROM task_steps WHERE task_id = ?", id);

                executeForTask(conn,
                        "DELETE FROM task_category_links WHERE task_id = ?", id);

                executeForTask(conn,
                        "DELETE FROM task_project_links WHERE task_id = ?", id);

                executeForTask(conn,
                        "DELETE FROM tasks WHERE id = ?", id);

                conn.commit();

            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            }
        }
    }

    private void executeForTask(
            Connection conn, String sql, String id)
            throws SQLException {

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            stmt.executeUpdate();
        }
    }
}