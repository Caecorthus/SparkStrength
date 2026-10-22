package annina.sparkstrength.client.screen.tablet;

import annina.sparkstrength.component.sabotage.SabotageWorldComponent;
import annina.sparkstrength.network.tablet.TabletSnapshot;
import annina.sparkstrength.sabotage.EnginePhase;
import annina.sparkstrength.sabotage.EngineRules;
import annina.sparkstrength.sabotage.OutageMode;
import annina.sparkstrength.sabotage.SabotageRowState;
import annina.sparkstrength.sabotage.SabotageType;
import annina.sparkstrength.tablet.TabletLayout;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * Paints the tablet's sabotage page: one card per sabotage type (its trigger button is a widget, see TabletSabotagePage)
 * and the team log below. Countdowns are extrapolated from the last snapshot so they tick every frame.
 * 绘制平板破坏页：每种破坏一张卡片（触发按钮是控件，见 TabletSabotagePage），下方为团队日志。倒计时根据上一份快照外推，每帧走动。
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
    /** The lock badge on a locked card's tile: radius, centre inset from the tile's bottom-right. / 锁定卡片图块上的锁徽标。 */
    private static final float LOCK_BADGE_R = 7f;
    private static final float LOCK_BADGE_INSET = 5f;
    private static final int LOCK_BADGE_GLYPH = 8;
    /** A log line's type glyph, in place of a plain dot. / 日志行的类型图形，代替原来的圆点。 */
    private static final int LOG_GLYPH = 9;

    private TabletSabotagePainter() {
    }

    static SabotageRowState state(TabletSnapshot.SabotageRow row) {
        return SabotageRowState.byWire(row.stateWire());
    }

    /**
     * The phase of an active engine card ({@code modeWire} carries it), null for any other card.
     * 进行中的动力卡片的阶段（由 modeWire 携带），其他卡片为 null。
     */
    static @Nullable EnginePhase enginePhase(TabletSnapshot.SabotageRow row) {
        return row.typeWire() == SabotageType.ENGINE.wire() && state(row) == SabotageRowState.ACTIVE
                ? EnginePhase.byWire(row.modeWire()) : null;
    }

    /**
     * Seconds shown now: remaining counts down and elapsed counts up since the snapshot; an engine card counts its
     * repair window down while CRITICAL and the halted time up afterwards.
     * 当前显示的秒数：剩余向下、已持续向上计；动力卡片在 CRITICAL 时抢修剩余时间向下数，之后已停驶时间向上数。
     */
    static int liveSeconds(TabletSnapshot.SabotageRow row, int sinceSnapshot) {
        return switch (state(row)) {
            case ACTIVE -> enginePhase(row) == EnginePhase.CRITICAL
                    ? Math.max(0, row.seconds() - sinceSnapshot) : row.seconds() + sinceSnapshot;
            case ARMING, COOLDOWN -> Math.max(0, row.seconds() - sinceSnapshot);
            default -> 0;
        };
    }

    /** Header chip text while a sabotage is on. / 破坏进行中时的顶栏胶囊文字。 */
    static String activeChip(TabletSnapshot.SabotageRow row, int sinceSnapshot) {
        EnginePhase phase = enginePhase(row);
        String key = phase == null ? "chip.active" : "chip.engine_" + phase.name().toLowerCase(java.util.Locale.ROOT);
        return Text.translatable(KEY + key, clock(liveSeconds(row, sinceSnapshot))).getString();
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
                "button.active", "button.blocked", "button.needs_mask"}) {
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
        // Every card shows its own type, so lights and engine tell apart at a glance; a locked one adds a lock badge.
        // 每张卡片都显示自己的类型图形，灯光和动力一眼就能分清；未解锁的再加一个锁形徽标。
        typeGlyph(c, type, tileX + glyphOffset, tileY + glyphOffset, GLYPH, gearTurn(enginePhase(row), now), tone);
        if (state == SabotageRowState.LOCKED) {
            float badgeX = tileX + TILE - LOCK_BADGE_INSET;
            float badgeY = tileY + TILE - LOCK_BADGE_INSET;
            c.circle(badgeX, badgeY, LOCK_BADGE_R, TabletTheme.LOCKED_BADGE);
            TabletIcons.lock(c, badgeX - LOCK_BADGE_GLYPH / 2f, badgeY - LOCK_BADGE_GLYPH / 2f, LOCK_BADGE_GLYPH,
                    TabletTheme.TEXT_2);
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
            case READY -> Text.translatable(KEY + (type == SabotageType.ENGINE ? "status.engine_ready" : "status.ready"))
                    .getString();
            case COOLDOWN -> Text.translatable(KEY + "status.cooldown", clock(seconds)).getString();
            case ACTIVE -> Text.translatable(activeStatusKey(row), clock(seconds)).getString();
            case BLOCKED -> Text.translatable(KEY + "status.blocked").getString();
        };
        int statusColor = switch (state) {
            case LOCKED -> TabletTheme.TEXT_2;
            case ACTIVE -> activeStatusColor(row, seconds);
            default -> tone;
        };
        c.text(tr, TabletCanvas.trim(tr, status, textW), textX, y + 27, statusColor);
        EnginePhase phase = enginePhase(row);
        if (phase != null && phase.boostsKillerEconomy()) {
            // Wrecked until repaired, emergency power included: the killers' bonus replaces the box counts.
            // 从损毁到修好（应急动力期间也算）：用杀手的经济加成代替接线箱统计。
            c.text(tr, TabletCanvas.trim(tr, Text.translatable(KEY + "status.engine_bonus").getString(), textW),
                    textX, y + 41, TabletTheme.SABOTAGE.pale());
            return;
        }
        String stats = row.boxes() == 0
                ? Text.translatable(KEY + "stats.no_boxes").getString()
                : Text.translatable(KEY + "stats", row.boxes(), row.opened(), row.hacked()).getString();
        c.text(tr, TabletCanvas.trim(tr, stats, textW), textX, y + 41, TabletTheme.TEXT_3);
    }

    /** Bolt for lights, gear for the engine (and for an unknown type, the bolt). / 灯光用闪电，动力用齿轮（未知类型用闪电）。 */
    static void typeGlyph(TabletCanvas c, @Nullable SabotageType type, float x, float y, float size, float turnDeg,
                          int color) {
        if (type == SabotageType.ENGINE) {
            TabletIcons.gear(c, x, y, size, turnDeg, color);
        } else {
            TabletIcons.bolt(c, x, y, size, color);
        }
    }

    /**
     * How far an engine card's gear is turned: it shudders while damaged, stands still while the train is halted and
     * turns slowly on emergency power. Still on every other card.
     * 动力卡片齿轮的转角：受损时抖动，停驶时静止，应急动力时缓慢转动。其他卡片都不转。
     */
    static float gearTurn(@Nullable EnginePhase phase, long nowMs) {
        if (phase == null) {
            return 0f;
        }
        return switch (phase) {
            case CRITICAL -> 4f * (float) Math.sin(nowMs / 55.0);
            case WRECKED -> 0f;
            case EMERGENCY -> (nowMs % 12_000L) * 0.03f;
        };
    }

    /**
     * Status line colour of an active card. Engine: amber while the repair window runs, red in its last 20 s and while
     * halted, amber again on emergency power. Lights: red.
     * 进行中卡片状态行的颜色。动力：抢修窗口内为琥珀色，最后 20 秒和停驶时为红色，应急动力时回到琥珀色。灯光：红色。
     */
    static int activeStatusColor(TabletSnapshot.SabotageRow row, int seconds) {
        EnginePhase phase = enginePhase(row);
        if (phase == null) {
            return TabletTheme.DANGER;
        }
        return switch (phase) {
            case CRITICAL -> seconds * 20L <= EngineRules.URGENT_TICKS ? TabletTheme.DANGER : TabletTheme.WARNING;
            case WRECKED -> TabletTheme.DANGER;
            case EMERGENCY -> TabletTheme.WARNING;
        };
    }

    private static String activeStatusKey(TabletSnapshot.SabotageRow row) {
        if (row.typeWire() == SabotageType.ENGINE.wire()) {
            EnginePhase phase = enginePhase(row);
            return KEY + "status.engine_" + (phase == null ? "critical" : phase.name().toLowerCase(java.util.Locale.ROOT));
        }
        return OutageMode.byWire(row.modeWire()) == OutageMode.TIMED ? KEY + "status.active_timed" : KEY + "status.active";
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
            case TRIGGERED, ENGINE_WRECKED -> TabletTheme.DANGER;
            case REPAIRED, REMOTE_REPAIRED, BOX_REPAIRED -> TabletTheme.SUCCESS;
            case BOX_BROKEN, ENGINE_EMERGENCY -> TabletTheme.WARNING;
            case EXPIRED -> TabletTheme.TEXT_2;
        };
        if (type == null) {
            c.circle(dotX, y + LOG_ROW_H / 2f, 2.5f, color);
        } else {
            // The line's type at a glance (both types share most log kinds), coloured by what happened.
            // 一眼看出这一行属于哪种破坏（两种破坏共用大部分日志类型），颜色表示发生了什么。
            typeGlyph(c, type, dotX - LOG_GLYPH / 2f, y + (LOG_ROW_H - LOG_GLYPH) / 2f, LOG_GLYPH, 0f, color);
        }
        Text typeName = type == null ? Text.literal("?") : Text.translatable(type.translationKey());
        // A repaired engine is not "a breaker repaired": it has its own line. / 修好动力不是「修好电闸」：单独一句。
        String logKey = kind == SabotageWorldComponent.LogKind.REPAIRED && type == SabotageType.ENGINE
                ? "log.engine_repaired" : "log." + kind.name().toLowerCase(java.util.Locale.ROOT);
        // Lines with an actor arrive with an empty name when the viewer has not linked with them: show "???".
        // 有操作者的行，若查看者与其未互认，名字以空串到达：显示「???」。
        String actor = row.actor().isEmpty()
                ? Text.translatable("screen.sparkstrength.tablet.chat.anonymous").getString() : row.actor();
        String sentence = Text.translatable(KEY + logKey, actor, typeName).getString();
        int textX = dotX + 8;
        c.text(tr, TabletCanvas.trim(tr, sentence, Math.max(0, x + w - textX - 4)), textX, textY, TabletTheme.TEXT);
    }
}
