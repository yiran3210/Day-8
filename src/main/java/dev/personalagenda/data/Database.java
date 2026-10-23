package dev.personalagenda.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;
import java.util.prefs.Preferences;
import java.util.prefs.BackingStoreException;

public class Database {

    private static final String DATA_DIR_PREF_KEY = "data.directory";
    private static final Path DB_PATH = resolveDatabasePath();

    private static Path resolveDatabasePath() {

        // 1. Explicit launch overrides take priority over stored preferences.
        String configured = System.getProperty("agenda.data.dir");
        if (configured == null || configured.isBlank()) {
            configured = System.getenv("AGENDA_DATA_DIR");
        }
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured.trim())
                    .toAbsolutePath()
                    .normalize()
                    .resolve("agenda.db");
        }

        // 2. User-selected directory (applies on the next launch).
        String saved = Preferences.userNodeForPackage(Database.class)
                .get(DATA_DIR_PREF_KEY, null);
        if (saved != null && !saved.isBlank()) {
            return Path.of(saved)
                    .toAbsolutePath()
                    .normalize()
                    .resolve("agenda.db");
        }

        String os = System.getProperty("os.name", "")
                .toLowerCase(java.util.Locale.ROOT);

        if (os.contains("win")) {
            // Preserve data on existing Windows installations without migration.
            Path legacy = Path.of("F:/AgendaData/agenda.db");
            if (Files.isRegularFile(legacy)
                    || Files.isDirectory(legacy.getParent())) {
                return legacy;
            }

            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null && !localAppData.isBlank()) {
                return Path.of(localAppData, "PersonalAgenda", "agenda.db");
            }
        }

        if (os.contains("mac")) {
            // Normal writable per-user app data location on macOS.
            return Path.of(System.getProperty("user.home"),
                    "Library", "Application Support", "Day 8", "agenda.db");
        }

        // Fallback for Linux and other development environments.
        return Path.of(System.getProperty("user.home"),
                ".personal-agenda", "agenda.db");
    }

    public static Connection connect() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
    }

    public static Path databasePath() {
        return DB_PATH;
    }

    public static boolean hasExternalDataOverride() {
        String property = System.getProperty("agenda.data.dir");
        String environment = System.getenv("AGENDA_DATA_DIR");

        return (property != null && !property.isBlank())
                || (environment != null && !environment.isBlank());
    }

    public static void setDataDirectoryForNextLaunch(Path directory)
            throws IOException, SQLException, BackingStoreException {

        if (hasExternalDataOverride()) {
            throw new IOException(
                    "Data path is controlled by a JVM property "
                            + "or environment variable.");
        }

        Path selected = directory.toAbsolutePath().normalize();

        if (!Files.isDirectory(selected)
                || !Files.isWritable(selected)) {
            throw new IOException(
                    "Selected directory is missing or not writable.");
        }

        Path candidate = selected.resolve("agenda.db");

        if (Files.exists(candidate)) {

            if (!Files.isRegularFile(candidate)) {
                throw new IOException(
                        "agenda.db exists but is not a regular file.");
            }

            // Validate an existing SQLite database before saving.
            try (Connection conn = DriverManager.getConnection(
                    "jdbc:sqlite:" + candidate);
                    Statement statement = conn.createStatement()) {

                try (ResultSet rs = statement.executeQuery(
                        "PRAGMA quick_check")) {

                    if (!rs.next()
                            || !"ok".equalsIgnoreCase(rs.getString(1))) {
                        throw new SQLException(
                                "SQLite integrity check failed.");
                    }
                }

                try (ResultSet rs = statement.executeQuery("""
                        SELECT 1 FROM sqlite_master
                        WHERE type = 'table' AND name = 'tasks'
                        """)) {

                    if (!rs.next()) {
                        throw new SQLException(
                                "Selected database is not a Day 8 database.");
                    }
                }
            }
        }

        Preferences prefs = Preferences.userNodeForPackage(Database.class);

        prefs.put(DATA_DIR_PREF_KEY, selected.toString());
        prefs.flush();
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

            UiSortOrderRepository.ensureSchema(connection);

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
                            CREATE TABLE IF NOT EXISTS daily_plan_items (
                                local_date TEXT NOT NULL,
                                item_key TEXT NOT NULL,
                                task_id TEXT NOT NULL,
                                step_id TEXT,
                                task_title TEXT NOT NULL,
                                step_title TEXT,
                                category_name TEXT NOT NULL,
                                planned_minutes INTEGER NOT NULL
                                    CHECK(planned_minutes > 0),
                                completed INTEGER NOT NULL DEFAULT 0
                                    CHECK(completed IN (0, 1)),
                                recorded_at_ms INTEGER NOT NULL,
                                completed_at_ms INTEGER,
                                PRIMARY KEY (local_date, item_key)
                            )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS plan_change_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        occurred_at_ms INTEGER NOT NULL,
                        task_id TEXT NOT NULL,
                        item_key TEXT,
                        event_type TEXT NOT NULL CHECK (
                            event_type IN (
                                'SCHEDULE',
                                'RESCHEDULE',
                                'TO_BACKLOG',
                                'CORRECT_PLAN'
                            )
                        ),
                        old_date TEXT,
                        new_date TEXT,
                        affected_date TEXT,
                        old_planned_minutes INTEGER,
                        new_planned_minutes INTEGER,
                        reason TEXT
                    )
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

            statement.executeUpdate("""
                    CREATE TRIGGER IF NOT EXISTS trg_daily_step_completion
                    AFTER UPDATE OF completed ON task_steps
                    WHEN OLD.completed <> NEW.completed
                    BEGIN
                        UPDATE daily_plan_items
                        SET completed = NEW.completed,
                            completed_at_ms =
                                CASE WHEN NEW.completed = 1
                                     THEN CAST(strftime('%s', 'now') AS INTEGER) * 1000
                                     ELSE NULL
                                END
                        WHERE step_id = NEW.id
                          AND local_date = date('now', 'localtime');
                    END
                    """);

            statement.executeUpdate("""
                    CREATE TRIGGER IF NOT EXISTS trg_daily_task_completion
                    AFTER UPDATE OF completed ON tasks
                    WHEN OLD.completed <> NEW.completed
                    BEGIN
                        UPDATE daily_plan_items
                        SET completed = NEW.completed,
                            completed_at_ms =
                                CASE WHEN NEW.completed = 1
                                     THEN CAST(strftime('%s', 'now') AS INTEGER) * 1000
                                     ELSE NULL
                                END
                        WHERE task_id = NEW.id
                          AND step_id IS NULL
                          AND local_date = date('now', 'localtime');
                    END
                    """);

            // Capture today's scheduled items not yet recorded.
            // Existing snapshots must not be overwritten.
            statement.executeUpdate("""
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
                    WHERE t.planned_date = date('now', 'localtime')
                      AND (
                          CASE WHEN s.id IS NULL
                               THEN t.estimated_minutes
                               ELSE s.estimated_minutes END
                      ) > 0
                    """);

            statement.executeUpdate("""
                    CREATE VIEW IF NOT EXISTS daily_completion_snapshot AS
                    WITH effective_items AS (
                        SELECT
                            d.local_date,
                            d.task_id,
                            d.planned_minutes,
                            d.completed
                        FROM daily_plan_items d
                        WHERE d.step_id IS NOT NULL
                           OR NOT EXISTS (
                               SELECT 1
                               FROM daily_plan_items s
                               WHERE s.local_date = d.local_date
                                 AND s.task_id = d.task_id
                                 AND s.step_id IS NOT NULL
                           )
                    ),
                    per_task AS (
                        SELECT
                            local_date,
                            task_id,
                            SUM(planned_minutes) AS planned,
                            SUM(
                                CASE WHEN completed = 1
                                     THEN planned_minutes
                                     ELSE 0 END
                            ) AS completed
                        FROM effective_items
                        GROUP BY local_date, task_id
                    )
                    SELECT
                        local_date,
                        SUM(planned) AS planned_minutes,
                        SUM(completed) AS completed_minutes
                    FROM per_task
                    GROUP BY local_date
                    """);

            statement.executeUpdate("""
                    CREATE VIEW IF NOT EXISTS daily_completion_effective AS
                    WITH effective_items AS (
                        SELECT
                            d.local_date,
                            d.task_id,
                            d.completed,
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
                            ) AS effective_minutes
                        FROM daily_plan_items d
                        WHERE d.step_id IS NOT NULL
                           OR NOT EXISTS (
                               SELECT 1
                               FROM daily_plan_items s
                               WHERE s.local_date = d.local_date
                                 AND s.task_id = d.task_id
                                 AND s.step_id IS NOT NULL
                           )
                    ),
                    per_task AS (
                        SELECT
                            local_date,
                            task_id,
                            SUM(effective_minutes) AS planned,
                            SUM(
                                CASE WHEN completed = 1
                                     THEN effective_minutes
                                     ELSE 0 END
                            ) AS completed
                        FROM effective_items
                        WHERE effective_minutes > 0
                        GROUP BY local_date, task_id
                    )
                    SELECT
                        local_date,
                        SUM(planned) AS planned_minutes,
                        SUM(completed) AS completed_minutes
                    FROM per_task
                    GROUP BY local_date
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS completion_observations_v2 (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        observed_at_ms INTEGER NOT NULL,
                        local_date TEXT NOT NULL,
                        planned_minutes INTEGER NOT NULL,
                        completed_minutes INTEGER NOT NULL
                    )
                    """);

            statement.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_completion_v2_date
                    ON completion_observations_v2(local_date, id)
                    """);

            String recordV2 = """
                    INSERT INTO completion_observations_v2
                        (observed_at_ms, local_date,
                         planned_minutes, completed_minutes)
                    SELECT
                        CAST(strftime('%s', 'now') AS INTEGER) * 1000,
                        date('now', 'localtime'),
                        v.planned_minutes,
                        v.completed_minutes
                    FROM (
                        SELECT
                            COALESCE((
                                SELECT planned_minutes
                                FROM daily_completion_effective
                                WHERE local_date = date('now', 'localtime')
                            ), 0) AS planned_minutes,
                            COALESCE((
                                SELECT completed_minutes
                                FROM daily_completion_effective
                                WHERE local_date = date('now', 'localtime')
                            ), 0) AS completed_minutes
                    ) v
                    WHERE NOT EXISTS (
                        SELECT 1
                        FROM completion_observations_v2 o
                        WHERE o.id = (
                            SELECT MAX(id)
                            FROM completion_observations_v2
                            WHERE local_date = date('now', 'localtime')
                        )
                          AND o.planned_minutes = v.planned_minutes
                          AND o.completed_minutes = v.completed_minutes
                    )
                    """;

            for (String operation : new String[] {
                    "INSERT", "UPDATE", "DELETE" }) {

                String row = operation.equals("DELETE") ? "OLD" : "NEW";

                String triggerName = "trg_v2_daily_plan_"
                        + operation.toLowerCase(java.util.Locale.ROOT);

                statement.executeUpdate(
                        "DROP TRIGGER IF EXISTS " + triggerName);

                statement.executeUpdate(
                        "CREATE TRIGGER " + triggerName
                                + " AFTER " + operation
                                + " ON daily_plan_items"
                                + " WHEN " + row
                                + ".local_date = date('now', 'localtime')"
                                + " BEGIN " + recordV2 + "; END");
            }

            statement.executeUpdate(
                    "DROP TRIGGER IF EXISTS trg_v2_correct_plan_insert");

            statement.executeUpdate(
                    "CREATE TRIGGER trg_v2_correct_plan_insert "
                            + "AFTER INSERT ON plan_change_events "
                            + "WHEN NEW.event_type = 'CORRECT_PLAN' "
                            + "AND NEW.affected_date = date('now', 'localtime') "
                            + "BEGIN " + recordV2 + "; END");

            // Record the current baseline once on application startup.
            statement.executeUpdate(recordV2);

            // Capture the baseline of the current day.
            statement.executeUpdate(recordObservation);

        }

        System.out.println("Database initialized: " + DB_PATH);
    }
}