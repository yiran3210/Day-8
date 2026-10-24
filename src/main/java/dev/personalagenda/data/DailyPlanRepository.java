package dev.personalagenda.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDate;

public final class DailyPlanRepository {

    public record DailySummary(
            int plannedMinutes,
            int completedMinutes,
            int totalTasks,
            int completedTasks) {
    }

    public record CorrectableItem(
            String itemKey,
            String taskId,
            String stepId,
            String title,
            int originalMinutes,
            int effectiveMinutes,
            boolean completed) {
    }

    public DailySummary findByDate(LocalDate date)
            throws SQLException {

        String sql = """
                                                WITH effective_items AS (
                                SELECT
                                    d.task_id,
                                    COALESCE(
                                        (
                                            SELECT e.new_planned_minutes
                                            FROM plan_change_events e
                                            WHERE e.event_type = 'CORRECT_PLAN'
                                              AND e.affected_date = d.local_date
                                              AND e.item_key = d.item_key
                                            ORDER BY e.id DESC
                                            LIMIT 1
                                        ),
                                        d.planned_minutes
                                    ) AS planned_minutes,
                                    d.completed
                                FROM daily_plan_items d
                                                    WHERE d.local_date = ?
                                                      AND (
                                                          d.step_id IS NOT NULL
                                                          OR NOT EXISTS (
                                                              SELECT 1
                                                              FROM daily_plan_items s
                                                              WHERE s.local_date = d.local_date
                                                                AND s.task_id = d.task_id
                                                                AND s.step_id IS NOT NULL
                                                          )
                                                      )
                                                ),
                                                per_task AS (
                                                    SELECT task_id,
                                                           SUM(planned_minutes) AS planned,
                                                           SUM(
                                                               CASE WHEN completed = 1
                                                                    THEN planned_minutes
                                                                    ELSE 0 END
                                                           ) AS completed,
                                                           MIN(completed) AS task_completed
                FROM effective_items
                WHERE planned_minutes > 0
                GROUP BY task_id
                                                )
                                                SELECT
                                                    COALESCE(SUM(planned), 0) AS planned,
                                                    COALESCE(SUM(completed), 0) AS completed,
                                                    COUNT(*) AS total_tasks,
                                                    COALESCE(SUM(task_completed), 0) AS completed_tasks
                                                FROM per_task
                                                """;

        try (Connection conn = Database.connect();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, date.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();

                return new DailySummary(
                        rs.getInt("planned"),
                        rs.getInt("completed"),
                        rs.getInt("total_tasks"),
                        rs.getInt("completed_tasks"));
            }
        }
    }

    public boolean correctPlannedMinutes(
            LocalDate date,
            String itemKey,
            int newMinutes,
            String reason) throws SQLException {

        if (date == null || itemKey == null || itemKey.isBlank()
                || newMinutes < 0 || reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "Valid date, item, minutes and reason are required");
        }

        String selectSql = """
                SELECT d.task_id,
                       COALESCE((
                           SELECT e.new_planned_minutes
                           FROM plan_change_events e
                           WHERE e.event_type = 'CORRECT_PLAN'
                             AND e.affected_date = d.local_date
                             AND e.item_key = d.item_key
                           ORDER BY e.id DESC LIMIT 1
                       ), d.planned_minutes) AS current_minutes
                FROM daily_plan_items d
                WHERE d.local_date = ?
                  AND d.item_key = ?
                  AND (
                      d.step_id IS NOT NULL
                      OR NOT EXISTS (
                          SELECT 1 FROM daily_plan_items s
                          WHERE s.local_date = d.local_date
                            AND s.task_id = d.task_id
                            AND s.step_id IS NOT NULL
                      )
                  )
                """;

        String insertSql = """
                INSERT INTO plan_change_events (
                    occurred_at_ms, task_id, item_key,
                    event_type, affected_date,
                    old_planned_minutes, new_planned_minutes,
                    reason
                )
                VALUES (?, ?, ?, 'CORRECT_PLAN', ?, ?, ?, ?)
                """;

        try (Connection conn = Database.connect()) {
            conn.setAutoCommit(false);

            try {
                String taskId;
                int previousMinutes;

                try (PreparedStatement stmt = conn.prepareStatement(selectSql)) {
                    stmt.setString(1, date.toString());
                    stmt.setString(2, itemKey);

                    try (ResultSet rs = stmt.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException(
                                    "Effective plan item not found: " + itemKey);
                        }
                        taskId = rs.getString("task_id");
                        previousMinutes = rs.getInt("current_minutes");
                    }
                }

                if (previousMinutes == newMinutes) {
                    conn.rollback();
                    return false;
                }

                try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                    stmt.setLong(1, System.currentTimeMillis());
                    stmt.setString(2, taskId);
                    stmt.setString(3, itemKey);
                    stmt.setString(4, date.toString());
                    stmt.setInt(5, previousMinutes);
                    stmt.setInt(6, newMinutes);
                    stmt.setString(7, reason.trim());
                    stmt.executeUpdate();
                }

                conn.commit();
                return true;

            } catch (SQLException | RuntimeException ex) {
                conn.rollback();
                throw ex;
            }
        }
    }

    public List<CorrectableItem> findCorrectableItems(
            LocalDate date) throws SQLException {

        String sql = """
                SELECT
                    d.item_key,
                    d.task_id,
                    d.step_id,
                    CASE WHEN d.step_id IS NULL
                         THEN d.task_title
                         ELSE d.step_title END AS item_title,
                    d.planned_minutes AS original_minutes,
                    COALESCE((
                        SELECT e.new_planned_minutes
                        FROM plan_change_events e
                        WHERE e.event_type = 'CORRECT_PLAN'
                          AND e.affected_date = d.local_date
                          AND e.item_key = d.item_key
                        ORDER BY e.id DESC
                        LIMIT 1
                    ), d.planned_minutes) AS effective_minutes,
                    d.completed
                FROM daily_plan_items d
                WHERE d.local_date = ?
                  AND (
                      d.step_id IS NOT NULL
                      OR NOT EXISTS (
                          SELECT 1 FROM daily_plan_items s
                          WHERE s.local_date = d.local_date
                            AND s.task_id = d.task_id
                            AND s.step_id IS NOT NULL
                      )
                  )
                ORDER BY d.task_title COLLATE NOCASE, d.item_key
                """;

        List<CorrectableItem> items = new ArrayList<>();

        try (Connection conn = Database.connect();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, date.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    items.add(new CorrectableItem(
                            rs.getString("item_key"),
                            rs.getString("task_id"),
                            rs.getString("step_id"),
                            rs.getString("item_title"),
                            rs.getInt("original_minutes"),
                            rs.getInt("effective_minutes"),
                            rs.getBoolean("completed")));
                }
            }
        }

        return items;
    }
}