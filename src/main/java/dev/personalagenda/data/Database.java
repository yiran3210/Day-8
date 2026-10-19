package dev.personalagenda.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;

public class Database {

    private static final Path DB_PATH = resolveDatabasePath();

    private static Path resolveDatabasePath() {

        // 1. Explicit JVM configuration has highest priority.
        String configured = System.getProperty("agenda.data.dir");

        if (configured == null || configured.isBlank()) {
            configured = System.getenv("AGENDA_DATA_DIR");
        }

        if (configured != null && !configured.isBlank()) {
            return Path.of(configured.trim())
                    .toAbsolutePath()
                    .resolve("agenda.db");
        }

        // 2. Preserve the existing installation and its database.
        Path legacy = Path.of("F:/AgendaData/agenda.db");

        if (Files.isRegularFile(legacy)) {
            return legacy;
        }

        // 3. If the existing data directory is already present,
        // continue using it.
        if (Files.isDirectory(legacy.getParent())) {
            return legacy;
        }

        // 4. Standard per-user directory on other Windows machines.
        String localAppData = System.getenv("LOCALAPPDATA");

        if (localAppData != null && !localAppData.isBlank()) {
            return Path.of(localAppData, "PersonalAgenda", "agenda.db");
        }

        // 5. Fallback for non-Windows development environments.
        return Path.of(
                System.getProperty("user.home"),
                ".personal-agenda",
                "agenda.db");
    }

