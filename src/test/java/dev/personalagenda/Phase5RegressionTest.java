package dev.personalagenda;

import dev.personalagenda.data.*;
import dev.personalagenda.model.*;
import dev.personalagenda.service.FocusSessionService;
import dev.personalagenda.view.ScheduleTimeValidation;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.*;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class Phase5RegressionTest {

    @TempDir
    static Path testDir;

    @BeforeAll
    static void initializeTestDatabase() throws Exception {

        System.setProperty("agenda.data.dir", testDir.toString());

        // Safety guard: fail before initialization if the path is wrong.
        assertEquals(
                testDir.resolve("agenda.db")
                        .toAbsolutePath().normalize(),
                Database.databasePath(),
                "Test must never use the production database.");

        Database.initialize();
    }

    @Test
    void overlappingBlocksCountUniqueScheduledMinutes()
            throws Exception {

        Task task = new Task("QA overlapping schedule", 120);
        new TaskRepository().insert(task, "General");

        TimeBlockRepository blocks = new TimeBlockRepository();

        LocalDateTime start = LocalDate.of(2026, 11, 12).atTime(9, 0);

        blocks.insert(new TimeBlock(
                "Block 1",
                start,
                start.plusMinutes(60),
                TimeBlock.Type.TASK,
                task.getId(),
                null));

        blocks.insert(new TimeBlock(
                "Block 2",
                start.plusMinutes(30),
                start.plusMinutes(90),
                TimeBlock.Type.TASK,
                task.getId(),
                null));

        // 09:00–10:00 + 09:30–10:30 = 90 unique minutes.
        assertEquals(
                90,
                blocks.sumScheduledMinutes(task.getId(), null));
    }

    @Test
    void unlinkedFocusDoesNotCreateTask()
            throws Exception {

        WorkSessionRepository sessions = new WorkSessionRepository();

        FocusSessionService focus = new FocusSessionService(sessions);

        String title = "QA unlinked " + UUID.randomUUID();

        WorkSession first = focus.startUnlinked(title);

        assertNull(first.taskId());
        assertNull(first.stepId());

        focus.pause();

        assertEquals(
                FocusSessionService.State.PAUSED,
                focus.state());

        WorkSession second = focus.resumeUnlinked();

        assertNotEquals(first.id(), second.id());
        assertNull(second.taskId());

        focus.stop();

        assertEquals(
                FocusSessionService.State.IDLE,
                focus.state());

        long count = sessions.findFinishedOverlapping(
                Instant.now().minusSeconds(3600),
                Instant.now().plusSeconds(3600))
                .stream()
                .filter(s -> title.equals(s.taskTitle()))
                .count();

        assertEquals(2, count);

        try (Connection connection = Database.connect();
                PreparedStatement stmt = connection.prepareStatement(
                        "SELECT COUNT(*) FROM tasks WHERE title = ?")) {

            stmt.setString(1, title);

            try (var rs = stmt.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1));
            }
        }
    }

    @Test
    void cannotDeleteTaskOrStepDuringActiveFocus()
            throws Exception {

        TaskRepository tasks = new TaskRepository();
        TaskStepRepository steps = new TaskStepRepository();
        WorkSessionRepository sessions = new WorkSessionRepository();

        Task task = new Task("QA deletion guard", 60);
        tasks.insert(task, "General");

        TaskStep step = new TaskStep(task.getId(), "QA Step", 30);

        steps.insert(step);

        WorkSession active = sessions.start(task, step);

        assertThrows(
                java.sql.SQLException.class,
                () -> steps.delete(step.id()));

        assertThrows(
                java.sql.SQLException.class,
                () -> tasks.delete(task.getId()));

        assertTrue(tasks.exists(task.getId()));

        assertTrue(
                steps.findByTaskId(task.getId()).stream()
                        .anyMatch(s -> s.id().equals(step.id())));

        assertTrue(sessions.stop(active.id(), Instant.now()));

        assertDoesNotThrow(() -> steps.delete(step.id()));
        assertDoesNotThrow(() -> tasks.delete(task.getId()));

        assertFalse(tasks.exists(task.getId()));

        assertTrue(
                sessions.findFinishedOverlapping(
                        active.startedAt().minusSeconds(1),
                        Instant.now().plusSeconds(60))
                        .stream()
                        .anyMatch(s -> s.id().equals(active.id())));
    }

    @Test
    void actualHoursHandleAutumnDstAndPauseGap()
            throws Exception {

        ZoneId london = ZoneId.of("Europe/London");

        LocalDate day = LocalDate.of(2026, 10, 25);

        // UK autumn clock change: this day has 25 hours.
        assertEquals(
                25,
                Duration.between(
                        day.atStartOfDay(london).toInstant(),
                        day.plusDays(1)
                                .atStartOfDay(london).toInstant())
                        .toHours());

        insertFinishedSession(
                "2026-10-25T00:20:00Z",
                "2026-10-25T00:50:00Z");

        // A 20-minute gap, representing paused time.
        insertFinishedSession(
                "2026-10-25T01:10:00Z",
                "2026-10-25T01:40:00Z");

        WorkSessionRepository sessions = new WorkSessionRepository();

        assertEquals(
                Duration.ofMinutes(60).toMillis(),
                sessions.finishedMillisForDate(day, london));
    }

    @Test
    void dstValidationRejectsAmbiguousPlannedTimes() {

        ZoneId london = ZoneId.of("Europe/London");

        assertNotNull(ScheduleTimeValidation.check(
                LocalDateTime.of(2026, 10, 25, 1, 30),
                LocalDateTime.of(2026, 10, 25, 2, 30),
                london));

        assertNotNull(ScheduleTimeValidation.check(
                LocalDateTime.of(2026, 3, 29, 1, 30),
                LocalDateTime.of(2026, 3, 29, 2, 30),
                london));

        assertNull(ScheduleTimeValidation.check(
                LocalDateTime.of(2026, 10, 25, 2, 0),
                LocalDateTime.of(2026, 10, 25, 3, 0),
                london));
    }

    private void insertFinishedSession(
            String startIso,
            String endIso) throws Exception {

        String sql = """
                INSERT INTO work_sessions
                (id, task_id, step_id, task_title, step_title,
                 started_at_ms, ended_at_ms)
                VALUES (?, NULL, NULL, ?, NULL, ?, ?)
                """;

        try (Connection connection = Database.connect();
                PreparedStatement stmt = connection.prepareStatement(sql)) {

            stmt.setString(1, UUID.randomUUID().toString());
            stmt.setString(2, "QA DST Focus");
            stmt.setLong(
                    3, Instant.parse(startIso).toEpochMilli());
            stmt.setLong(
                    4, Instant.parse(endIso).toEpochMilli());

            stmt.executeUpdate();
        }
    }
}