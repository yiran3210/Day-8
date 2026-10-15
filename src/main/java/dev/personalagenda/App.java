package dev.personalagenda;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage stage) {
        Label title = new Label("Personal Agenda");
        CheckBox task = new CheckBox("Finish today's first task");

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