    public static Connection connect() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
    }

    public static void initialize() throws SQLException, IOException {

        Files.createDirectories(DB_PATH.getParent());

        String sql = """
                CREATE TABLE IF NOT EXISTS tasks (
                    id TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    estimated_minutes INTEGER NOT NULL,
                    completed INTEGER NOT NULL DEFAULT 0,
                    planned_date TEXT
                )
                """;

        try (Connection connection = connect();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);

            boolean hasPlannedDate = false;

            try (ResultSet columns = statement.executeQuery("PRAGMA table_info(tasks)")) {

                while (columns.next()) {
                    if ("planned_date".equals(columns.getString("name"))) {
                        hasPlannedDate = true;
                        break;
                    }
                }
            }

            if (!hasPlannedDate) {
                statement.executeUpdate("ALTER TABLE tasks ADD COLUMN planned_date TEXT");

                System.out.println("Added planned_date column");
            }

            String timeBlockSql = """
                    CREATE TABLE IF NOT EXISTS time_blocks (
                    id TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    start_time TEXT NOT NULL,
                    end_time TEXT NOT NULL, type TEXT NOT NULL,
                    task_id TEXT)
                    """;

            statement.executeUpdate(timeBlockSql);

            boolean hasStepId = false;

            try (ResultSet columns = statement.executeQuery(
                    "PRAGMA table_info(time_blocks)")) {

                while (columns.next()) {
                    if ("step_id".equals(columns.getString("name"))) {
                        hasStepId = true;
                        break;
                    }
                }
            }

            if (!hasStepId) {
                statement.executeUpdate(
                        "ALTER TABLE time_blocks ADD COLUMN step_id TEXT");
            }

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS categories (
                        name TEXT PRIMARY KEY COLLATE NOCASE,
                        color TEXT NOT NULL
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS task_category_links (
                        task_id TEXT PRIMARY KEY,
                        category_name TEXT NOT NULL
                    )
                    """);

            statement.executeUpdate("""
                    INSERT OR IGNORE INTO categories (name, color)
                    VALUES
                        ('General', '#E7ECF3'),
                        ('Paper', '#DCEAFE'),
                        ('Thesis', '#EADFF7'),
                        ('Career', '#F7E5CC'),
                        ('Training', '#D9F1E8'),
                        ('Personal', '#E4EAF0')
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS task_steps (
                        id TEXT PRIMARY KEY,
                        task_id TEXT NOT NULL,
                        title TEXT NOT NULL,
                        estimated_minutes INTEGER NOT NULL CHECK(estimated_minutes > 0),
                        completed INTEGER NOT NULL DEFAULT 0,
                        completed_at TEXT
                    )
                    """);

            statement.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_task_steps_task_id
                    ON task_steps(task_id)
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS projects (
                        id TEXT PRIMARY KEY,
                        name TEXT NOT NULL,
                        category_name TEXT NOT NULL,
                        start_date TEXT NOT NULL,
                        due_date TEXT NOT NULL
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS task_project_links (
                        task_id TEXT PRIMARY KEY,
                        project_id TEXT NOT NULL
                    )
                    """);

            statement.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_task_project
                    ON task_project_links(project_id)
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS work_sessions (
                        id TEXT PRIMARY KEY,
                        task_id TEXT,
                        step_id TEXT,
                        task_title TEXT,
                        step_title TEXT,
                        started_at_ms INTEGER NOT NULL,
                        ended_at_ms INTEGER,
                        CHECK (
                            ended_at_ms IS NULL
                            OR ended_at_ms >= started_at_ms
                        )
                    )
                    """);

            statement.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_work_sessions_start
                    ON work_sessions(started_at_ms)
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS completion_observations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        observed_at_ms INTEGER NOT NULL,
                        local_date TEXT NOT NULL,
                        planned_minutes INTEGER NOT NULL,
                        completed_minutes INTEGER NOT NULL
                    )
                    """);

            statement.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_completion_date
                    ON completion_observations(local_date, id)
                    """);

            statement.executeUpdate("""
                    CREATE VIEW IF NOT EXISTS daily_completion_live AS
                    WITH task_totals AS (
                        SELECT
                            t.estimated_minutes AS task_estimate,
                            t.completed AS parent_completed,

                            COALESCE((
                                SELECT SUM(s.estimated_minutes)
                                FROM task_steps s
                                WHERE s.task_id = t.id
                            ), 0) AS step_total,

                            COALESCE((
                                SELECT SUM(s.estimated_minutes)
                                FROM task_steps s
                                WHERE s.task_id = t.id
                                  AND s.completed = 1
                            ), 0) AS step_done

                        FROM tasks t
                        WHERE t.planned_date = date('now', 'localtime')
                    )
                    SELECT
                        COALESCE(SUM(
                            MAX(task_estimate, step_total)
                        ), 0) AS planned_minutes,

                        COALESCE(SUM(
                            CASE
                                WHEN step_total = 0 THEN
                                    CASE WHEN parent_completed = 1
                                         THEN task_estimate ELSE 0 END
                                ELSE
                                    MIN(step_done,
                                        MAX(task_estimate, step_total))
                            END
                        ), 0) AS completed_minutes
                    FROM task_totals
                    """);

            String recordObservation = """
                    INSERT INTO completion_observations
                    (observed_at_ms, local_date,
                     planned_minutes, completed_minutes)

                    SELECT
                        CAST(strftime('%s', 'now') AS INTEGER) * 1000,
                        date('now', 'localtime'),
                        v.planned_minutes,
                        v.completed_minutes

                    FROM daily_completion_live v

                    WHERE NOT EXISTS (
                        SELECT 1
                        FROM completion_observations o
                        WHERE o.id = (
                            SELECT MAX(id)
                            FROM completion_observations
                            WHERE local_date = date('now', 'localtime')
                        )
                          AND o.planned_minutes = v.planned_minutes
                          AND o.completed_minutes = v.completed_minutes
                    )
                    """;

            for (String table : new String[] { "tasks", "task_steps" }) {
                for (String operation : new String[] {
                        "INSERT", "UPDATE", "DELETE" }) {

                    String triggerName = "trg_completion_" + table + "_"
                            + operation.toLowerCase(java.util.Locale.ROOT);

                    statement.executeUpdate(
                            "CREATE TRIGGER IF NOT EXISTS " + triggerName
                                    + " AFTER " + operation
                                    + " ON " + table
                                    + " BEGIN "
                                    + recordObservation
                                    + "; END");
                }
            }

            // Capture the baseline of the current day.
            statement.executeUpdate(recordObservation);

        }

        System.out.println("Database initialized: " + DB_PATH);
    }
}