package annina.sparkstrength.client.screen.tablet;

import annina.sparkstrength.component.sabotage.SabotageWorldComponent;
import annina.sparkstrength.network.tablet.TabletSnapshot;
import annina.sparkstrength.sabotage.OutageMode;
import annina.sparkstrength.sabotage.SabotageRowState;
import annina.sparkstrength.sabotage.SabotageType;
import annina.sparkstrength.tablet.TabletLayout;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;

/**
 * Paints the tablet's sabotage page: one card per sabotage type (its trigger button is a widget, see TabletScreen)
 * and the team log below. Countdowns are extrapolated from the last snapshot so they tick every frame.
 * 绘制平板破坏页：每种破坏一张卡片（触发按钮是控件，见 TabletScreen），下方为团队日志。倒计时根据上一份快照外推，每帧走动。
 */
final class TabletSabotagePainter {
    static final int CARD_H = 64;
    static final int CARD_STEP = 70;
    static final int LOG_ROW_H = 18;
    static final int LOG_HEADER_H = 18;
    private static final String KEY = TabletSectionPainter.KEY + "sabotage.";
    private static final int CARD_R = 8;
    private static final int TILE = 40;
    private static final int TILE_INSET = 12;
    private static final int GLYPH = 22;
    private static final int TEXT_GAP = 12;
    private static final int BUTTON_MIN_W = 78;
    private static final int BUTTON_H = 24;
    private static final int BUTTON_INSET = 12;

    private TabletSabotagePainter() {
    }

    static SabotageRowState state(TabletSnapshot.SabotageRow row) {
        return SabotageRowState.byWire(row.stateWire());
    }

    /** Seconds shown now: remaining counts down and elapsed counts up since the snapshot. / 当前显示的秒数：剩余向下、已持续向上计。 */
    static int liveSeconds(TabletSnapshot.SabotageRow row, int sinceSnapshot) {
        return switch (state(row)) {
            case ACTIVE -> row.seconds() + sinceSnapshot;
            case ARMING, COOLDOWN -> Math.max(0, row.seconds() - sinceSnapshot);
            default -> 0;
        };
    }

