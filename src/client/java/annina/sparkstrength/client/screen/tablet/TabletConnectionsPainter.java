package annina.sparkstrength.client.screen.tablet;

import annina.sparkstrength.network.tablet.TabletSnapshot;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;

import java.util.List;

import static annina.sparkstrength.client.screen.tablet.TabletSectionPainter.CARD_R;
import static annina.sparkstrength.client.screen.tablet.TabletSectionPainter.KEY;
import static annina.sparkstrength.client.screen.tablet.TabletSectionPainter.LINE_STEP;
import static annina.sparkstrength.client.screen.tablet.TabletSectionPainter.MIN_NAME_W;
import static annina.sparkstrength.client.screen.tablet.TabletSectionPainter.PILL_PAD;
import static annina.sparkstrength.client.screen.tablet.TabletSectionPainter.visibleWidth;
import static annina.sparkstrength.client.screen.tablet.TabletSectionPainter.wrap;

/**
 * Stateless painters for the connections section: member cards and the link / task-unlock hint callouts.
 * 成员分区的无状态绘制器：成员卡片与配对 / 任务解锁提示卡片。
 */
final class TabletConnectionsPainter {
    static final int MEMBER_MIN_W = 190;
    static final int MEMBER_CARD_H = 30;
    static final int MEMBER_ROW_STEP = 38;

    private static final int MEMBER_AVATAR = 20;
    private static final int MEMBER_TEXT_X = 34;

    private static final int HINT_PAD_X = 10;
    private static final int HINT_PAD_Y = 11;
    private static final int HINT_ICON = 14;
    private static final int HINT_TEXT_X = HINT_PAD_X + HINT_ICON + 8;
    private static final int HINT_MAX_LINES = 4;

    private TabletConnectionsPainter() {
    }

    static void memberCard(TabletCanvas c, TextRenderer tr, int x, int y, int w, TabletSnapshot.PlayerRow row,
                           boolean self, TabletTheme.Accent a) {
        int h = MEMBER_CARD_H;
        int centerY = y + h / 2;
        int textY = centerY - 4;
        c.roundRect(x, y, w, h, CARD_R, TabletTheme.SURFACE);
        int avatarY = centerY - MEMBER_AVATAR / 2;
        c.avatar(row.uuid(), row.name(), x + 6, avatarY, MEMBER_AVATAR, 5f, TabletTheme.SURFACE);

        int nameX = x + MEMBER_TEXT_X;
        int right = x + w - 8;
        int room = right - nameX;
        if (room <= 0) {
            return;
        }

        // Status pill on the right; it may take everything except a minimal name slot.
        // 右侧状态胶囊：除保留最小名字宽度外可占用其余空间。
        boolean inGame = row.inGame();
        String status = Text.translatable(KEY + (inGame ? "status.ingame" : "status.outside")).getString();
        int dotSpace = inGame ? 9 : 0;
        int pillMax = Math.max(0, room - MIN_NAME_W - 8);
        String statusText = TabletCanvas.trim(tr, status, pillMax - 2 * PILL_PAD - dotSpace);
        int pillW = statusText.isEmpty()
                ? (inGame && pillMax >= 15 ? 15 : 0)
                : 2 * PILL_PAD + dotSpace + visibleWidth(tr, statusText);
        if (pillW > 0) {
            int pillH = 14;
            int pillX = right - pillW;
            int pillY = centerY - pillH / 2;
            c.roundRect(pillX, pillY, pillW, pillH, pillH / 2f,
                    inGame ? TabletTheme.withAlpha(TabletTheme.SUCCESS, 0x2E) : TabletTheme.FAINT);
            if (inGame) {
                float dotCx = statusText.isEmpty() ? pillX + pillW / 2f : pillX + PILL_PAD + 2.5f;
                c.circle(dotCx, pillY + pillH / 2f, 2.5f, TabletTheme.SUCCESS);
            }
            if (!statusText.isEmpty()) {
                c.text(tr, statusText, pillX + PILL_PAD + dotSpace, textY,
                        inGame ? TabletTheme.SUCCESS : TabletTheme.TEXT_3);
            }
        }

        int nameRoom = right - (pillW > 0 ? pillW + 8 : 0) - nameX;
        String tag = self ? Text.translatable(KEY + "connections.you").getString() : "";
        int tagW = tag.isEmpty() ? 0 : visibleWidth(tr, tag) + 10;
        boolean showTag = self && tagW > 10 && nameRoom - tagW - 5 >= MIN_NAME_W;
        String name = TabletCanvas.trim(tr, row.name(), nameRoom - (showTag ? tagW + 5 : 0));
        int nameW = c.text(tr, name, nameX, textY, TabletTheme.TEXT);
        if (showTag) {
            int tagX = nameX + nameW + 5;
            int tagH = 12;
            c.roundRect(tagX, centerY - tagH / 2, tagW, tagH, tagH / 2f, TabletTheme.withAlpha(a.base(), 0x40));
            c.text(tr, tag, tagX + 5, textY, a.pale());
        }
    }

