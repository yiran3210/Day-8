package dev.personalagenda.view;

import dev.personalagenda.model.TimeBlock;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import javafx.animation.Timeline;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Line;
import javafx.scene.paint.Color;

public class TimelineView extends VBox {

    private static final double HOUR_HEIGHT = 40;
    private final Pane canvas = new Pane();

    public TimelineView() {

        Label heading = new Label("24h Schedule");

        canvas.setPrefSize(320, 24 * HOUR_HEIGHT + 20);

        ScrollPane scrollPane = new ScrollPane(canvas);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(
                ScrollPane.ScrollBarPolicy.NEVER);

        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        setSpacing(10);
        setPrefWidth(320);
        setMinWidth(320);

        getChildren().addAll(heading, scrollPane);
    }

    public void showDate(LocalDate date, List<TimeBlock> blocks) {

        canvas.getChildren().clear();

        for (int hour = 0; hour <= 24; hour++) {

            double y = hour * HOUR_HEIGHT;

            Label timeLabel = new Label(
                    String.format("%02d:00", hour));

            timeLabel.setLayoutX(4);
            timeLabel.setLayoutY(y);

            Line line = new Line(56, y, 280, y);
            line.setStroke(Color.LIGHTGRAY);

            canvas.getChildren().addAll(timeLabel, line);
        }

        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();

        for (TimeBlock block : blocks) {

            LocalDateTime start = block.start().isBefore(dayStart)
                    ? dayStart
                    : block.start();

            LocalDateTime end = block.end().isAfter(dayEnd)
                    ? dayEnd
                    : block.end();

            if (!end.isAfter(start)) {
                continue;
            }

            double startMinutes = Duration.between(dayStart, start).toMinutes();

            double durationMinutes = Duration.between(start, end).toMinutes();

            double y = startMinutes / 60 * HOUR_HEIGHT;
            double height = durationMinutes / 60 * HOUR_HEIGHT;
            Label eventLabel = new Label(
                    block.title() + "\n" +
                            block.start().toLocalTime()
                            + " – " + block.end().toLocalTime());

            eventLabel.setLayoutX(64);
            eventLabel.setLayoutY(y);
            eventLabel.setPrefSize(240, height);
            eventLabel.setMinHeight(0);
            eventLabel.setWrapText(true);

            String color = switch (block.type()) {
                case TASK -> "#f0c6d3";
                case FIXED -> "#cdf5cb";
                case REST -> "#8d89db";
                case FREE -> "#f1f2ca";
            };

            eventLabel.setStyle(
                    "-fx-background-color: " + color + ";" +
                            "-fx-background-radius: 5;" +
                            "-fx-padding: 3 6;" +
                            "-fx-font-size: 12px;");

            canvas.getChildren().add(eventLabel);
        }

    }
}