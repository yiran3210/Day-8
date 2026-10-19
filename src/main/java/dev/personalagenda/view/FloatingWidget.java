package dev.personalagenda.view;

import dev.personalagenda.data.CategoryRepository;
import dev.personalagenda.data.ProjectRepository;
import dev.personalagenda.data.TaskRepository;
import dev.personalagenda.data.TaskStepRepository;
import dev.personalagenda.data.TimeBlockRepository;
import dev.personalagenda.data.WorkSessionRepository;
import dev.personalagenda.model.Project;
import dev.personalagenda.model.Task;
import dev.personalagenda.model.TaskStep;
import dev.personalagenda.model.TimeBlock;
import dev.personalagenda.model.WorkSession;
import dev.personalagenda.service.FocusSessionService;

import java.net.URL;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;
import javafx.util.StringConverter;

public class FloatingWidget {

        private final Stage window = new Stage();
        private final Stage mainWindow;
        private final Runnable onDataChanged;
        private final FocusSessionService focusService;

        private final TaskRepository tasks = new TaskRepository();
        private final TaskStepRepository steps = new TaskStepRepository();
        private final TimeBlockRepository blocks = new TimeBlockRepository();
        private final ProjectRepository projects = new ProjectRepository();
        private final CategoryRepository categories = new CategoryRepository();
        private final WorkSessionRepository workSessions = new WorkSessionRepository();

        // Progress: estimated completed workload, NOT elapsed focus time.
        private final Label currentLabel = new Label();
        private final Label nextLabel = new Label();
        private final Label dailyLabel = new Label();
        private final Label taskProgressLabel = new Label();
        private final Label projectProgressLabel = new Label();
        private final Label projectExpectedLabel = new Label();
        private final Label errorLabel = new Label();
        private final CompositeProgressBar dailyBar = new CompositeProgressBar();
        private final CompositeProgressBar taskBar = new CompositeProgressBar();
        private final CompositeProgressBar projectBar = new CompositeProgressBar();

        // Focus selection.
        private final ComboBox<Task> focusTaskChoice = new ComboBox<>();
        private final ComboBox<TaskStep> focusStepChoice = new ComboBox<>();
        private final Button autoFocusButton = new Button("Auto");
        private final VBox taskControls = new VBox(6);
        private Task currentFocusedTask;
        private boolean updatingFocusChoice;
        private boolean updatingStepChoice;
        private String manualFocusTaskId;
        private String preferredStepId;
        private String stepChoiceTaskId;

        private boolean manualStepOverride = false;

        private TimeBlock scheduledNow;
        private final Label scheduleFocusLabel = new Label();

        private final Button wholeTaskButton = new Button("Whole task");

        // Session state. Pause/Resume creates separate, non-overlapping DB rows.
        private final Label sessionTimerLabel = new Label("00:00:00");
        private final Label sessionStatusLabel = new Label("Ready to focus");
        private final Label todayActualLabel = new Label("Today's actual work: 0h 0m");
        private final Button sessionStartButton = new Button("Start");
        private final Button sessionPauseButton = new Button("Pause");
        private final Button sessionStopButton = new Button("Stop");
        private final Button recoverSessionButton = new Button("Recover");
        private final VBox recentSessionsList = new VBox(5);
        private final TitledPane recentSessionsPane = new TitledPane("Recent sessions", recentSessionsList);

        private boolean unresolvedSession;
        private long finishedMillisToday;
        private LocalDate finishedMillisDate;
        private boolean dayRefreshPending = false;

        // Clock / project section.
        private final ClockFace clockFace = new ClockFace();
        private final Label clockTimeLabel = new Label();
        private final Label clockFocusLabel = new Label();
        private final Label clockFocusCategory = new Label();
        private final VBox clockFocusCard = new VBox(5, clockFocusLabel, clockFocusCategory);
        private final ComboBox<Project> clockProjectChoice = new ComboBox<>();
        private boolean updatingProjectChoice;

        // The 30-second refresh accesses repositories. The one-second ticker doesn't.
        private final Timeline dataClock = new Timeline(
                        new KeyFrame(javafx.util.Duration.seconds(30), e -> refresh()));
        private final Timeline sessionTicker = new Timeline(
                        new KeyFrame(javafx.util.Duration.seconds(1), e -> updateSessionTimer()));

        private final TabPane tabs;
        private final VBox root;
        private Runnable resizeWidgetAction = () -> {
        };

