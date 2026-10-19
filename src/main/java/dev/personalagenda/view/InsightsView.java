package dev.personalagenda.view;

import dev.personalagenda.data.WorkSessionRepository;
import dev.personalagenda.data.CompletionObservationRepository;
import dev.personalagenda.model.CompletionObservation;
import dev.personalagenda.model.WorkSession;

import java.sql.SQLException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

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

    private final Label status = new Label();

    private final NumberAxis xAxis = new NumberAxis();
    private final NumberAxis yAxis = new NumberAxis();

    private final LineChart<Number, Number> hoursChart = new LineChart<>(xAxis, yAxis);

    private final CompletionObservationRepository completionRepository = new CompletionObservationRepository();

    private final NumberAxis completionXAxis = new NumberAxis();

    private final NumberAxis completionYAxis = new NumberAxis(0, 100, 25);

    private final LineChart<Number, Number> completionChart = new LineChart<>(completionXAxis, completionYAxis);

    private final Label actualHoursValue = new Label("—");
    private final Label completionValue = new Label("—");
    private final Label completionStatus = new Label();

    FlowPane kpis = new FlowPane(
            12, 10,
            kpiCard("Actual Work", actualHoursValue),
            kpiCard("Observed Completion", completionValue));

    public InsightsView(WorkSessionRepository repository) {

        this.repository = repository;

        setSpacing(12);
        setPadding(new Insets(14));
        setMaxWidth(Double.MAX_VALUE);
        getStyleClass().add("content-card");

        Label heading = new Label("Insights");
        heading.getStyleClass().add("section-heading");

        periodChoice.getItems().setAll(Period.values());
        periodChoice.setValue(Period.WEEK);

        Button previous = new Button("◀");
        Button next = new Button("▶");
        Button today = new Button("Today");

        previous.setTooltip(new Tooltip("Previous period"));
        next.setTooltip(new Tooltip("Next period"));
        today.setTooltip(new Tooltip("Return to today"));

        previous.setMinWidth(Region.USE_PREF_SIZE);
        next.setMinWidth(Region.USE_PREF_SIZE);
        today.setMinWidth(Region.USE_PREF_SIZE);

        anchorDate.setPrefWidth(160);

        previous.setOnAction(e -> movePeriod(-1));
        next.setOnAction(e -> movePeriod(1));
        today.setOnAction(e -> anchorDate.setValue(LocalDate.now()));

        periodChoice.setOnAction(e -> reload());
        anchorDate.setOnAction(e -> reload());

        CheckBox showGrid = new CheckBox();

        Label gridText = new Label("Grid");

        HBox gridControl = new HBox(6, showGrid, gridText);
        gridControl.setAlignment(Pos.CENTER_LEFT);
        gridControl.setMinWidth(Region.USE_PREF_SIZE);

        showGrid.setSelected(true);

        showGrid.selectedProperty().addListener(
                (obs, oldValue, enabled) -> {

                    hoursChart.setHorizontalGridLinesVisible(enabled);
                    hoursChart.setVerticalGridLinesVisible(enabled);

                    completionChart.setHorizontalGridLinesVisible(enabled);
                    completionChart.setVerticalGridLinesVisible(enabled);
                });

        hoursChart.setHorizontalGridLinesVisible(true);
        hoursChart.setVerticalGridLinesVisible(true);

        FlowPane controls = new FlowPane(
                8, 8,
                periodChoice, previous, anchorDate,
                next, today, gridControl);

        controls.setAlignment(Pos.CENTER_LEFT);

        yAxis.setLabel("Actual hours");
        yAxis.setAutoRanging(false);
        yAxis.setLowerBound(0);
        yAxis.setUpperBound(2);
        yAxis.setTickUnit(0.5);

        xAxis.setAutoRanging(false);

        xAxis.setTickLabelsVisible(true);
        yAxis.setTickLabelsVisible(true);

        xAxis.setTickMarkVisible(true);
        yAxis.setTickMarkVisible(true);

        xAxis.setMinorTickVisible(false);
        yAxis.setMinorTickVisible(false);

        hoursChart.getStyleClass().add("insights-chart");
        hoursChart.setMaxHeight(300);
        hoursChart.setAnimated(false);
        hoursChart.setLegendVisible(false);
        hoursChart.setCreateSymbols(true);
        hoursChart.setMinHeight(220);
        hoursChart.setPrefHeight(260);

        completionXAxis.setAutoRanging(false);
        completionYAxis.setAutoRanging(false);
        completionYAxis.setLabel("Completion (%)");

        completionChart.setTitle("Task Completion Rate");
        completionChart.setAnimated(false);
        completionChart.setLegendVisible(false);
        completionChart.setCreateSymbols(true);
        completionChart.setMinHeight(220);
        completionChart.setPrefHeight(260);
        completionChart.setMaxHeight(300);

        completionChart.getStyleClass().addAll(
                "insights-chart", "completion-chart");

        status.setWrapText(true);

        Label explanation = new Label(
                "Completed work sessions only. "
                        + "Active sessions are added after Pause or Stop.");

        explanation.setWrapText(true);
        explanation.getStyleClass().add("mini-stat");

        getChildren().addAll(
                heading,
                controls,
                kpis,
                hoursChart,
                completionChart,
                completionStatus,
                explanation,
                status);

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

            double maxHours = series.getData().stream()
                    .mapToDouble(point -> point.getYValue().doubleValue())
                    .max()
                    .orElse(0.0);

            double tickStep = Math.max(
                    0.5,
                    Math.ceil(maxHours / 4.0 * 2.0) / 2.0);

            yAxis.setLowerBound(0);
            yAxis.setUpperBound(tickStep * 4);
            yAxis.setTickUnit(tickStep);

            hoursChart.getData().setAll(series);

            reloadCompletion(
                    period,
                    start,
                    end,
                    boundaries);

            double hours = totalMillis / 3_600_000.0;
            double average = elapsedBuckets == 0
                    ? 0
                    : hours / elapsedBuckets;

            actualHoursValue.setText(
                    String.format(Locale.ROOT, "%.2f h", hours));

            if (elapsedBuckets == 0) {
                status.setText(
                        "Future period: no actual work data yet.");
            } else if (sessions.isEmpty()) {
                status.setText(
                        "No completed work sessions in this period.");
            } else {
                status.setText("");
            }

        } catch (SQLException ex) {
            status.setText("Unable to load work statistics.");
            hoursChart.getData().clear();
            ex.printStackTrace();
        }
    }

    private static VBox kpiCard(String caption, Label value) {

        Label title = new Label(caption);
        title.getStyleClass().add("mini-stat");

        value.getStyleClass().add("section-heading");

        VBox card = new VBox(5, title, value);
        card.getStyleClass().add("insights-kpi");

        return card;
    }

    private void reloadCompletion(
            Period period,
            LocalDate start,
            LocalDate end,
            List<Instant> boundaries) throws SQLException {

        List<CompletionObservation> history = completionRepository.findBetween(start, end);

        Map<LocalDate, CompletionObservation> lastByDay = new HashMap<>();

        for (CompletionObservation observation : history) {
            lastByDay.put(observation.date(), observation);
        }

        int count = boundaries.size() - 1;

        completionXAxis.setLowerBound(1);
        completionXAxis.setUpperBound(count);
        completionXAxis.setTickUnit(xAxis.getTickUnit());
        completionXAxis.setTickLabelFormatter(
                xAxis.getTickLabelFormatter());
        completionXAxis.setLabel(xAxis.getLabel());

        List<Double> rates = new ArrayList<>();

        if (period == Period.DAY) {

            CompletionObservation last = null;
            int cursor = 0;

            for (int i = 0; i < count; i++) {

                Instant bucketEnd = boundaries.get(i + 1);

                while (cursor < history.size()
                        && history.get(cursor).observedAt()
                                .isBefore(bucketEnd)) {

                    last = history.get(cursor++);
                }

                rates.add(
                        boundaries.get(i).isAfter(Instant.now())
                                ? null
                                : rate(last));
            }

        } else if (period == Period.YEAR) {

            for (int i = 0; i < count; i++) {

                LocalDate monthStart = start.plusMonths(i);
                LocalDate monthEnd = monthStart.plusMonths(1);

                long planned = 0;
                long completed = 0;

                for (CompletionObservation observation : lastByDay.values()) {

                    if (!observation.date().isBefore(monthStart)
                            && observation.date().isBefore(monthEnd)
                            && observation.plannedMinutes() > 0) {

                        planned += observation.plannedMinutes();
                        completed += observation.completedMinutes();
                    }
                }

                rates.add(planned == 0
                        ? null
                        : 100.0 * completed / planned);
            }

        } else {

            // Week and Month: one observation per calendar day
            for (int i = 0; i < count; i++) {
                rates.add(rate(lastByDay.get(start.plusDays(i))));
            }
        }

        // Split the line at missing dates.
        List<XYChart.Series<Number, Number>> segments = new ArrayList<>();

        XYChart.Series<Number, Number> segment = null;

        for (int i = 0; i < rates.size(); i++) {

            Double value = rates.get(i);

            if (value == null) {
                segment = null;
                continue;
            }

            if (segment == null) {
                segment = new XYChart.Series<>();
                segments.add(segment);
            }

            segment.getData().add(
                    new XYChart.Data<>(i + 1, value));
        }

        completionChart.getData().setAll(segments);

        long plannedSum = 0;
        long completedSum = 0;

        if (period == Period.DAY) {

            CompletionObservation last = history.isEmpty()
                    ? null
                    : history.get(history.size() - 1);

            if (last != null && last.plannedMinutes() > 0) {
                plannedSum = last.plannedMinutes();
                completedSum = last.completedMinutes();
            }

        } else {

            for (CompletionObservation observation : lastByDay.values()) {

                if (observation.plannedMinutes() > 0) {
                    plannedSum += observation.plannedMinutes();
                    completedSum += observation.completedMinutes();
                }
            }
        }

        completionValue.setText(plannedSum == 0
                ? "—"
                : String.format(Locale.ROOT, "%.1f%%",
                        100.0 * completedSum / plannedSum));

        completionStatus.setText(
                history.isEmpty()
                        ? "No recorded completion history for this period."
                        : lastByDay.size()
                                + " observed day(s). Missing dates are not 0%.");
    }

    private static Double rate(CompletionObservation observation) {

        if (observation == null
                || observation.plannedMinutes() <= 0) {
            return null;
        }

        return 100.0 * observation.completedMinutes()
                / observation.plannedMinutes();
    }
}