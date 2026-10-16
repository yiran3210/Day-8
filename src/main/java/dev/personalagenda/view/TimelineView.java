package dev.personalagenda.view;

import dev.personalagenda.model.TimeBlock;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
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

        private final ScrollPane scrollPane = new ScrollPane(canvas);
        private final Button nowButton = new Button("Now");

        private boolean autoFollow = true;

        private final ProgressBar currentTimeProgress = new ProgressBar(0);

        private LocalDate displayedDate;
        private List<TimeBlock> displayedBlocks = List.of();
        private Map<String, String> taskColors = new HashMap<>();

        private final Label currentStatus = new Label();
        private final Label nextStatus = new Label();

        private final Line nowLine = new Line();

        public void setTaskColors(Map<String, String> colors) {
                taskColors = new HashMap<>(colors);
        }

        public TimelineView() {

                nowLine.endXProperty().bind(
                                canvas.widthProperty().subtract(10));

                Label heading = new Label("24h Schedule");

                HBox headingRow = new HBox(12, heading, nowButton);

                nowButton.setOnAction(event -> {
                        autoFollow = true;
                        scrollToNow();
                });

                canvas.setPrefSize(320, 24 * HOUR_HEIGHT + 20);
                canvas.setMinWidth(0);

                scrollPane.setFitToWidth(true);
                scrollPane.setHbarPolicy(
                                ScrollPane.ScrollBarPolicy.NEVER);

                VBox.setVgrow(scrollPane, Priority.ALWAYS);

                setSpacing(10);
                setPrefWidth(360);
                setMinWidth(240);

                currentStatus.setWrapText(true);
                nextStatus.setWrapText(true);

                currentTimeProgress.setMaxWidth(Double.MAX_VALUE);
                currentTimeProgress.setPrefHeight(10);
                currentTimeProgress.setVisible(false);
                currentTimeProgress.setManaged(false);

                getChildren().addAll(
                                headingRow,
                                currentStatus,
                                currentTimeProgress,
                                nextStatus,
                                scrollPane);

                // Pause following when the user scrolls manually.
                scrollPane.addEventFilter(
                                ScrollEvent.SCROLL,
                                event -> autoFollow = false);

                // Also detect manual scrollbar dragging.
                scrollPane.addEventFilter(
                                MouseEvent.MOUSE_PRESSED,
                                event -> {
                                        Node node = event.getPickResult().getIntersectedNode();

                                        while (node != null && node != scrollPane) {
                                                if (node.getStyleClass().contains("scroll-bar")) {
                                                        autoFollow = false;
                                                        break;
                                                }
                                                node = node.getParent();
                                        }
                                });

                // Reposition when the viewport obtains its actual size.
                scrollPane.viewportBoundsProperty().addListener(
                                (obs, oldBounds, newBounds) -> {
                                        if (autoFollow) {
                                                Platform.runLater(this::scrollToNow);
                                        }
                                });

                Timeline clock = new Timeline(
                                new KeyFrame(javafx.util.Duration.seconds(30), event -> updateNow()));

                clock.setCycleCount(Timeline.INDEFINITE);
                clock.play();
        }

        public void showDate(LocalDate date, List<TimeBlock> blocks) {

                canvas.getChildren().clear();

                if (!date.equals(displayedDate)) {
                        autoFollow = date.equals(LocalDate.now());
                }

                displayedDate = date;
                displayedBlocks = blocks;

                nowButton.setDisable(!date.equals(LocalDate.now()));

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

                        String categoryColor = block.taskId() == null
                                        ? null
                                        : taskColors.get(block.taskId());

                        if (block.type() == TimeBlock.Type.TASK
                                        && categoryColor != null) {

                                Color background = Color.web(categoryColor);

                                double brightness = background.getRed() * 0.2126
                                                + background.getGreen() * 0.7152
                                                + background.getBlue() * 0.0722;

                                String textColor = brightness > 0.55
                                                ? "#26354A"
                                                : "#FFFFFF";

                                eventLabel.setStyle(
                                                "-fx-background-color: " + categoryColor + ";"
                                                                + "-fx-text-fill: " + textColor + ";");
                        }

                        canvas.getChildren().add(eventLabel);
                }
                updateNow();

                if (autoFollow) {
                        Platform.runLater(this::scrollToNow);
                }
        }

        private void updateNow() {

                if (displayedDate == null) {
                        return;
                }

                LocalDateTime now = LocalDateTime.now();

                canvas.getChildren().remove(nowLine);

                currentTimeProgress.setVisible(false);
                currentTimeProgress.setManaged(false);

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

                        } else if (block.start().isAfter(now)
                                        && (next == null || block.start().isBefore(next.start()))) {
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

                        currentTimeProgress.setProgress(
                                        Math.max(0, Math.min(1, elapsed / total)));

                        currentTimeProgress.setVisible(true);
                        currentTimeProgress.setManaged(true);

                        long remainingMinutes = (long) Math.ceil(
                                        Duration.between(now, current.end()).toSeconds() / 60.0);

                        currentStatus.setText(
                                        "Current: " + current.title()
                                                        + " | " + percent + "% elapsed"
                                                        + " | " + remainingMinutes + " min left");
                }

                if (next == null) {
                        nextStatus.setText("Next: No further activities");
                } else {
                        nextStatus.setText(
                                        "Next: " + next.title() + " | " + next.start().toLocalTime());
                }

                if (autoFollow) {
                        scrollToNow();
                }
        }

        private void scrollToNow() {

                if (displayedDate == null
                                || !displayedDate.equals(LocalDate.now())) {
                        return;
                }

                double viewportHeight = scrollPane.getViewportBounds().getHeight();

                if (viewportHeight <= 0) {
                        return;
                }

                double nowMinutes = java.time.LocalTime.now().toSecondOfDay() / 60.0;

                double nowY = nowMinutes / 60.0 * HOUR_HEIGHT;

                double contentHeight = Math.max(
                                canvas.getHeight(),
                                canvas.getPrefHeight());

                double scrollableHeight = contentHeight - viewportHeight;

                if (scrollableHeight <= 0) {
                        return;
                }

                // Position NOW roughly 45% down the viewport.
                double targetOffset = nowY - viewportHeight * 0.45;

                double position = Math.max(
                                0,
                                Math.min(1, targetOffset / scrollableHeight));

                scrollPane.setVvalue(position);
        }

}