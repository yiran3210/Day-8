package dev.personalagenda.view;

import dev.personalagenda.data.WorkSessionRepository;
import dev.personalagenda.model.WorkSession;

import java.sql.SQLException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.StringConverter;

public class InsightsView extends VBox {

    private enum Period {
        DAY("Day"), WEEK("Week"),
        MONTH("Month"), YEAR("Year");

        private final String title;

        Period(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    private final WorkSessionRepository repository;

    private final ComboBox<Period> periodChoice = new ComboBox<>();
    private final DatePicker anchorDate = new DatePicker(LocalDate.now());

    private final Label summary = new Label();
    private final Label status = new Label();

    private final NumberAxis xAxis = new NumberAxis();
    private final NumberAxis yAxis = new NumberAxis();

    private final LineChart<Number, Number> hoursChart = new LineChart<>(xAxis, yAxis);

    public InsightsView(WorkSessionRepository repository) {

        this.repository = repository;

        setSpacing(14);
        setPadding(new Insets(16));
        setMaxWidth(Double.MAX_VALUE);
        getStyleClass().add("content-card");

        Label heading = new Label("Insights");
        heading.getStyleClass().add("section-heading");

        periodChoice.getItems().setAll(Period.values());
        periodChoice.setValue(Period.WEEK);

        Button previous = new Button("◀");
        Button next = new Button("▶");
        Button today = new Button("Today");

        previous.setOnAction(e -> movePeriod(-1));
        next.setOnAction(e -> movePeriod(1));
        today.setOnAction(e -> anchorDate.setValue(LocalDate.now()));

        periodChoice.setOnAction(e -> reload());
        anchorDate.setOnAction(e -> reload());

        FlowPane controls = new FlowPane(
                8, 8,
                periodChoice, previous, anchorDate, next, today);

        controls.setAlignment(Pos.CENTER_LEFT);

        yAxis.setLabel("Actual hours");
        yAxis.setForceZeroInRange(true);

        hoursChart.setTitle("Actual Work Hours");
        hoursChart.setAnimated(false);
        hoursChart.setLegendVisible(false);
        hoursChart.setCreateSymbols(true);
        hoursChart.setMinHeight(260);
        hoursChart.setPrefHeight(320);

        summary.setWrapText(true);
        status.setWrapText(true);

        Label explanation = new Label(
                "Completed work sessions only. "
                        + "Active sessions are added after Pause or Stop.");

        explanation.setWrapText(true);
        explanation.getStyleClass().add("mini-stat");

        getChildren().addAll(
                heading, controls, summary,
                hoursChart, explanation, status);

        reload();
    }

    private void movePeriod(int direction) {

        LocalDate date = anchorDate.getValue();
        if (date == null)
            date = LocalDate.now();

        LocalDate target = switch (periodChoice.getValue()) {
            case DAY -> date.plusDays(direction);
            case WEEK -> date.plusWeeks(direction);
            case MONTH -> date.withDayOfMonth(1).plusMonths(direction);
            case YEAR -> date.withDayOfYear(1).plusYears(direction);
        };

        anchorDate.setValue(target);
        reload();
    }

    public void reload() {

        Period period = periodChoice.getValue();
        LocalDate date = anchorDate.getValue();

        if (period == null || date == null)
            return;

        ZoneId zone = ZoneId.systemDefault();

        LocalDate start = switch (period) {
            case DAY -> date;
            case WEEK -> date.with(
                    TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> date.withDayOfMonth(1);
            case YEAR -> date.withDayOfYear(1);
        };

        LocalDate end = switch (period) {
            case DAY -> start.plusDays(1);
            case WEEK -> start.plusWeeks(1);
            case MONTH -> start.plusMonths(1);
            case YEAR -> start.plusYears(1);
        };

        List<Instant> boundaries = new ArrayList<>();

        ZonedDateTime cursor = start.atStartOfDay(zone);
        Instant rangeEnd = end.atStartOfDay(zone).toInstant();

        while (cursor.toInstant().isBefore(rangeEnd)) {

            boundaries.add(cursor.toInstant());

            cursor = switch (period) {
                case DAY -> cursor.plusHours(1);
                case WEEK, MONTH -> cursor.plusDays(1);
                case YEAR -> cursor.plusMonths(1);
            };
        }

        boundaries.add(rangeEnd);

        int count = boundaries.size() - 1;

        try {
            List<WorkSession> sessions = repository.findFinishedOverlapping(
                    boundaries.get(0), rangeEnd);

            XYChart.Series<Number, Number> series = new XYChart.Series<>();

            Instant now = Instant.now();
            long totalMillis = 0;
            int elapsedBuckets = 0;

            for (int i = 0; i < count; i++) {

                Instant bucketStart = boundaries.get(i);
                Instant bucketEnd = boundaries.get(i + 1);

                // Future periods must not be shown as zero work.
                if (bucketStart.isAfter(now)) {
                    continue;
                }

                long millis = 0;

                for (WorkSession session : sessions) {

                    Instant from = session.startedAt()
                            .isAfter(bucketStart)
                                    ? session.startedAt()
                                    : bucketStart;

                    Instant to = session.endedAt()
                            .isBefore(bucketEnd)
                                    ? session.endedAt()
                                    : bucketEnd;

                    if (to.isAfter(from)) {
                        millis += Duration.between(from, to).toMillis();
                    }
                }

                totalMillis += millis;
                elapsedBuckets++;

                series.getData().add(
                        new XYChart.Data<>(
                                i + 1,
                                millis / 3_600_000.0));
            }

            xAxis.setAutoRanging(false);
            xAxis.setLowerBound(1);
            xAxis.setUpperBound(count);

            xAxis.setTickUnit(switch (period) {
                case DAY -> 3;
                case WEEK, YEAR -> 1;
                case MONTH -> 5;
            });

            xAxis.setLabel(switch (period) {
                case DAY -> "Local hour";
                case WEEK -> "Day (Mon–Sun)";
                case MONTH -> "Day of month";
                case YEAR -> "Month";
            });

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(
                    period == Period.DAY ? "HH"
                            : period == Period.WEEK ? "EEE"
                                    : period == Period.YEAR ? "MMM"
                                            : "dd",
                    Locale.ENGLISH).withZone(zone);

            xAxis.setTickLabelFormatter(new StringConverter<>() {
                @Override
                public String toString(Number value) {
                    int index = value.intValue() - 1;

                    if (index < 0 || index >= count
                            || value.doubleValue() != index + 1) {
                        return "";
                    }

                    return formatter.format(boundaries.get(index));
                }

                @Override
                public Number fromString(String value) {
                    return null;
                }
            });

            hoursChart.getData().setAll(series);

            double hours = totalMillis / 3_600_000.0;
            double average = elapsedBuckets == 0
                    ? 0
                    : hours / elapsedBuckets;

            summary.setText(String.format(
                    Locale.ROOT,
                    "%s to %s  |  Total: %.2f h  |  "
                            + "Average per elapsed interval: %.2f h",
                    start, end.minusDays(1), hours, average));

            status.setText("");

        } catch (SQLException ex) {
            status.setText("Unable to load work statistics.");
            hoursChart.getData().clear();
            ex.printStackTrace();
        }
    }
}