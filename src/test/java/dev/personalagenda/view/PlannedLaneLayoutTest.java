
package dev.personalagenda.view;

import dev.personalagenda.model.TimeBlock;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlannedLaneLayoutTest {

    private static PlannedLaneLayout.Item item(
            String id, int startMinute, int endMinute) {

        LocalDateTime midnight = LocalDate.of(2026, 10, 23).atStartOfDay();

        Instant utcMidnight = Instant.parse("2026-10-23T00:00:00Z");

        TimeBlock block = new TimeBlock(
                id, id,
                midnight.plusMinutes(startMinute),
                midnight.plusMinutes(endMinute),
                TimeBlock.Type.FIXED,
                null, null);

        return new PlannedLaneLayout.Item(
                block,
                utcMidnight.plusSeconds(startMinute * 60L),
                utcMidnight.plusSeconds(endMinute * 60L));
    }

    @Test
    void connectedOverlapsReuseFreeLaneWithoutCoveringBlocks() {
        var placements = PlannedLaneLayout.assign(List.of(
                item("B", 570, 630),
                item("C", 600, 660),
                item("A", 540, 600)));

        assertEquals(List.of("A", "B", "C"), placements.stream()
                .map(p -> p.item().block().id()).toList());

        assertEquals(List.of(0, 1, 0), placements.stream()
                .map(PlannedLaneLayout.Placement::lane).toList());

        assertEquals(List.of(2, 2, 2), placements.stream()
                .map(PlannedLaneLayout.Placement::laneCount).toList());
    }

    @Test
    void touchingEndpointsAreNotOverlaps() {
        var placements = PlannedLaneLayout.assign(List.of(
                item("A", 540, 600),
                item("B", 600, 660)));

        assertEquals(List.of(0, 0), placements.stream()
                .map(PlannedLaneLayout.Placement::lane).toList());

        assertEquals(List.of(1, 1), placements.stream()
                .map(PlannedLaneLayout.Placement::laneCount).toList());
    }

    @Test
    void simultaneousBlocksUseThreeLanesAndLaterGroupResets() {
        var placements = PlannedLaneLayout.assign(List.of(
                item("A", 540, 600),
                item("B", 540, 585),
                item("C", 540, 570),
                item("D", 600, 630)));

        assertEquals(List.of(3, 3, 3, 1), placements.stream()
                .map(PlannedLaneLayout.Placement::laneCount).toList());

        assertEquals(List.of(0, 1, 2, 0), placements.stream()
                .map(PlannedLaneLayout.Placement::lane).toList());
    }
}
