
package dev.personalagenda.data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UiSortOrderRepositoryTest {

    @TempDir
    Path directory;

    private UiSortOrderRepository openStore()
            throws Exception {

        String url = "jdbc:sqlite:"
                + directory.resolve("ui-only.db");

        try (Connection connection = DriverManager.getConnection(url)) {

            UiSortOrderRepository.ensureSchema(connection);
            UiSortOrderRepository.ensureSchema(connection);
        }

        return new UiSortOrderRepository(
                () -> DriverManager.getConnection(url));
    }

    @Test
    void defaultAndSavedOrderPersist() throws Exception {
        var store = openStore();
        var defaults = List.of("A", "B", "C");

        assertEquals(defaults,
                store.ordered("projects", defaults, x -> x));

        store.move(
                "projects", defaults, "C", "A", false);

        assertEquals(
                List.of("C", "A", "B"),
                store.ordered("projects", defaults, x -> x));
    }

    @Test
    void differentDatesStayIndependent() throws Exception {
        var store = openStore();
        var defaults = List.of("A", "B", "C");

        store.move(
                "dayplan:2026-10-23",
                defaults, "B", "A", false);

        assertEquals(
                List.of("B", "A", "C"),
                store.ordered(
                        "dayplan:2026-10-23",
                        defaults, x -> x));

        assertEquals(
                defaults,
                store.ordered(
                        "dayplan:2026-10-24",
                        defaults, x -> x));
    }

    @Test
    void movingDownAndResetWork() throws Exception {
        var store = openStore();
        var defaults = List.of("A", "B", "C");

        store.move(
                "task:backlog",
                defaults, "A", "C", true);

        assertEquals(
                List.of("B", "C", "A"),
                store.ordered(
                        "task:backlog", defaults, x -> x));

        store.reset("task:backlog");

        assertEquals(
                defaults,
                store.ordered(
                        "task:backlog", defaults, x -> x));
    }

    @Test
    void invalidItemCannotBeMoved() throws Exception {
        var store = openStore();

        assertThrows(IllegalArgumentException.class,
                () -> store.move(
                        "projects",
                        List.of("A", "B"),
                        "outsider", "A", false));

        assertEquals(
                List.of("A", "B"),
                store.ordered(
                        "projects",
                        List.of("A", "B"),
                        x -> x));
    }

    @Test
    void sortingDoesNotEditBusinessTable()
            throws Exception {

        var store = openStore();

        String url = "jdbc:sqlite:"
                + directory.resolve("ui-only.db");

        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {

            statement.executeUpdate(
                    "CREATE TABLE tasks "
                            + "(id TEXT, planned_date TEXT)");

            statement.executeUpdate(
                    "INSERT INTO tasks VALUES "
                            + "('A', '2026-10-23')");
        }

        store.move(
                "task:2026-10-23",
                List.of("A", "B"),
                "B", "A", false);

        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT id, planned_date FROM tasks")) {

            assertTrue(rows.next());
            assertEquals("A", rows.getString(1));
            assertEquals("2026-10-23", rows.getString(2));
            assertFalse(rows.next());
        }
    }
}
