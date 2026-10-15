package dev.personalagenda.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

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
                    completed INTEGER NOT NULL DEFAULT 0
                )
                """;

        try (Connection connection = connect();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }

        System.out.println("Database initialized: " + DB_PATH);
    }
}