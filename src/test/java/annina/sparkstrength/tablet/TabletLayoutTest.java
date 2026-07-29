package annina.sparkstrength.tablet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TabletLayoutTest {
    @Test
    void wideViewportUsesContainedRailLayout() {
        TabletLayout layout = TabletLayout.forViewport(640, 360);

        assertEquals(TabletLayout.Mode.WIDE, layout.mode());
        assertContained(layout.viewport(), layout.panel());
        assertContained(layout.panel(), layout.statusBar());
        assertContained(layout.panel(), layout.navigation());
        assertContained(layout.panel(), layout.body());
        assertContained(layout.panel(), layout.footer());
        assertContained(layout.body(), layout.list());
        assertFalse(layout.navigation().overlaps(layout.body()));
        assertFalse(layout.statusBar().overlaps(layout.body()));
        assertFalse(layout.body().overlaps(layout.footer()));
        assertEquals(4, layout.tabs().size());
        layout.tabs().forEach(tab -> assertContained(layout.navigation(), tab));
        assertContained(layout.statusBar(), layout.closeButton());
        assertTrue(layout.visibleRows() > 0);
    }

    @Test
    void compactViewportMovesNavigationAboveTheBody() {
        TabletLayout layout = TabletLayout.forViewport(480, 270);

        assertEquals(TabletLayout.Mode.COMPACT, layout.mode());
        assertContained(layout.viewport(), layout.panel());
        assertEquals(layout.panel().width(), layout.navigation().width());
        assertEquals(layout.statusBar().bottom(), layout.navigation().y());
        assertEquals(layout.navigation().bottom(), layout.body().y());
        assertFalse(layout.navigation().overlaps(layout.body()));
        assertFalse(layout.body().overlaps(layout.footer()));
        assertEquals(4, layout.tabs().size());
        layout.tabs().forEach(tab -> assertContained(layout.navigation(), tab));
        assertTrue(layout.visibleRows() >= 3);
    }

    @Test
    void narrowViewportKeepsEveryPrimaryRegionReachable() {
        TabletLayout layout = TabletLayout.forViewport(320, 220);

        assertEquals(TabletLayout.Mode.NARROW, layout.mode());
        assertContained(layout.viewport(), layout.panel());
        assertContained(layout.panel(), layout.statusBar());
        assertContained(layout.panel(), layout.navigation());
        assertContained(layout.panel(), layout.body());
        assertContained(layout.panel(), layout.footer());
        assertContained(layout.body(), layout.list());
        assertContained(layout.statusBar(), layout.closeButton());
        layout.tabs().forEach(tab -> {
            assertContained(layout.navigation(), tab);
            assertTrue(tab.width() > 0);
            assertTrue(tab.height() > 0);
        });
        assertTrue(layout.visibleRows() >= 3);
    }

    private static void assertContained(TabletLayout.Rect outer, TabletLayout.Rect inner) {
        assertTrue(outer.contains(inner), () -> inner + " must be inside " + outer);
    }
}
