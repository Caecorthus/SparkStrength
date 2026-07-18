package annina.sparkstrength.tablet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class TabletMeetingScrollRulesTest {
    @Test
    void scrollingDownRevealsTheEighthTargetAndStopsAtTheEnd() {
        assertEquals(1, TabletMeetingScrollRules.scrollFirstRow(0, -1.0D, 8, 7));
        assertEquals(1, TabletMeetingScrollRules.scrollFirstRow(1, -1.0D, 8, 7));
    }

    @Test
    void eachScrollEventMovesExactlyOneRow() {
        assertEquals(2, TabletMeetingScrollRules.scrollFirstRow(1, -1.0D, 10, 7));
        assertEquals(1, TabletMeetingScrollRules.scrollFirstRow(2, 1.0D, 10, 7));
    }

    @Test
    void fractionalTrackpadScrollStillMovesOneRow() {
        assertEquals(2, TabletMeetingScrollRules.scrollFirstRow(1, -0.25D, 10, 7));
        assertEquals(1, TabletMeetingScrollRules.scrollFirstRow(2, 0.25D, 10, 7));
    }

    @Test
    void scrollingUpReturnsToTheFirstTarget() {
        assertEquals(0, TabletMeetingScrollRules.scrollFirstRow(1, 1.0D, 8, 7));
        assertEquals(0, TabletMeetingScrollRules.scrollFirstRow(0, 1.0D, 8, 7));
    }

    @Test
    void snapshotShrinkClampsTheExistingScrollPosition() {
        assertEquals(2, TabletMeetingScrollRules.clampFirstRow(4, 9, 7));
        assertEquals(0, TabletMeetingScrollRules.clampFirstRow(2, 6, 7));
        assertEquals(0, TabletMeetingScrollRules.clampFirstRow(2, 0, 7));
    }

    @Test
    void zeroScrollOnlyClampsTheExistingPosition() {
        assertEquals(1, TabletMeetingScrollRules.scrollFirstRow(3, 0.0D, 8, 7));
    }

    @Test
    void inactiveSnapshotResetsScrollWhileActiveSnapshotPreservesIt() {
        assertEquals(0, TabletMeetingScrollRules.firstRowAfterSnapshot(2, false, 10, 7));
        assertEquals(2, TabletMeetingScrollRules.firstRowAfterSnapshot(2, true, 10, 7));
    }
}
