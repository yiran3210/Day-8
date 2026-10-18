
package dev.personalagenda.view;

import dev.personalagenda.data.TaskRepository;
import dev.personalagenda.data.TaskStepRepository;
import dev.personalagenda.data.TimeBlockRepository;
import dev.personalagenda.data.ProjectRepository;
import dev.personalagenda.data.CategoryRepository;
import dev.personalagenda.data.WorkSessionRepository;
import dev.personalagenda.model.Project;
import dev.personalagenda.model.Task;
import dev.personalagenda.model.TaskStep;
import dev.personalagenda.model.TimeBlock;
import dev.personalagenda.model.WorkSession;

import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.StringConverter;

public class FloatingWidget {

        private final Stage window = new Stage();
        private final Stage mainWindow;
        private final Runnable onDataChanged;

        private final TaskRepository tasks = new TaskRepository();
        private final TaskStepRepository steps = new TaskStepRepository();
        private final TimeBlockRepository blocks = new TimeBlockRepository();

        private final Label currentLabel = new Label();
        private final Label nextLabel = new Label();
        private final Label dailyLabel = new Label();
        private final Label errorLabel = new Label();

        private final CompositeProgressBar dailyBar = new CompositeProgressBar();

        private final CompositeProgressBar taskBar = new CompositeProgressBar();

        private final CompositeProgressBar projectBar = new CompositeProgressBar();

        private final Label taskProgressLabel = new Label();
        private final Label projectProgressLabel = new Label();
        private final Label projectExpectedLabel = new Label();
        private final Label clockTimeLabel = new Label();

        private Task currentFocusedTask;

        private final Label clockFocusLabel = new Label();
        private final Label clockFocusCategory = new Label();

        private final VBox clockFocusCard = new VBox(
                        5, clockFocusLabel, clockFocusCategory);

        private final ClockFace clockFace = new ClockFace();

        private final ComboBox<Task> focusTaskChoice = new ComboBox<>();

        private final ComboBox<Project> clockProjectChoice = new ComboBox<>();

        private final ProjectRepository projects = new ProjectRepository();

        private final CategoryRepository categories = new CategoryRepository();

        private boolean updatingFocusChoice = false;
        private String manualFocusTaskId = null;

        private final VBox taskControls = new VBox(6);

        // Persistent focus sessions
        private final WorkSessionRepository workSessions = new WorkSessionRepository();

        private final ComboBox<TaskStep> focusStepChoice = new ComboBox<>();

        private final Label sessionTimerLabel = new Label("00:00:00");
        private final Label sessionStatusLabel = new Label("Ready to focus");

        private final Button sessionStartButton = new Button("Start");
        private final Button sessionPauseButton = new Button("Pause");
        private final Button sessionStopButton = new Button("Stop");
        private final Button recoverSessionButton = new Button("Recover");

        private final Button autoFocusButton = new Button("Auto");

        private WorkSession activeSession;
        private WorkSession pausedSession;

        private Duration focusElapsed = Duration.ZERO;
        private boolean unresolvedSession = false;

        private final Timeline sessionTicker = new Timeline(
                        new KeyFrame(
                                        javafx.util.Duration.seconds(1),
                                        event -> updateSessionTimer()));

        private final Timeline clock = new Timeline(
                        new KeyFrame(
                                        javafx.util.Duration.seconds(30),
                                        event -> refresh()));

