
package dev.personalagenda.data;

import java.sql.*;
import java.util.*;
import java.util.function.Function;

public final class UiSortOrderRepository {

    @FunctionalInterface
    public interface ConnectionSource {
        Connection open() throws SQLException;
    }

    private final ConnectionSource source;

    public UiSortOrderRepository() {
        this(Database::connect);
    }

    public UiSortOrderRepository(ConnectionSource source) {
        this.source = source;
    }

    public static void ensureSchema(Connection connection)
            throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS ui_sort_orders (
                        scope TEXT NOT NULL,
                        item_id TEXT NOT NULL,
                        position INTEGER NOT NULL,
                        PRIMARY KEY (scope, item_id)
                    )
                    """);
        }
    }

    public <T> List<T> ordered(
            String scope,
            List<T> items,
            Function<T, String> idOf) throws SQLException {

        Map<String, Integer> positions = new HashMap<>();

        try (Connection connection = source.open();
                PreparedStatement statement = connection.prepareStatement("""
                        SELECT item_id, position
                        FROM ui_sort_orders
                        WHERE scope = ?
                        """)) {

            statement.setString(1, scope);

            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    positions.put(
                            rows.getString(1),
                            rows.getInt(2));
                }
            }
        }

        List<T> result = new ArrayList<>(items);

        // Stable sort retains the default order for unsaved items.
        result.sort(Comparator.comparingInt(
                item -> positions.getOrDefault(
                        idOf.apply(item), Integer.MAX_VALUE)));

        return result;
    }

    public void move(
            String scope,
            List<String> visibleIds,
            String movingId,
            String targetId,
            boolean after) throws SQLException {

        save(scope, moved(
                visibleIds, movingId, targetId, after));
    }

    public static List<String> moved(
            List<String> ids,
            String movingId,
            String targetId,
            boolean after) {

        if (ids == null || ids.isEmpty()
                || new HashSet<>(ids).size() != ids.size()
                || !ids.contains(movingId)
                || !ids.contains(targetId)) {
            throw new IllegalArgumentException(
                    "Invalid sort group or item");
        }

        List<String> result = new ArrayList<>(ids);

        if (movingId.equals(targetId)) {
            return result;
        }

        result.remove(movingId);

        int insertAt = result.indexOf(targetId)
                + (after ? 1 : 0);

        result.add(insertAt, movingId);

        return result;
    }

    public void save(String scope, List<String> ids)
            throws SQLException {

        if (scope == null || scope.isBlank()
                || ids == null
                || ids.stream().anyMatch(
                        id -> id == null || id.isBlank())
                || new HashSet<>(ids).size() != ids.size()) {
            throw new IllegalArgumentException(
                    "Invalid UI sort order");
        }

        try (Connection connection = source.open()) {
            connection.setAutoCommit(false);

            try {
                try (PreparedStatement delete = connection.prepareStatement("""
                        DELETE FROM ui_sort_orders
                        WHERE scope = ?
                        """)) {
                    delete.setString(1, scope);
                    delete.executeUpdate();
                }

                try (PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO ui_sort_orders
                            (scope, item_id, position)
                        VALUES (?, ?, ?)
                        """)) {

                    for (int i = 0; i < ids.size(); i++) {
                        insert.setString(1, scope);
                        insert.setString(2, ids.get(i));
                        insert.setInt(3, i);
                        insert.addBatch();
                    }

                    insert.executeBatch();
                }

                connection.commit();

            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            }
        }
    }

    public void reset(String scope) throws SQLException {
        try (Connection connection = source.open();
                PreparedStatement statement = connection.prepareStatement("""
                        DELETE FROM ui_sort_orders
                        WHERE scope = ?
                        """)) {

            statement.setString(1, scope);
            statement.executeUpdate();
        }
    }
}
