
package dev.personalagenda.view;

import dev.personalagenda.data.ProjectRepository;
import dev.personalagenda.data.TaskRepository;
import dev.personalagenda.data.TaskStepRepository;
import dev.personalagenda.model.Project;
import dev.personalagenda.model.Task;
import dev.personalagenda.model.TaskStep;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

public class ProjectDashboard extends VBox {

    private final ProjectRepository projects = new ProjectRepository();

    private final TaskRepository tasks = new TaskRepository();

    private final TaskStepRepository steps = new TaskStepRepository();

    private final Map<String, String> categoryColors;

    private final VBox projectCards = new VBox(16);
    private final Label status = new Label();
    private final ComboBox<String> categoryInput = new ComboBox<>();

    public ProjectDashboard(Map<String, String> categoryColors) {

        this.categoryColors = categoryColors;
        setSpacing(16);

        Label heading = new Label("Projects");

        TextField nameInput = new TextField();
        nameInput.setPromptText("Project name");

        DatePicker startInput = new DatePicker(LocalDate.now());

        DatePicker dueInput = new DatePicker(LocalDate.now().plusDays(30));

        Button createButton = new Button("Create Project");

        categoryInput.setOnShowing(event -> loadCategories());
        loadCategories();

        createButton.setOnAction(event -> {
            try {
                Project project = new Project(
                        nameInput.getText().trim(),
                        categoryInput.getValue(),
                        startInput.getValue(),
                        dueInput.getValue());

                projects.insert(project);
                nameInput.clear();
                status.setText("Project created.");
                refresh();

            } catch (IllegalArgumentException ex) {
                status.setText(ex.getMessage());
            } catch (SQLException ex) {
                status.setText("Unable to create project.");
                ex.printStackTrace();
            }
        });

        VBox creation = new VBox(
                8,
                heading,
                nameInput,
                new HBox(
                        8, new Label("Category"), categoryInput),
                new HBox(
                        8, new Label("Start"), startInput,
                        new Label("Due"), dueInput),
                createButton,
                status);

        getChildren().addAll(creation, projectCards);
        refresh();
    }

    private void loadCategories() {

        String selected = categoryInput.getValue();

        categoryInput.getItems().setAll(
                categoryColors.keySet());

        if (selected != null
                && categoryColors.containsKey(selected)) {
            categoryInput.setValue(selected);

        } else if (!categoryInput.getItems().isEmpty()) {
            categoryInput.setValue(
                    categoryInput.getItems().get(0));
        }
    }

    public void refresh() {

        projectCards.getChildren().clear();
        loadCategories();

        try {
            List<Task> allTasks = tasks.findAll();

            for (Project project : projects.findAll()) {

                Set<String> ids = new HashSet<>(
                        projects.findTaskIds(project.id()));

                int totalMinutes = 0;
                int completedMinutes = 0;
                int todayPendingMinutes = 0;

                VBox assignedRows = new VBox(5);

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

                    int done = taskSteps.isEmpty()
                            ? (task.isCompleted() ? planned : 0)
                            : taskSteps.stream()
                                    .filter(TaskStep::completed)
                                    .mapToInt(TaskStep::estimatedMinutes)
                                    .sum();

                    done = Math.min(done, planned);

                    totalMinutes += planned;
                    completedMinutes += done;

                    if (LocalDate.now().equals(
                            task.getPlannedDate())) {

                        todayPendingMinutes += planned - done;
                    }

                    Button remove = new Button("×");
                    remove.setOnAction(event -> {
                        try {
                            projects.assignTask(task.getId(), null);
                            refresh();
                        } catch (SQLException ex) {
                            ex.printStackTrace();
                        }
                    });

                    Label taskLabel = new Label(task.getTitle());
                    HBox.setHgrow(taskLabel, Priority.ALWAYS);

                    assignedRows.getChildren().add(
                            new HBox(8, taskLabel, remove));
                }

                CompositeProgressBar bar = new CompositeProgressBar();

                bar.setColor(Color.web(
                        categoryColors.getOrDefault(
                                project.categoryName(), "#5F91BC")));

                double actual = totalMinutes == 0
                        ? 0
                        : completedMinutes / (double) totalMinutes;

                double todayPending = totalMinutes == 0
                        ? 0
                        : todayPendingMinutes / (double) totalMinutes;

                bar.setValues(actual, todayPending);

                long duration = java.time.temporal.ChronoUnit.DAYS.between(
                        project.startDate(), project.dueDate());

                long elapsed = java.time.temporal.ChronoUnit.DAYS.between(
                        project.startDate(), LocalDate.now());

                double expected = Math.max(
                        0, Math.min(1, elapsed / (double) duration));

                Label progressText = new Label(
                        totalMinutes == 0
                                ? "No tasks assigned"
                                : String.format(
                                        "Done: %d/%d min | Today pending: %d min",
                                        completedMinutes,
                                        totalMinutes,
                                        todayPendingMinutes));

                Label expectedText = new Label(
                        String.format(
                                "Actual %.0f%% | Expected %.0f%% | Difference %+.0f pp",
                                actual * 100,
                                expected * 100,
                                (actual - expected) * 100));

                ComboBox<Task> taskInput = new ComboBox<>();
                taskInput.getItems().setAll(allTasks);
                taskInput.setPromptText("Choose task");
                taskInput.setMaxWidth(Double.MAX_VALUE);

                Button assignButton = new Button("Assign");

                assignButton.setOnAction(event -> {
                    Task selected = taskInput.getValue();
                    if (selected == null) {
                        return;
                    }

                    try {
                        projects.assignTask(
                                selected.getId(), project.id());
                        refresh();

                    } catch (SQLException ex) {
                        ex.printStackTrace();
                    }
                });

                HBox assignment = new HBox(
                        8, taskInput, assignButton);
                HBox.setHgrow(taskInput, Priority.ALWAYS);

                VBox card = new VBox(
                        9,
                        new Label(project.name()),
                        new Label(
                                project.startDate()
                                        + " → " + project.dueDate()),
                        bar,
                        progressText,
                        expectedText,
                        assignedRows,
                        assignment);

                card.getStyleClass().add("project-card");
                projectCards.getChildren().add(card);
            }

        } catch (SQLException ex) {
            status.setText("Unable to load projects.");
            ex.printStackTrace();
        }
    }
}
