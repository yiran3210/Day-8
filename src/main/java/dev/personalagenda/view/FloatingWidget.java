
package dev.personalagenda.view;

import dev.personalagenda.data.TaskRepository;
import dev.personalagenda.data.TaskStepRepository;
import dev.personalagenda.data.TimeBlockRepository;
import dev.personalagenda.model.Task;
import dev.personalagenda.model.TaskStep;
import dev.personalagenda.model.TimeBlock;

import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

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

    private final ProgressBar timeBar = new ProgressBar(0);
    private final ProgressBar dailyBar = new ProgressBar(0);

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
        window.setMinHeight(240);

        Label heading = new Label("Personal Agenda");
        heading.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Label todayTitle = new Label("Today's Progress");

        timeBar.setMaxWidth(Double.MAX_VALUE);
        dailyBar.setMaxWidth(Double.MAX_VALUE);

        currentLabel.setWrapText(true);
        nextLabel.setWrapText(true);
        dailyLabel.setWrapText(true);

        ScrollPane taskScroll = new ScrollPane(taskControls);
        taskScroll.setFitToWidth(true);
        taskScroll.setPrefHeight(120);
        taskScroll.setMaxHeight(140);

        Button openButton = new Button("Open Agenda");
        openButton.setOnAction(event -> {
            mainWindow.show();
            mainWindow.setIconified(false);
            mainWindow.toFront();
        });

        Button refreshButton = new Button("Refresh");
        refreshButton.setOnAction(event -> refresh());

        VBox root = new VBox(
                10,
                heading,
                todayTitle,
                dailyBar,
                dailyLabel,
                new Separator(),
                currentLabel,
                timeBar,
                nextLabel,
                taskScroll,
                errorLabel,
                new HBox(8, openButton, refreshButton));

        root.setPadding(new Insets(16));

        Scene scene = new Scene(root, 320, 360);
        scene.getStylesheets().add(
                getClass().getResource(
                        "/styles/light.css").toExternalForm());

        window.setScene(scene);

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

            dailyBar.setProgress(
                    planned == 0 ? 0 : done / (double) planned);

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
            timeBar.setProgress(0);

            if (current == null) {

                currentLabel.setText("Current: Unscheduled");

            } else {

                double elapsed = Duration.between(
                        current.start(), now).toSeconds();

                double total = Duration.between(
                        current.start(), current.end()).toSeconds();

                timeBar.setProgress(elapsed / total);

                long remaining = (long) Math.ceil(
                        (total - elapsed) / 60.0);

                currentLabel.setText(
                        "Current: " + current.title()
                                + " · " + remaining + " min left");

                showLinkedTask(current, allTasks);
            }

            nextLabel.setText(
                    next == null
                            ? "Next: No upcoming activity"
                            : "Next: " + next.title()
                                    + " · " + next.start().toLocalTime());

            errorLabel.setText("");

        } catch (SQLException ex) {
            errorLabel.setText("Unable to refresh Agenda.");
            ex.printStackTrace();
        }
    }

    private void showLinkedTask(
            TimeBlock block,
            List<Task> allTasks) throws SQLException {

        if (block.taskId() == null) {
            return;
        }

        Task linkedTask = allTasks.stream()
                .filter(task -> task.getId().equals(block.taskId()))
                .findFirst()
                .orElse(null);

        if (linkedTask == null) {
            return;
        }

        List<TaskStep> taskSteps = steps.findByTaskId(linkedTask.getId());

        Label heading = new Label(
                "Task: " + linkedTask.getTitle());

        taskControls.getChildren().add(heading);

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

                        int allocated = updated.stream()
                                .mapToInt(TaskStep::estimatedMinutes)
                                .sum();

                        int completed = updated.stream()
                                .filter(TaskStep::completed)
                                .mapToInt(TaskStep::estimatedMinutes)
                                .sum();

                        int total = Math.max(
                                linkedTask.getEstimatedMinutes(),
                                allocated);

                        tasks.updateCompleted(
                                linkedTask.getId(),
                                completed >= total);

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
}
