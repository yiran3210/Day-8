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
                SELECT *
                FROM completion_observations
                WHERE local_date >= ?
                  AND local_date < ?
                ORDER BY local_date, id
                """;

        List<CompletionObservation> result = new ArrayList<>();

        try (Connection conn = Database.connect();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, startInclusive.toString());
            stmt.setString(2, endExclusive.toString());

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