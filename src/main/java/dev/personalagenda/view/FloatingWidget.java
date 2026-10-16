
package dev.personalagenda.view;

import dev.personalagenda.data.TaskRepository;
import dev.personalagenda.data.TaskStepRepository;
import dev.personalagenda.data.TimeBlockRepository;
import dev.personalagenda.data.ProjectRepository;
import dev.personalagenda.data.CategoryRepository;
import dev.personalagenda.model.Project;
import dev.personalagenda.model.Task;
import dev.personalagenda.model.TaskStep;
import dev.personalagenda.model.TimeBlock;

import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
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

    private final Timeline clock = new Timeline(
            new KeyFrame(
                    javafx.util.Duration.seconds(30),
                    event -> refresh()));

    public FloatingWidget(
            Stage mainWindow,
            Runnable onDataChanged) {

        this.mainWindow = mainWindow;
        this.onDataChanged = onDataChanged;

        window.initStyle(StageStyle.UTILITY);
        window.setTitle("Agenda · Mini");
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

        Button autoButton = new Button("Auto");
        autoButton.setOnAction(event -> {
            manualFocusTaskId = null;
            refresh();
        });

        HBox focusPicker = new HBox(
                6, focusTaskChoice, autoButton);
        HBox.setHgrow(focusTaskChoice, Priority.ALWAYS);

        taskBar.setColor(Color.web("#887ABD"));

        ScrollPane taskScroll = new ScrollPane(taskControls);
        taskScroll.setFitToWidth(true);
        taskScroll.setPrefHeight(100);
        taskScroll.setMaxHeight(120);

        VBox focusContent = new VBox(
                10,
                currentLabel,
                focusTitle,
                focusPicker,
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

        clockFocusCard.setPrefWidth(140);
        clockFocusCard.setMaxWidth(140);
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
                heading,
                dailySection,
                tabs,
                errorLabel,
                footer);

        root.setPadding(new Insets(14));
        root.getStyleClass().add("mini-widget");

        root.setPrefWidth(390);

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

        window.setOnHidden(event -> clock.stop());
    }

    public void show() {
        refresh();

        if (!window.isShowing()) {
            window.show();
            clock.play();
        } else {
            window.toFront();
        }
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
