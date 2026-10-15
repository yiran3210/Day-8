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
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import dev.personalagenda.model.Task;

public class App extends Application {

    @Override
    public void start(Stage stage) {

        List<Task> tasks = new ArrayList<>();

        Label title = new Label("Personal Agenda");

        TextField taskInput = new TextField();
        taskInput.setPromptText("Enter a task: ");

        Button addButton = new Button("Add");

        VBox taskList = new VBox(8);

        addButton.setOnAction(event -> {

            String taskTitle = taskInput.getText().trim();

            if (taskTitle.isEmpty()) {
                return;
            }

            Task newTask = new Task(taskTitle, 60);
            tasks.add(newTask);

            CheckBox checkBox = new CheckBox(newTask.getTitle());

            checkBox.setOnAction(e -> {
                newTask.setCompleted(checkBox.isSelected());

                System.out.println(
                        newTask.getTitle() + ": " + newTask.isCompleted());
            });

            taskList.getChildren().add(checkBox);
            taskInput.clear();

            System.out.println(
                    "Total tasks: " + tasks.size());
        });

        taskInput.setOnAction(event -> addButton.fire());

        HBox inputRow = new HBox(
                10, taskInput, addButton);

        VBox layout = new VBox(
                16, title, inputRow, taskList);
        layout.setPadding(new Insets(24));

        Scene scene = new Scene(
                layout, 500, 400);

        stage.setTitle("Personal Agenda");
        stage.setScene(scene);
        stage.show();

    }

    public static void main(String[] args) {
        launch(args);
    }
}