        public FloatingWidget(
                        Stage mainWindow,
                        Runnable onDataChanged) {

                this.mainWindow = mainWindow;
                this.onDataChanged = onDataChanged;

                window.initStyle(StageStyle.DECORATED);
                window.setTitle("Agenda · Mini");
                AppIcons.apply(window);
                window.setAlwaysOnTop(true);
                window.setMinWidth(280);
                window.setMinHeight(260);

                // begin change

                Label heading = new Label("Agenda Mini");
                heading.getStyleClass().add("mini-heading");

                // Daily progress: always visible in both tabs
                Label todayTitle = new Label("Today's Progress");

                todayTitle.getStyleClass().add("mini-section-title");
                dailyLabel.getStyleClass().add("mini-stat");
                taskProgressLabel.getStyleClass().add("mini-stat");
                projectExpectedLabel.getStyleClass().add("mini-stat");

                dailyBar.setColor(Color.web("#5F91BC"));

                VBox dailySection = new VBox(
                                6, todayTitle, dailyBar, dailyLabel);
                dailySection.getStyleClass().add("mini-card");

                // Focus tab
                Label focusTitle = new Label("Current Task");
                focusTaskChoice.setMaxWidth(Double.MAX_VALUE);
                focusTaskChoice.setPromptText("Choose focus task");

                focusTaskChoice.setOnAction(event -> {
                        if (updatingFocusChoice) {
                                return;
                        }

                        Task selected = focusTaskChoice.getValue();
                        manualFocusTaskId = selected == null ? null : selected.getId();

                        refresh();
                });

                autoFocusButton.setOnAction(event -> {
                        if (activeSession != null || pausedSession != null) {
                                return;
                        }

                        manualFocusTaskId = null;
                        refresh();
                });

                HBox focusPicker = new HBox(
                                6, focusTaskChoice, autoFocusButton);

                taskBar.setColor(Color.web("#887ABD"));

                focusStepChoice.setPromptText("Whole task");
                focusStepChoice.setMaxWidth(Double.MAX_VALUE);

                focusStepChoice.setConverter(new StringConverter<>() {

                        @Override
                        public String toString(TaskStep step) {
                                return step == null
                                                ? "Whole task"
                                                : step.title() + " · "
                                                                + step.estimatedMinutes() + " min";
                        }

                        @Override
                        public TaskStep fromString(String text) {
                                return null;
                        }
                });

                sessionTimerLabel.setStyle(
                                "-fx-font-size: 23px; -fx-font-weight: bold;");

                sessionStatusLabel.setWrapText(true);

                sessionStartButton.setOnAction(
                                event -> startFocusSession());

                sessionPauseButton.setOnAction(
                                event -> pauseFocusSession());

                sessionStopButton.setOnAction(
                                event -> stopFocusSession());

                recoverSessionButton.setOnAction(
                                event -> recoverUnfinishedSession());

                recoverSessionButton.managedProperty().bind(
                                recoverSessionButton.visibleProperty());

                HBox sessionButtons = new HBox(
                                6,
                                sessionStartButton,
                                sessionPauseButton,
                                sessionStopButton,
                                recoverSessionButton);

                sessionButtons.setAlignment(
                                javafx.geometry.Pos.CENTER_LEFT);

                VBox sessionControls = new VBox(
                                6,
                                new Label("Focus Step"),
                                focusStepChoice,
                                sessionTimerLabel,
                                sessionButtons,
                                sessionStatusLabel);

                updateSessionTimer();

                ScrollPane taskScroll = new ScrollPane(taskControls);
                taskScroll.setFitToWidth(true);
                taskScroll.setPrefHeight(72);
                taskScroll.setMaxHeight(96);

                VBox focusContent = new VBox(
                                10,
                                currentLabel,
                                focusTitle,
                                focusPicker, sessionControls,
                                taskBar,
                                taskProgressLabel,
                                taskScroll,
                                new Separator(),
                                nextLabel);
                focusContent.getStyleClass().add("mini-card");

                // Clock tab
                clockProjectChoice.setMaxWidth(Double.MAX_VALUE);

                clockProjectChoice.setConverter(
                                new StringConverter<Project>() {

                                        @Override
                                        public String toString(Project project) {
                                                return project == null
                                                                ? "Choose project"
                                                                : project.name();
                                        }

                                        @Override
                                        public Project fromString(String value) {
                                                return null;
                                        }
                                });

                clockProjectChoice.setOnAction(event -> {
                        try {
                                refreshProjectProgress(tasks.findAll());
                        } catch (SQLException ex) {
                                errorLabel.setText("Project load failed.");
                                ex.printStackTrace();
                        }
                });

                clockTimeLabel.getStyleClass().add("mini-clock-time");

                clockFocusLabel.setWrapText(true);
                clockFocusCategory.setWrapText(true);

                clockFocusCard.setPrefWidth(120);
                clockFocusCard.setMaxWidth(120);
                clockFocusCard.getStyleClass().add("clock-focus-card");

                VBox clockInfo = new VBox(
                                12, clockTimeLabel, clockFocusCard);

                HBox clockHero = new HBox(
                                10, clockFace, clockInfo);

                clockHero.setAlignment(
                                javafx.geometry.Pos.CENTER_LEFT);

                VBox clockContent = new VBox(
                                10,
                                clockHero,
                                new Label("Project Progress"),
                                clockProjectChoice,
                                projectBar,
                                projectProgressLabel,
                                projectExpectedLabel);

                clockContent.setAlignment(
                                javafx.geometry.Pos.TOP_CENTER);
                clockContent.getStyleClass().add("mini-card");

                // Two tabs
                Tab focusTab = new Tab("Focus", focusContent);
                Tab clockTab = new Tab("Clock", clockContent);

                focusTab.setClosable(false);
                clockTab.setClosable(false);

                TabPane tabs = new TabPane(focusTab, clockTab);

                // Footer
                Button openButton = new Button("Open Agenda");
                openButton.setOnAction(event -> {
                        mainWindow.show();
                        mainWindow.setIconified(false);
                        mainWindow.toFront();
                });

                Button refreshButton = new Button("↻");
                refreshButton.setOnAction(event -> refresh());

                HBox footer = new HBox(
                                8, openButton, refreshButton);

                VBox root = new VBox(
                                11,
                                dailySection,
                                tabs,
                                errorLabel,
                                footer);

                root.setPadding(new Insets(10));
                root.setSpacing(7);
                root.getStyleClass().add("mini-widget");

                root.setPrefWidth(340);

                Scene scene = new Scene(root);

                scene.getStylesheets().add(
                                getClass().getResource(
                                                "/styles/light.css").toExternalForm());

                window.setScene(scene);

                tabs.setMinHeight(Region.USE_PREF_SIZE);
                tabs.setMaxHeight(Region.USE_PREF_SIZE);

                Runnable resizeWidget = () -> {

                        if (!window.isShowing()) {
                                return;
                        }

                        root.applyCss();

                        Region content = (Region) tabs.getSelectionModel()
                                        .getSelectedItem().getContent();

                        double contentHeight = content.prefHeight(
                                        Math.max(300, tabs.getWidth() - 24));

                        double headerHeight = 42;

                        javafx.scene.Node header = tabs.lookup(".tab-header-area");

                        if (header instanceof Region region) {
                                headerHeight = Math.max(
                                                36, region.prefHeight(-1));
                        }

                        tabs.setPrefHeight(
                                        contentHeight + headerHeight + 4);

                        root.requestLayout();
                        root.applyCss();
                        root.layout();

                        window.sizeToScene();
                };

                tabs.getSelectionModel()
                                .selectedItemProperty()
                                .addListener((obs, oldTab, newTab) -> Platform.runLater(resizeWidget));

                window.setOnShown(event -> Platform.runLater(resizeWidget));

                clock.setCycleCount(Timeline.INDEFINITE);
                sessionTicker.setCycleCount(Timeline.INDEFINITE);

                window.setOnHidden(event -> {
                        clock.stop();
                        sessionTicker.stop();
                });
        }

