package annina.sparkstrength.client.screen.tablet;

import annina.sparkstrength.client.screen.tablet.TabletMeetingPainter.HeroState;
import annina.sparkstrength.client.screen.tablet.TabletScreen.Grid;
import annina.sparkstrength.client.screen.tablet.TabletSectionPainter.ButtonIcon;
import annina.sparkstrength.client.screen.tablet.TabletSectionPainter.ButtonStyle;
import annina.sparkstrength.network.tablet.CallTabletMeetingC2SPacket;
import annina.sparkstrength.network.tablet.CastTabletVoteC2SPacket;
import annina.sparkstrength.network.tablet.ConfirmTabletVoteC2SPacket;
import annina.sparkstrength.network.tablet.TabletSnapshot;
import annina.sparkstrength.tablet.TabletChannel;
import annina.sparkstrength.tablet.TabletLayout;
import annina.sparkstrength.tablet.TabletLayout.Rect;
import annina.sparkstrength.tablet.TabletUiSession;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;

import java.util.List;
import java.util.UUID;

import static annina.sparkstrength.client.screen.tablet.TabletSectionPainter.KEY;

/**
 * Police meeting section: the call-meeting hero while no meeting runs, else the vote grid over the abstain/confirm
 * footer. Rebuilt on every snapshot (votes and the timer change); the server re-validates every action.
 * 义警会议分区：未开会时为召开会议英雄卡，开会时为投票网格与弃票/确认操作栏。每次快照都会重建（票数与计时变化）；
 * 服务端会重新校验每个操作。
 */
final class TabletMeetingPage implements TabletPage {
    // Action bar buttons (meeting footer). 会议操作栏按钮。
    private static final int ACTION_PAD = 14;
    private static final int ACTION_GAP = 8;
    private static final int ACTION_ICON = 12;
    private static final int ACTION_ICON_GAP = 5;
    private static final int ACTION_MIN_W = 74;

    private final TabletScreen screen;

    TabletMeetingPage(TabletScreen screen) {
        this.screen = screen;
    }

    @Override
    public void prepare(TabletSnapshot snapshot) {
        clampRows(snapshot);
    }

    @Override
    public void onSnapshot(TabletSnapshot snapshot, boolean laidOut) {
        if (laidOut) {
            clampRows(snapshot);
        }
    }

    private void clampRows(TabletSnapshot snapshot) {
        Grid votes = voteGrid();
        screen.session().applyMeetingSnapshot(snapshot.meeting().active(),
                votes.totalRows(snapshot.meeting().targets().size()), votes.visibleRows());
    }

    @Override
    public boolean needsRebuild(TabletSnapshot snapshot) {
        return true;
    }

    @Override
    public void init(TabletSnapshot snapshot) {
        if (hasMeetingFeatures(snapshot)) {
            initMeeting(snapshot);
        }
    }

    private void initMeeting(TabletSnapshot snapshot) {
        TextRenderer textRenderer = screen.textRenderer();
        TabletSnapshot.Meeting meeting = snapshot.meeting();
        if (!meeting.active()) {
            Rect call = TabletMeetingPainter.heroButtonRect(screen.layout().bodyNoFooter());
            screen.addButton("call", call, Text.translatable(KEY + "meeting.call"), ButtonStyle.DANGER,
                    ButtonIcon.MEGAPHONE,
                    () -> ClientPlayNetworking.send(new CallTabletMeetingC2SPacket()))
                    .active = canCallMeeting(snapshot);
            return;
        }

        boolean participant = snapshot.localMeetingParticipant();
        boolean confirmed = meeting.localConfirmed();
        Grid grid = voteGrid();
        List<TabletSnapshot.VoteTarget> targets = meeting.targets();
        int first = screen.session().meetingFirstRow();
        for (int row = 0; row < grid.visibleRows(); row++) {
            for (int column = 0; column < grid.columns(); column++) {
                int index = (first + row) * grid.columns() + column;
                if (index >= targets.size()) {
                    break;
                }
                TabletSnapshot.VoteTarget target = targets.get(index);
                boolean selected = target.uuid().equals(meeting.localVoteTarget());
                boolean clickable = participant && target.selectable() && !confirmed;
                TabletPressable card = new TabletPressable(grid.cellX(column), grid.rowY(row), grid.cellWidth(),
                        TabletMeetingPainter.VOTE_CARD_H, Text.literal(target.name()),
                        () -> ClientPlayNetworking.send(new CastTabletVoteC2SPacket(target.uuid())),
                        (context, button, hovered, delta) -> TabletMeetingPainter.voteCard(screen.canvas(),
                                textRenderer, button, target, selected, hovered, clickable, !participant,
                                screen.accent()));
                card.active = clickable;
                card.setSelectedState(selected);
                screen.keyed("vote:" + target.uuid(), screen.addChild(card));
            }
        }

        ActionBar bar = actionBar(snapshot);
        screen.addButton("abstain", bar.abstain(), bar.abstainLabel(), bar.abstainStyle(), ButtonIcon.NONE,
                () -> ClientPlayNetworking.send(new CastTabletVoteC2SPacket(null)))
                .active = participant && !confirmed;
        screen.addButton("confirm", bar.confirm(), bar.confirmLabel(), bar.confirmStyle(), bar.confirmIcon(),
                () -> ClientPlayNetworking.send(new ConfirmTabletVoteC2SPacket()))
                .active = participant && !confirmed;
    }

