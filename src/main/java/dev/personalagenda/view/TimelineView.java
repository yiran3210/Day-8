package dev.personalagenda.view;

import dev.personalagenda.model.TimeBlock;
import dev.personalagenda.model.WorkSession;
import dev.personalagenda.service.FocusSessionService;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;

/**
 * Timeline with separate planned and actual tracks, using real elapsed time on
 * DST days.
 */
public class TimelineView extends VBox {

        private static final double HOUR_HEIGHT = 40.0;
        private static final DateTimeFormatter DST_TICK_FORMAT = DateTimeFormatter.ofPattern("HH:mm z", Locale.ENGLISH);

        private final Pane canvas = new Pane();
        private final ScrollPane scrollPane = new ScrollPane(canvas);
        private final Button nowButton = new Button("Now");
        private final Label heading = new Label("24h Schedule");
        private final Label dstNotice = new Label();
        private final Label legacyDstNotice = new Label();
        private final Label legacyNotice = new Label();
        private final Label liveFocusLabel = new Label();
        private final Label liveFocusStatus = new Label();
        private final Tooltip liveFocusTooltip = new Tooltip();
        private final ProgressBar currentTimeProgress = new ProgressBar(0);
        private final Label currentStatus = new Label();
        private final Label nextStatus = new Label();
        private final Line nowLine = new Line();

        private FocusSessionService focusService;
        private boolean autoFollow = true;
        private LocalDate displayedDate;
        private List<TimeBlock> displayedBlocks = List.of();
        private Map<String, String> taskColors = new HashMap<>();

        private Instant axisStart;
        private Instant axisEnd;
        private ZoneId axisZone;