        public void show() {
                refresh();

                if (!window.isShowing()) {
                        window.show();

                        clock.play();
                        sessionTicker.play();

                        if (activeSession == null) {
                                recoverUnfinishedSession();
                        }

                } else {
                        window.toFront();
                }

                updateSessionTimer();
        }

        private void recoverUnfinishedSession() {

                if (activeSession != null) {
                        return;
                }

                try {
                        Optional<WorkSession> pending = workSessions.findActive();

                        if (pending.isEmpty()) {
                                unresolvedSession = false;
                                updateSessionTimer();
                                return;
                        }

                        unresolvedSession = true;

                        WorkSession session = pending.get();

                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

                        String started = LocalDateTime.ofInstant(
                                        session.startedAt(),
                                        ZoneId.systemDefault()).format(formatter);

                        TextInputDialog dialog = new TextInputDialog("");

                        dialog.setTitle("Recover Focus Session");
                        dialog.setHeaderText(
                                        "Unfinished: " + session.taskTitle()
                                                        + "\nStarted: " + started);

                        dialog.setContentText(
                                        "Actual end time (yyyy-MM-dd HH:mm):");

                        Optional<String> response = dialog.showAndWait();

                        if (response.isEmpty() || response.get().isBlank()) {
                                sessionStatusLabel.setText(
                                                "Session unresolved. Click Recover to finish it.");
                                return;
                        }

                        Instant actualEnd = LocalDateTime.parse(
                                        response.get().trim(), formatter)
                                        .atZone(ZoneId.systemDefault())
                                        .toInstant();

                        if (actualEnd.isBefore(session.startedAt())
                                        || actualEnd.isAfter(Instant.now())) {
                                throw new IllegalArgumentException(
                                                "End must be between start time and now.");
                        }

                        if (!workSessions.stop(session.id(), actualEnd)) {
                                throw new SQLException("Recovery could not be saved.");
                        }

                        unresolvedSession = false;
                        sessionStatusLabel.setText("Previous session recovered.");

                } catch (SQLException | DateTimeParseException
                                | IllegalArgumentException ex) {

                        unresolvedSession = true;
                        sessionStatusLabel.setText(
                                        "Recovery failed: " + ex.getMessage());

                } finally {
                        updateSessionTimer();
                }
        }