        public FloatingWidget(
                        Stage mainWindow,
                        Runnable onDataChanged,
                        FocusSessionService focusService) {
                this.mainWindow = mainWindow;
                this.onDataChanged = onDataChanged;
                this.focusService = Objects.requireNonNull(focusService);

                window.initStyle(StageStyle.DECORATED);
                window.setTitle("Agenda · Mini");
                window.setAlwaysOnTop(true);
                window.setMinWidth(280);
                window.setMinHeight(260);
                installWindowIcons();

                Label todayTitle = new Label("Today's Progress");
                todayTitle.getStyleClass().add("mini-section-title");
                dailyLabel.getStyleClass().add("mini-stat");
                taskProgressLabel.getStyleClass().add("mini-stat");
                projectExpectedLabel.getStyleClass().add("mini-stat");
                dailyBar.setColor(Color.web("#5F91BC"));
                VBox dailySection = new VBox(6, todayTitle, dailyBar, dailyLabel);
                dailySection.getStyleClass().add("mini-card");

                // Focus task/step controls.
                Label focusTitle = new Label("Current Task");
                focusTaskChoice.setMaxWidth(Double.MAX_VALUE);
                focusTaskChoice.setPromptText("Choose focus task");
                focusTaskChoice.setOnAction(e -> {
                        if (updatingFocusChoice || focusIsLocked())
                                return;
                        Task chosen = focusTaskChoice.getValue();
                        manualFocusTaskId = chosen == null ? null : chosen.getId();
                        preferredStepId = null;
                        manualStepOverride = false;
                        stepChoiceTaskId = null;
                        refresh();
                });
                autoFocusButton.setOnAction(e -> {
                        if (focusIsLocked())
                                return;
                        manualFocusTaskId = null;
                        preferredStepId = null;
                        manualStepOverride = false;
                        stepChoiceTaskId = null;
                        refresh();
                });
                HBox focusPicker = new HBox(6, focusTaskChoice, autoFocusButton);
                focusPicker.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(focusTaskChoice, Priority.ALWAYS);

                focusStepChoice.setPromptText("Whole task");
                focusStepChoice.setMaxWidth(Double.MAX_VALUE);
                focusStepChoice.setConverter(new StringConverter<>() {
                        @Override
                        public String toString(TaskStep step) {
                                return step == null ? "Whole task"
                                                : step.title() + " · " + step.estimatedMinutes() + " min";
                        }

                        @Override
                        public TaskStep fromString(String text) {
                                return null;
                        }
                });
                focusStepChoice.setOnAction(e -> {
                        if (updatingStepChoice || focusIsLocked())
                                return;

                        TaskStep selected = focusStepChoice.getValue();

                        manualStepOverride = true;
                        preferredStepId = selected == null ? null : selected.id();

                        updateScheduleFocusStatus();
                });

                wholeTaskButton.setOnAction(e -> {

                        if (focusIsLocked())
                                return;

                        manualStepOverride = true;
                        preferredStepId = null;

                        updatingStepChoice = true;
                        try {
                                focusStepChoice.setValue(null);
                        } finally {
                                updatingStepChoice = false;
                        }

                        updateScheduleFocusStatus();
                });

                scheduleFocusLabel.setWrapText(true);
                scheduleFocusLabel.setMaxWidth(Double.MAX_VALUE);

                scheduleFocusLabel.setStyle(
                                "-fx-font-size: 12px;"
                                                + "-fx-text-fill: #526478;");

                scheduleFocusLabel.setVisible(false);

                scheduleFocusLabel.managedProperty().bind(
                                scheduleFocusLabel.visibleProperty());

                sessionTimerLabel.setStyle("-fx-font-size: 23px; -fx-font-weight: bold;");
                sessionStatusLabel.setWrapText(true);
                todayActualLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");
                sessionStartButton.setOnAction(e -> startFocusSession());
                sessionPauseButton.setOnAction(e -> pauseFocusSession());
                sessionStopButton.setOnAction(e -> stopFocusSession());
                recoverSessionButton.setOnAction(e -> recoverUnfinishedSession());
                recoverSessionButton.managedProperty().bind(
                                recoverSessionButton.visibleProperty());

                HBox sessionButtons = new HBox(6, sessionStartButton,
                                sessionPauseButton, sessionStopButton, recoverSessionButton);
                sessionButtons.setAlignment(Pos.CENTER_LEFT);
                for (Button button : new Button[] { sessionStartButton, sessionPauseButton,
                                sessionStopButton, recoverSessionButton }) {
                        button.setMinWidth(Region.USE_PREF_SIZE);
                }

                recentSessionsPane.setExpanded(false);
                recentSessionsPane.setAnimated(false);
                recentSessionsPane.setMaxWidth(Double.MAX_VALUE);

                HBox stepPicker = new HBox(
                                6, focusStepChoice, wholeTaskButton);

                stepPicker.setAlignment(Pos.CENTER_LEFT);

                HBox.setHgrow(focusStepChoice, Priority.ALWAYS);
                wholeTaskButton.setMinWidth(Region.USE_PREF_SIZE);

                VBox sessionControls = new VBox(6,
                                new Label("Focus Step"), stepPicker,
                                sessionTimerLabel, sessionButtons,
                                sessionStatusLabel, todayActualLabel, recentSessionsPane);

                taskBar.setColor(Color.web("#887ABD"));
                ScrollPane taskScroll = new ScrollPane(taskControls);
                taskScroll.setFitToWidth(true);
                taskScroll.setPrefHeight(72);
                taskScroll.setMaxHeight(96);
                VBox focusContent = new VBox(10,
                                currentLabel, focusTitle, focusPicker,
                                scheduleFocusLabel, sessionControls,
                                taskBar, taskProgressLabel, taskScroll,
                                new Separator(), nextLabel);
                focusContent.getStyleClass().add("mini-card");

                // Clock / Project tab retains the existing project progress algorithm.
                clockProjectChoice.setMaxWidth(Double.MAX_VALUE);
                clockProjectChoice.setConverter(new StringConverter<>() {
                        @Override
                        public String toString(Project value) {
                                return value == null ? "Choose project" : value.name();
                        }

                        @Override
                        public Project fromString(String text) {
                                return null;
                        }
                });
                clockProjectChoice.setOnAction(e -> {
                        if (updatingProjectChoice)
                                return;
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
                VBox clockInfo = new VBox(12, clockTimeLabel, clockFocusCard);
                HBox clockHero = new HBox(10, clockFace, clockInfo);
                clockHero.setAlignment(Pos.CENTER_LEFT);
                VBox clockContent = new VBox(10, clockHero,
                                new Label("Project Progress"), clockProjectChoice,
                                projectBar, projectProgressLabel, projectExpectedLabel);
                clockContent.setAlignment(Pos.TOP_CENTER);
                clockContent.getStyleClass().add("mini-card");

                Tab focusTab = new Tab("Focus", focusContent);
                Tab clockTab = new Tab("Clock", clockContent);
                focusTab.setClosable(false);
                clockTab.setClosable(false);
                tabs = new TabPane(focusTab, clockTab);
                tabs.setMinHeight(Region.USE_PREF_SIZE);
                tabs.setMaxHeight(Region.USE_PREF_SIZE);

                Button openButton = new Button("Open Agenda");
                openButton.setOnAction(e -> {
                        mainWindow.show();
                        mainWindow.setIconified(false);
                        mainWindow.toFront();
                });
                Button refreshButton = new Button("↻");
                refreshButton.setOnAction(e -> refresh());
                HBox footer = new HBox(8, openButton, refreshButton);

                root = new VBox(7, dailySection, tabs, errorLabel, footer);
                root.setPadding(new Insets(10));
                root.getStyleClass().add("mini-widget");
                root.setPrefWidth(340);
                errorLabel.managedProperty().bind(errorLabel.visibleProperty());
                errorLabel.setVisible(false);

                Scene scene = new Scene(root);
                URL stylesheet = getClass().getResource("/styles/light.css");
                if (stylesheet != null) {
                        scene.getStylesheets().add(stylesheet.toExternalForm());
                }
                window.setScene(scene);

                // Compute the height of the selected tab, rather than fixing a taller widget.
                resizeWidgetAction = () -> {
                        if (!window.isShowing())
                                return;
                        root.applyCss();
                        Region content = (Region) tabs.getSelectionModel()
                                        .getSelectedItem().getContent();
                        double contentWidth = Math.max(300, tabs.getWidth() - 24);
                        double contentHeight = content.prefHeight(contentWidth);
                        double headerHeight = 42;
                        javafx.scene.Node header = tabs.lookup(".tab-header-area");
                        if (header instanceof Region region) {
                                headerHeight = Math.max(36, region.prefHeight(-1));
                        }
                        tabs.setPrefHeight(contentHeight + headerHeight + 4);
                        root.requestLayout();
                        root.applyCss();
                        root.layout();
                        window.sizeToScene();
                };
                tabs.getSelectionModel().selectedItemProperty().addListener(
                                (obs, previous, next) -> requestResize());
                recentSessionsPane.expandedProperty().addListener(
                                (obs, before, after) -> requestResize());
                window.setOnShown(e -> requestResize());

                dataClock.setCycleCount(Timeline.INDEFINITE);
                sessionTicker.setCycleCount(Timeline.INDEFINITE);
                window.setOnHidden(e -> {
                        dataClock.stop();
                        sessionTicker.stop();
                        // Intentional: hiding does not stop an ongoing work session.
                });

                window.addEventFilter(
                                WindowEvent.WINDOW_CLOSE_REQUEST,
                                event -> {

                                        // If Mini is the only visible window,
                                        // restore Main before closing Mini.
                                        if (!mainWindow.isShowing()) {

                                                mainWindow.show();
                                                mainWindow.setIconified(false);
                                                mainWindow.toFront();
                                        }
                                });

                mainWindow.addEventFilter(
                                WindowEvent.WINDOW_CLOSE_REQUEST,
                                event -> handleMainWindowClose(event));

                updateSessionTimer();
        }

        private void installWindowIcons() {
                // Resource-only icons: no hard dependency on another utility class.
                for (int size : new int[] { 16, 32, 64, 256 }) {
                        URL image = getClass().getResource("/icons/agenda-icon-" + size + ".png");
                        if (image != null) {
                                window.getIcons().add(new javafx.scene.image.Image(image.toExternalForm()));
                        }
                }
        }

        private void handleMainWindowClose(WindowEvent event) {

                // No recording: normal exit.
                if (focusService.state() != FocusSessionService.State.RUNNING) {
                        window.hide();
                        return;
                }

                ButtonType stopChoice = new ButtonType(
                                "Stop & Exit",
                                ButtonBar.ButtonData.OK_DONE);

                ButtonType recoverChoice = new ButtonType(
                                "Recover Later",
                                ButtonBar.ButtonData.OTHER);

                ButtonType cancelChoice = new ButtonType(
                                "Cancel",
                                ButtonBar.ButtonData.CANCEL_CLOSE);

                Alert dialog = new Alert(Alert.AlertType.CONFIRMATION);

                dialog.initOwner(mainWindow);
                dialog.initModality(Modality.WINDOW_MODAL);

                dialog.setTitle("Active Focus Session");
                dialog.setHeaderText("A work session is still running.");

                dialog.setContentText(
                                "Stop & Exit: save the actual end time now.\n"
                                                + "Recover Later: confirm the end time next time.\n"
                                                + "Cancel: return to Agenda.");

                dialog.getButtonTypes().setAll(
                                stopChoice, recoverChoice, cancelChoice);

                dialog.getDialogPane()
                                .getStyleClass().add("agenda-confirmation");

                java.net.URL css = getClass().getResource("/styles/light.css");

                if (css != null) {
                        dialog.getDialogPane().getStylesheets()
                                        .add(css.toExternalForm());
                }

                // Prevent the always-on-top widget from covering the dialog.
                boolean wasOnTop = window.isAlwaysOnTop();

                Optional<ButtonType> result;

                window.setAlwaysOnTop(false);

                try {
                        result = dialog.showAndWait();
                } finally {
                        window.setAlwaysOnTop(wasOnTop);
                }

                ButtonType choice = result.orElse(cancelChoice);

                if (choice == stopChoice) {

                        stopFocusSession();

                        // If database saving failed, do not exit.
                        if (focusService.state() == FocusSessionService.State.RUNNING) {
                                event.consume();
                                return;
                        }

                        window.hide();

                } else if (choice == recoverChoice) {

                        // Leave the unfinished DB row for next-launch recovery.
                        window.hide();

                } else {

                        // Cancel the main window close request.
                        event.consume();
                }
        }

        private boolean focusIsLocked() {
                return focusService.state() != FocusSessionService.State.IDLE;
        }

        private void requestResize() {
                Platform.runLater(resizeWidgetAction);
        }

        public void show() {
                refresh();
                if (!window.isShowing()) {
                        window.show();
                        dataClock.play();
                        sessionTicker.play();
                        if (focusService.state() == FocusSessionService.State.IDLE
                                        && !unresolvedSession) {
                                // Run after the stage is visible so the owner-owned dialog can be focused.
                                Platform.runLater(this::recoverUnfinishedSession);
                        }
                } else {
                        window.toFront();
                }
                updateSessionTimer();
        }

        public void showMiniOnly() {

                // Show Mini first to avoid closing the last visible Stage.
                show();

                if (window.isShowing()) {
                        mainWindow.hide();
                }
        }

        private void refresh() {
                try {
                        LocalDateTime now = LocalDateTime.now();
                        LocalDate today = now.toLocalDate();
                        List<Task> allTasks = tasks.findAll();

                        // Today's estimated completed workload.
                        int planned = 0;
                        int done = 0;
                        for (Task task : allTasks) {
                                if (!today.equals(task.getPlannedDate()))
                                        continue;
                                List<TaskStep> taskSteps = steps.findByTaskId(task.getId());
                                int allocated = taskSteps.stream().mapToInt(TaskStep::estimatedMinutes).sum();
                                int taskPlanned = Math.max(task.getEstimatedMinutes(), allocated);
                                int taskDone = taskSteps.isEmpty()
                                                ? (task.isCompleted() ? taskPlanned : 0)
                                                : taskSteps.stream().filter(TaskStep::completed)
                                                                .mapToInt(TaskStep::estimatedMinutes).sum();
                                planned += taskPlanned;
                                done += Math.min(taskDone, taskPlanned);
                        }
                        dailyBar.setValues(planned == 0 ? 0 : done / (double) planned, 0);
                        dailyLabel.setText(done + " / " + planned + " min completed");

                        // Scheduled now/next. findForDate(today) also includes overnight blocks.
                        List<TimeBlock> schedule = blocks.findForDate(today);
                        schedule.addAll(blocks.findForDate(today.plusDays(1)));
                        TimeBlock current = null;
                        TimeBlock next = null;
                        for (TimeBlock block : schedule) {
                                if (current == null && !now.isBefore(block.start())
                                                && now.isBefore(block.end())) {
                                        current = block;
                                } else if (block.start().isAfter(now)
                                                && (next == null || block.start().isBefore(next.start()))) {
                                        next = block;
                                }
                        }
                        if (current == null) {
                                currentLabel.setText("Current: Unscheduled");
                        } else {
                                long elapsed = Duration.between(current.start(), now).toSeconds();
                                long total = Math.max(1, Duration.between(
                                                current.start(), current.end()).toSeconds());
                                long remaining = Math.max(0, (long) Math.ceil((total - elapsed) / 60.0));
                                currentLabel.setText("Now: " + current.title() + " · "
                                                + Math.min(100, Math.round(elapsed * 100.0 / total))
                                                + "% elapsed · " + remaining + " min left");
                        }

                        scheduledNow = current;

                        updateFocusTask(current, allTasks, today);
                        updateScheduleFocusStatus();

                        refreshClockFocus();

                        nextLabel.setText(next == null ? "Next: No upcoming activity"
                                        : "Next: " + next.title() + " · " + next.start().toLocalTime());

                        clockFace.setTime(now.toLocalTime());
                        clockTimeLabel.setText(String.format("%02d:%02d", now.getHour(), now.getMinute()));

                        String selectedProjectId = clockProjectChoice.getValue() == null
                                        ? null
                                        : clockProjectChoice.getValue().id();
                        List<Project> availableProjects = projects.findAll();
                        Project selectedProject = availableProjects.stream()
                                        .filter(project -> project.id().equals(selectedProjectId))
                                        .findFirst()
                                        .orElse(availableProjects.isEmpty() ? null : availableProjects.get(0));
                        updatingProjectChoice = true;
                        try {
                                clockProjectChoice.getItems().setAll(availableProjects);
                                clockProjectChoice.setValue(selectedProject);
                        } finally {
                                updatingProjectChoice = false;
                        }
                        refreshProjectProgress(allTasks);
                        reloadWorkHistory();

                        errorLabel.setVisible(false);
                        errorLabel.setText("");
                } catch (SQLException ex) {
                        errorLabel.setText("Unable to refresh Agenda: " + ex.getMessage());
                        errorLabel.setVisible(true);
                        ex.printStackTrace();
                }
                requestResize();
        }

        private void updateFocusTask(TimeBlock current, List<Task> allTasks,
                        LocalDate today) throws SQLException {

                WorkSession activeSession = focusService.activeSession();
                WorkSession pausedSession = focusService.pausedSession();
                String wantedTaskId = activeSession != null ? activeSession.taskId()
                                : pausedSession != null ? pausedSession.taskId() : manualFocusTaskId;
                if (wantedTaskId == null && current != null)
                        wantedTaskId = current.taskId();

                final String taskId = wantedTaskId;
                Task selected = allTasks.stream()
                                .filter(task -> task.getId().equals(taskId))
                                .findFirst().orElse(null);
                if (selected == null && !focusIsLocked()) {
                        selected = allTasks.stream()
                                        .filter(task -> today.equals(task.getPlannedDate()) && !task.isCompleted())
                                        .findFirst().orElse(null);
                }

                updatingFocusChoice = true;
                try {
                        focusTaskChoice.getItems().setAll(allTasks);
                        focusTaskChoice.setValue(selected);
                } finally {
                        updatingFocusChoice = false;
                }
                currentFocusedTask = selected;
                taskControls.getChildren().clear();

                String selectedTaskId = selected == null ? null : selected.getId();

                if (!Objects.equals(stepChoiceTaskId, selectedTaskId)) {
                        stepChoiceTaskId = selectedTaskId;
                        preferredStepId = null;
                        manualStepOverride = false;
                }

                String wantedStepId;

                if (activeSession != null) {

                        // Never change the Step of an active recording.
                        wantedStepId = activeSession.stepId();

                } else if (pausedSession != null) {

                        // Resume must use the original Step.
                        wantedStepId = pausedSession.stepId();

                } else if (manualStepOverride) {

                        // Respect explicit manual selection, including Whole task.
                        wantedStepId = preferredStepId;

                } else if (current != null
                                && current.type() == TimeBlock.Type.TASK
                                && Objects.equals(current.taskId(), selectedTaskId)) {

                        // Automatically follow the scheduled Step.
                        wantedStepId = current.stepId();

                } else {

                        wantedStepId = null;
                }

                List<TaskStep> availableSteps = selected == null
                                ? List.of()
                                : steps.findByTaskId(selected.getId());
                final String targetStepId = wantedStepId;
                TaskStep selectedStep = availableSteps.stream()
                                .filter(step -> step.id().equals(targetStepId))
                                .findFirst().orElse(null);
                updatingStepChoice = true;
                try {
                        focusStepChoice.getItems().setAll(availableSteps);
                        focusStepChoice.setValue(selectedStep);
                } finally {
                        updatingStepChoice = false;
                }

                if (selected == null) {
                        taskBar.setValues(0, 0);
                        taskProgressLabel.setText("No focus task selected.");
                } else {
                        showFocusedTask(selected);
                }
                updateSessionTimer();
        }

        private String describeScheduled(TimeBlock block) {

                if (block.type() != TimeBlock.Type.TASK) {
                        return block.title() + " (" + block.type() + ")";
                }

                if (block.taskId() == null) {
                        return block.title() + " (unlinked)";
                }

                Task task = focusTaskChoice.getItems().stream()
                                .filter(t -> t.getId().equals(block.taskId()))
                                .findFirst()
                                .orElse(null);

                String taskName = task == null
                                ? block.title()
                                : task.getTitle();

                if (block.stepId() == null) {
                        return taskName + " / Whole task";
                }

                try {
                        String stepName = steps.findByTaskId(block.taskId())
                                        .stream()
                                        .filter(s -> s.id().equals(block.stepId()))
                                        .map(TaskStep::title)
                                        .findFirst()
                                        .orElse("Unavailable step");

                        return taskName + " / " + stepName;

                } catch (SQLException ex) {
                        return taskName + " / Step unavailable";
                }
        }

        private void updateScheduleFocusStatus() {

                WorkSession activeSession = focusService.activeSession();
                WorkSession pausedSession = focusService.pausedSession();

                TimeBlock planned = scheduledNow;
                Task selectedTask = currentFocusedTask;
                TaskStep selectedStep = focusStepChoice.getValue();

                String actualTaskId = activeSession != null
                                ? activeSession.taskId()
                                : pausedSession != null
                                                ? pausedSession.taskId()
                                                : selectedTask == null ? null : selectedTask.getId();

                String actualStepId = activeSession != null
                                ? activeSession.stepId()
                                : pausedSession != null
                                                ? pausedSession.stepId()
                                                : selectedStep == null ? null : selectedStep.id();

                boolean matching = planned != null
                                && planned.type() == TimeBlock.Type.TASK
                                && Objects.equals(planned.taskId(), actualTaskId)
                                && Objects.equals(planned.stepId(), actualStepId);

                boolean showDifference = planned != null
                                && actualTaskId != null
                                && !matching
                                && (planned.type() == TimeBlock.Type.TASK
                                                || activeSession != null
                                                || pausedSession != null);

                if (showDifference) {

                        String actualDescription;

                        WorkSession session = activeSession != null
                                        ? activeSession
                                        : pausedSession;

                        if (session != null) {
                                actualDescription = session.taskTitle()
                                                + (session.stepTitle() == null
                                                                ? " / Whole task"
                                                                : " / " + session.stepTitle());
                        } else {
                                actualDescription = selectedTask.getTitle()
                                                + (selectedStep == null
                                                                ? " / Whole task"
                                                                : " / " + selectedStep.title());
                        }

                        String mode = activeSession != null
                                        ? "Recording"
                                        : pausedSession != null
                                                        ? "Paused"
                                                        : "Selected";

                        scheduleFocusLabel.setText(
                                        "Scheduled: " + describeScheduled(planned)
                                                        + "\n"
                                                        + mode + ": " + actualDescription);

                } else {
                        scheduleFocusLabel.setText("");
                }

                boolean visibilityChanged = scheduleFocusLabel.isVisible() != showDifference;

                scheduleFocusLabel.setVisible(showDifference);

                if (visibilityChanged) {
                        requestResize();
                }
        }

        private void showFocusedTask(Task linkedTask) throws SQLException {
                List<TaskStep> taskSteps = steps.findByTaskId(linkedTask.getId());
                int allocated = taskSteps.stream().mapToInt(TaskStep::estimatedMinutes).sum();
                int total = Math.max(linkedTask.getEstimatedMinutes(), allocated);
                int completed = taskSteps.isEmpty()
                                ? (linkedTask.isCompleted() ? total : 0)
                                : taskSteps.stream().filter(TaskStep::completed)
                                                .mapToInt(TaskStep::estimatedMinutes).sum();
                completed = Math.min(completed, total);
                taskBar.setValues(total == 0 ? 0 : completed / (double) total, 0);
                taskProgressLabel.setText(completed + " / " + total + " min · "
                                + (total == 0 ? 0 : Math.round(completed * 100.0 / total))
                                + "% completed");

                if (taskSteps.isEmpty()) {
                        CheckBox checkBox = new CheckBox("Completed");
                        checkBox.setSelected(linkedTask.isCompleted());
                        checkBox.setOnAction(e -> {
                                try {
                                        tasks.updateCompleted(linkedTask.getId(), checkBox.isSelected());
                                        onDataChanged.run();
                                        refresh();
                                } catch (SQLException ex) {
                                        errorLabel.setText("Unable to save task.");
                                        errorLabel.setVisible(true);
                                        ex.printStackTrace();
                                }
                        });
                        taskControls.getChildren().add(checkBox);
                } else {
                        for (TaskStep step : taskSteps) {
                                CheckBox checkBox = new CheckBox(
                                                step.title() + " · " + step.estimatedMinutes() + " min");
                                checkBox.setSelected(step.completed());
                                checkBox.setOnAction(e -> {
                                        try {
                                                steps.updateCompleted(step.id(), checkBox.isSelected());
                                                List<TaskStep> updated = steps.findByTaskId(linkedTask.getId());
                                                int updatedAllocated = updated.stream()
                                                                .mapToInt(TaskStep::estimatedMinutes).sum();
                                                int updatedCompleted = updated.stream()
                                                                .filter(TaskStep::completed)
                                                                .mapToInt(TaskStep::estimatedMinutes).sum();
                                                int updatedTotal = Math.max(
                                                                linkedTask.getEstimatedMinutes(), updatedAllocated);
                                                tasks.updateCompleted(linkedTask.getId(),
                                                                updatedCompleted >= updatedTotal);
                                                onDataChanged.run();
                                                refresh();
                                        } catch (SQLException ex) {
                                                errorLabel.setText("Unable to save step.");
                                                errorLabel.setVisible(true);
                                                ex.printStackTrace();
                                        }
                                });
                                taskControls.getChildren().add(checkBox);
                        }
                }
        }

        private void refreshProjectProgress(List<Task> allTasks) throws SQLException {
                Project project = clockProjectChoice.getValue();
                if (project == null) {
                        projectBar.setValues(0, 0);
                        projectBar.setExpected(0);
                        projectProgressLabel.setText("No project selected");
                        projectExpectedLabel.setText("");
                        return;
                }
                Set<String> ids = new HashSet<>(projects.findTaskIds(project.id()));
                int total = 0;
                int done = 0;
                int todayPending = 0;
                for (Task task : allTasks) {
                        if (!ids.contains(task.getId()))
                                continue;
                        List<TaskStep> taskSteps = steps.findByTaskId(task.getId());
                        int allocated = taskSteps.stream().mapToInt(TaskStep::estimatedMinutes).sum();
                        int planned = Math.max(task.getEstimatedMinutes(), allocated);
                        int completed = taskSteps.isEmpty()
                                        ? (task.isCompleted() ? planned : 0)
                                        : taskSteps.stream().filter(TaskStep::completed)
                                                        .mapToInt(TaskStep::estimatedMinutes).sum();
                        completed = Math.min(completed, planned);
                        total += planned;
                        done += completed;
                        if (LocalDate.now().equals(task.getPlannedDate())) {
                                todayPending += planned - completed;
                        }
                }
                double actual = total == 0 ? 0 : done / (double) total;
                double pending = total == 0 ? 0 : todayPending / (double) total;
                long duration = ChronoUnit.DAYS.between(
                                project.startDate(), project.dueDate());
                long elapsed = ChronoUnit.DAYS.between(
                                project.startDate(), LocalDate.now());
                double expected = duration <= 0 ? 0
                                : Math.max(0, Math.min(1, elapsed / (double) duration));
                Map<String, String> colors = categories.findAll();
                projectBar.setColor(Color.web(
                                colors.getOrDefault(project.categoryName(), "#5F91BC")));
                projectBar.setValues(actual, pending);
                projectBar.setExpected(expected);
                projectProgressLabel.setText(total == 0 ? "No tasks assigned"
                                : done + "/" + total + " min done · "
                                                + todayPending + " min today pending");
                projectExpectedLabel.setText("Actual " + Math.round(actual * 100)
                                + "% · Expected " + Math.round(expected * 100) + "%");
        }

        private void refreshClockFocus() throws SQLException {
                if (currentFocusedTask == null) {
                        clockFocusLabel.setText("No focus task");
                        clockFocusCategory.setText("Choose a task in Focus");
                        clockFocusCard.setStyle("-fx-background-color: #E8EDF5;"
                                        + "-fx-background-radius: 10; -fx-padding: 10;");
                        return;
                }
                String category = categories.findForTask(currentFocusedTask.getId());
                String colorHex = categories.findAll().getOrDefault(category, "#E8EDF5");
                Color color = Color.web(colorHex);
                double brightness = color.getRed() * 0.2126
                                + color.getGreen() * 0.7152 + color.getBlue() * 0.0722;
                String textColor = brightness > 0.55 ? "#26364A" : "#FFFFFF";
                clockFocusLabel.setText(currentFocusedTask.getTitle());
                clockFocusCategory.setText("FOCUS · " + category);
                clockFocusLabel.setStyle("-fx-text-fill: " + textColor
                                + "; -fx-font-weight: bold;");
                clockFocusCategory.setStyle("-fx-text-fill: " + textColor + ";");
                clockFocusCard.setStyle("-fx-background-color: " + colorHex + ";"
                                + "-fx-background-radius: 10; -fx-padding: 10;"
                                + "-fx-effect: dropshadow(gaussian,rgba(40,55,80,0.16),10,0.15,0,3);");
        }

        // -------- Persistent focus sessions --------

        private void startFocusSession() {

                if (unresolvedSession
                                || focusService.state() == FocusSessionService.State.RUNNING) {
                        return;
                }

                Task task = currentFocusedTask;
                TaskStep step = focusStepChoice.getValue();

                if (task == null) {
                        sessionStatusLabel.setText("Choose a task first.");
                        return;
                }

                try {
                        if (focusService.state() == FocusSessionService.State.PAUSED) {
                                focusService.resume(task, step);
                        } else {
                                focusService.start(task, step);
                        }

                        sessionStatusLabel.setText("Recording actual work.");
                        reloadWorkHistorySafely();

                } catch (SQLException | IllegalArgumentException
                                | IllegalStateException ex) {
                        sessionStatusLabel.setText("Start failed: " + ex.getMessage());
                }

                updateSessionTimer();
                updateScheduleFocusStatus();
        }

        private void pauseFocusSession() {

                if (focusService.state() != FocusSessionService.State.RUNNING) {
                        return;
                }

                try {
                        focusService.pause();

                        sessionStatusLabel.setText("Paused.");
                        reloadWorkHistorySafely();

                } catch (SQLException ex) {
                        sessionStatusLabel.setText("Pause failed: " + ex.getMessage());
                }

                updateSessionTimer();
                updateScheduleFocusStatus();
        }

        private void stopFocusSession() {

                if (focusService.state() == FocusSessionService.State.IDLE) {
                        return;
                }

                try {
                        focusService.stop();

                        sessionStatusLabel.setText("Focus finished. Time saved.");
                        reloadWorkHistorySafely();

                        // Restore automatic Timeline / Focus selection if appropriate.
                        refresh();

                } catch (SQLException ex) {
                        sessionStatusLabel.setText("Stop failed: " + ex.getMessage());
                }

                updateSessionTimer();
                updateScheduleFocusStatus();
        }

        private void updateSessionTimer() {

                Duration elapsed = focusService.elapsed(Instant.now());

                long seconds = Math.max(0, elapsed.getSeconds());

                sessionTimerLabel.setText(String.format(
                                "%02d:%02d:%02d",
                                seconds / 3600,
                                (seconds % 3600) / 60,
                                seconds % 60));

                boolean recording = focusService.state() == FocusSessionService.State.RUNNING;

                boolean paused = focusService.state() == FocusSessionService.State.PAUSED;

                boolean locked = focusIsLocked();

                sessionStartButton.setText(paused ? "Resume" : "Start");

                sessionStartButton.setDisable(
                                recording || unresolvedSession || currentFocusedTask == null);

                sessionPauseButton.setDisable(!recording);
                sessionStopButton.setDisable(!locked);

                focusTaskChoice.setDisable(locked);
                focusStepChoice.setDisable(locked || currentFocusedTask == null);
                wholeTaskButton.setDisable(locked || currentFocusedTask == null);
                autoFocusButton.setDisable(locked);

                recoverSessionButton.setVisible(unresolvedSession);

                updateActualTodayLabel();
        }

        private void recoverUnfinishedSession() {
                if (focusService.state() != FocusSessionService.State.IDLE) {
                        return;
                }
                try {
                        Optional<WorkSession> pending = workSessions.findActive();
                        if (pending.isEmpty()) {
                                unresolvedSession = false;
                                return;
                        }
                        unresolvedSession = true;
                        WorkSession session = pending.get();
                        ZoneId zone = ZoneId.systemDefault();
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                        String started = LocalDateTime.ofInstant(session.startedAt(), zone)
                                        .format(formatter);
                        TextInputDialog dialog = new TextInputDialog("");
                        dialog.initOwner(window);
                        dialog.initModality(Modality.WINDOW_MODAL);
                        dialog.setTitle("Recover Focus Session");
                        dialog.setHeaderText("Unfinished: " + session.taskTitle()
                                        + "\nStarted: " + started);
                        dialog.setContentText("Actual end time (yyyy-MM-dd HH:mm):");
                        URL stylesheet = getClass().getResource("/styles/light.css");
                        if (stylesheet != null) {
                                dialog.getDialogPane().getStylesheets()
                                                .add(stylesheet.toExternalForm());
                        }

                        // The owner is always-on-top; temporarily release it so the dialog is usable.
                        boolean originalTop = window.isAlwaysOnTop();
                        Optional<String> response;
                        window.setAlwaysOnTop(false);
                        try {
                                response = dialog.showAndWait();
                        } finally {
                                window.setAlwaysOnTop(originalTop);
                        }
                        if (response.isEmpty() || response.get().isBlank()) {
                                sessionStatusLabel.setText(
                                                "Unfinished session. Click Recover to resolve it.");
                                return;
                        }
                        Instant actualEnd = LocalDateTime.parse(response.get().trim(), formatter)
                                        .atZone(zone).toInstant();
                        Instant now = Instant.now();
                        if (actualEnd.isBefore(session.startedAt()) || actualEnd.isAfter(now)) {
                                throw new IllegalArgumentException(
                                                "End time must be between session start and now.");
                        }
                        if (!workSessions.stop(session.id(), actualEnd)) {
                                throw new SQLException("Recovery could not be saved.");
                        }
                        unresolvedSession = false;
                        sessionStatusLabel.setText("Previous session recovered.");
                } catch (SQLException | DateTimeParseException | IllegalArgumentException ex) {
                        unresolvedSession = true;
                        sessionStatusLabel.setText("Recovery failed: " + ex.getMessage());
                } finally {
                        reloadWorkHistorySafely();
                        updateSessionTimer();
                }
        }

        // -------- Actual work history (separate from task checkbox progress) --------

        private void reloadWorkHistorySafely() {
                try {
                        reloadWorkHistory();
                } catch (SQLException ex) {
                        sessionStatusLabel.setText("Unable to load work history.");
                        ex.printStackTrace();
                }
        }

        private void reloadWorkHistory() throws SQLException {
                LocalDate today = LocalDate.now();
                ZoneId zone = ZoneId.systemDefault();
                finishedMillisToday = workSessions.finishedMillisForDate(today, zone);
                finishedMillisDate = today;
                List<WorkSession> recent = workSessions.findRecentFinishedForDate(today, zone, 3);
                recentSessionsList.getChildren().clear();
                if (recent.isEmpty()) {
                        recentSessionsList.getChildren().add(
                                        new Label("No completed sessions today."));
                }
                DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("HH:mm").withZone(zone);
                Instant dayStart = today.atStartOfDay(zone).toInstant();
                Instant dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant();
                for (WorkSession session : recent) {
                        Instant from = session.startedAt().isBefore(dayStart)
                                        ? dayStart
                                        : session.startedAt();
                        Instant to = session.endedAt().isAfter(dayEnd)
                                        ? dayEnd
                                        : session.endedAt();
                        long minutes = Math.max(0, Duration.between(from, to).toMinutes());
                        String activity = session.stepTitle() == null
                                        ? session.taskTitle()
                                        : session.taskTitle() + " / " + session.stepTitle();
                        Label entry = new Label(timeFormat.format(from) + "–"
                                        + timeFormat.format(to) + " · " + activity
                                        + " · " + minutes + "m");
                        entry.setWrapText(true);
                        entry.setMaxWidth(Double.MAX_VALUE);
                        recentSessionsList.getChildren().add(entry);
                }
                updateActualTodayLabel();
                if (recentSessionsPane.isExpanded())
                        requestResize();
        }

        private void updateActualTodayLabel() {
                LocalDate today = LocalDate.now();
                if (!today.equals(finishedMillisDate)) {

                        todayActualLabel.setText(
                                        "Today's actual work: updating...");

                        if (!dayRefreshPending && window.isShowing()) {

                                dayRefreshPending = true;

                                Platform.runLater(() -> {
                                        try {
                                                reloadWorkHistorySafely();
                                        } finally {
                                                dayRefreshPending = false;
                                        }
                                });
                        }

                        return;
                }
                long totalMillis = finishedMillisToday;

                WorkSession activeSession = focusService.activeSession();

                if (activeSession != null) {
                        ZoneId zone = ZoneId.systemDefault();
                        Instant dayStart = today.atStartOfDay(zone).toInstant();
                        Instant dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant();
                        Instant from = activeSession.startedAt().isBefore(dayStart)
                                        ? dayStart
                                        : activeSession.startedAt();
                        Instant now = Instant.now();
                        Instant to = now.isAfter(dayEnd) ? dayEnd : now;
                        if (to.isAfter(from)) {
                                totalMillis += Duration.between(from, to).toMillis();
                        }
                }
                long minutes = totalMillis / 60_000L;
                todayActualLabel.setText("Today's actual work: "
                                + (minutes / 60) + "h " + (minutes % 60) + "m");
        }
}
