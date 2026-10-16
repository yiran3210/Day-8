package dev.personalagenda;

import java.util.ArrayList;
import java.util.List;
import java.sql.SQLException;
import java.time.LocalDate;

import javafx.application.Application;
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
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import dev.personalagenda.model.Task;
import dev.personalagenda.data.Database;
import dev.personalagenda.data.TaskRepository;

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

        for (Task savedTask : tasks) {

            HBox taskRow = createTaskRow(savedTask, tasks, taskList, progressLabel, progressBar, repository);

            taskList.getChildren().add(taskRow);
        }

        updateProgress(tasks, progressLabel, progressBar);

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

            HBox taskRow = createTaskRow(
                    newTask, tasks, taskList, progressLabel, progressBar, repository);

            taskList.getChildren().add(taskRow);

            taskInput.clear();

            updateProgress(tasks, progressLabel, progressBar);
        });

        taskInput.setOnAction(event -> addButton.fire());

        HBox inputRow = new HBox(
                10, taskInput, durationInput, addButton);

        HBox dateRow = new HBox(
                10, new Label("Planned date:"), plannedDateInput, backlogButton);

        VBox layout = new VBox(
                16, title, inputRow, dateRow, taskList, progressLabel, progressBar);
        layout.setPadding(new Insets(24));

        Scene scene = new Scene(
                layout, 500, 400);

        stage.setTitle("Personal Agenda");
        stage.setScene(scene);
        stage.show();

    }

    private HBox createTaskRow(
            Task task,
            List<Task> tasks,
            VBox taskList,
            Label progressLabel,
            ProgressBar progressBar,
            TaskRepository repository) {

        CheckBox checkBox = new CheckBox(task.getTitle());
        checkBox.setSelected(task.isCompleted());

        Label durationLabel = new Label(task.getEstimatedMinutes() + " min");

        Label dateLabel = new Label(
                task.getPlannedDate() == null ? "Backlog" : task.getPlannedDate().toString());

        Button deleteButton = new Button("x");

        HBox taskRow = new HBox(
                12, checkBox, durationLabel, dateLabel, deleteButton);

        checkBox.setOnAction(e -> {

            boolean completed = checkBox.isSelected();

            try {
                repository.updateCompleted(task.getId(), completed);

                task.setCompleted(completed);
                updateProgress(tasks, progressLabel, progressBar);
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

                updateProgress(tasks, progressLabel, progressBar);
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        });

        return taskRow;
    }

    private void updateProgress(List<Task> tasks, Label progressLabel, ProgressBar progressBar) {

        int completed = 0;

        for (Task task : tasks) {
            if (task.isCompleted()) {
                completed++;
            }
        }

        int total = tasks.size();

        double progress = total == 0
                ? 0.0
                : (double) completed / total;

        progressLabel.setText(
                completed + " / " + total + " completed");

        progressBar.setProgress(progress);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