        private void startFocusSession() {

                try {
                        if (unresolvedSession) {
                                sessionStatusLabel.setText(
                                                "Resolve the unfinished session first.");
                                return;
                        }

                        Task task = currentFocusedTask;
                        TaskStep step = focusStepChoice.getValue();

                        if (task == null) {
                                sessionStatusLabel.setText("Choose a task first.");
                                return;
                        }

                        if (pausedSession != null) {
                                String stepId = step == null ? null : step.id();

                                if (!task.getId().equals(pausedSession.taskId())
                                                || !java.util.Objects.equals(
                                                                stepId, pausedSession.stepId())) {

                                        sessionStatusLabel.setText(
                                                        "Resume requires the original Task/Step.");
                                        return;
                                }
                        }

                        WorkSession started = workSessions.start(task, step);

                        if (pausedSession == null) {
                                focusElapsed = Duration.ZERO;
                        }

                        activeSession = started;
                        pausedSession = null;

                        manualFocusTaskId = task.getId();

                        sessionStatusLabel.setText("Recording actual work.");

                } catch (SQLException | IllegalArgumentException ex) {
                        sessionStatusLabel.setText(
                                        "Unable to start: " + ex.getMessage());
                }

                updateSessionTimer();
        }

        private void pauseFocusSession() {

                if (activeSession == null) {
                        return;
                }

                try {
                        Instant endedAt = Instant.now();

                        if (!workSessions.stop(activeSession.id(), endedAt)) {
                                throw new SQLException("Session could not be closed.");
                        }

                        focusElapsed = focusElapsed.plus(
                                        Duration.between(
                                                        activeSession.startedAt(), endedAt));

                        pausedSession = activeSession;
                        activeSession = null;

                        sessionStatusLabel.setText("Paused.");

                } catch (SQLException ex) {
                        sessionStatusLabel.setText(
                                        "Pause failed: " + ex.getMessage());
                }

                updateSessionTimer();
        }

        private void stopFocusSession() {

                try {
                        if (activeSession != null) {
                                if (!workSessions.stop(
                                                activeSession.id(), Instant.now())) {
                                        throw new SQLException("Session could not be closed.");
                                }
                        }

                        activeSession = null;
                        pausedSession = null;
                        focusElapsed = Duration.ZERO;

                        sessionStatusLabel.setText("Focus session finished.");

                } catch (SQLException ex) {
                        sessionStatusLabel.setText(
                                        "Stop failed: " + ex.getMessage());
                }

                updateSessionTimer();
        }

        private void updateSessionTimer() {

                Duration elapsed = focusElapsed;

                if (activeSession != null) {
                        elapsed = elapsed.plus(
                                        Duration.between(
                                                        activeSession.startedAt(), Instant.now()));
                }

                long seconds = Math.max(0, elapsed.getSeconds());

                sessionTimerLabel.setText(String.format(
                                "%02d:%02d:%02d",
                                seconds / 3600,
                                (seconds % 3600) / 60,
                                seconds % 60));

                boolean recording = activeSession != null;
                boolean paused = pausedSession != null;
                boolean locked = recording || paused;

                sessionStartButton.setText(paused ? "Resume" : "Start");
                sessionStartButton.setDisable(
                                recording || unresolvedSession || currentFocusedTask == null);

                sessionPauseButton.setDisable(!recording);
                sessionStopButton.setDisable(!locked);

                focusTaskChoice.setDisable(locked);
                focusStepChoice.setDisable(locked || currentFocusedTask == null);
                autoFocusButton.setDisable(locked);

                recoverSessionButton.setVisible(unresolvedSession);
        }