        public TimelineView() {
                nowLine.endXProperty().bind(canvas.widthProperty().subtract(10));

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                HBox headingRow = new HBox(12, heading, spacer, nowButton);
                headingRow.setAlignment(Pos.CENTER_LEFT);
                nowButton.setMinWidth(Region.USE_PREF_SIZE);
                nowButton.setOnAction(event -> {
                        autoFollow = true;
                        scrollToNow();
                });

                canvas.setPrefSize(320, 24 * HOUR_HEIGHT + 20);
                canvas.setMinWidth(0);
                scrollPane.setFitToWidth(true);
                scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
                VBox.setVgrow(scrollPane, Priority.ALWAYS);

                liveFocusLabel.layoutXProperty().bind(
                                canvas.widthProperty().subtract(72).multiply(0.58).add(64));
                liveFocusLabel.prefWidthProperty().bind(
                                canvas.widthProperty().subtract(72).multiply(0.42).subtract(4));
                liveFocusLabel.setMinHeight(0);
                liveFocusLabel.setWrapText(true);
                liveFocusLabel.setTooltip(liveFocusTooltip);
                liveFocusLabel.setStyle(
                                "-fx-background-color: #357E76;"
                                                + "-fx-background-radius: 4;"
                                                + "-fx-text-fill: white;"
                                                + "-fx-padding: 2;");

                setSpacing(10);
                setPrefWidth(360);
                setMinWidth(240);
                currentStatus.setWrapText(true);
                nextStatus.setWrapText(true);
                currentTimeProgress.setMaxWidth(Double.MAX_VALUE);
                currentTimeProgress.setPrefHeight(10);
                currentTimeProgress.setVisible(false);
                currentTimeProgress.setManaged(false);

                Label trackLegend = new Label("Planned (left)    |    Actual Focus (right)");
                trackLegend.setStyle("-fx-font-size: 11px;");
                dstNotice.setWrapText(true);
                dstNotice.setStyle("-fx-font-size: 11px; -fx-text-fill: #8A5A24;");
                dstNotice.setVisible(false);
                dstNotice.setManaged(false);

                legacyDstNotice.setWrapText(true);
                legacyDstNotice.setVisible(false);
                legacyDstNotice.setManaged(false);

                legacyDstNotice.setStyle(
                                "-fx-font-size: 11px;"
                                                + "-fx-text-fill: #A05220;"
                                                + "-fx-font-weight: bold;");

                legacyNotice.setWrapText(true);
                legacyNotice.setStyle("-fx-font-size: 11px; -fx-text-fill: #893030;");
                legacyNotice.setVisible(false);
                legacyNotice.setManaged(false);
                liveFocusStatus.setWrapText(true);
                liveFocusStatus.setStyle(
                                "-fx-font-size: 12px; -fx-text-fill: #357E76; -fx-font-weight: bold;");
                liveFocusStatus.setVisible(false);
                liveFocusStatus.setManaged(false);

                // Keep both DST warnings in the VBox before the timeline ScrollPane.
                // Never insert at indexOf(scrollPane) before it has been added.
                getChildren().addAll(headingRow, currentStatus, currentTimeProgress,
                                nextStatus, trackLegend, dstNotice, legacyDstNotice, legacyNotice,
                                liveFocusStatus, scrollPane);

                // Manual interaction turns off auto-follow until the user presses Now.
                scrollPane.addEventFilter(ScrollEvent.SCROLL, event -> autoFollow = false);
                scrollPane.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                        Node node = event.getPickResult().getIntersectedNode();
                        while (node != null && node != scrollPane) {
                                if (node.getStyleClass().contains("scroll-bar")) {
                                        autoFollow = false;
                                        break;
                                }
                                node = node.getParent();
                        }
                });
                scrollPane.viewportBoundsProperty().addListener((obs, oldBounds, newBounds) -> {
                        if (autoFollow) {
                                Platform.runLater(this::scrollToNow);
                        }
                });

                Timeline clock = new Timeline(new KeyFrame(
                                javafx.util.Duration.seconds(30), event -> updateNow()));
                clock.setCycleCount(Timeline.INDEFINITE);
                clock.play();

                Timeline liveTicker = new Timeline(new KeyFrame(
                                javafx.util.Duration.seconds(1), event -> updateLiveFocus()));
                liveTicker.setCycleCount(Timeline.INDEFINITE);
                liveTicker.play();
        }

        public void setTaskColors(Map<String, String> colors) {
                taskColors = new HashMap<>(Objects.requireNonNull(colors));
        }

        public void setFocusSessionService(FocusSessionService service) {
                focusService = Objects.requireNonNull(service);
                updateLiveFocus();
        }

        private void configureTimeAxis(LocalDate date) {
                axisZone = ZoneId.systemDefault();
                axisStart = date.atStartOfDay(axisZone).toInstant();
                axisEnd = date.plusDays(1).atStartOfDay(axisZone).toInstant();
                long seconds = Duration.between(axisStart, axisEnd).getSeconds();
                double hours = seconds / 3600.0;
                canvas.setPrefHeight(hours * HOUR_HEIGHT + 20);
                heading.setText(seconds == 86400 ? "24h Schedule"
                                : ((int) hours) + "h Schedule");
                dstNotice.setText(seconds == 90000
                                ? "DST: 25-hour day · repeated hour shown twice"
                                : seconds == 82800
                                                ? "DST: 23-hour day · skipped hour omitted"
                                                : "Time-zone transition: " + hours + "-hour day");
                dstNotice.setVisible(seconds != 86400);
                dstNotice.setManaged(seconds != 86400);
        }

        private double yForInstant(Instant instant) {
                return Duration.between(axisStart, instant).toMillis()
                                / 3_600_000.0 * HOUR_HEIGHT;
        }

        /**
         * No guessing whether an ambiguous wall-clock time is the first or second
         * occurrence.
         */
        private Instant strictLocalInstant(LocalDateTime time) {
                List<ZoneOffset> offsets = axisZone.getRules().getValidOffsets(time);
                return offsets.size() == 1 ? time.toInstant(offsets.get(0)) : null;
        }

        public void showDate(LocalDate date, List<TimeBlock> blocks) {
                showDate(date, blocks, List.of());
        }

        public void showDate(LocalDate date, List<TimeBlock> blocks,
                        List<WorkSession> actualSessions) {
                Objects.requireNonNull(date);
                Objects.requireNonNull(blocks);
                Objects.requireNonNull(actualSessions);

                canvas.getChildren().clear();
                if (!date.equals(displayedDate)) {
                        autoFollow = date.equals(LocalDate.now());
                }
                displayedDate = date;
                configureTimeAxis(date);

                List<TimeBlock> unresolvedBlocks = blocks.stream()
                                .filter(block -> ScheduleTimeValidation.check(
                                                block.start(),
                                                block.end(),
                                                axisZone) != null)
                                .toList();

                boolean hasUnresolved = !unresolvedBlocks.isEmpty();

                legacyDstNotice.setVisible(hasUnresolved);
                legacyDstNotice.setManaged(hasUnresolved);

                if (hasUnresolved) {
                        legacyDstNotice.setText(
                                        "⚠ " + unresolvedBlocks.size()
                                                        + " Activity(s) have ambiguous DST times. "
                                                        + "Review them in Planned Activities.");

                        String details = String.join("\n\n",
                                        unresolvedBlocks.stream()
                                                        .map(block -> block.title() + "\n"
                                                                        + block.start() + " → " + block.end()
                                                                        + "\n"
                                                                        + ScheduleTimeValidation.check(
                                                                                        block.start(),
                                                                                        block.end(),
                                                                                        axisZone))
                                                        .toList());

                        legacyDstNotice.setTooltip(
                                        new Tooltip(details));

                } else {
                        legacyDstNotice.setText("");
                        legacyDstNotice.setTooltip(null);
                }

                displayedBlocks = List.copyOf(blocks);
                nowButton.setDisable(!date.equals(LocalDate.now()));

                long daySeconds = Duration.between(axisStart, axisEnd).getSeconds();
                int hours = (int) (daySeconds / 3600);
                boolean dstDay = daySeconds != 86400;
                for (int hour = 0; hour <= hours; hour++) {
                        double y = hour * HOUR_HEIGHT;
                        Instant tick = axisStart.plusSeconds(hour * 3600L);
                        String text = tick.equals(axisEnd) ? "00:00 (+1d)"
                                        : dstDay ? tick.atZone(axisZone).format(DST_TICK_FORMAT)
                                                        : String.format("%02d:00", hour);
                        Label timeLabel = new Label(text);
                        timeLabel.setLayoutX(4);
                        timeLabel.setLayoutY(y);
                        Line line = new Line(56, y, 280, y);
                        line.endXProperty().bind(canvas.widthProperty().subtract(10));
                        line.setStroke(Color.LIGHTGRAY);
                        canvas.getChildren().addAll(timeLabel, line);
                }

                LocalDateTime dayStart = date.atStartOfDay();
                LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();

                int skippedLegacy = 0;
                List<PlannedLaneLayout.Item> plannedItems = new ArrayList<>();

                for (TimeBlock block : blocks) {

                        // Preserve the existing strict DST validation.
                        if (ScheduleTimeValidation.check(
                                        block.start(), block.end(), axisZone) != null) {
                                skippedLegacy++;
                                continue;
                        }

                        LocalDateTime start = block.start().isBefore(dayStart)
                                        ? dayStart
                                        : block.start();

                        LocalDateTime end = block.end().isAfter(dayEnd)
                                        ? dayEnd
                                        : block.end();

                        if (!end.isAfter(start)) {
                                continue;
                        }

                        Instant from = strictLocalInstant(start);
                        Instant to = strictLocalInstant(end);

                        if (from == null || to == null || !to.isAfter(from)) {
                                skippedLegacy++;
                                continue;
                        }

                        plannedItems.add(
                                        new PlannedLaneLayout.Item(block, from, to));
                }

                // Render each planned block in its assigned lane.
                for (PlannedLaneLayout.Placement placement : PlannedLaneLayout.assign(plannedItems)) {

                        TimeBlock block = placement.item().block();

                        double y = yForInstant(placement.item().from());
                        double height = yForInstant(placement.item().to()) - y;

                        boolean overlaps = placement.laneCount() > 1;

                        Label eventLabel = new Label(
                                        (overlaps ? "⚠ " : "") + block.title()
                                                        + (overlaps ? ""
                                                                        : "\n" + block.start().toLocalTime()
                                                                                        + " – "
                                                                                        + block.end().toLocalTime()));
                        if (block.type() == TimeBlock.Type.TASK
                                        && block.taskId() == null) {

                                eventLabel.setText(
                                                "⚠ Unlinked\n" + eventLabel.getText());
                        }

                        eventLabel.setLayoutY(y);
                        eventLabel.setPrefHeight(height);

                        DoubleBinding slotWidth = canvas.widthProperty()
                                        .subtract(72)
                                        .multiply(0.58)
                                        .subtract(6)
                                        .divide(placement.laneCount());

                        eventLabel.layoutXProperty().bind(
                                        slotWidth.multiply(placement.lane()).add(64));

                        eventLabel.prefWidthProperty().bind(
                                        Bindings.createDoubleBinding(
                                                        () -> Math.max(1.0, slotWidth.get() - 3.0),
                                                        slotWidth));

                        eventLabel.setMinWidth(0);
                        eventLabel.setMinHeight(0);

                        eventLabel.setWrapText(!overlaps);
                        eventLabel.setTextOverrun(OverrunStyle.ELLIPSIS);

                        String typeStyle = switch (block.type()) {
                                case TASK -> "time-block-task";
                                case FIXED -> "time-block-fixed";
                                case REST -> "time-block-rest";
                                case FREE -> "time-block-free";
                        };
                        eventLabel.getStyleClass().addAll("time-block", typeStyle);
                        String categoryColor = block.taskId() == null
                                        ? null
                                        : taskColors.get(block.taskId());
                        if (block.type() == TimeBlock.Type.TASK && categoryColor != null) {
                                Color background = Color.web(categoryColor);
                                double brightness = background.getRed() * 0.2126
                                                + background.getGreen() * 0.7152
                                                + background.getBlue() * 0.0722;
                                String textColor = brightness > 0.55 ? "#26354A" : "#FFFFFF";
                                eventLabel.setStyle("-fx-background-color: " + categoryColor
                                                + ";-fx-text-fill: " + textColor + ";");
                        }

                        if (overlaps) {
                                eventLabel.setStyle(
                                                eventLabel.getStyle() + "-fx-padding: 2px 3px;");
                        }

                        eventLabel.setTooltip(new Tooltip(
                                        block.title() + "\n"
                                                        + block.start() + " → " + block.end()
                                                        + (overlaps
                                                                        ? "\n⚠ Overlapping planned activities · lane "
                                                                                        + (placement.lane() + 1)
                                                                                        + "/" + placement.laneCount()
                                                                        : "")));

                        canvas.getChildren().add(eventLabel);
                }
                legacyNotice.setText(skippedLegacy == 0 ? ""
                                : "⚠ " + skippedLegacy + " planned activity(s) hidden: "
                                                + "ambiguous or invalid local time. Check/edit these activities in Day Plan.");
                legacyNotice.setVisible(skippedLegacy > 0);
                legacyNotice.setManaged(skippedLegacy > 0);

                for (WorkSession session : actualSessions) {
                        if (session.endedAt() == null) {
                                continue;
                        }
                        Instant from = session.startedAt().isBefore(axisStart)
                                        ? axisStart
                                        : session.startedAt();
                        Instant to = session.endedAt().isAfter(axisEnd)
                                        ? axisEnd
                                        : session.endedAt();
                        if (!to.isAfter(from)) {
                                continue;
                        }
                        double y = yForInstant(from);
                        double height = yForInstant(to) - y;
                        String title = focusTitle(session);
                        Label actualLabel = new Label(title);
                        actualLabel.setLayoutY(y);
                        actualLabel.setPrefHeight(Math.max(6, height));
                        actualLabel.setMinHeight(0);
                        actualLabel.layoutXProperty().bind(
                                        canvas.widthProperty().subtract(72).multiply(0.58).add(64));
                        actualLabel.prefWidthProperty().bind(
                                        canvas.widthProperty().subtract(72).multiply(0.42).subtract(4));
                        actualLabel.setWrapText(true);
                        actualLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
                        actualLabel.setStyle("-fx-background-color: #26364A;"
                                        + "-fx-background-radius: 4;"
                                        + "-fx-text-fill: white;"
                                        + "-fx-padding: 2;");
                        actualLabel.setTooltip(new Tooltip(title + "\n"
                                        + session.startedAt().atZone(axisZone) + " → "
                                        + session.endedAt().atZone(axisZone)
                                        + "\nActual Focus · Pause excluded"));
                        canvas.getChildren().add(actualLabel);
                }

                updateLiveFocus();
                updateNow();
                if (autoFollow) {
                        Platform.runLater(this::scrollToNow);
                }
        }

        private static String focusTitle(WorkSession session) {
                String title = session.taskTitle();
                if (title == null || title.isBlank()) {
                        title = "Unlinked Focus";
                }
                if (session.stepTitle() != null && !session.stepTitle().isBlank()) {
                        title += " · " + session.stepTitle();
                }
                return title;
        }

        private void hideLiveFocus() {
                canvas.getChildren().remove(liveFocusLabel);
                liveFocusStatus.setVisible(false);
                liveFocusStatus.setManaged(false);
        }

        private void updateLiveFocus() {
                WorkSession session = focusService == null
                                ? null
                                : focusService.activeSession();
                if (session == null || displayedDate == null || axisStart == null) {
                        hideLiveFocus();
                        return;
                }
                Instant instantNow = Instant.now();
                Instant from = session.startedAt().isBefore(axisStart)
                                ? axisStart
                                : session.startedAt();
                Instant to = instantNow.isAfter(axisEnd) ? axisEnd : instantNow;
                if (!to.isAfter(from)) {
                        hideLiveFocus();
                        return;
                }
                double y = yForInstant(from);
                double height = yForInstant(to) - y;
                String title = focusTitle(session);
                liveFocusLabel.setText("● REC · " + title);

                long elapsed = Math.max(0, Duration.between(
                                session.startedAt(), instantNow).getSeconds());
                String elapsedText = String.format("%02d:%02d:%02d",
                                elapsed / 3600, (elapsed % 3600) / 60, elapsed % 60);
                liveFocusStatus.setText("● REC · " + title + " · " + elapsedText);
                liveFocusStatus.setVisible(true);
                liveFocusStatus.setManaged(true);
                liveFocusLabel.setLayoutY(y);
                liveFocusLabel.setPrefHeight(Math.max(6, height));
                liveFocusTooltip.setText(title + "\nStarted: "
                                + session.startedAt().atZone(axisZone)
                                + "\nActual time through: " + instantNow.atZone(axisZone));
                if (!canvas.getChildren().contains(liveFocusLabel)) {
                        int nowIndex = canvas.getChildren().indexOf(nowLine);
                        if (nowIndex >= 0) {
                                canvas.getChildren().add(nowIndex, liveFocusLabel);
                        } else {
                                canvas.getChildren().add(liveFocusLabel);
                        }
                }
        }

        private void updateNow() {
                if (displayedDate == null || axisStart == null) {
                        return;
                }
                Instant nowInstant = Instant.now();
                LocalDateTime now = LocalDateTime.ofInstant(nowInstant, axisZone);
                canvas.getChildren().remove(nowLine);
                currentTimeProgress.setVisible(false);
                currentTimeProgress.setManaged(false);
                if (!now.toLocalDate().equals(displayedDate)) {
                        currentStatus.setText("Viewing: " + displayedDate);
                        nextStatus.setText("");
                        return;
                }
                double y = yForInstant(nowInstant);
                nowLine.setStartX(56);
                nowLine.setStartY(y);
                nowLine.setEndY(y);
                nowLine.setStroke(Color.web("#893030"));
                nowLine.setStrokeWidth(2);
                canvas.getChildren().add(nowLine);

                TimeBlock current = null;
                TimeBlock next = null;
                for (TimeBlock block : displayedBlocks) {

                        if (ScheduleTimeValidation.check(
                                        block.start(),
                                        block.end(),
                                        axisZone) != null) {
                                continue;
                        }

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
                        double elapsed = Duration.between(current.start(), now).toSeconds();
                        double total = Duration.between(current.start(), current.end()).toSeconds();
                        if (total > 0) {
                                int percent = (int) Math.round(100.0 * elapsed / total);
                                currentTimeProgress.setProgress(Math.max(0, Math.min(1, elapsed / total)));
                                currentTimeProgress.setVisible(true);
                                currentTimeProgress.setManaged(true);
                                long remainingMinutes = (long) Math.ceil(
                                                Duration.between(now, current.end()).toSeconds() / 60.0);
                                currentStatus.setText("Current: " + current.title()
                                                + " | " + percent + "% elapsed"
                                                + " | " + remainingMinutes + " min left");
                        }
                }
                nextStatus.setText(next == null ? "Next: No further activities"
                                : "Next: " + next.title() + " | " + next.start().toLocalTime());
                if (autoFollow) {
                        scrollToNow();
                }
        }

        private void scrollToNow() {
                if (displayedDate == null || axisStart == null
                                || !displayedDate.equals(LocalDate.now())) {
                        return;
                }
                double viewportHeight = scrollPane.getViewportBounds().getHeight();
                if (viewportHeight <= 0) {
                        return;
                }
                double nowY = yForInstant(Instant.now());
                double contentHeight = Math.max(canvas.getHeight(), canvas.getPrefHeight());
                double scrollableHeight = contentHeight - viewportHeight;
                if (scrollableHeight <= 0) {
                        return;
                }
                double targetOffset = nowY - viewportHeight * 0.45;
                scrollPane.setVvalue(Math.max(0, Math.min(1, targetOffset / scrollableHeight)));
        }
}
