package dev.personalagenda.data;

import dev.personalagenda.model.TimeBlock;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;

public class TimeBlockRepository {

    public void insert(TimeBlock block) throws SQLException {

        String sql = """
                INSERT INTO time_blocks
                (id, title, start_time, end_time, type, task_id, step_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                                """;

        try (Connection conn = Database.connect();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, block.id());
            stmt.setString(2, block.title());
            stmt.setString(3, block.start().toString());
            stmt.setString(4, block.end().toString());
            stmt.setString(5, block.type().name());
            stmt.setString(6, block.taskId());
            stmt.setString(7, block.stepId());

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
                            rs.getString("task_id"),
                            rs.getString("step_id"));
                    ;

                    blocks.add(block);
                }
            }
        }

        return blocks;
    }

    public long sumScheduledMinutes(
            String taskId,
            String stepId) throws SQLException {

        if (taskId == null || taskId.isBlank()) {
            return 0;
        }

        String sql = """
                SELECT start_time, end_time
                FROM time_blocks
                WHERE type = 'TASK'
                  AND task_id = ?
                  AND (? IS NULL OR step_id = ?)
                """;

        List<ScheduledRange> ranges = new ArrayList<>();

        try (Connection conn = Database.connect();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, taskId);
            stmt.setString(2, stepId);
            stmt.setString(3, stepId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    LocalDateTime start = LocalDateTime.parse(
                            rs.getString("start_time"));

                    LocalDateTime end = LocalDateTime.parse(
                            rs.getString("end_time"));

                    ranges.add(new ScheduledRange(start, end));
                }
            }
        }

        return distinctScheduledMinutes(ranges);
    }

    public long sumScheduledMinutesForDate(
            String taskId,
            String stepId,
            LocalDate date) throws SQLException {

        if (date == null) {
            throw new IllegalArgumentException("Date is required.");
        }

        if (taskId == null || taskId.isBlank()) {
            return 0L;
        }

        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();

        List<ScheduledRange> ranges = new ArrayList<>();

        for (TimeBlock block : findForDate(date)) {

            if (block.type() != TimeBlock.Type.TASK
                    || !taskId.equals(block.taskId())) {
                continue;
            }

            if (stepId != null
                    && !stepId.equals(block.stepId())) {
                continue;
            }

            LocalDateTime from = block.start().isAfter(dayStart)
                    ? block.start()
                    : dayStart;

            LocalDateTime to = block.end().isBefore(dayEnd)
                    ? block.end()
                    : dayEnd;

            if (to.isAfter(from)) {
                ranges.add(new ScheduledRange(from, to));
            }
        }

        return distinctScheduledMinutes(ranges);
    }

    private record ScheduledRange(
            LocalDateTime start,
            LocalDateTime end) {
    }

    private static long distinctScheduledMinutes(
            List<ScheduledRange> ranges) {

        if (ranges.isEmpty()) {
            return 0L;
        }

        ranges.sort(Comparator.comparing(ScheduledRange::start));

        LocalDateTime currentStart = ranges.get(0).start();
        LocalDateTime currentEnd = ranges.get(0).end();

        long minutes = 0L;

        for (int i = 1; i < ranges.size(); i++) {
            ScheduledRange next = ranges.get(i);

            if (!next.start().isAfter(currentEnd)) {
                if (next.end().isAfter(currentEnd)) {
                    currentEnd = next.end();
                }
            } else {
                minutes += Duration.between(
                        currentStart, currentEnd).toMinutes();

                currentStart = next.start();
                currentEnd = next.end();
            }
        }

        minutes += Duration.between(
                currentStart, currentEnd).toMinutes();

        return minutes;
    }

    public List<TimeBlock> findOverlaps(
            LocalDateTime start,
            LocalDateTime end,
            String excludeId) throws SQLException {

        if (!end.isAfter(start)) {
            throw new IllegalArgumentException(
                    "End must be after start.");
        }

        String sql = """
                SELECT *
                FROM time_blocks
                WHERE start_time < ?
                  AND end_time > ?
                  AND (? IS NULL OR id <> ?)
                ORDER BY start_time
                """;

        List<TimeBlock> overlaps = new ArrayList<>();

        try (Connection conn = Database.connect();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, end.toString());
            stmt.setString(2, start.toString());
            stmt.setString(3, excludeId);
            stmt.setString(4, excludeId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    overlaps.add(new TimeBlock(
                            rs.getString("id"),
                            rs.getString("title"),
                            LocalDateTime.parse(rs.getString("start_time")),
                            LocalDateTime.parse(rs.getString("end_time")),
                            TimeBlock.Type.valueOf(rs.getString("type")),
                            rs.getString("task_id"),
                            rs.getString("step_id")));
                }
            }
        }

        return overlaps;
    }

    public void delete(String id) throws SQLException {

        String sql = "DELETE FROM  time_blocks WHERE id = ?";

        try (Connection conn = Database.connect();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);

            int deleted = stmt.executeUpdate();

            if (deleted != 1) {
                throw new SQLException(
                        "Activity has already been deleted: " + id);
            }
        }
    }

    public void update(TimeBlock block) throws SQLException {

        String sql = """
                                UPDATE time_blocks
                                SET title = ?, start_time = ?, end_time = ?,
                type = ?, task_id = ?, step_id = ?
                WHERE id = ?
                                """;

        try (Connection conn = Database.connect();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, block.title());
            stmt.setString(2, block.start().toString());
            stmt.setString(3, block.end().toString());
            stmt.setString(4, block.type().name());
            stmt.setString(5, block.taskId());
            stmt.setString(6, block.stepId());
            stmt.setString(7, block.id());
            ;

            int updated = stmt.executeUpdate();

            if (updated != 1) {
                throw new SQLException(
                        "Activity no longer exists: " + block.id());
            }
        }
    }

}