        private void refresh() {

                try {
                        LocalDateTime now = LocalDateTime.now();
                        LocalDate today = now.toLocalDate();

                        List<Task> allTasks = tasks.findAll();

                        // Daily weighted progress
                        int planned = 0;
                        int done = 0;

                        for (Task task : allTasks) {

                                if (!today.equals(task.getPlannedDate())) {
                                        continue;
                                }

                                List<TaskStep> taskSteps = steps.findByTaskId(task.getId());

                                int allocated = taskSteps.stream()
                                                .mapToInt(TaskStep::estimatedMinutes)
                                                .sum();

                                int taskPlanned = Math.max(
                                                task.getEstimatedMinutes(), allocated);

                                int taskDone = taskSteps.isEmpty()
                                                ? (task.isCompleted() ? taskPlanned : 0)
                                                : taskSteps.stream()
                                                                .filter(TaskStep::completed)
                                                                .mapToInt(TaskStep::estimatedMinutes)
                                                                .sum();

                                planned += taskPlanned;
                                done += Math.min(taskDone, taskPlanned);
                        }

                        dailyBar.setValues(
                                        planned == 0 ? 0 : done / (double) planned,
                                        0);

                        dailyLabel.setText(
                                        done + " / " + planned + " min completed");

                        // Current and next time blocks
                        List<TimeBlock> schedule = blocks.findForDate(today);
                        schedule.addAll(
                                        blocks.findForDate(today.plusDays(1)));

                        TimeBlock current = null;
                        TimeBlock next = null;

                        for (TimeBlock block : schedule) {

                                if (current == null
                                                && !now.isBefore(block.start())
                                                && now.isBefore(block.end())) {

                                        current = block;

                                } else if (block.start().isAfter(now)
                                                && (next == null
                                                                || block.start().isBefore(next.start()))) {

                                        next = block;
                                }
                        }

                        taskControls.getChildren().clear();

                        if (current == null) {

                                currentLabel.setText("Current: Unscheduled");

                        } else {

                                double elapsed = Duration.between(
                                                current.start(), now).toSeconds();

                                double total = Duration.between(
                                                current.start(), current.end()).toSeconds();

                                long remaining = (long) Math.ceil(
                                                (total - elapsed) / 60.0);

                                currentLabel.setText(
                                                "Now: " + current.title()
                                                                + " · " + Math.round(elapsed / total * 100)
                                                                + "% elapsed · " + remaining + " min left");

                        }

                        updateFocusTask(current, allTasks, today);

                        refreshClockFocus();

                        nextLabel.setText(
                                        next == null
                                                        ? "Next: No upcoming activity"
                                                        : "Next: " + next.title()
                                                                        + " · " + next.start().toLocalTime());

                        clockFace.setTime(now.toLocalTime());

                        clockTimeLabel.setText(
                                        String.format(
                                                        "%02d:%02d",
                                                        now.getHour(),
                                                        now.getMinute()));

                        String selectedProjectId = clockProjectChoice.getValue() == null
                                        ? null
                                        : clockProjectChoice.getValue().id();

                        List<Project> availableProjects = projects.findAll();

                        clockProjectChoice.getItems().setAll(availableProjects);

                        Project selectedProject = availableProjects.stream()
                                        .filter(project -> project.id().equals(selectedProjectId))
                                        .findFirst()
                                        .orElse(
                                                        availableProjects.isEmpty()
                                                                        ? null
                                                                        : availableProjects.get(0));

                        clockProjectChoice.setValue(selectedProject);

                        refreshProjectProgress(allTasks);

                        errorLabel.setText("");

                } catch (SQLException ex) {
                        errorLabel.setText("Unable to refresh Agenda.");
                        ex.printStackTrace();
                }
        }

