
package dev.personalagenda.view;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FocusHitLayoutTest {

    @Test
    void tinyFocusHasSeparateVisualAndHitHeights() {
        var result = FocusHitLayout.assign(
                List.of(new FocusHitLayout.Span(
                        "tiny", 100, 0.1)),
                1000);

        var item = result.get(0);

        assertEquals(0.1,
                item.span().realHeight(), 0.0001);

        assertEquals(6.0,
                item.barHeight(), 0.0001);

        assertEquals(18.0,
                item.hitHeight(), 0.0001);

        assertEquals(94.0,
                item.hitY(), 0.0001);
    }

    @Test
    void nearbyShortFocusSegmentsReceiveSeparateLanes() {
        var result = FocusHitLayout.assign(
                List.of(
                        new FocusHitLayout.Span("A", 100, 0.1),
                        new FocusHitLayout.Span("B", 102, 1),
                        new FocusHitLayout.Span("C", 105, 0.2),
                        new FocusHitLayout.Span("D", 200, 2)),
                1000);

        assertEquals(3, result.get(0).laneCount());
        assertEquals(3, result.get(1).laneCount());
        assertEquals(3, result.get(2).laneCount());

        assertNotEquals(
                result.get(0).lane(),
                result.get(1).lane());

        assertEquals(1, result.get(3).laneCount());
        assertEquals(0, result.get(3).lane());
    }

    @Test
    void longFocusKeepsItsActualPixelHeight() {
        var result = FocusHitLayout.assign(
                List.of(new FocusHitLayout.Span(
                        "long", 200, 80)),
                1000);

        var item = result.get(0);

        assertEquals(80, item.barHeight(), 0.0001);
        assertEquals(80, item.hitHeight(), 0.0001);
        assertEquals(200, item.hitY(), 0.0001);
    }

    @Test
    void hitRegionsStayInsideCanvasEdges() {
        var result = FocusHitLayout.assign(
                List.of(
                        new FocusHitLayout.Span(
                                "top", 1, 1),
                        new FocusHitLayout.Span(
                                "bottom", 990, 1)),
                1000);

        assertEquals(0,
                result.get(0).hitY(), 0.0001);

        assertEquals(982,
                result.get(1).hitY(), 0.0001);

        for (var item : result) {
            assertTrue(item.hitY() >= 0);
            assertTrue(
                    item.hitY() + item.hitHeight() <= 1000);
        }
    }
}
