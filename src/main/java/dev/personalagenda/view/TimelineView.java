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
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;

public class TimelineView extends VBox {

    private static final double HOUR_HEIGHT = 40;
    private final Pane canvas = new Pane();

    private LocalDate displayedDate;
    private List<TimeBlock> displayedBlocks = List.of();

    private final Label currentStatus = new Label();
    private final Label nextStatus = new Label();

    private final Line nowLine = new Line();

    public TimelineView() {

        nowLine.endXProperty().bind(
                canvas.widthProperty().subtract(10));

        Label heading = new Label("24h Schedule");

        canvas.setPrefSize(320, 24 * HOUR_HEIGHT + 20);
        canvas.setMinWidth(0);

        ScrollPane scrollPane = new ScrollPane(canvas);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(
                ScrollPane.ScrollBarPolicy.NEVER);

        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        setSpacing(10);
        setPrefWidth(360);
        setMinWidth(240);

        currentStatus.setWrapText(true);
        nextStatus.setWrapText(true);

        getChildren().addAll(heading, currentStatus, nextStatus, scrollPane);

        Timeline clock = new Timeline(
                new KeyFrame(javafx.util.Duration.seconds(30), event -> updateNow()));

        clock.setCycleCount(Timeline.INDEFINITE);
        clock.play();
    }

    public void showDate(LocalDate date, List<TimeBlock> blocks) {

        canvas.getChildren().clear();

        displayedDate = date;
        displayedBlocks = blocks;

        for (int hour = 0; hour <= 24; hour++) {

            double y = hour * HOUR_HEIGHT;

            Label timeLabel = new Label(
                    String.format("%02d:00", hour));

            timeLabel.setLayoutX(4);
            timeLabel.setLayoutY(y);

            Line line = new Line(56, y, 280, y);
            line.endXProperty().bind(
                    canvas.widthProperty().subtract(10));

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
            eventLabel.setPrefHeight(height);

            eventLabel.prefWidthProperty().bind(
                    canvas.widthProperty().subtract(72));
            eventLabel.setMinHeight(0);
            eventLabel.setWrapText(true);

            String typeStyle = switch (block.type()) {
                case TASK -> "time-block-task";
                case FIXED -> "time-block-fixed";
                case REST -> "time-block-rest";
                case FREE -> "time-block-free";
            };

            eventLabel.getStyleClass().addAll(
                    "time-block",
                    typeStyle);

            canvas.getChildren().add(eventLabel);
        }
        updateNow();
    }

    private void updateNow() {

        if (displayedDate == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        canvas.getChildren().remove(nowLine);

        if (!now.toLocalDate().equals(displayedDate)) {
            currentStatus.setText("Viewing: " + displayedDate);
            nextStatus.setText("");
            return;
        }

        double minutes = now.getHour() * 60
                + now.getMinute()
                + now.getSecond() / 60.0;

        double y = minutes / 60.0 * HOUR_HEIGHT;

        nowLine.setStartX(56);
        nowLine.setStartY(y);
        nowLine.setEndY(y);
        nowLine.setStroke(Color.web("#893030"));
        nowLine.setStrokeWidth(2);

        canvas.getChildren().add(nowLine);

        TimeBlock current = null;
        TimeBlock next = null;

        for (TimeBlock block : displayedBlocks) {

            if (!now.isBefore(block.start()) && now.isBefore(block.end())) {

                current = block;

            } else if (block.start().isAfter(now) && (next == null || block.start().isBefore(next.start()))) {
                next = block;
            }
        }
        if (current == null) {
            currentStatus.setText("Current: Unscheduled");
        } else {

            double elapsed = Duration.between(
                    current.start(), now).toSeconds();

            double total = Duration.between(
                    current.start(), current.end()).toSeconds();
            int percent = (int) Math.round(
                    100.0 * elapsed / total);

            currentStatus.setText(
                    "Current: " + current.title()
                            + " | " + percent + "% elapsed");
        }

        if (next == null) {
            nextStatus.setText("Next: No further activities");
        } else {
            nextStatus.setText(
                    "Next: " + next.title() + " | " + next.start().toLocalTime());
        }
    }
}