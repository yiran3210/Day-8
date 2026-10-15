package dev.personalagenda;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import dev.personalagenda.model.Task;

public class App extends Application {

    @Override
    public void start(Stage stage) {
        Label title = new Label("Personal Agenda");
        Task taskModel = new Task("Write Paper Section 2", 90);

        CheckBox task = new CheckBox(taskModel.getTitle());

        task.setOnAction(event -> {
            taskModel.setCompleted(task.isSelected());
            System.out.println(
                    taskModel.getTitle() + ": " + taskModel.isCompleted());
        });

        VBox layout = new VBox(16, title, task);
        layout.setPadding(new Insets(24));

        Scene scene = new Scene(layout, 500, 320);

        stage.setTitle("Personal Agenda");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