    @Override
    public boolean mouseScrolled(TabletSnapshot snapshot, double x, double y, double verticalAmount) {
        TabletUiSession session = screen.session();
        Grid grid = voteGrid();
        if (hasMeetingFeatures(snapshot) && snapshot.meeting().active() && grid.area().contains(x, y)) {
            int previous = session.meetingFirstRow();
            session.scrollMeeting(verticalAmount, grid.totalRows(snapshot.meeting().targets().size()), grid.visibleRows());
            if (session.meetingFirstRow() != previous) {
                screen.refresh();
            }
            return true;
        }
        return false;
    }

    @Override
    public int paintHeaderChip(TabletSnapshot snapshot, int right, int centerY, int maxWidth, long now) {
        TabletSnapshot.Meeting meeting = snapshot.meeting();
        return meeting.active()
                ? screen.paintPill(right, centerY, maxWidth,
                Text.translatable(KEY + "meeting.timer", meeting.remainingSeconds()).getString(),
                TabletTheme.KILLER.pale(), TabletTheme.withAlpha(TabletTheme.DANGER, 0x33), 0,
                (canvas, x, y, size, color) -> screen.paintPulseDot(x + size / 2f, y + size / 2f, 3f, color, now),
                TabletTheme.DANGER)
                // Inactive: the hero card already shows the remaining chances, so no duplicate chip.
                // 未开会：英雄卡已显示剩余次数，页眉不再重复显示。
                : right;
    }

    @Override
    public void paintBadge(TabletSnapshot snapshot, Rect icon, long now) {
        if (snapshot.meeting().active()) {
            screen.paintBadgePulse(icon, TabletTheme.DANGER, now);
        }
    }

    @Override
    public void paintBody(TabletSnapshot snapshot, int hoverX, int hoverY, long now) {
        if (hasMeetingFeatures(snapshot)) {
            paintMeeting(snapshot, now);
        }
    }

    private void paintMeeting(TabletSnapshot snapshot, long now) {
        TabletCanvas c = screen.canvas();
        TextRenderer textRenderer = screen.textRenderer();
        TabletLayout layout = screen.layout();
        TabletSnapshot.Meeting meeting = snapshot.meeting();
        if (!meeting.active()) {
            TabletMeetingPainter.meetingHero(c, textRenderer, layout.bodyNoFooter(), heroState(snapshot),
                    snapshot.cooldownSeconds(), snapshot.localMeetingCallsRemaining(), now);
            return;
        }
        Rect list = layout.list();
        TabletMeetingPainter.meetingProgress(c, list.x(), list.y(), list.width(), meeting.remainingSeconds());
        Grid grid = voteGrid();
        TabletSectionPainter.scrollThumb(c, screen.scrollTrack(grid.area()), screen.session().meetingFirstRow(),
                grid.visibleRows(), grid.totalRows(meeting.targets().size()));

        ActionBar bar = actionBar(snapshot);
        int statusX = layout.footer().x() + layout.mode().padX();
        TabletMeetingPainter.meetingStatus(c, textRenderer, statusX, layout.footer().centerY(),
                Math.max(0, bar.abstain().x() - 10 - statusX), meetingStatus(snapshot), meeting.localConfirmed()
                        && snapshot.localMeetingParticipant());
    }

    private Grid voteGrid() {
        Rect area = screen.layout().list().inset(0, TabletMeetingPainter.PROGRESS_BLOCK_H, 0, 0);
        return Grid.of(area, TabletLayout.gridColumns(area.width(), TabletMeetingPainter.VOTE_MIN_W,
                TabletSectionPainter.GRID_GAP), TabletMeetingPainter.VOTE_CARD_H, TabletMeetingPainter.VOTE_ROW_STEP);
    }

