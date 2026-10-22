package annina.sparkstrength.client.screen.tablet;

import annina.sparkstrength.client.screen.tablet.TabletScreen.Grid;
import annina.sparkstrength.client.screen.tablet.TabletSectionPainter.EmptyIcon;
import annina.sparkstrength.network.tablet.TabletSnapshot;
import annina.sparkstrength.tablet.TabletChannel;
import annina.sparkstrength.tablet.TabletIdentityRules;
import annina.sparkstrength.tablet.TabletLayout;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;

import java.util.List;
import java.util.UUID;

import static annina.sparkstrength.client.screen.tablet.TabletSectionPainter.KEY;

/**
 * Connections section: the viewed channel's members as a card grid, painted only (no widgets).
 * 成员分区：当前频道的成员卡片网格，仅绘制（无控件）。
 */
final class TabletConnectionsPage implements TabletPage {
    // Room kept below the member rows for a hint callout (two text lines). 成员行下方为提示卡片保留的高度（两行文字）。
    private static final int HINT_CALLOUT_MIN_H = 42;

    private final TabletScreen screen;

    TabletConnectionsPage(TabletScreen screen) {
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

    /** Row scrolls are in grid rows (ceil(n / columns)). 以网格行计。 */
    private void clampRows(TabletSnapshot snapshot) {
        Grid members = connectionsGrid();
        screen.session().clampConnections(members.totalRows(snapshot.connections().size()), members.visibleRows());
    }

    @Override
    public boolean mouseScrolled(TabletSnapshot snapshot, double x, double y, double verticalAmount) {
        Grid grid = connectionsGrid();
        if (grid.area().contains(x, y)) {
            // Painted only, so no rebuild is needed. 仅绘制，无需重建控件。
            screen.session().scrollConnections(verticalAmount, grid.totalRows(snapshot.connections().size()), grid.visibleRows());
            return true;
        }
        return false;
    }

    @Override
    public int paintHeaderChip(TabletSnapshot snapshot, int right, int centerY, int maxWidth, long now) {
        TabletTheme.Accent frameAccent = screen.accent();
        // Locked police view: a lock + task progress instead of a misleading "1 connected".
        // 义警身份未解锁：显示锁与任务进度，而非具有误导性的“已接入 1”。
        return identitiesLocked(snapshot)
                ? screen.paintPill(right, centerY, maxWidth, Text.translatable(KEY + "channel.tasks_locked",
                        identityTasksDone(snapshot), TabletIdentityRules.POLICE_REVEAL_TASKS).getString(),
                frameAccent.pale(), TabletTheme.withAlpha(frameAccent.base(), 0x2E), 0, TabletIcons::lock,
                frameAccent.pale())
                : screen.paintPill(right, centerY, maxWidth, memberCount(snapshot).getString(),
                TabletTheme.TEXT_2, TabletTheme.SURFACE, 0, null, 0);
    }

    @Override
    public void paintBody(TabletSnapshot snapshot, int hoverX, int hoverY, long now) {
        TabletCanvas c = screen.canvas();
        TextRenderer textRenderer = screen.textRenderer();
        TabletTheme.Accent frameAccent = screen.accent();
        Grid grid = connectionsGrid();
        List<TabletSnapshot.PlayerRow> rows = snapshot.connections();
        if (rows.isEmpty()) {
            TabletSectionPainter.emptyState(c, textRenderer, grid.area(), EmptyIcon.MEMBERS,
                    Text.translatable(KEY + "connections.empty"));
            return;
        }
        int totalRows = grid.totalRows(rows.size());
        int first = screen.session().connectionsFirstRow();
        int shownRows = Math.min(grid.visibleRows(), totalRows - first);
        // A locked police viewer was sent only their own row (dead viewers too, so they still learn why); an unlinked
        // live killer was sent only theirs. 未解锁的义警查看者只收到自己的行（死亡者也显示，以便知道原因）；未互认的存活杀手同理。
        boolean taskHint = identitiesLocked(snapshot);
        boolean linkHint = !taskHint && snapshot.canSend() && isAnonymousChannel(snapshot) && linkedPartners(snapshot) == 0;
        if (taskHint || linkHint) {
            // Pin the callout to the bottom by giving up the last row(s) when it would not fit after them.
            // 若放在最后一行之后放不下，就让出末尾的行，把提示卡片固定在列表底部。
            while (shownRows > 0 && grid.area().bottom() - grid.rowY(shownRows) < HINT_CALLOUT_MIN_H) {
                shownRows--;
            }
        }

        UUID self = screen.localUuid();
        for (int row = 0; row < shownRows; row++) {
            for (int column = 0; column < grid.columns(); column++) {
                int index = (first + row) * grid.columns() + column;
                if (index >= rows.size()) {
                    break;
                }
                TabletSnapshot.PlayerRow player = rows.get(index);
                TabletConnectionsPainter.memberCard(c, textRenderer, grid.cellX(column), grid.rowY(row), grid.cellWidth(),
                        player, player.uuid().equals(self), frameAccent);
            }
        }
        if (taskHint) {
            int top = grid.rowY(shownRows);
            TabletConnectionsPainter.taskHint(c, textRenderer, grid.area().x(), top, grid.area().width(),
                    grid.area().bottom() - top, frameAccent, identityTasksDone(snapshot),
                    TabletIdentityRules.POLICE_REVEAL_TASKS);
        } else if (linkHint) {
            int top = grid.rowY(shownRows);
            TabletConnectionsPainter.linkHint(c, textRenderer, grid.area().x(), top, grid.area().width(),
                    grid.area().bottom() - top, frameAccent);
        }
        TabletSectionPainter.scrollThumb(c, screen.scrollTrack(grid.area()), first, grid.visibleRows(), totalRows);
    }

    private Grid connectionsGrid() {
        TabletLayout layout = screen.layout();
        return Grid.of(layout.bodyNoFooter(), TabletLayout.gridColumns(layout.bodyNoFooter().width(),
                        TabletConnectionsPainter.MEMBER_MIN_W, TabletSectionPainter.GRID_GAP),
                TabletConnectionsPainter.MEMBER_CARD_H, TabletConnectionsPainter.MEMBER_ROW_STEP);
    }

    /**
     * Header/chip member count. Anonymous channels list only the viewer plus linked partners (server-filtered), so they
     * count partners rather than channel size. Members may include dead teammates, hence "connected", not "online".
     * 成员计数。匿名频道只列出观看者本人与已互认同伴（服务端过滤），因此计数显示互认人数而非频道人数。
     * 成员可能包含死亡队友，因此显示为“已接入”而非“在线”。
     */
    private static Text memberCount(TabletSnapshot snapshot) {
        if (isAnonymousChannel(snapshot)) {
            return Text.translatable(KEY + "channel.linked", linkedPartners(snapshot));
        }
        return Text.translatable(KEY + "channel.connected", snapshot.connections().size());
    }

    /**
     * The viewed channel hides other members until this viewer completes their tasks (police); the server then sent
     * only the viewer's own member row and sender-less chat rows for everyone else.
     * 当前频道在查看者完成任务前隐藏其他成员（义警）；此时服务端只下发查看者自己的成员行，其他人的聊天行不带发送者。
     */
    private static boolean identitiesLocked(TabletSnapshot snapshot) {
        TabletChannel channel = snapshot.channel();
        return channel != null && channel.revealsAfterTasks() && snapshot.identityTasksRemaining() > 0;
    }

    private static int identityTasksDone(TabletSnapshot snapshot) {
        return Math.max(0, TabletIdentityRules.POLICE_REVEAL_TASKS - snapshot.identityTasksRemaining());
    }

    private static boolean isAnonymousChannel(TabletSnapshot snapshot) {
        TabletChannel channel = snapshot.channel();
        return channel != null && channel.anonymousSenders();
    }

    private static int linkedPartners(TabletSnapshot snapshot) {
        // The viewer is always a member of their own selected channel. 观看者总是其所选频道的成员。
        return Math.max(0, snapshot.connections().size() - 1);
    }
}