    /**
     * Anonymous-channel link hint callout inside [x, y, w, maxHeight]; returns the height used (0 = nothing fit).
     * 匿名频道的配对提示卡片，绘制在 [x, y, w, maxHeight] 内；返回使用的高度（0 表示放不下，未绘制）。
     */
    static int linkHint(TabletCanvas c, TextRenderer tr, int x, int y, int w, int maxHeight, TabletTheme.Accent a) {
        return hintCallout(c, tr, x, y, w, maxHeight, a, false,
                Text.translatable(KEY + "connections.link_hint").getString());
    }

    /**
     * Police task-unlock callout (lock icon, {@code done}/{@code required} progress in the text), same box as
     * {@link #linkHint}; returns the height used (0 = nothing fit).
     * 义警任务解锁提示卡片（锁图标，文字内含进度），外观与 linkHint 相同；返回使用的高度（0 表示放不下）。
     */
    static int taskHint(TabletCanvas c, TextRenderer tr, int x, int y, int w, int maxHeight, TabletTheme.Accent a,
                        int done, int required) {
        return hintCallout(c, tr, x, y, w, maxHeight, a, true,
                Text.translatable(KEY + "connections.task_hint", done, required).getString());
    }

    private static int hintCallout(TabletCanvas c, TextRenderer tr, int x, int y, int w, int maxHeight,
                                   TabletTheme.Accent a, boolean lockIcon, String text) {
        int textWidth = w - HINT_TEXT_X - HINT_PAD_X;
        int maxLines = Math.min(HINT_MAX_LINES, (maxHeight - 2 * HINT_PAD_Y + 2) / LINE_STEP);
        if (textWidth < 40 || maxLines < 1) {
            return 0;
        }
        List<String> lines = wrap(tr, text, textWidth, maxLines);
        if (lines.isEmpty()) {
            return 0;
        }
        int textHeight = lines.size() * LINE_STEP - 2;
        int h = 2 * HINT_PAD_Y + textHeight;
        c.roundRect(x, y, w, h, CARD_R, TabletTheme.withAlpha(a.base(), 0x14));
        c.roundRectOutline(x, y, w, h, CARD_R, 1f, TabletTheme.withAlpha(a.base(), 0x40));
        // Icon centred on the first text line. 图标与首行文字垂直居中对齐。
        float iconY = y + HINT_PAD_Y + 4 - HINT_ICON / 2f;
        if (lockIcon) {
            TabletIcons.lock(c, x + HINT_PAD_X, iconY, HINT_ICON, a.pale());
        } else {
            TabletIcons.link(c, x + HINT_PAD_X, iconY, HINT_ICON, a.pale());
        }
        for (int i = 0; i < lines.size(); i++) {
            c.text(tr, lines.get(i), x + HINT_TEXT_X, y + HINT_PAD_Y + i * LINE_STEP, TabletTheme.TEXT_2);
        }
        return h;
    }
}
