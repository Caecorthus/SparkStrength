package annina.sparkstrength.client.screen.tablet;

import annina.sparkstrength.network.tablet.TabletSnapshot;
import annina.sparkstrength.tablet.TabletLayout;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;

import static annina.sparkstrength.client.screen.tablet.TabletSectionPainter.CARD_R;
import static annina.sparkstrength.client.screen.tablet.TabletSectionPainter.KEY;

/**
 * Stateless painters for the suspects section: one card per suspect; its approve/cancel toggle is a widget placed by
 * TabletSuspectsPage at {@link #suspectButtonRect}.
 * 嫌疑分区的无状态绘制器：每名嫌疑人一张卡片；其建议/取消切换按钮是 TabletSuspectsPage 按 suspectButtonRect 放置的控件。
 */
final class TabletSuspectsPainter {
    static final int SUSPECT_CARD_H = 40;
    static final int SUSPECT_ROW_STEP = 46;

    private static final int SUSPECT_AVATAR = 24;
    private static final int SUSPECT_TEXT_X = 49;
    private static final int SUSPECT_BAR_W = 120;
    private static final int SUSPECT_BAR_H = 4;
    private static final int SUSPECT_BUTTON_MIN_W = 92;
    private static final int SUSPECT_BUTTON_H = 24;
    private static final int SUSPECT_BUTTON_INSET = 8;
    private static final int SUSPECT_BUTTON_TEXT_PAD = 28;

    private TabletSuspectsPainter() {
    }

    /**
     * Suspect card; everything right of {@link #suspectButtonRect} minus 10 px stays free for the toggle widget.
     * 按钮区域左侧 10 px 以右留给切换控件。
     */
    static void suspectCard(TabletCanvas c, TextRenderer tr, int x, int y, int w, TabletSnapshot.SuspectRow row,
                            TabletTheme.Accent a) {
        int h = SUSPECT_CARD_H;
        int centerY = y + h / 2;
        c.roundRect(x, y, w, h, CARD_R, TabletTheme.SURFACE);
        c.roundRect(x + 6, centerY - 9, 3, 18, 1.5f, TabletTheme.WARNING);

        int avatarX = x + 15;
        int avatarY = centerY - SUSPECT_AVATAR / 2;
        c.avatar(row.uuid(), row.name(), avatarX, avatarY, SUSPECT_AVATAR, 6f, TabletTheme.SURFACE);
        c.roundRectOutline(avatarX - 1, avatarY - 1, SUSPECT_AVATAR + 2, SUSPECT_AVATAR + 2, 7f, 1f,
                TabletTheme.withAlpha(TabletTheme.WARNING, 0x99));

        int textX = x + SUSPECT_TEXT_X;
        int contentRight = suspectButtonRect(tr, x, y, w).x() - 10;
        int room = contentRight - textX;
        if (room <= 0) {
            return;
        }
        // Two lines centred as a block: name (8) + gap 8 + bar/votes line (8). 两行整体居中：名字、间距、进度行。
        int nameY = centerY - 11;
        int lineY = centerY + 3;
        c.text(tr, TabletCanvas.trim(tr, row.name(), room), textX, nameY, TabletTheme.TEXT);

        int required = Math.max(0, row.requiredApprovals());
        int approvals = Math.max(0, row.approvals());
        String full = Text.translatable(KEY + "suspects.votes", approvals, required).getString();
        String compact = approvals + "/" + required;
        String label;
        int barW;
        int fullW = tr.getWidth(full);
        if (room >= 60 + 6 + fullW) {
            label = full;
            barW = Math.min(SUSPECT_BAR_W, room - 6 - fullW);
        } else {
            // Narrow card: keep the bar, fall back to "a/b". 卡片较窄：保留进度条，文字退化为“a/b”。
            label = compact;
            barW = Math.min(SUSPECT_BAR_W, room - 6 - tr.getWidth(compact));
            if (barW < 16) {
                barW = 0;
                label = TabletCanvas.trim(tr, compact, room);
            }
        }
        if (barW > 0) {
            float fraction = required <= 0 ? 0f : Math.max(0f, Math.min(1f, approvals / (float) required));
            float barY = lineY + 4 - SUSPECT_BAR_H / 2f;
            c.roundRect(textX, barY, barW, SUSPECT_BAR_H, SUSPECT_BAR_H / 2f, TabletTheme.FAINT);
            if (fraction > 0f) {
                c.roundRect(textX, barY, Math.max(SUSPECT_BAR_H, barW * fraction), SUSPECT_BAR_H,
                        SUSPECT_BAR_H / 2f, TabletTheme.WARNING);
            }
        }
        c.text(tr, label, textX + (barW > 0 ? barW + 6 : 0), lineY, TabletTheme.TEXT_3);
    }

    /**
     * Toggle button rect inside a suspect card (right-aligned, vertically centred). The width fits the wider of the
     * approve/cancel labels (clamped to [92, card width / 3]) and is identical for every row, so buttons line up and
     * never change size when toggled. Painter and screen must both call this so paint and hit box stay identical.
     * 嫌疑卡片内的切换按钮区域（右对齐、垂直居中）。宽度取“建议/取消”两种文字中较宽者（限制在 [92, 卡片宽/3]），
     * 每行相同，按钮对齐且切换时不变宽。绘制器与界面都必须调用此方法，使绘制与点击区域一致。
     */
    static TabletLayout.Rect suspectButtonRect(TextRenderer tr, int x, int y, int w) {
        int labelW = Math.max(tr.getWidth(Text.translatable(KEY + "suspects.approve")),
                tr.getWidth(Text.translatable(KEY + "suspects.cancel")));
        int bw = Math.max(SUSPECT_BUTTON_MIN_W, labelW + SUSPECT_BUTTON_TEXT_PAD);
        // Upper clamp never drops below the minimum (Math.clamp would throw when w / 3 < 92).
        // 上限不低于最小宽度（w / 3 < 92 时 Math.clamp 会抛异常）。
        bw = Math.min(bw, Math.max(SUSPECT_BUTTON_MIN_W, w / 3));
        bw = Math.min(bw, Math.max(0, w - 2 * SUSPECT_BUTTON_INSET));
        return new TabletLayout.Rect(x + w - SUSPECT_BUTTON_INSET - bw, y + (SUSPECT_CARD_H - SUSPECT_BUTTON_H) / 2,
                bw, SUSPECT_BUTTON_H);
    }
}
