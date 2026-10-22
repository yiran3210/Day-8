package dev.personalagenda.view;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;
import java.util.List;
import java.util.Objects;

public final class ScheduleTimeValidation {

    private ScheduleTimeValidation() {
    }

    // Returns null when valid; otherwise an explanation.
    public static String check(
            LocalDateTime start,
            LocalDateTime end,
            ZoneId zone) {

        Objects.requireNonNull(start);
        Objects.requireNonNull(end);
        Objects.requireNonNull(zone);

        if (!end.isAfter(start)) {
            return "End must be after start.";
        }

        ZoneRules rules = zone.getRules();

        for (LocalDateTime point : List.of(start, end)) {
            List<ZoneOffset> offsets = rules.getValidOffsets(point);

            if (offsets.isEmpty()) {
                return point + " does not exist in " + zone
                        + " (clock moves forward).";
            }

            if (offsets.size() > 1) {
                return point + " occurs twice in " + zone
                        + " (clock moves back).";
            }
        }

        Instant from = start.toInstant(
                rules.getValidOffsets(start).get(0));

        Instant to = end.toInstant(
                rules.getValidOffsets(end).get(0));

        ZoneOffsetTransition change = rules.nextTransition(from);

        if (change != null
                && !change.getInstant().isAfter(to)) {

            return "This activity crosses a clock change in "
                    + zone
                    + ". Reschedule it outside the transition window.";
        }

        return null;
    }
}