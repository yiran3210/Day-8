
package dev.personalagenda.view;

import dev.personalagenda.model.Task;
import dev.personalagenda.model.TaskStep;
import dev.personalagenda.data.TaskRepository;
import dev.personalagenda.data.TaskStepRepository;

import java.sql.SQLException;
import java.util.List;
import java.util.function.Consumer;

import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class TaskStepsView extends VBox {

    private final Task task;
    private final CheckBox parentCheckbox;
    private final TaskRepository taskRepository;
    private final TaskStepRepository stepRepository = new TaskStepRepository();
    private final Runnable onChanged;
    private final Consumer<TaskStep> onSchedule;
    private final Consumer<TaskStep> onFocus;

    private final ProgressBar bar = new ProgressBar(0);
    private final Label summary = new Label();
    private final VBox stepRows = new VBox(6);

    private final Button toggleButton = new Button("Steps ▸");
    private final VBox details;
    private Boolean previousAllStepsCompleted = null;

    public TaskStepsView(
            Task task,
            CheckBox parentCheckbox,
            TaskRepository taskRepository,
            Runnable onChanged,
            Consumer<TaskStep> onSchedule,
            Consumer<TaskStep> onFocus) {

        this.task = task;
        this.parentCheckbox = parentCheckbox;
        this.taskRepository = taskRepository;
        this.onChanged = onChanged;
        this.onSchedule = onSchedule;
        this.onFocus = onFocus;

        setSpacing(6);
        getStyleClass().add("task-steps");

        // Compact progress header
        bar.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(bar, Priority.ALWAYS);

        HBox header = new HBox(
                10, new Label("Progress"), bar,
                summary, toggleButton);
        header.setAlignment(Pos.CENTER_LEFT);

        // Step creation controls
        TextField stepName = new TextField();
        stepName.setPromptText("New step");

        Spinner<Integer> stepMinutes = new Spinner<>(5, 600, 30, 5);
        MinuteSpinnerSupport.enable(stepMinutes);
        stepMinutes.setPrefWidth(95);

        Button addButton = new Button("+ Step");

        HBox addRow = new HBox(
                8, stepName, stepMinutes, addButton);
        HBox.setHgrow(stepName, Priority.ALWAYS);

        addRow.setAlignment(Pos.CENTER_LEFT);

        stepName.setMinWidth(60);
        stepName.setMaxWidth(Double.MAX_VALUE);

        HBox.setHgrow(stepName, Priority.ALWAYS);

        stepMinutes.setMinWidth(Region.USE_PREF_SIZE);
        addButton.setMinWidth(Region.USE_PREF_SIZE);

        details = new VBox(8, stepRows, addRow);

        setDetailsExpanded(false);

        toggleButton.setOnAction(event -> setDetailsExpanded(!details.isVisible()));

        addButton.setOnAction(event -> {

            String title = stepName.getText().trim();
            if (title.isEmpty()) {
                return;
            }

            int minutes;

            try {
                minutes = MinuteSpinnerSupport.read(stepMinutes);
            } catch (IllegalArgumentException ex) {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setHeaderText("Invalid step duration");
                alert.setContentText(ex.getMessage());
                alert.showAndWait();
                return;
            }

            try {
                TaskStep step = new TaskStep(
                        task.getId(),
                        title,
                        minutes);

                stepRepository.insert(step);
                stepName.clear();

                refresh();
                onChanged.run();

            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        });

        getChildren().addAll(header, details);
        refresh();
    }

    private void setDetailsExpanded(boolean expanded) {
        details.setVisible(expanded);
        details.setManaged(expanded);
        toggleButton.setText(expanded ? "Steps ▾" : "Steps ▸");
    }

    public void refresh() {

        try {
            List<TaskStep> steps = stepRepository.findByTaskId(task.getId());

            int allocated = steps.stream()
                    .mapToInt(TaskStep::estimatedMinutes)
                    .sum();

            int completedMinutes = steps.stream()
                    .filter(TaskStep::completed)
                    .mapToInt(TaskStep::estimatedMinutes)
                    .sum();

            boolean hasSteps = !steps.isEmpty();

            boolean allStepsCompleted = hasSteps
                    && steps.stream().allMatch(TaskStep::completed);

            // Auto-collapse only when the task changes
            // from partially completed to fully completed.
            if (Boolean.FALSE.equals(previousAllStepsCompleted)
                    && allStepsCompleted) {
                setDetailsExpanded(false);
            }

            previousAllStepsCompleted = allStepsCompleted;

            int total = hasSteps
                    ? allocated
                    : task.getEstimatedMinutes();

            int bufferMinutes = hasSteps
                    ? Math.max(0, task.getEstimatedMinutes() - allocated)
                    : 0;

            // Steps determine the parent task's status
            if (hasSteps) {

                boolean completed = steps.stream()
                        .allMatch(TaskStep::completed);

                if (task.isCompleted() != completed) {
                    taskRepository.updateCompleted(
                            task.getId(), completed);
                    task.setCompleted(completed);
                }
            }

            parentCheckbox.setDisable(hasSteps);
            parentCheckbox.setSelected(task.isCompleted());

            double progress = hasSteps
                    ? completedMinutes / (double) total
                    : (task.isCompleted() ? 1.0 : 0.0);

            bar.setProgress(progress);

            if (hasSteps) {
                summary.setText(
                        Math.round(progress * 100) + "%"
                                + " · Steps " + completedMinutes
                                + "/" + total + " min"
                                + (bufferMinutes > 0
                                        ? " · Buffer " + bufferMinutes + " min"
                                        : ""));
            } else {
                summary.setText(
                        task.isCompleted()
                                ? "100% · manual"
                                : "0% · no steps");
            }

            // Rebuild step checkboxes
            stepRows.getChildren().clear();

            for (TaskStep step : steps) {

                CheckBox box = new CheckBox(step.title());
                box.setSelected(step.completed());

                Label duration = new Label(
                        step.estimatedMinutes() + " min");

                Button scheduleButton = new Button("Schedule");

                Button focusButton = new Button("Focus");

                focusButton.setOnAction(event -> onFocus.accept(step));

                Button deleteButton = new Button("×");

                HBox row = new HBox(
                        8, box, duration, scheduleButton, deleteButton);

                Button editStepButton = new Button("Edit");

                row.getChildren().add(
                        row.getChildren().size() - 1, editStepButton);

                row.getChildren().removeAll(
                        scheduleButton,
                        editStepButton,
                        deleteButton);

                row.setAlignment(Pos.CENTER_LEFT);

                box.setMinWidth(0);
                box.setWrapText(true);

                duration.setMinWidth(Region.USE_PREF_SIZE);

                HBox stepActions = UiLayout.rightActions(
                        scheduleButton,
                        focusButton,
                        editStepButton,
                        deleteButton);

                stepActions.getStyleClass().add("step-actions");

                TextField stepTitleEdit = new TextField(step.title());

                Spinner<Integer> stepMinutesEdit = new Spinner<>(
                        1, 1440, step.estimatedMinutes(), 5);

                MinuteSpinnerSupport.enable(stepMinutesEdit);

                stepMinutesEdit.setPrefWidth(105);

                Button saveStepEdit = new Button("Save");
                Button cancelStepEdit = new Button("Cancel");

                Label editStatus = new Label();

                FlowPane editActions = new FlowPane(
                        8, 6,
                        new Label("Minutes"),
                        stepMinutesEdit,
                        saveStepEdit,
                        cancelStepEdit);

                editActions.setAlignment(Pos.CENTER_RIGHT);

                VBox stepEditor = new VBox(
                        6, stepTitleEdit, editActions, editStatus);

                stepEditor.setVisible(false);
                stepEditor.setManaged(false);

                editStepButton.setOnAction(event -> {
                    stepEditor.setVisible(true);
                    stepEditor.setManaged(true);
                });

                cancelStepEdit.setOnAction(event -> {
                    stepEditor.setVisible(false);
                    stepEditor.setManaged(false);
                });

                saveStepEdit.setOnAction(event -> {
                    String title = stepTitleEdit.getText().trim();

                    if (title.isEmpty()) {
                        editStatus.setText("Step title is required.");
                        return;
                    }

                    int minutes;

                    try {
                        minutes = MinuteSpinnerSupport.read(stepMinutesEdit);
                    } catch (IllegalArgumentException ex) {
                        editStatus.setText(ex.getMessage());
                        return;
                    }

                    try {
                        stepRepository.updateDetails(
                                step.id(), title, minutes);

                        refresh();
                        onChanged.run();

                    } catch (SQLException ex) {
                        editStatus.setText("Unable to update step.");
                        ex.printStackTrace();
                    }
                });

                scheduleButton.setOnAction(event -> onSchedule.accept(step));

                box.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(box, Priority.ALWAYS);

                box.setOnAction(event -> {
                    try {
                        stepRepository.updateCompleted(
                                step.id(), box.isSelected());

                        refresh();
                        onChanged.run();

                    } catch (SQLException ex) {
                        ex.printStackTrace();
                        refresh();
                    }
                });

                deleteButton.setOnAction(event -> {
                    try {
                        stepRepository.delete(step.id());

                        refresh();
                        onChanged.run();

                    } catch (SQLException ex) {
                        ex.printStackTrace();
                    }
                });

                VBox stepEntry = new VBox(
                        6,
                        row,
                        stepActions,
                        stepEditor);

                stepEntry.getStyleClass().add("step-entry");

                stepRows.getChildren().add(stepEntry);
            }

        } catch (SQLException ex) {
            summary.setText("Unable to load steps");
            ex.printStackTrace();
        }
    }
}
