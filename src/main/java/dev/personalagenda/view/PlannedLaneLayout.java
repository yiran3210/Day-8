
package dev.personalagenda.view;

import dev.personalagenda.model.TimeBlock;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Assign non-overlapping horizontal lanes to planned blocks
 * on the displayed day.
 */
final class PlannedLaneLayout {

    record Item(TimeBlock block, Instant from, Instant to) {
    }

    record Placement(Item item, int lane, int laneCount) {
    }

    private PlannedLaneLayout() {
    }

    static List<Placement> assign(List<Item> items) {
        List<Item> sorted = items.stream()
                .sorted(Comparator.comparing(Item::from)
                        .thenComparing(Item::to)
                        .thenComparing(item -> item.block().id()))
                .toList();

        List<Placement> output = new ArrayList<>();
        List<Item> group = new ArrayList<>();
        Instant groupEnd = null;

        for (Item item : sorted) {
            // [start, end): touching endpoints do not overlap.
            if (!group.isEmpty() && !item.from().isBefore(groupEnd)) {
                addGroup(group, output);
                group.clear();
                groupEnd = null;
            }

            group.add(item);

            if (groupEnd == null || item.to().isAfter(groupEnd)) {
                groupEnd = item.to();
            }
        }

        if (!group.isEmpty()) {
            addGroup(group, output);
        }

        return output;
    }

    private static void addGroup(
            List<Item> group,
            List<Placement> output) {

        List<Instant> laneEnds = new ArrayList<>();
        List<Integer> assignedLanes = new ArrayList<>();

        for (Item item : group) {
            int lane = 0;

            while (lane < laneEnds.size()
                    && laneEnds.get(lane).isAfter(item.from())) {
                lane++;
            }

            if (lane == laneEnds.size()) {
                laneEnds.add(item.to());
            } else {
                laneEnds.set(lane, item.to());
            }

            assignedLanes.add(lane);
        }

        for (int i = 0; i < group.size(); i++) {
            output.add(new Placement(
                    group.get(i),
                    assignedLanes.get(i),
                    laneEnds.size()));
        }
    }
}
