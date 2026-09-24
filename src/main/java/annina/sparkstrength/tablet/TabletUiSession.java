package annina.sparkstrength.tablet;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class TabletUiSession {
    private Section section = Section.CONNECTIONS;
    private String draft;
    private int meetingFirstRow;
    private int suspectFirstRow;

    public TabletUiSession(String initialDraft) {
        draft = initialDraft == null ? "" : initialDraft;
    }

    public Section section() {
        return section;
    }

    public void select(Section nextSection) {
        section = Objects.requireNonNull(nextSection, "nextSection");
    }

    /**
     * Sections a channel may show, in {@link Section} order. Client-side presentation only: the server already
     * redacts meeting/suspect data for non-police channels.
     * 频道可显示的分区（按 Section 顺序）。仅用于客户端展示：服务端已为非义警频道裁剪会议/嫌疑人数据。
     */
    public static List<Section> visibleSections(@Nullable TabletChannel channel) {
        if (channel == null) {
            return List.of();
        }
        ArrayList<Section> sections = new ArrayList<>();
        for (Section candidate : Section.values()) {
            if (!candidate.requiresMeetingFeatures() || channel.hasMeetingFeatures()) {
                sections.add(candidate);
            }
        }
        return List.copyOf(sections);
    }

    /**
     * Falls back to the first visible section when the current one was hidden; returns whether it changed.
     * 当前分区被隐藏时回退到第一个可见分区；返回是否发生变化。
     */
    public boolean ensureVisible(List<Section> visibleSections) {
        if (visibleSections == null || visibleSections.isEmpty() || visibleSections.contains(section)) {
            return false;
        }
        section = visibleSections.get(0);
        return true;
    }

    public String draft() {
        return draft;
    }

    public void updateDraft(String nextDraft) {
        draft = nextDraft == null ? "" : nextDraft;
    }

    public Optional<String> submitDraft() {
        String message = draft.trim();
        draft = "";
        return message.isEmpty() ? Optional.empty() : Optional.of(message);
    }

    public int meetingFirstRow() {
        return meetingFirstRow;
    }

    public void scrollMeeting(double verticalAmount, int totalRows, int visibleRows) {
        meetingFirstRow = TabletMeetingScrollRules.scrollFirstRow(
                meetingFirstRow,
                verticalAmount,
                totalRows,
                visibleRows
        );
    }

    public void applyMeetingSnapshot(boolean active, int totalRows, int visibleRows) {
        meetingFirstRow = TabletMeetingScrollRules.firstRowAfterSnapshot(
                meetingFirstRow,
                active,
                totalRows,
                visibleRows
        );
    }

    public int suspectFirstRow() {
        return suspectFirstRow;
    }

    public void scrollSuspects(double verticalAmount, int totalRows, int visibleRows) {
        suspectFirstRow = TabletMeetingScrollRules.scrollFirstRow(
                suspectFirstRow,
                verticalAmount,
                totalRows,
                visibleRows
        );
    }

    public void applySuspectSnapshot(int totalRows, int visibleRows) {
        suspectFirstRow = TabletMeetingScrollRules.clampFirstRow(
                suspectFirstRow,
                totalRows,
                visibleRows
        );
    }

    public enum Section {
        CONNECTIONS("screen.sparkstrength.tablet.tab.connections", false),
        CHAT("screen.sparkstrength.tablet.tab.chat", false),
        MEETING("screen.sparkstrength.tablet.tab.meeting", true),
        SUSPECTS("screen.sparkstrength.tablet.tab.suspects", true);

        private final String translationKey;
        private final boolean requiresMeetingFeatures;

        Section(String translationKey, boolean requiresMeetingFeatures) {
            this.translationKey = translationKey;
            this.requiresMeetingFeatures = requiresMeetingFeatures;
        }

        public String translationKey() {
            return translationKey;
        }

        public boolean requiresMeetingFeatures() {
            return requiresMeetingFeatures;
        }
    }
}
