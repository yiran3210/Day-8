package dev.personalagenda.data;

import dev.personalagenda.model.CompletionObservation;

import java.sql.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class CompletionObservationRepository {

    public List<CompletionObservation> findBetween(
            LocalDate startInclusive,
            LocalDate endExclusive) throws SQLException {

        String sql = """
                                                SELECT local_date, observed_at_ms,
                                                       planned_minutes, completed_minutes
                                                FROM (
                                                    SELECT id, local_date, observed_at_ms,
                                                           planned_minutes, completed_minutes
                                                    FROM completion_observations_v2
                                                    WHERE local_date >= ?
                                                      AND local_date < ?

                                                    UNION ALL

                                                    SELECT o.id, o.local_date, o.observed_at_ms,
                                                           o.planned_minutes, o.completed_minutes
                                                    FROM completion_observations o
                                                    WHERE o.local_date >= ?
                                                      AND o.local_date < ?
                AND NOT EXISTS (
                    SELECT 1
                    FROM completion_observations_v2 v2
                    WHERE v2.local_date = o.local_date
                      AND v2.observed_at_ms <= o.observed_at_ms
                )
                                                )
                                                ORDER BY local_date, observed_at_ms, id
                                                """;

        List<CompletionObservation> result = new ArrayList<>();

        try (Connection conn = Database.connect();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, startInclusive.toString());
            stmt.setString(2, endExclusive.toString());
            stmt.setString(3, startInclusive.toString());
            stmt.setString(4, endExclusive.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(new CompletionObservation(
                            LocalDate.parse(
                                    rs.getString("local_date")),
                            Instant.ofEpochMilli(
                                    rs.getLong("observed_at_ms")),
                            rs.getInt("planned_minutes"),
                            rs.getInt("completed_minutes")));
                }
            }
        }

        return result;
    }
}