        private void showFocusedTask(Task linkedTask) throws SQLException {

                if (linkedTask == null) {
                        return;
                }

                List<TaskStep> taskSteps = steps.findByTaskId(linkedTask.getId());

                int allocated = taskSteps.stream()
                                .mapToInt(TaskStep::estimatedMinutes)
                                .sum();

                int total = Math.max(
                                linkedTask.getEstimatedMinutes(),
                                allocated);

                int completed = taskSteps.isEmpty()
                                ? (linkedTask.isCompleted() ? total : 0)
                                : taskSteps.stream()
                                                .filter(TaskStep::completed)
                                                .mapToInt(TaskStep::estimatedMinutes)
                                                .sum();

                completed = Math.min(completed, total);

                taskBar.setValues(
                                total == 0 ? 0 : completed / (double) total,
                                0);

                taskProgressLabel.setText(
                                completed + " / " + total
                                                + " min · "
                                                + (total == 0
                                                                ? 0
                                                                : Math.round(completed * 100.0 / total))
                                                + "% completed");

                if (taskSteps.isEmpty()) {

                        CheckBox checkbox = new CheckBox("Completed");
                        checkbox.setSelected(linkedTask.isCompleted());

                        checkbox.setOnAction(event -> {
                                try {
                                        tasks.updateCompleted(
                                                        linkedTask.getId(),
                                                        checkbox.isSelected());

                                        onDataChanged.run();
                                        refresh();

                                } catch (SQLException ex) {
                                        errorLabel.setText("Unable to save task.");
                                        ex.printStackTrace();
                                }
                        });

                        taskControls.getChildren().add(checkbox);

                } else {

                        for (TaskStep step : taskSteps) {

                                CheckBox checkbox = new CheckBox(
                                                step.title() + " · "
                                                                + step.estimatedMinutes() + " min");

                                checkbox.setSelected(step.completed());

                                checkbox.setOnAction(event -> {
                                        try {
                                                steps.updateCompleted(
                                                                step.id(),
                                                                checkbox.isSelected());

                                                // Synchronize parent completion
                                                List<TaskStep> updated = steps.findByTaskId(linkedTask.getId());

                                                int updatedAllocated = updated.stream()
                                                                .mapToInt(TaskStep::estimatedMinutes)
                                                                .sum();

                                                int updatedCompleted = updated.stream()
                                                                .filter(TaskStep::completed)
                                                                .mapToInt(TaskStep::estimatedMinutes)
                                                                .sum();

                                                int updatedTotal = Math.max(
                                                                linkedTask.getEstimatedMinutes(),
                                                                updatedAllocated);

                                                tasks.updateCompleted(
                                                                linkedTask.getId(),
                                                                updatedCompleted >= updatedTotal);

                                                onDataChanged.run();
                                                refresh();

                                        } catch (SQLException ex) {
                                                errorLabel.setText("Unable to save step.");
                                                ex.printStackTrace();
                                        }
                                });

                                taskControls.getChildren().add(checkbox);
                        }
                }
        }

        private void updateFocusTask(
                        TimeBlock current,
                        List<Task> allTasks,
                        LocalDate today) throws SQLException {

                String preferredId = manualFocusTaskId;

                if (activeSession != null) {
                        preferredId = activeSession.taskId();
                } else if (pausedSession != null) {
                        preferredId = pausedSession.taskId();
                }

                if (preferredId == null && current != null) {
                        preferredId = current.taskId();
                }

                Task selected = null;

                for (Task task : allTasks) {
                        if (task.getId().equals(preferredId)) {
                                selected = task;
                                break;
                        }
                }

                // Fallback: first unfinished task planned for today
                if (selected == null) {
                        selected = allTasks.stream()
                                        .filter(task -> today.equals(task.getPlannedDate())
                                                        && !task.isCompleted())
                                        .findFirst()
                                        .orElse(null);
                }

                updatingFocusChoice = true;

                try {
                        focusTaskChoice.getItems().setAll(allTasks);
                        focusTaskChoice.setValue(selected);
                } finally {
                        updatingFocusChoice = false;
                }

                taskControls.getChildren().clear();

                currentFocusedTask = selected;

                String previousStepId = focusStepChoice.getValue() == null
                                ? null
                                : focusStepChoice.getValue().id();

                if (activeSession != null) {
                        previousStepId = activeSession.stepId();
                } else if (pausedSession != null) {
                        previousStepId = pausedSession.stepId();
                }

                List<TaskStep> availableSteps = selected == null
                                ? List.of()
                                : steps.findByTaskId(selected.getId());

                focusStepChoice.getItems().setAll(availableSteps);

                String wantedStepId = previousStepId;

                TaskStep selectedStep = availableSteps.stream()
                                .filter(step -> step.id().equals(wantedStepId))
                                .findFirst()
                                .orElse(null);

                focusStepChoice.setValue(selectedStep);

                updateSessionTimer();

                if (selected == null) {
                        taskBar.setValues(0, 0);
                        taskProgressLabel.setText("No focus task selected.");
                        return;
                }

                showFocusedTask(selected);
        }

