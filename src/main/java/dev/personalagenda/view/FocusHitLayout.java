
package dev.personalagenda.view;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pixel-only layout for Focus display and mouse hit targets.
 * Recorded durations are never modified.
 */
final class FocusHitLayout {

    static final double MIN_BAR_HEIGHT = 6.0;
    static final double MIN_HIT_HEIGHT = 18.0;

    record Span(String id, double y, double realHeight) {
    }

    record Placement(
            Span span,
            double hitY,
            double hitHeight,
            double barHeight,
            int lane,
            int laneCount) {
    }

    private record Prepared(
            Span span,
            double hitY,
            double hitHeight,
            double barHeight) {
    }

    private FocusHitLayout() {
    }

    static List<Placement> assign(
            List<Span> spans,
            double canvasHeight) {

        if (!Double.isFinite(canvasHeight) || canvasHeight <= 0) {
            throw new IllegalArgumentException("Invalid canvas height");
        }

        List<Prepared> sorted = spans.stream()
                .map(span -> {
                    if (span.id() == null
                            || !Double.isFinite(span.y())
                            || !Double.isFinite(span.realHeight())
                            || span.realHeight() <= 0) {
                        throw new IllegalArgumentException("Invalid Focus span");
                    }

                    double barHeight = Math.max(
                            MIN_BAR_HEIGHT, span.realHeight());

                    double hitHeight = Math.max(
                            MIN_HIT_HEIGHT, barHeight);

                    double desiredY = span.y()
                            - (hitHeight - barHeight) / 2;

                    double hitY = Math.max(0,
                            Math.min(desiredY,
                                    Math.max(0,
                                            canvasHeight - hitHeight)));

                    return new Prepared(
                            span, hitY, hitHeight, barHeight);
                })
                .sorted(Comparator
                        .comparingDouble(Prepared::hitY)
                        .thenComparing(p -> p.span().id()))
                .toList();

        List<Placement> result = new ArrayList<>();
        List<Prepared> group = new ArrayList<>();
        double groupEnd = Double.NEGATIVE_INFINITY;

        for (Prepared item : sorted) {
            if (!group.isEmpty() && item.hitY() >= groupEnd) {
                addGroup(group, result);
                group.clear();
                groupEnd = Double.NEGATIVE_INFINITY;
            }

            group.add(item);
            groupEnd = Math.max(
                    groupEnd, item.hitY() + item.hitHeight());
        }

        if (!group.isEmpty()) {
            addGroup(group, result);
        }

        return List.copyOf(result);
    }

    private static void addGroup(
            List<Prepared> group,
            List<Placement> result) {

        List<Double> laneEnds = new ArrayList<>();
        List<Integer> lanes = new ArrayList<>();

        for (Prepared item : group) {
            int lane = 0;

            while (lane < laneEnds.size()
                    && laneEnds.get(lane) > item.hitY()) {
                lane++;
            }

            if (lane == laneEnds.size()) {
                laneEnds.add(item.hitY() + item.hitHeight());
            } else {
                laneEnds.set(lane, item.hitY() + item.hitHeight());
            }

            lanes.add(lane);
        }

        for (int i = 0; i < group.size(); i++) {
            Prepared item = group.get(i);

            result.add(new Placement(
                    item.span(),
                    item.hitY(),
                    item.hitHeight(),
                    item.barHeight(),
                    lanes.get(i),
                    laneEnds.size()));
        }
    }
}
