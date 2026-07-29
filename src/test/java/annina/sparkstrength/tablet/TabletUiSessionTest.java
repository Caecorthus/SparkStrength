package annina.sparkstrength.tablet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class TabletUiSessionTest {
    @Test
    void switchingSectionsPreservesTheDraft() {
        TabletUiSession session = new TabletUiSession("coordinate at reactor");

        assertEquals(TabletUiSession.Section.CONNECTIONS, session.section());
        session.select(TabletUiSession.Section.CHAT);
        session.updateDraft("hold the east corridor");
        session.select(TabletUiSession.Section.MEETING);

        assertEquals(TabletUiSession.Section.MEETING, session.section());
        assertEquals("hold the east corridor", session.draft());
    }

    @Test
    void submittingChatReturnsTrimmedMessageAndClearsTheDraft() {
        TabletUiSession session = new TabletUiSession("  meet at reactor  ");

        assertEquals("meet at reactor", session.submitDraft().orElseThrow());
        assertEquals("", session.draft());

        session.updateDraft("   ");
        assertEquals(java.util.Optional.empty(), session.submitDraft());
        assertEquals("", session.draft());
    }

    @Test
    void meetingScrollSurvivesActiveSnapshotsAndResetsWhenMeetingEnds() {
        TabletUiSession session = new TabletUiSession("");

        session.scrollMeeting(-1.0D, 10, 4);
        session.scrollMeeting(-1.0D, 10, 4);
        assertEquals(2, session.meetingFirstRow());

        session.applyMeetingSnapshot(true, 5, 4);
        assertEquals(1, session.meetingFirstRow());

        session.applyMeetingSnapshot(false, 5, 4);
        assertEquals(0, session.meetingFirstRow());
    }

    @Test
    void suspectScrollRevealsLaterRowsAndClampsAfterSnapshotShrink() {
        TabletUiSession session = new TabletUiSession("");

        session.scrollSuspects(-1.0D, 5, 4);
        assertEquals(1, session.suspectFirstRow());

        session.applySuspectSnapshot(4, 4);
        assertEquals(0, session.suspectFirstRow());
    }
}
