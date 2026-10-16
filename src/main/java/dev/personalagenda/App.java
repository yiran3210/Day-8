package dev.personalagenda;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.sql.SQLException;
import java.time.LocalDate;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.control.TextField;
import javafx.scene.control.Button;
import javafx.scene.control.Spinner;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.DatePicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import dev.personalagenda.model.Task;
import dev.personalagenda.model.TimeBlock;
import dev.personalagenda.data.Database;
import dev.personalagenda.data.TaskRepository;
import dev.personalagenda.data.TimeBlockReposity;
import dev.personalagenda.view.TimelineView;

public class App extends Application {

    @Override
    public void start(Stage stage) throws Exception {

        Database.initialize();
        TaskRepository repository = new TaskRepository();

        System.out.println(
                "Saved tasks to database: " + repository.findAll().size());

        List<Task> tasks = repository.findAll();

        Label title = new Label("Personal Agenda");

        TextField taskInput = new TextField();
        taskInput.setPromptText("Enter a task: ");

        Button addButton = new Button("Add");

        VBox taskList = new VBox(8);

        ComboBox<String> viewFilter = new ComboBox<>();
        viewFilter.getItems().addAll("Today", "Tomorrow", "Replan", "Backlog", "Earlier", "All");
        viewFilter.setValue("Today");

        Spinner<Integer> durationInput = new Spinner<>(15, 480, 60, 15);
        durationInput.setPrefWidth(100);

        DatePicker plannedDateInput = new DatePicker(LocalDate.now());
        plannedDateInput.setPrefWidth(150);

        Button backlogButton = new Button("Backlog");
        backlogButton.setOnAction(event -> {
            plannedDateInput.setValue(null);
        });

        Label progressLabel = new Label("0 / 0 completed");

        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(400);

        refreshTaskList(tasks, taskList, progressLabel, progressBar, repository, viewFilter.getValue());

        viewFilter.setOnAction(event -> {
            refreshTaskList(tasks, taskList, progressLabel, progressBar, repository, viewFilter.getValue());
        });

        addButton.setOnAction(event -> {

            String taskTitle = taskInput.getText().trim();

            if (taskTitle.isEmpty()) {
                return;
            }

            int minutes = durationInput.getValue();
            Task newTask = new Task(taskTitle, minutes);
            newTask.setPlannedDate(plannedDateInput.getValue());

            try {
                repository.insert(newTask);
            } catch (SQLException e) {
                e.printStackTrace();
                return;
            }

            tasks.add(newTask);

            if (matchesFilter(newTask, viewFilter.getValue())) {
                HBox taskRow = createTaskRow(
                        newTask, tasks, taskList, progressLabel, progressBar, repository, viewFilter.getValue());

                taskList.getChildren().add(taskRow);
            } else {
                viewFilter.setValue("All");
            }

            taskInput.clear();

            updateProgress(tasks, progressLabel, progressBar, viewFilter.getValue());
        });

        taskInput.setOnAction(event -> addButton.fire());

        TimelineView timeline = new TimelineView();

        TimeBlockReposity blockReposity = new TimeBlockReposity();

        // begin test timeline

        LocalDate today = LocalDate.now();

        TimeBlock demoBlock = new TimeBlock(
                "Journal Paper Writing",
                today.atTime(12, 0),
                today.atTime(14, 0),
                TimeBlock.Type.TASK,
                null);

        timeline.showDate(today, List.of(demoBlock));

        // timeline.showDate(LocalDate.now(),
        // blockReposity.findForDate(LocalDate.now()));

        HBox inputRow = new HBox(
                10, taskInput, durationInput, addButton);

        HBox dateRow = new HBox(
                10, new Label("Planned date:"), plannedDateInput, backlogButton);

        VBox mainColumn = new VBox(16, title, inputRow, dateRow, new Label("View: "), viewFilter, taskList,
                progressLabel, progressBar);

        ScrollPane checklistPane = new ScrollPane(mainColumn);
        checklistPane.setFitToWidth(true);

        HBox layout = new HBox(
                16, checklistPane, timeline);

        layout.setPadding(new Insets(20));

        Scene scene = new Scene(layout, 880, 640);

        stage.setTitle("Personal Agenda");
        stage.setScene(scene);
        stage.show();

    }

