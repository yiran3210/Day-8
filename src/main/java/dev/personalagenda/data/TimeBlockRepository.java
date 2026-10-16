package dev.personalagenda.data;

import dev.personalagenda.model.TimeBlock;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class TimeBlockRepository {

    public void insert(TimeBlock block) throws SQLException {

        String sql = """
                INSERT INTO time_blocks
                (id, title, start_time, end_time, type, task_id)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = Database.connect();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, block.id());
            stmt.setString(2, block.title());
            stmt.setString(3, block.start().toString());
            stmt.setString(4, block.end().toString());
            stmt.setString(5, block.type().name());
            stmt.setString(6, block.taskId());

            stmt.executeUpdate();
        }
    }

    public List<TimeBlock> findForDate(LocalDate date) throws SQLException {

        List<TimeBlock> blocks = new ArrayList<>();

        String sql = """
                SELECT *
                FROM time_blocks
                WHERE end_time > ? AND start_time < ?
                ORDER BY start_time
                """;

        try (Connection conn = Database.connect();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, date.atStartOfDay().toString());
            stmt.setString(2, date.plusDays(1).atStartOfDay().toString());

            try (ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {

                    TimeBlock block = new TimeBlock(
                            rs.getString("id"),
                            rs.getString("title"),
                            LocalDateTime.parse(rs.getString("start_time")),
                            LocalDateTime.parse(rs.getString("end_time")),
                            TimeBlock.Type.valueOf(rs.getString("type")),
                            rs.getString("task_id"));

                    blocks.add(block);
                }
            }
        }

        return blocks;
    }

    public void delete(String id) throws SQLException {

        String sql = "DELETE FROM  time_blocks WHERE id = ?";

        try (Connection conn = Database.connect();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            stmt.executeUpdate();
        }
    }
}