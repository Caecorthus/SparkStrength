package annina.sparkstrength.client.screen.tablet;

import annina.sparkstrength.client.screen.tablet.TabletScreen.Grid;
import annina.sparkstrength.client.screen.tablet.TabletSectionPainter.EmptyIcon;
import annina.sparkstrength.network.tablet.TabletSnapshot;
import annina.sparkstrength.role.attendant.DoorLog;
import annina.sparkstrength.tablet.TabletFeature;
import annina.sparkstrength.tablet.TabletLayout.Rect;
import annina.sparkstrength.tablet.TabletUiSession;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Objects;

import static annina.sparkstrength.client.screen.tablet.TabletSectionPainter.KEY;

/**
 * Attendant door monitor section: the door log, kept on the same entry for a scrolled-down reader when newer ones arrive.
 * 乘务员房门监控分区：房门记录；有更新条目到达时，已向下翻阅的读者保持停在同一条目。
 */
final class TabletDoorLogPage implements TabletPage {
    private final TabletScreen screen;
    // Door-log rows the scroll position refers to; anchors the reader when newer rows arrive. 门记录滚动位置所对应的行。
    private List<TabletSnapshot.DoorLogRow> lastDoorLogRows = List.of();

    TabletDoorLogPage(TabletScreen screen) {
        this.screen = screen;
    }

    @Override
    public void prepare(TabletSnapshot snapshot) {
        clampRows(snapshot);
        lastDoorLogRows = doorLogRows(snapshot);
    }

    @Override
    public void onSnapshot(TabletSnapshot snapshot, boolean laidOut) {
        if (laidOut) {
            anchorDoorLog(snapshot);
            clampRows(snapshot);
        }
    }

    private void clampRows(TabletSnapshot snapshot) {
        screen.session().applyDoorLogSnapshot(-1, doorLogRows(snapshot).size(), doorLogGrid().visibleRows());
    }

    /**
     * Door log is newest first: a reader scrolled down stays on the same entry when newer ones are prepended.
     * 房门记录最新在前：已向下翻阅的读者在顶部插入新条目时保持停在同一条目。
     */
    private void anchorDoorLog(TabletSnapshot snapshot) {
        TabletUiSession session = screen.session();
        List<TabletSnapshot.DoorLogRow> rows = doorLogRows(snapshot);
        int first = session.doorLogFirstRow();
        int anchor = -1;
        if (first > 0 && first < lastDoorLogRows.size()) {
            int anchorId = lastDoorLogRows.get(first).id();
            for (int index = 0; index < rows.size(); index++) {
                if (rows.get(index).id() == anchorId) {
                    anchor = index;
                    break;
                }
            }
        }
        session.applyDoorLogSnapshot(anchor, rows.size(), doorLogGrid().visibleRows());
        lastDoorLogRows = rows;
    }

    @Override
    public boolean mouseScrolled(TabletSnapshot snapshot, double x, double y, double verticalAmount) {
        Grid grid = doorLogGrid();
        if (grid.area().contains(x, y)) {
            // Painted only, like CONNECTIONS. 与成员分区相同，仅绘制。
            List<TabletSnapshot.DoorLogRow> rows = doorLogRows(snapshot);
            screen.session().scrollDoorLog(verticalAmount, rows.size(), grid.visibleRows());
            lastDoorLogRows = rows;
            return true;
        }
        return false;
    }

    @Override
    public int paintHeaderChip(TabletSnapshot snapshot, int right, int centerY, int maxWidth, long now) {
        // Live indicator in the monitor accent whatever the channel: the feed belongs to the door monitor.
        // 无论频道如何都使用监控强调色的实时指示：该画面属于房门监控。
        return screen.paintPill(right, centerY, maxWidth, Text.translatable(KEY + "door_log.live").getString(),
                TabletTheme.MONITOR.pale(), TabletTheme.withAlpha(TabletTheme.MONITOR.base(), 0x2E), 0,
                (canvas, x, y, size, color) -> screen.paintPulseDot(x + size / 2f, y + size / 2f, 3f, color, now),
                TabletTheme.MONITOR.base());
    }

    @Override
    public void paintBadge(TabletSnapshot snapshot, Rect icon, long now) {
        // New entries only ever surface as this red dot (no HUD). 新条目只以此红点提示（无 HUD）。
        if (TabletClientState.hasUnreadDoorLog(snapshot)) {
            screen.paintBadgeDot(icon, TabletTheme.DANGER);
        }
    }

    /** Newest first, painted only (no per-row widgets). 最新在前，仅绘制（每行无控件）。 */
    @Override
    public void paintBody(TabletSnapshot snapshot, int hoverX, int hoverY, long now) {
        TabletCanvas c = screen.canvas();
        TextRenderer textRenderer = screen.textRenderer();
        MinecraftClient client = screen.client();
        Grid grid = doorLogGrid();
        List<TabletSnapshot.DoorLogRow> rows = doorLogRows(snapshot);
        int first = screen.session().doorLogFirstRow();
        // Only seeing the newest row clears the rail dot: entries that arrive while the reader is scrolled down (the
        // anchor keeps them off-screen) stay unread.
        // 只有看到最新一行才清除侧栏红点：阅读者向下滚动时到达的新记录（锚定使其不在屏幕内）保持未读。
        if (first == 0) {
            TabletClientState.markDoorLogSeen(snapshot);
        }
        if (rows.isEmpty()) {
            TabletSectionPainter.emptyState(c, textRenderer, grid.area(), EmptyIcon.DOOR,
                    Text.translatable(KEY + "door_log.empty"), Text.translatable(KEY + "door_log.empty_hint"));
            return;
        }
        int ageColumn = TabletDoorLogPainter.doorAgeColumn(textRenderer);
        // Ages come from the synced world clock, so the server never resends the log just because it got older.
        // 经过时间取自同步的世界时钟，服务端不会仅因记录变旧而重发。
        long worldTime = client.world == null ? 0L : client.world.getTime();
        for (int row = 0; row < grid.visibleRows() && first + row < rows.size(); row++) {
            TabletSnapshot.DoorLogRow entry = rows.get(first + row);
            TabletDoorLogPainter.doorLogRow(c, textRenderer, grid.area().x(), grid.rowY(row), grid.area().width(), entry,
                    Objects.requireNonNull(entry.kind()),
                    TabletDoorLogPainter.doorAge(DoorLog.ageSeconds(entry.lastTick(), worldTime)), ageColumn);
        }
        TabletSectionPainter.scrollThumb(c, screen.scrollTrack(grid.area()), first, grid.visibleRows(), rows.size());
    }

    private Grid doorLogGrid() {
        return Grid.of(screen.layout().bodyNoFooter(), 1, TabletDoorLogPainter.DOOR_ROW_H,
                TabletDoorLogPainter.DOOR_ROW_STEP);
    }

    /**
     * Door-log rows this client can show, newest first; empty without the feature. Rows of a kind this client does
     * not know (newer server) are skipped.
     * 本客户端可显示的房门记录行（最新在前）；无此功能时为空。跳过本客户端不认识的类型（更新的服务端）。
     */
    private static List<TabletSnapshot.DoorLogRow> doorLogRows(TabletSnapshot snapshot) {
        if (!snapshot.hasFeature(TabletFeature.DOOR_LOG) || snapshot.doorLog().isEmpty()) {
            return List.of();
        }
        List<TabletSnapshot.DoorLogRow> rows = snapshot.doorLog();
        for (TabletSnapshot.DoorLogRow row : rows) {
            if (row.kind() == null) {
                return rows.stream().filter(candidate -> candidate.kind() != null).toList();
            }
        }
        return rows;
    }
}
