package annina.sparkstrength.client.screen.tablet;

import annina.sparkstrength.client.screen.tablet.TabletScreen.Grid;
import annina.sparkstrength.client.screen.tablet.TabletSectionPainter.ButtonIcon;
import annina.sparkstrength.client.screen.tablet.TabletSectionPainter.ButtonStyle;
import annina.sparkstrength.client.screen.tablet.TabletSectionPainter.EmptyIcon;
import annina.sparkstrength.network.tablet.ApproveSuspectRemovalC2SPacket;
import annina.sparkstrength.network.tablet.TabletSnapshot;
import annina.sparkstrength.tablet.TabletLayout.Rect;
import annina.sparkstrength.tablet.TabletUiSession;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;

import java.util.List;

import static annina.sparkstrength.client.screen.tablet.TabletMeetingPage.hasMeetingFeatures;
import static annina.sparkstrength.client.screen.tablet.TabletSectionPainter.KEY;

/**
 * Police suspects section: one card per suspect with an approve/cancel removal toggle. Rebuilt on every snapshot
 * (approvals change); the server re-validates every toggle.
 * 义警嫌疑分区：每名嫌疑人一张卡片，带建议/取消移除切换按钮。每次快照都会重建（赞成数变化）；服务端会重新校验每次切换。
 */
final class TabletSuspectsPage implements TabletPage {
    private final TabletScreen screen;

    TabletSuspectsPage(TabletScreen screen) {
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
        Grid suspects = suspectGrid();
        screen.session().applySuspectSnapshot(suspects.totalRows(snapshot.suspects().size()), suspects.visibleRows());
    }

    @Override
    public boolean needsRebuild(TabletSnapshot snapshot) {
        return true;
    }

    @Override
    public void init(TabletSnapshot snapshot) {
        if (hasMeetingFeatures(snapshot)) {
            initSuspects(snapshot);
        }
    }

    private void initSuspects(TabletSnapshot snapshot) {
        TextRenderer textRenderer = screen.textRenderer();
        Grid grid = suspectGrid();
        List<TabletSnapshot.SuspectRow> suspects = snapshot.suspects();
        int first = screen.session().suspectFirstRow();
        for (int row = 0; row < grid.visibleRows() && first + row < suspects.size(); row++) {
            TabletSnapshot.SuspectRow suspect = suspects.get(first + row);
            Rect toggle = TabletSuspectsPainter.suspectButtonRect(textRenderer, grid.area().x(), grid.rowY(row),
                    grid.area().width());
            screen.addButton("suspect:" + suspect.uuid(), toggle,
                    Text.translatable(KEY + (suspect.localApproved() ? "suspects.cancel" : "suspects.approve")),
                    suspect.localApproved() ? ButtonStyle.WARNING_TOGGLED : ButtonStyle.SECONDARY,
                    ButtonIcon.NONE,
                    () -> ClientPlayNetworking.send(new ApproveSuspectRemovalC2SPacket(suspect.uuid(), !suspect.localApproved())))
                    .active = snapshot.localMeetingParticipant();
        }
    }

    @Override
    public boolean mouseScrolled(TabletSnapshot snapshot, double x, double y, double verticalAmount) {
        TabletUiSession session = screen.session();
        Grid grid = suspectGrid();
        if (hasMeetingFeatures(snapshot) && grid.area().contains(x, y)) {
            int previous = session.suspectFirstRow();
            session.scrollSuspects(verticalAmount, grid.totalRows(snapshot.suspects().size()), grid.visibleRows());
            if (session.suspectFirstRow() != previous) {
                screen.refresh();
            }
            return true;
        }
        return false;
    }

    @Override
    public int paintHeaderChip(TabletSnapshot snapshot, int right, int centerY, int maxWidth, long now) {
        return screen.paintPill(right, centerY, maxWidth, String.valueOf(snapshot.suspects().size()),
                TabletTheme.TEXT_2, TabletTheme.SURFACE, 0, TabletIcons::target, TabletTheme.TEXT_2);
    }

    @Override
    public void paintBadge(TabletSnapshot snapshot, Rect icon, long now) {
        TabletCanvas c = screen.canvas();
        TextRenderer textRenderer = screen.textRenderer();
        int count = snapshot.suspects().size();
        if (count > 0) {
            String text = count > 99 ? "99+" : String.valueOf(count);
            int width = Math.max(11, textRenderer.getWidth(text) + 4);
            float left = icon.right() - 4;
            float top = icon.y() - 4;
            c.roundRect(left, top, width, 10f, 5f, TabletTheme.WARNING);
            c.text(textRenderer, text, Math.round(left + (width - textRenderer.getWidth(text) + 1) / 2f),
                    Math.round(top + 2), TabletTheme.WARNING_INK);
        }
    }

    @Override
    public void paintBody(TabletSnapshot snapshot, int hoverX, int hoverY, long now) {
        if (hasMeetingFeatures(snapshot)) {
            paintSuspects(snapshot);
        }
    }

    private void paintSuspects(TabletSnapshot snapshot) {
        TabletCanvas c = screen.canvas();
        TextRenderer textRenderer = screen.textRenderer();
        Grid grid = suspectGrid();
        List<TabletSnapshot.SuspectRow> suspects = snapshot.suspects();
        if (suspects.isEmpty()) {
            TabletSectionPainter.emptyState(c, textRenderer, grid.area(), EmptyIcon.SHIELD,
                    Text.translatable(KEY + "suspects.none"));
            return;
        }
        int first = screen.session().suspectFirstRow();
        for (int row = 0; row < grid.visibleRows() && first + row < suspects.size(); row++) {
            TabletSuspectsPainter.suspectCard(c, textRenderer, grid.area().x(), grid.rowY(row), grid.area().width(),
                    suspects.get(first + row), screen.accent());
        }
        TabletSectionPainter.scrollThumb(c, screen.scrollTrack(grid.area()), first, grid.visibleRows(), suspects.size());
    }

    private Grid suspectGrid() {
        return Grid.of(screen.layout().bodyNoFooter(), 1, TabletSuspectsPainter.SUSPECT_CARD_H,
                TabletSuspectsPainter.SUSPECT_ROW_STEP);
    }
}