    private boolean matchesFilter(Task task, String filter) {

        LocalDate date = task.getPlannedDate();
        LocalDate today = LocalDate.now();

        return switch (filter) {
            case "Today" -> today.equals(date);
            case "Tomorrow" -> today.plusDays(1).equals(date);
            case "Backlog" -> date == null;
            case "Replan" -> date != null && date.isBefore(today) && !task.isCompleted();
            case "Earlier" -> date != null && date.isBefore(today);
            case "All" -> true;
            default -> false;
        };
    }

    private void refreshTaskList(
            List<Task> tasks, VBox taskList, Label progressLabel, ProgressBar progressBar, TaskRepository repository,
            String filter) {

        taskList.getChildren().clear();

        for (Task task : tasks) {
            if (!matchesFilter(task, filter)) {
                continue;
            }

            HBox row = createTaskRow(task, tasks, taskList, progressLabel, progressBar, repository, filter);

            taskList.getChildren().add(row);
        }

        updateProgress(tasks, progressLabel, progressBar, filter);
    }

    private HBox createTaskRow(
            Task task,
            List<Task> tasks,
            VBox taskList,
            Label progressLabel,
            ProgressBar progressBar,
            TaskRepository repository, String filter) {

        CheckBox checkBox = new CheckBox(task.getTitle());
        checkBox.setSelected(task.isCompleted());

        Label durationLabel = new Label(task.getEstimatedMinutes() + " min");

        // Label dateLabel = new Label(
        // task.getPlannedDate() == null ? "Backlog" :
        // task.getPlannedDate().toString());

        DatePicker taskDateInput = new DatePicker(task.getPlannedDate());
        taskDateInput.setPrefWidth(150);

        Button clearDateButton = new Button("Backlog");

        Button deleteButton = new Button("x");

        HBox taskRow = new HBox(
                12, checkBox, durationLabel, taskDateInput, clearDateButton, deleteButton);

        taskDateInput.valueProperty().addListener((observable, oldDate, newDate) -> {

            if (Objects.equals(task.getPlannedDate(), newDate)) {
                return;
            }

            try {
                repository.updatePlannedDate(task.getId(), newDate);

                task.setPlannedDate(newDate);

                Platform.runLater(() -> refreshTaskList(
                        tasks, taskList, progressLabel, progressBar, repository, filter));
            } catch (SQLException ex) {
                ex.printStackTrace();

                taskDateInput.setValue(task.getPlannedDate());
            }
        });

        clearDateButton.setOnAction(e -> taskDateInput.setValue(null));

        checkBox.setOnAction(e -> {

            boolean completed = checkBox.isSelected();

            try {
                repository.updateCompleted(task.getId(), completed);

                task.setCompleted(completed);
                if ("Replan".equals(filter)) {
                    Platform.runLater(
                            () -> refreshTaskList(tasks, taskList, progressLabel, progressBar, repository, filter));
                } else {
                    updateProgress(tasks, progressLabel, progressBar, filter);
                }
            } catch (SQLException ex) {
                checkBox.setSelected(task.isCompleted());
                ex.printStackTrace();
            }
        });

        deleteButton.setOnAction(e -> {

            try {
                repository.delete(task.getId());

                tasks.remove(task);
                taskList.getChildren().remove(taskRow);

                updateProgress(tasks, progressLabel, progressBar, filter);
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        });

        return taskRow;
    }

    private void updateProgress(List<Task> tasks, Label progressLabel, ProgressBar progressBar, String filter) {

        int completed = 0;
        int total = 0;
        int plannedMinutes = 0;
        int remainingMinutes = 0;

        for (Task task : tasks) {

            if (!matchesFilter(task, filter)) {
                continue;
            }

            total++;
            plannedMinutes += task.getEstimatedMinutes();

            if (task.isCompleted()) {
                completed++;
            } else {
                remainingMinutes += task.getEstimatedMinutes();
            }
        }

        double progress = total == 0
                ? 0.0
                : (double) completed / total;

        progressLabel.setText(
                filter + ": " + completed + "/" + total + " completed | Planned: " + plannedMinutes
                        + " min | Remaining: "
                        + remainingMinutes + " min");

        progressBar.setProgress(progress);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
