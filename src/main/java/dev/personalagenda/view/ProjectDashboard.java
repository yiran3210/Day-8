
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

                GridPane creationGrid = new GridPane();

                creationGrid.setHgap(12);
                creationGrid.setVgap(10);

                ColumnConstraints labelColumn = new ColumnConstraints(85);

                ColumnConstraints inputColumn = new ColumnConstraints();
                inputColumn.setHgrow(Priority.ALWAYS);
                inputColumn.setFillWidth(true);

                creationGrid.getColumnConstraints().addAll(
                                labelColumn, inputColumn);

                nameInput.setMaxWidth(Double.MAX_VALUE);
                categoryInput.setMaxWidth(Double.MAX_VALUE);
                startInput.setMaxWidth(Double.MAX_VALUE);
                dueInput.setMaxWidth(Double.MAX_VALUE);

                creationGrid.add(new Label("Project"), 0, 0);
                creationGrid.add(nameInput, 1, 0);

                creationGrid.add(new Label("Category"), 0, 1);
                creationGrid.add(categoryInput, 1, 1);

                creationGrid.add(new Label("Start"), 0, 2);
                creationGrid.add(startInput, 1, 2);

                creationGrid.add(new Label("Due"), 0, 3);
                creationGrid.add(dueInput, 1, 3);

                creationGrid.add(createButton, 1, 4);

                GridPane.setHalignment(
                                createButton, javafx.geometry.HPos.RIGHT);

                VBox creation = new VBox(
                                10, heading, creationGrid, status);

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

                                        int planned = taskSteps.isEmpty()
                                                        ? task.getEstimatedMinutes()
                                                        : allocated;

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

                                bar.setExpected(expected);

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

                                assignButton.setMinWidth(Region.USE_PREF_SIZE);

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

                                taskInput.setMinWidth(0);
                                assignment.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

                                int assignedCount = assignedRows.getChildren().size();

                                VBox taskManagement = new VBox(
                                                10,
                                                assignedRows,
                                                assignment);

                                TitledPane tasksPane = new TitledPane(
                                                "Assigned Tasks (" + assignedCount + ")",
                                                taskManagement);

                                tasksPane.setExpanded(false);
                                tasksPane.setAnimated(false);

                                Button editProjectButton = new Button("Edit");

                                Button deleteProjectButton = new Button("Delete");

                                TextField projectNameEdit = new TextField(project.name());

                                ComboBox<String> projectCategoryEdit = new ComboBox<>();
                                projectCategoryEdit.getItems().setAll(
                                                categoryColors.keySet());
                                projectCategoryEdit.setValue(project.categoryName());

                                DatePicker projectStartEdit = new DatePicker(project.startDate());

                                DatePicker projectDueEdit = new DatePicker(project.dueDate());

                                Button saveProjectEdit = new Button("Save");
                                Button cancelProjectEdit = new Button("Cancel");
                                Label projectEditStatus = new Label();

                                GridPane editGrid = new GridPane();
                                editGrid.setHgap(12);
                                editGrid.setVgap(10);

                                ColumnConstraints editLabelCol = new ColumnConstraints(85);

                                ColumnConstraints editInputCol = new ColumnConstraints();
                                editInputCol.setHgrow(Priority.ALWAYS);
                                editInputCol.setFillWidth(true);

                                editGrid.getColumnConstraints().addAll(
                                                editLabelCol, editInputCol);

                                projectNameEdit.setMaxWidth(Double.MAX_VALUE);
                                projectCategoryEdit.setMaxWidth(Double.MAX_VALUE);
                                projectStartEdit.setMaxWidth(Double.MAX_VALUE);
                                projectDueEdit.setMaxWidth(Double.MAX_VALUE);

                                editGrid.add(new Label("Project"), 0, 0);
                                editGrid.add(projectNameEdit, 1, 0);

                                editGrid.add(new Label("Category"), 0, 1);
                                editGrid.add(projectCategoryEdit, 1, 1);

                                editGrid.add(new Label("Start"), 0, 2);
                                editGrid.add(projectStartEdit, 1, 2);

                                editGrid.add(new Label("Due"), 0, 3);
                                editGrid.add(projectDueEdit, 1, 3);

                                HBox editActions = new HBox(
                                                8, saveProjectEdit, cancelProjectEdit);

                                editGrid.add(editActions, 1, 4);

                                VBox projectEditor = new VBox(
                                                8, editGrid, projectEditStatus);

                                projectEditor.setVisible(false);
                                projectEditor.setManaged(false);

                                editProjectButton.setOnAction(event -> {
                                        boolean open = !projectEditor.isVisible();
                                        projectEditor.setVisible(open);
                                        projectEditor.setManaged(open);
                                });

                                cancelProjectEdit.setOnAction(event -> {
                                        projectEditor.setVisible(false);
                                        projectEditor.setManaged(false);
                                });

                                saveProjectEdit.setOnAction(event -> {
                                        try {
                                                Project updated = new Project(
                                                                project.id(),
                                                                projectNameEdit.getText().trim(),
                                                                projectCategoryEdit.getValue(),
                                                                projectStartEdit.getValue(),
                                                                projectDueEdit.getValue());

                                                projects.update(updated);
                                                refresh();

                                        } catch (IllegalArgumentException ex) {
                                                projectEditStatus.setText(ex.getMessage());
                                        } catch (SQLException ex) {
                                                projectEditStatus.setText("Update failed.");
                                                ex.printStackTrace();
                                        }
                                });

                                deleteProjectButton.setOnAction(event -> {

                                        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);

                                        confirmation.setGraphic(null);

                                        confirmation.getDialogPane().getStylesheets().add(
                                                        getClass().getResource("/styles/light.css").toExternalForm());

                                        confirmation.getDialogPane()
                                                        .getStyleClass().add("agenda-confirmation");

                                        confirmation.setTitle("Delete Project");
                                        confirmation.setHeaderText(
                                                        "Delete project: " + project.name() + "?");

                                        confirmation.setContentText(
                                                        "The project will be permanently deleted.\n"
                                                                        + "Associated tasks, steps and scheduled activities will be kept.");

                                        ButtonType deleteChoice = new ButtonType(
                                                        "Delete Project",
                                                        ButtonBar.ButtonData.OK_DONE);

                                        confirmation.getButtonTypes().setAll(
                                                        deleteChoice, ButtonType.CANCEL);

                                        Button dangerButton = (Button) confirmation
                                                        .getDialogPane().lookupButton(deleteChoice);

                                        dangerButton.getStyleClass().add("danger-action");
                                        ButtonType result = confirmation.showAndWait()
                                                        .orElse(ButtonType.CANCEL);

                                        if (result != deleteChoice) {
                                                return;
                                        }

                                        try {
                                                projects.delete(project.id());
                                                refresh();

                                        } catch (SQLException ex) {
                                                status.setText("Unable to delete project.");
                                                ex.printStackTrace();
                                        }
                                });

                                Label projectNameLabel = new Label(project.name());
                                projectNameLabel.getStyleClass().add("project-name");

                                Region headerSpacer = new Region();
                                HBox.setHgrow(headerSpacer, Priority.ALWAYS);

                                projectNameLabel.setWrapText(true);
                                projectNameLabel.setMinWidth(0);

                                HBox projectHeader = new HBox(
                                                10,
                                                projectNameLabel,
                                                headerSpacer,
                                                editProjectButton,
                                                deleteProjectButton);

                                projectHeader.setAlignment(
                                                javafx.geometry.Pos.CENTER_LEFT);

                                editProjectButton.setMinWidth(Region.USE_PREF_SIZE);
                                deleteProjectButton.setMinWidth(Region.USE_PREF_SIZE);

                                VBox card = new VBox(
                                                10,
                                                projectHeader,
                                                new Label(
                                                                project.startDate()
                                                                                + " → " + project.dueDate()),
                                                bar,
                                                progressText,
                                                expectedText, projectEditor,
                                                tasksPane);

                                card.getStyleClass().add("project-card");
                                projectCards.getChildren().add(card);
                        }

                } catch (SQLException ex) {
                        status.setText("Unable to load projects.");
                        ex.printStackTrace();
                }
        }
}