    /** Meeting footer: [Abstain][Confirm] right-aligned; the status line gets the rest. 会议操作栏：按钮右对齐，其余给状态行。 */
    private ActionBar actionBar(TabletSnapshot snapshot) {
        TabletLayout layout = screen.layout();
        TabletSnapshot.Meeting meeting = snapshot.meeting();
        Rect footer = layout.footer();
        int padX = layout.mode().padX();
        int height = Math.min(TabletMeetingPainter.ACTION_BUTTON_H, footer.height());
        int y = footer.centerY() - height / 2;
        boolean confirmed = meeting.localConfirmed();
        Text confirmLabel = Text.translatable(KEY + (confirmed ? "meeting.locked" : "meeting.confirm"));
        ButtonIcon confirmIcon = confirmed ? ButtonIcon.LOCK : ButtonIcon.NONE;
        ButtonStyle confirmStyle = confirmed ? ButtonStyle.LOCKED : ButtonStyle.PRIMARY;
        Text abstainLabel = Text.translatable(KEY + "meeting.abstain");
        ButtonStyle abstainStyle = meeting.localAbstained() ? ButtonStyle.WARNING_TOGGLED : ButtonStyle.SECONDARY;

        // Widths come from the widest label each button can ever show, so nothing shifts when it locks.
        // 宽度取各按钮可能显示的最宽文字，锁定时布局不会移动。
        int room = Math.max(0, footer.width() - 2 * padX - ACTION_GAP);
        int confirmWidth = Math.min(room / 2, Math.max(
                actionButtonWidth(Text.translatable(KEY + "meeting.confirm"), ButtonIcon.NONE),
                actionButtonWidth(Text.translatable(KEY + "meeting.locked"), ButtonIcon.LOCK)));
        int abstainWidth = Math.min(room / 2, actionButtonWidth(abstainLabel, ButtonIcon.NONE));
        int confirmX = footer.right() - padX - confirmWidth;
        int abstainX = confirmX - ACTION_GAP - abstainWidth;
        return new ActionBar(
                new Rect(abstainX, y, abstainWidth, height), abstainLabel, abstainStyle,
                new Rect(confirmX, y, confirmWidth, height), confirmLabel, confirmStyle, confirmIcon);
    }

    private int actionButtonWidth(Text label, ButtonIcon icon) {
        int iconWidth = icon == ButtonIcon.NONE ? 0 : ACTION_ICON + ACTION_ICON_GAP;
        return Math.max(ACTION_MIN_W, 2 * ACTION_PAD + iconWidth + screen.textRenderer().getWidth(label));
    }

    private static HeroState heroState(TabletSnapshot snapshot) {
        if (snapshot.cooldownSeconds() > 0) {
            return HeroState.COOLDOWN;
        }
        return canCallMeeting(snapshot) ? HeroState.READY : HeroState.DISABLED;
    }

    private static Text meetingStatus(TabletSnapshot snapshot) {
        TabletSnapshot.Meeting meeting = snapshot.meeting();
        if (!snapshot.localMeetingParticipant()) {
            return Text.translatable(KEY + "meeting.status.spectator");
        }
        if (meeting.localConfirmed()) {
            return Text.translatable(KEY + "meeting.status.locked");
        }
        if (meeting.localAbstained()) {
            return Text.translatable(KEY + "meeting.status.abstained");
        }
        UUID target = meeting.localVoteTarget();
        if (target != null) {
            String name = "?";
            for (TabletSnapshot.VoteTarget candidate : meeting.targets()) {
                if (candidate.uuid().equals(target)) {
                    name = candidate.name();
                    break;
                }
            }
            return Text.translatable(KEY + "meeting.status.voted", name);
        }
        return Text.translatable(KEY + "meeting.status.none");
    }

    /** Meeting and suspects exist only on a channel with meeting features (police). 会议与嫌疑分区只存在于带会议功能的频道（义警）。 */
    static boolean hasMeetingFeatures(TabletSnapshot snapshot) {
        TabletChannel channel = snapshot.channel();
        return channel != null && channel.hasMeetingFeatures();
    }

    private static boolean canCallMeeting(TabletSnapshot snapshot) {
        return snapshot.localMeetingParticipant()
                && snapshot.cooldownSeconds() <= 0
                && snapshot.localMeetingCallsRemaining() > 0;
    }

    private record ActionBar(
            Rect abstain,
            Text abstainLabel,
            ButtonStyle abstainStyle,
            Rect confirm,
            Text confirmLabel,
            ButtonStyle confirmStyle,
            ButtonIcon confirmIcon
    ) {
    }
}