        private void refreshProjectProgress(
                        List<Task> allTasks) throws SQLException {

                Project project = clockProjectChoice.getValue();

                if (project == null) {
                        projectBar.setValues(0, 0);
                        projectBar.setExpected(0);
                        projectProgressLabel.setText("No project selected");
                        projectExpectedLabel.setText("");
                        return;
                }

                Set<String> ids = new HashSet<>(
                                projects.findTaskIds(project.id()));

                int total = 0;
                int done = 0;
                int todayPending = 0;

                for (Task task : allTasks) {

                        if (!ids.contains(task.getId())) {
                                continue;
                        }

                        List<TaskStep> taskSteps = steps.findByTaskId(task.getId());

                        int allocated = taskSteps.stream()
                                        .mapToInt(TaskStep::estimatedMinutes)
                                        .sum();

                        int planned = Math.max(
                                        task.getEstimatedMinutes(), allocated);

                        int completed = taskSteps.isEmpty()
                                        ? (task.isCompleted() ? planned : 0)
                                        : taskSteps.stream()
                                                        .filter(TaskStep::completed)
                                                        .mapToInt(TaskStep::estimatedMinutes)
                                                        .sum();

                        completed = Math.min(completed, planned);

                        total += planned;
                        done += completed;

                        if (LocalDate.now().equals(task.getPlannedDate())) {
                                todayPending += planned - completed;
                        }
                }

                double actual = total == 0
                                ? 0
                                : done / (double) total;

                double pending = total == 0
                                ? 0
                                : todayPending / (double) total;

                long duration = ChronoUnit.DAYS.between(
                                project.startDate(), project.dueDate());

                long elapsed = ChronoUnit.DAYS.between(
                                project.startDate(), LocalDate.now());

                double expected = Math.max(
                                0, Math.min(1, elapsed / (double) duration));

                Map<String, String> colors = categories.findAll();

                projectBar.setColor(Color.web(
                                colors.getOrDefault(
                                                project.categoryName(), "#5F91BC")));

                projectBar.setValues(actual, pending);
                projectBar.setExpected(expected);

                projectProgressLabel.setText(
                                total == 0
                                                ? "No tasks assigned"
                                                : done + "/" + total
                                                                + " min done · " + todayPending
                                                                + " min today pending");

                projectExpectedLabel.setText(
                                "Actual " + Math.round(actual * 100)
                                                + "% · Expected "
                                                + Math.round(expected * 100) + "%");
        }

        private void refreshClockFocus() throws SQLException {

                if (currentFocusedTask == null) {

                        clockFocusLabel.setText("No focus task");
                        clockFocusCategory.setText("Choose a task in Focus");

                        clockFocusCard.setStyle(
                                        "-fx-background-color: #E8EDF5;"
                                                        + "-fx-background-radius: 10;"
                                                        + "-fx-padding: 10;");

                        return;
                }

                String category = categories.findForTask(
                                currentFocusedTask.getId());

                String colorHex = categories.findAll()
                                .getOrDefault(category, "#E8EDF5");

                Color color = Color.web(colorHex);

                double brightness = color.getRed() * 0.2126
                                + color.getGreen() * 0.7152
                                + color.getBlue() * 0.0722;

                String textColor = brightness > 0.55
                                ? "#26364A"
                                : "#FFFFFF";

                clockFocusLabel.setText(
                                currentFocusedTask.getTitle());

                clockFocusCategory.setText(
                                "FOCUS · " + category);

                clockFocusLabel.setStyle(
                                "-fx-text-fill: " + textColor + ";"
                                                + "-fx-font-weight: bold;");

                clockFocusCategory.setStyle(
                                "-fx-text-fill: " + textColor + ";");

                clockFocusCard.setStyle(
                                "-fx-background-color: " + colorHex + ";"
                                                + "-fx-background-radius: 10;"
                                                + "-fx-padding: 10;"
                                                + "-fx-effect: dropshadow(gaussian,"
                                                + "rgba(40,55,80,0.16),10,0.15,0,3);");
        }

}
