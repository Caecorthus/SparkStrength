package annina.sparkstrength.tablet;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class TabletUiSession {
    private Section section = Section.CONNECTIONS;
    private String draft;
    private int meetingFirstRow;
    private int suspectFirstRow;
    private int connectionsFirstRow;
    private int doorLogFirstRow;
    private int chatScroll;

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
     * Sections the holder may show, in {@link Section} order: channel sections need a channel (meeting/suspects only on
     * a channel with meeting features); feature sections ({@link Section#requiredFeature()}) need that feature and
     * ignore the channel, so a holder with features but no channel still gets a rail. Client-side presentation only:
     * the server already redacts meeting/suspect/door data the viewer may not see.
     * 持有者可显示的分区（按 Section 顺序）：频道分区需要频道（会议/嫌疑人只在带会议功能的频道）；功能分区只看对应功能、
     * 与频道无关，因此有功能但无频道的持有者仍有侧栏。仅用于客户端展示：服务端已裁剪观看者不可见的会议/嫌疑人/房门数据。
     */
    public static List<Section> visibleSections(@Nullable TabletChannel channel, @Nullable Set<TabletFeature> features) {
        ArrayList<Section> sections = new ArrayList<>();
        for (Section candidate : Section.values()) {
            if (candidate.visibleFor(channel, features)) {
                sections.add(candidate);
            }
        }
        return List.copyOf(sections);
    }

    /**
     * Largest section count across every channel the holder can switch to (plus the current one and "no channel"),
     * with the same features. Layout picks REGULAR/NARROW from it, so switching channels never resizes the frame
     * (an Impostor Attendant has 5 tabs on the police network but 3 on the killer network).
     * 持有者在所有可切换频道（含当前频道与“无频道”）下、相同功能时的最大分区数。布局据此选择 REGULAR/NARROW，
     * 切换频道时外框不会变化（内鬼乘务员在义警网络有 5 个标签，在杀手网络只有 3 个）。
     */
    public static int referenceSectionCount(
            @Nullable TabletChannel current,
            @Nullable Collection<TabletChannel> allowedChannels,
            @Nullable Set<TabletFeature> features
    ) {
        int count = Math.max(visibleSections(null, features).size(), visibleSections(current, features).size());
        if (allowedChannels != null) {
            for (TabletChannel channel : allowedChannels) {
                count = Math.max(count, visibleSections(channel, features).size());
            }
        }
        return count;
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

    public int connectionsFirstRow() {
        return connectionsFirstRow;
    }

    public void scrollConnections(double verticalAmount, int totalRows, int visibleRows) {
        connectionsFirstRow = TabletMeetingScrollRules.scrollFirstRow(
                connectionsFirstRow,
                verticalAmount,
                totalRows,
                visibleRows
        );
    }

    public void clampConnections(int totalRows, int visibleRows) {
        connectionsFirstRow = TabletMeetingScrollRules.clampFirstRow(
                connectionsFirstRow,
                totalRows,
                visibleRows
        );
    }

    public int doorLogFirstRow() {
        return doorLogFirstRow;
    }

    public void scrollDoorLog(double verticalAmount, int totalRows, int visibleRows) {
        doorLogFirstRow = TabletMeetingScrollRules.scrollFirstRow(
                doorLogFirstRow,
                verticalAmount,
                totalRows,
                visibleRows
        );
    }

    /**
     * Keeps a scrolled-down reader on the same entry when newer ones are prepended (the log is newest first).
     * {@code anchorRow} is where the previously first visible entry sits now, or -1 when it is gone (trimmed, merged,
     * new round); a reader at the top stays pinned to the newest entry.
     * 房门记录最新在前：向下翻阅时若有更新条目插入顶部，保持读者停在同一条目。anchorRow 为原先首个可见条目现在的行号，
     * 已不存在（被裁剪、合并或新回合）时为 -1；停在顶部的读者始终看到最新条目。
     */
    public void applyDoorLogSnapshot(int anchorRow, int totalRows, int visibleRows) {
        int target = doorLogFirstRow > 0 && anchorRow >= 0 ? anchorRow : doorLogFirstRow;
        doorLogFirstRow = TabletMeetingScrollRules.clampFirstRow(target, totalRows, visibleRows);
    }

    /**
     * Chat scroll in logical pixels measured from the bottom: 0 pins the newest message in view, larger values reveal
     * older messages. Positive {@code deltaPixels} scrolls towards older messages (mouse wheel up).
     * 聊天滚动量（逻辑像素），从底部起算：0 表示最新消息可见，数值越大显示越早的消息。
     * {@code deltaPixels} 为正时向更早的消息滚动（滚轮向上）。
     */
    public int chatScroll() {
        return chatScroll;
    }

    public void scrollChat(int deltaPixels, int maxScroll) {
        chatScroll = clampChatScroll((long) chatScroll + deltaPixels, maxScroll);
    }

    public void clampChat(int maxScroll) {
        chatScroll = clampChatScroll(chatScroll, maxScroll);
    }

    public void resetChatScroll() {
        chatScroll = 0;
    }

    private static int clampChatScroll(long scroll, int maxScroll) {
        return (int) Math.max(0L, Math.min(scroll, Math.max(0, maxScroll)));
    }

    public enum Section {
        CONNECTIONS("screen.sparkstrength.tablet.tab.connections", false, null),
        CHAT("screen.sparkstrength.tablet.tab.chat", false, null),
        MEETING("screen.sparkstrength.tablet.tab.meeting", true, null),
        SUSPECTS("screen.sparkstrength.tablet.tab.suspects", true, null),
        // Feature sections come last so channel sections keep their rail slots. 功能分区放在最后，频道分区的侧栏位置不变。
        DOOR_LOG("screen.sparkstrength.tablet.tab.door_log", false, TabletFeature.DOOR_LOG);

        private final String translationKey;
        private final boolean requiresMeetingFeatures;
        private final @Nullable TabletFeature requiredFeature;

        Section(String translationKey, boolean requiresMeetingFeatures, @Nullable TabletFeature requiredFeature) {
            this.translationKey = translationKey;
            this.requiresMeetingFeatures = requiresMeetingFeatures;
            this.requiredFeature = requiredFeature;
        }

        public String translationKey() {
            return translationKey;
        }

        public String shortTranslationKey() {
            return translationKey + ".short";
        }

        public boolean requiresMeetingFeatures() {
            return requiresMeetingFeatures;
        }

        /** Feature that grants this section regardless of channel; null for channel sections. 授予该分区的功能；频道分区为 null。 */
        public @Nullable TabletFeature requiredFeature() {
            return requiredFeature;
        }

        private boolean visibleFor(@Nullable TabletChannel channel, @Nullable Set<TabletFeature> features) {
            if (requiredFeature != null) {
                return features != null && features.contains(requiredFeature);
            }
            return channel != null && (!requiresMeetingFeatures || channel.hasMeetingFeatures());
        }
    }
}