    static String clock(int seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    static int tone(SabotageRowState state) {
        return switch (state) {
            case READY -> TabletTheme.SABOTAGE.base();
            case ACTIVE -> TabletTheme.DANGER;
            case ARMING -> TabletTheme.WARNING;
            default -> TabletTheme.TEXT_3;
        };
    }

    static TabletLayout.Rect buttonRect(TextRenderer tr, int x, int y, int w) {
        int labelW = 0;
        for (String key : new String[]{"button.trigger", "button.locked", "button.arming", "button.cooldown",
                "button.active", "button.blocked"}) {
            labelW = Math.max(labelW, tr.getWidth(Text.translatable(KEY + key)) + 18);
        }
        int bw = Math.max(BUTTON_MIN_W, labelW + 20);
        bw = Math.min(bw, Math.max(BUTTON_MIN_W, w / 3));
        bw = Math.min(bw, Math.max(0, w - 2 * BUTTON_INSET));
        return new TabletLayout.Rect(x + w - BUTTON_INSET - bw, y + (CARD_H - BUTTON_H) / 2, bw, BUTTON_H);
    }

    static void card(TabletCanvas c, TextRenderer tr, int x, int y, int w, TabletSnapshot.SabotageRow row,
                     int sinceSnapshot, long now) {
        if (w <= 0) {
            return;
        }
        SabotageRowState state = state(row);
        SabotageType type = SabotageType.byWire(row.typeWire());
        int tone = tone(state);
        int fill = switch (state) {
            case READY -> TabletTheme.mix(TabletTheme.SURFACE, TabletTheme.SABOTAGE.base(), 0.08f);
            case ACTIVE -> TabletTheme.mix(TabletTheme.SURFACE, TabletTheme.DANGER, 0.08f);
            default -> TabletTheme.SURFACE;
        };
        c.roundRect(x, y, w, CARD_H, CARD_R, fill);
        if (state == SabotageRowState.READY || state == SabotageRowState.ACTIVE) {
            c.roundRectOutline(x, y, w, CARD_H, CARD_R, 1f, TabletTheme.withAlpha(tone, 0x55));
        }
        int centerY = y + CARD_H / 2;
        int tileX = x + TILE_INSET;
        int tileY = centerY - TILE / 2;
        if (state == SabotageRowState.READY || state == SabotageRowState.ACTIVE) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(now / 420.0);
            c.glow(tileX + TILE / 2f, tileY + TILE / 2f, TILE * 0.75f,
                    TabletTheme.withAlpha(tone, (int) (0x22 + 0x22 * pulse)));
        }
        c.roundRect(tileX, tileY, TILE, TILE, 10f, TabletTheme.withAlpha(tone, 0x2E));
        float glyphOffset = (TILE - GLYPH) / 2f;
        if (state == SabotageRowState.LOCKED) {
            TabletIcons.lock(c, tileX + glyphOffset, tileY + glyphOffset, GLYPH, tone);
        } else {
            TabletIcons.bolt(c, tileX + glyphOffset, tileY + glyphOffset, GLYPH, tone);
        }

        TabletLayout.Rect button = buttonRect(tr, x, y, w);
        int textX = tileX + TILE + TEXT_GAP;
        int textW = Math.max(0, button.x() - 10 - textX);
        String title = type == null ? "?" : Text.translatable(KEY + "card." + type.id()).getString();
        c.text(tr, TabletCanvas.trim(tr, title, textW), textX, y + 13, TabletTheme.TEXT);
        int seconds = liveSeconds(row, sinceSnapshot);
        String status = switch (state) {
            case LOCKED -> Text.translatable(KEY + "status.locked",
                    type == null ? "" : Text.translatable(type.translationKey())).getString();
            case ARMING -> Text.translatable(KEY + "status.arming", clock(seconds)).getString();
            case READY -> Text.translatable(KEY + "status.ready").getString();
            case COOLDOWN -> Text.translatable(KEY + "status.cooldown", clock(seconds)).getString();
            case ACTIVE -> Text.translatable(OutageMode.byWire(row.modeWire()) == OutageMode.TIMED
                    ? KEY + "status.active_timed" : KEY + "status.active", clock(seconds)).getString();
            case BLOCKED -> Text.translatable(KEY + "status.blocked").getString();
        };
        c.text(tr, TabletCanvas.trim(tr, status, textW), textX, y + 27, state == SabotageRowState.LOCKED
                ? TabletTheme.TEXT_2 : tone);
        String stats = row.boxes() == 0
                ? Text.translatable(KEY + "stats.no_boxes").getString()
                : Text.translatable(KEY + "stats", row.boxes(), row.opened(), row.hacked()).getString();
        c.text(tr, TabletCanvas.trim(tr, stats, textW), textX, y + 41, TabletTheme.TEXT_3);
    }

    static void logHeader(TabletCanvas c, TextRenderer tr, int x, int y, int w) {
        c.text(tr, Text.translatable(KEY + "log.title").getString(), x + 2, y + 5, TabletTheme.TEXT_2);
        c.rect(x, y + LOG_HEADER_H - 2, w, Math.max(c.px(), 1f), TabletTheme.HAIRLINE);
    }

    static void logRow(TabletCanvas c, TextRenderer tr, int x, int y, int w, TabletSnapshot.SabotageLogRow row) {
        SabotageWorldComponent.LogKind kind = SabotageWorldComponent.LogKind.byWire(row.kindWire());
        SabotageType type = SabotageType.byWire(row.typeWire());
        if (kind == null || w <= 0) {
            return;
        }
        int textY = y + (LOG_ROW_H - 8) / 2;
        String time = clock(row.roundSeconds());
        int timeW = tr.getWidth("00:00") + 4;
        c.text(tr, time, x + 2, textY, TabletTheme.TEXT_3);
        int dotX = x + 2 + timeW + 4;
        int color = switch (kind) {
            case BOX_HACKED, READY -> TabletTheme.SABOTAGE.base();
            case TRIGGERED -> TabletTheme.DANGER;
            case REPAIRED, POWER_RESTORED, BOX_REPAIRED -> TabletTheme.SUCCESS;
            case BOX_BROKEN -> TabletTheme.WARNING;
            case EXPIRED -> TabletTheme.TEXT_2;
        };
        c.circle(dotX, y + LOG_ROW_H / 2f, 2.5f, color);
        Text typeName = type == null ? Text.literal("?") : Text.translatable(type.translationKey());
        String sentence = Text.translatable(KEY + "log." + kind.name().toLowerCase(java.util.Locale.ROOT),
                row.actor(), typeName).getString();
        int textX = dotX + 8;
        c.text(tr, TabletCanvas.trim(tr, sentence, Math.max(0, x + w - textX - 4)), textX, textY, TabletTheme.TEXT);
    }
}
