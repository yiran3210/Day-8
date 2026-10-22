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
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.time.*;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

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

        private String findTaskPlanKey(
                        LocalDate date,
                        String taskId) throws Exception {

                String sql = """
                                SELECT item_key
                                FROM daily_plan_items
                                WHERE local_date = ?
                                  AND task_id = ?
                                  AND step_id IS NULL
                                """;

                try (Connection conn = Database.connect();
                                PreparedStatement stmt = conn.prepareStatement(sql)) {

                        stmt.setString(1, date.toString());
                        stmt.setString(2, taskId);

                        try (ResultSet rs = stmt.executeQuery()) {
                                assertTrue(rs.next(), "Task snapshot must exist.");

                                String key = rs.getString("item_key");

                                assertFalse(rs.next(),
                                                "Task should have exactly one snapshot.");

                                return key;
                        }
                }
        }

        private long countCorrectPlanEvents(
                        LocalDate date,
                        String itemKey) throws Exception {

                String sql = """
                                SELECT COUNT(*)
                                FROM plan_change_events
                                WHERE event_type = 'CORRECT_PLAN'
                                  AND affected_date = ?
                                  AND item_key = ?
                                """;

                try (Connection conn = Database.connect();
                                PreparedStatement stmt = conn.prepareStatement(sql)) {

                        stmt.setString(1, date.toString());
                        stmt.setString(2, itemKey);

                        try (ResultSet rs = stmt.executeQuery()) {
                                assertTrue(rs.next());
                                return rs.getLong(1);
                        }
                }
        }

        private List<String> readPlanRows(
                        String source,
                        LocalDate date,
                        String taskId) throws Exception {

                if (!source.equals("daily_plan_items")
                                && !source.equals("daily_completion_effective")) {
                        throw new IllegalArgumentException(
                                        "Unsupported source: " + source);
                }

                String sql = source.equals("daily_plan_items")
                                ? """
                                                SELECT *
                                                FROM daily_plan_items
                                                WHERE local_date = ?
                                                  AND item_key LIKE ?
                                                ORDER BY item_key
                                                """
                                : """
                                                SELECT *
                                                FROM daily_completion_effective
                                                WHERE local_date = ?
                                                ORDER BY local_date
                                                """;

                List<String> rows = new ArrayList<>();

                try (Connection conn = Database.connect();
                                PreparedStatement stmt = conn.prepareStatement(sql)) {

                        stmt.setString(1, date.toString());

                        if (source.equals("daily_plan_items")) {
                                stmt.setString(2, "%" + taskId + "%");
                        }

                        try (ResultSet rs = stmt.executeQuery()) {

                                ResultSetMetaData metadata = rs.getMetaData();

                                while (rs.next()) {

                                        StringJoiner row = new StringJoiner(" | ");

                                        for (int i = 1; i <= metadata.getColumnCount(); i++) {

                                                row.add(
                                                                metadata.getColumnName(i)
                                                                                + "="
                                                                                + rs.getString(i));
                                        }

                                        rows.add(row.toString());
                                }
                        }
                }

                return rows;
        }

        @Test
        void backlogAndReschedulePreserveOriginalPlan()
                        throws Exception {

                LocalDate today = LocalDate.now();

                TaskRepository tasks = new TaskRepository();
                DailyPlanRepository dailyPlans = new DailyPlanRepository();

                Task task = new Task(
                                "QA historical snapshot " + UUID.randomUUID(),
                                75);

                task.setPlannedDate(today);
                tasks.insert(task, "General");

                // Ensure the original day snapshot is available.
                dailyPlans.findByDate(today);

                List<String> original = readPlanRows(
                                "daily_plan_items",
                                today,
                                task.getId());

                assertFalse(
                                original.isEmpty(),
                                "Scheduled Task must have an original snapshot.");

                // Move Today -> Backlog.
                tasks.updatePlannedDate(task.getId(), null);

                dailyPlans.findByDate(today);

                assertEquals(
                                original,
                                readPlanRows(
                                                "daily_plan_items",
                                                today,
                                                task.getId()),
                                "Moving to Backlog must preserve today's snapshot.");

                // Move Backlog -> Tomorrow.
                tasks.updatePlannedDate(
                                task.getId(),
                                today.plusDays(1));

                dailyPlans.findByDate(today);

                assertEquals(
                                original,
                                readPlanRows(
                                                "daily_plan_items",
                                                today,
                                                task.getId()),
                                "Rescheduling must preserve the original date.");

                // Edit title and estimated duration.
                tasks.updateDetails(
                                task.getId(),
                                "QA renamed historical task",
                                120);

                assertEquals(
                                original,
                                readPlanRows(
                                                "daily_plan_items",
                                                today,
                                                task.getId()),
                                "Later edits must not rewrite historical estimates.");

                // Delete the live Task.
                tasks.delete(task.getId());

                assertEquals(
                                original,
                                readPlanRows(
                                                "daily_plan_items",
                                                today,
                                                task.getId()),
                                "Deleting a Task must not delete its plan history.");
        }

        @Test
        void multipleBlocksDoNotDuplicatePlanBudget()
                        throws Exception {

                LocalDate today = LocalDate.now();

                TaskRepository tasks = new TaskRepository();
                TimeBlockRepository blocks = new TimeBlockRepository();
                DailyPlanRepository dailyPlans = new DailyPlanRepository();

                Task task = new Task(
                                "QA multiple blocks " + UUID.randomUUID(),
                                120);

                task.setPlannedDate(today);
                tasks.insert(task, "General");

                dailyPlans.findByDate(today);

                List<String> originalSnapshot = readPlanRows(
                                "daily_plan_items",
                                today,
                                task.getId());

                List<String> originalEffective = readPlanRows(
                                "daily_completion_effective",
                                today,
                                task.getId());

                assertFalse(originalSnapshot.isEmpty());

                assertFalse(
                                originalEffective.isEmpty(),
                                "Effective daily completion must exist before scheduling.");

                LocalDateTime start = today.atTime(9, 0);

                blocks.insert(new TimeBlock(
                                "QA scheduled block A",
                                start,
                                start.plusMinutes(60),
                                TimeBlock.Type.TASK,
                                task.getId(),
                                null));

                blocks.insert(new TimeBlock(
                                "QA scheduled block B",
                                start.plusMinutes(30),
                                start.plusMinutes(90),
                                TimeBlock.Type.TASK,
                                task.getId(),
                                null));

                // 09:00–10:00 and 09:30–10:30 overlap.
                assertEquals(
                                90,
                                blocks.sumScheduledMinutes(task.getId(), null),
                                "Overlapping schedule time must be counted once.");

                dailyPlans.findByDate(today);

                assertEquals(
                                originalSnapshot,
                                readPlanRows(
                                                "daily_plan_items",
                                                today,
                                                task.getId()),
                                "Scheduling more blocks must not create new plan budget.");

                assertEquals(
                                originalEffective,
                                readPlanRows(
                                                "daily_completion_effective",
                                                today,
                                                task.getId()),
                                "Effective completion weights must remain unchanged.");
        }

        @Test
        void correctPlanPreservesOriginalAndIsIdempotent()
                        throws Exception {

                LocalDate today = LocalDate.now();

                TaskRepository tasks = new TaskRepository();
                DailyPlanRepository dailyPlans = new DailyPlanRepository();
                WorkSessionRepository sessions = new WorkSessionRepository();

                Task task = new Task(
                                "QA Correct Plan " + UUID.randomUUID(),
                                60);

                task.setPlannedDate(today);
                tasks.insert(task, "General");

                int baseline = dailyPlans.findByDate(today).plannedMinutes();

                String itemKey = findTaskPlanKey(today, task.getId());

                List<String> original = readPlanRows(
                                "daily_plan_items", today, task.getId());

                long initialEvents = countCorrectPlanEvents(today, itemKey);

                long actualBefore = sessions.finishedMillisForDate(
                                today, ZoneId.systemDefault());

                // 60 -> 30
                dailyPlans.correctPlannedMinutes(
                                today, itemKey, 30, "QA correction to 30 minutes");

                assertEquals(
                                baseline - 30,
                                dailyPlans.findByDate(today).plannedMinutes());

                assertEquals(
                                initialEvents + 1,
                                countCorrectPlanEvents(today, itemKey));

                // 30 -> 0: exclude item from effective denominator.
                dailyPlans.correctPlannedMinutes(
                                today, itemKey, 0, "QA exclude planned item");

                assertEquals(
                                baseline - 60,
                                dailyPlans.findByDate(today).plannedMinutes());

                assertEquals(
                                initialEvents + 2,
                                countCorrectPlanEvents(today, itemKey));

                // 0 -> 60: restore original effective budget.
                dailyPlans.correctPlannedMinutes(
                                today, itemKey, 60, "QA restore original budget");

                assertEquals(
                                baseline,
                                dailyPlans.findByDate(today).plannedMinutes());

                assertEquals(
                                initialEvents + 3,
                                countCorrectPlanEvents(today, itemKey));

                // Same-value correction must not append another event.
                dailyPlans.correctPlannedMinutes(
                                today, itemKey, 60, "QA repeated identical value");

                assertEquals(
                                initialEvents + 3,
                                countCorrectPlanEvents(today, itemKey));

                // Original immutable snapshot must remain unchanged.
                assertEquals(
                                original,
                                readPlanRows(
                                                "daily_plan_items", today, task.getId()));

                // Correct Plan must never modify Actual Focus time.
                assertEquals(
                                actualBefore,
                                sessions.finishedMillisForDate(
                                                today, ZoneId.systemDefault()));
        }

        @Test
        void checkboxUpdatesCompletionWithoutCreatingFocusTime()
                        throws Exception {

                LocalDate today = LocalDate.now();

                TaskRepository tasks = new TaskRepository();
                DailyPlanRepository dailyPlans = new DailyPlanRepository();
                WorkSessionRepository sessions = new WorkSessionRepository();

                Task task = new Task(
                                "QA Checkbox Independence " + UUID.randomUUID(),
                                45);

                task.setPlannedDate(today);
                tasks.insert(task, "General");

                var before = dailyPlans.findByDate(today);

                int plannedBefore = before.plannedMinutes();
                int completedBefore = before.completedMinutes();

                long actualBefore = sessions.finishedMillisForDate(
                                today, ZoneId.systemDefault());

                // Check the Task as completed.
                tasks.updateCompleted(task.getId(), true);

                var completed = dailyPlans.findByDate(today);

                assertEquals(
                                plannedBefore,
                                completed.plannedMinutes());

                assertEquals(
                                completedBefore + 45,
                                completed.completedMinutes());

                assertEquals(
                                actualBefore,
                                sessions.finishedMillisForDate(
                                                today, ZoneId.systemDefault()));

                // Uncheck: completion should return to previous value.
                tasks.updateCompleted(task.getId(), false);

                var unchecked = dailyPlans.findByDate(today);

                assertEquals(
                                plannedBefore,
                                unchecked.plannedMinutes());

                assertEquals(
                                completedBefore,
                                unchecked.completedMinutes());

                assertEquals(
                                actualBefore,
                                sessions.finishedMillisForDate(
                                                today, ZoneId.systemDefault()));
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