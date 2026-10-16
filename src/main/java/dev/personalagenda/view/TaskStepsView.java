
package dev.personalagenda.view;

import dev.personalagenda.model.Task;
import dev.personalagenda.model.TaskStep;
import dev.personalagenda.data.TaskRepository;
import dev.personalagenda.data.TaskStepRepository;

import java.sql.SQLException;
import java.util.List;

import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class TaskStepsView extends VBox {

    private final Task task;
    private final CheckBox parentCheckbox;
    private final TaskRepository taskRepository;
    private final TaskStepRepository stepRepository = new TaskStepRepository();
    private final Runnable onChanged;

    private final ProgressBar bar = new ProgressBar(0);
    private final Label summary = new Label();
    private final VBox stepRows = new VBox(6);

    public TaskStepsView(
            Task task,
            CheckBox parentCheckbox,
            TaskRepository taskRepository,
            Runnable onChanged) {

        this.task = task;
        this.parentCheckbox = parentCheckbox;
        this.taskRepository = taskRepository;
        this.onChanged = onChanged;

        setSpacing(6);
        getStyleClass().add("task-steps");

        // Compact progress header
        bar.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(bar, Priority.ALWAYS);

        Button toggleButton = new Button("Steps ▾");

        HBox header = new HBox(
                10, new Label("Progress"), bar,
                summary, toggleButton);
        header.setAlignment(Pos.CENTER_LEFT);

        // Step creation controls
        TextField stepName = new TextField();
        stepName.setPromptText("New step");

        Spinner<Integer> stepMinutes = new Spinner<>(5, 600, 30, 5);
        stepMinutes.setPrefWidth(95);

        Button addButton = new Button("+ Step");

        HBox addRow = new HBox(
                8, stepName, stepMinutes, addButton);
        HBox.setHgrow(stepName, Priority.ALWAYS);

        VBox details = new VBox(8, stepRows, addRow);
        details.setVisible(false);
        details.setManaged(false);

        toggleButton.setOnAction(event -> {
            boolean expanded = !details.isVisible();
            details.setVisible(expanded);
            details.setManaged(expanded);
        });

        addButton.setOnAction(event -> {

            String title = stepName.getText().trim();
            if (title.isEmpty()) {
                return;
            }

            try {
                TaskStep step = new TaskStep(
                        task.getId(),
                        title,
                        stepMinutes.getValue());

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

            int total = Math.max(
                    task.getEstimatedMinutes(), allocated);

            boolean hasSteps = !steps.isEmpty();

            // Steps determine the parent task's status
            if (hasSteps) {

                boolean completed = completedMinutes >= total;

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
                                + " · " + completedMinutes
                                + "/" + total + " min");
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

                Button deleteButton = new Button("×");

                HBox row = new HBox(
                        8, box, duration, deleteButton);

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

                stepRows.getChildren().add(row);
            }

        } catch (SQLException ex) {
            summary.setText("Unable to load steps");
            ex.printStackTrace();
        }
    }
}
