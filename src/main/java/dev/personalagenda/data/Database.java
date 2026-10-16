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

    private static final Path DB_PATH = Path.of("F:/AgendaData/agenda.db");

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

        }

        System.out.println("Database initialized: " + DB_PATH);
    }
}