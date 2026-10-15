package dev.personalagenda;

import java.util.ArrayList;
import java.util.List;

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
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import dev.personalagenda.model.Task;
import dev.personalagenda.data.Database;

public class App extends Application {

    @Override
    public void start(Stage stage) throws Exception {

        Database.initialize();
        List<Task> tasks = new ArrayList<>();

        Label title = new Label("Personal Agenda");

        TextField taskInput = new TextField();
        taskInput.setPromptText("Enter a task: ");

        Button addButton = new Button("Add");

        VBox taskList = new VBox(8);

        Spinner<Integer> durationInput = new Spinner<>(15, 480, 60, 15);

        durationInput.setPrefWidth(100);

        Label progressLabel = new Label("0 / 0 completed");

        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(400);

        addButton.setOnAction(event -> {

            String taskTitle = taskInput.getText().trim();

            if (taskTitle.isEmpty()) {
                return;
            }

            int minutes = durationInput.getValue();
            Task newTask = new Task(taskTitle, minutes);
            tasks.add(newTask);

            CheckBox checkBox = new CheckBox(newTask.getTitle());
            Label durationLabel = new Label(minutes + " min");
            Button deleteButton = new Button("x");

            HBox taskRow = new HBox(
                    12, checkBox, durationLabel, deleteButton);

            checkBox.setOnAction(e -> {
                newTask.setCompleted(checkBox.isSelected());
                updateProgress(tasks, progressLabel, progressBar);
            });

            deleteButton.setOnAction(e -> {
                tasks.remove(newTask);
                taskList.getChildren().remove(taskRow);
                updateProgress(tasks, progressLabel, progressBar);
            });

            taskList.getChildren().add(taskRow);
            taskInput.clear();

            updateProgress(tasks, progressLabel, progressBar);
        });

        taskInput.setOnAction(event -> addButton.fire());

        HBox inputRow = new HBox(
                10, taskInput, durationInput, addButton);

        VBox layout = new VBox(
                16, title, inputRow, taskList, progressLabel, progressBar);
        layout.setPadding(new Insets(24));

        Scene scene = new Scene(
                layout, 500, 400);

        stage.setTitle("Personal Agenda");
        stage.setScene(scene);
        stage.show();

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
