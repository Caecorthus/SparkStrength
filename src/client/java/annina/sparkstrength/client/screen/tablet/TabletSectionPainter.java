package annina.sparkstrength.client.screen.tablet;

import annina.sparkstrength.network.tablet.TabletSnapshot;
import annina.sparkstrength.role.attendant.DoorLogKind;
import annina.sparkstrength.role.bomber.drone.DroneKind;
import annina.sparkstrength.tablet.TabletLayout;
import annina.sparkstrength.tablet.TabletRules;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Language;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Stateless painters for the tablet section bodies (connections, meeting, suspects, door log, drones) and for every
 * {@link TabletPressable} button look. Pure presentation of the server-redacted snapshot: nothing here decides
 * what a player may see or do; the screen passes in already-derived flags (clickable, active, state).
 * 平板各分区主体（成员、会议、嫌疑、房门记录、无人机）与所有 TabletPressable 按钮外观的无状态绘制器。仅呈现服务端已脱敏的快照：
 * 这里不决定玩家能看到或能做什么，可点击/启用/状态等标志均由界面预先算好后传入。
 *
 * <p>Package-visible geometry constants are shared with the screen, which places the widgets and scrolls by them;
 * changing one changes hit boxes as well as visuals. Private constants are painter-only; button hit boxes that depend
 * on them are exposed through rect helpers ({@code heroButtonRect}, {@code suspectButtonRect},
 * {@code droneButtonRect}). Text is 8 px high and
 * is always drawn at {@code centreY - 4}.
 * 包可见的几何常量与界面共享，界面据此放置控件与滚动；修改它们会同时改变点击区域与外观。私有常量仅供绘制器使用，
 * 依赖它们的按钮点击区域通过 heroButtonRect、suspectButtonRect、droneButtonRect 暴露。文字高 8 px，一律绘制在 centreY - 4。</p>
 */
final class TabletSectionPainter {
    static final int MEMBER_MIN_W = 190;
    static final int MEMBER_CARD_H = 30;
    static final int MEMBER_ROW_STEP = 38;
    static final int GRID_GAP = 8;
    static final int VOTE_MIN_W = 160;
    static final int VOTE_CARD_H = 32;
    static final int VOTE_ROW_STEP = 38;
    static final int SUSPECT_CARD_H = 40;
    static final int SUSPECT_ROW_STEP = 46;
    static final int ACTION_BUTTON_H = 26;
    static final int PROGRESS_BLOCK_H = 11;
    static final int DOOR_ROW_H = 26;
    static final int DOOR_ROW_STEP = 30;
    static final int DRONE_CARD_H = 50;
    static final int DRONE_ROW_STEP = 56;

    static final String KEY = "screen.sparkstrength.tablet.";
    private static final int CARD_R = 8;
    private static final int LINE_STEP = 10;
    private static final String ELLIPSIS = "…";

    private static final int EMPTY_DISC = 44;
    private static final int EMPTY_ICON = 22;
    private static final int EMPTY_GAP = 12;
    private static final int EMPTY_HINT_GAP = 5;

    private static final int MEMBER_AVATAR = 20;
    private static final int MEMBER_TEXT_X = 34;
    static final int PILL_PAD = 7;
    private static final int MIN_NAME_W = 28;

    private static final int HINT_PAD_X = 10;
    private static final int HINT_PAD_Y = 11;
    private static final int HINT_ICON = 14;
    private static final int HINT_TEXT_X = HINT_PAD_X + HINT_ICON + 8;
    private static final int HINT_MAX_LINES = 4;

    private static final int HERO_W = 360;
    private static final int HERO_H = 150;
    private static final int HERO_BUTTON_W = 132;
    private static final int HERO_BUTTON_H = 26;
    private static final int HERO_R = 12;
    private static final int HERO_DISC_R = 26;
    // Content stack inside the hero: disc 52, gap 12, status 8, gap 5, chances 8, gap 7, button 26.
    // 英雄卡内的内容堆叠：圆盘 52、间距 12、状态行 8、间距 5、次数行 8、间距 7、按钮 26。
    private static final int HERO_CONTENT_H = 118;
    private static final int HERO_STATUS_Y = 64;
    private static final int HERO_CHANCES_Y = 77;
    private static final int HERO_BUTTON_Y = 92;
    // Without the disc (short areas): status 8, gap 5, chances 8, gap 7, button 26.
    // 无圆盘的紧凑布局（区域过矮时）：状态行 8、间距 5、次数行 8、间距 7、按钮 26。
    private static final int HERO_COMPACT_H = 54;
    private static final int HERO_COMPACT_CHANCES_Y = 13;
    private static final int HERO_COMPACT_BUTTON_Y = 28;

    private static final int VOTE_AVATAR = 20;
    private static final int VOTE_TEXT_X = 32;
    private static final int BADGE_H = 16;
    private static final int BADGE_MIN_W = 20;

    private static final int SUSPECT_AVATAR = 24;
    private static final int SUSPECT_TEXT_X = 49;
    private static final int SUSPECT_BAR_W = 120;
    private static final int SUSPECT_BAR_H = 4;
    private static final int SUSPECT_BUTTON_MIN_W = 92;
    private static final int SUSPECT_BUTTON_H = 24;
    private static final int SUSPECT_BUTTON_INSET = 8;
    private static final int SUSPECT_BUTTON_TEXT_PAD = 28;

    private static final int DOOR_TILE = 18;
    private static final int DOOR_TILE_INSET = 4;
    private static final int DOOR_ICON = 12;
    private static final int DOOR_AVATAR = 16;
    private static final int DOOR_COUNT_H = 12;
    private static final int DOOR_COUNT_PAD = 5;
    // Ages shown at most: "59 s" and two-digit minutes (a round lasts well under 100 minutes).
    // 最宽的时间文本：59 秒与两位数分钟（一回合远短于 100 分钟）。
    private static final int DOOR_AGE_MAX_SECONDS = 59;
    private static final int DOOR_AGE_MAX_MINUTES = 99;

    private static final int DRONE_TILE = 34;
    private static final int DRONE_TILE_INSET = 8;
    private static final int DRONE_GLYPH = 22;
    private static final int DRONE_TEXT_GAP = 10;
    private static final int DRONE_BADGE_R = 6;
    private static final int DRONE_CHIP_H = 12;
    private static final int DRONE_CHIP_PAD = 5;
    private static final int DRONE_CHIP_GLYPH = 7;
    private static final int DRONE_CHIP_GAP = 6;
    private static final int DRONE_BATTERY_W = 34;
    private static final int DRONE_BATTERY_H = 10;
    private static final int DRONE_META_GAP = 12;
    private static final int DRONE_META_GLYPH = 9;
    private static final int DRONE_BUTTON_MIN_W = 84;
    private static final int DRONE_BUTTON_H = 24;
    private static final int DRONE_BUTTON_INSET = 10;
    private static final int DRONE_BUTTON_TEXT_PAD = 24;
    // Battery tones: green from 50%, amber from 20%, red below. 电量色：50% 起绿色，20% 起琥珀，低于 20% 红色。
    private static final int DRONE_MID_PERCENT = 50;
    private static final int DRONE_LOW_PERCENT = 20;
    private static final long DRONE_CHARGE_PULSE_MS = 1600L;
    private static final long DRONE_LOW_BLINK_MS = 800L;

    // Controls cheat-sheet keycaps. 操作提示键帽。
    private static final int KEYCAP_H = 14;
    private static final int KEYCAP_PAD = 4;
    private static final int KEYCAP_KEY_GAP = 3;
    private static final int KEYCAP_ACTION_GAP = 5;
    private static final int KEYCAP_ITEM_GAP = 14;

    private static final long PULSE_PERIOD_MS = 1200L;

    private static final int BUTTON_R = 8;
    private static final int BUTTON_ICON = 12;
    private static final int BUTTON_ICON_GAP = 5;
    private static final int BUTTON_PAD = 8;

    private TabletSectionPainter() {
    }

    enum EmptyIcon { MEMBERS, CHAT, SHIELD, DOOR, DRONE }

    enum HeroState { READY, COOLDOWN, DISABLED }

    /**
     * DANGER_SOFT: danger tint + danger ink that an inactive button keeps, so an alarm reason (a drone out of control)
     * stays legible on a danger-tinted card while still reading as not clickable.
     * DANGER_SOFT：危险色底纹加危险色文字，未启用时保留，使警报原因（无人机失控）在偏红的卡片上仍清晰可读，同时明显不可点击。
     */
    enum ButtonStyle { PRIMARY, SECONDARY, DANGER, DANGER_SOFT, WARNING_TOGGLED, LOCKED }

    enum ButtonIcon { NONE, LOCK, MEGAPHONE, LINK, CLOCK, SPINNER }

    /**
     * What a drone card's status chip says, derived from the row alone (presentation only).
     * 无人机卡片状态胶囊显示的内容，仅由该行数据推出（纯展示）。
     */
    enum DroneChip { COOLDOWN, COOLDOWN_CHARGING, CHARGING, STOWED, STANDBY, HOVERING, FLYING, PILOTING, FALLING }

    // ============================================================================================ empty state

    /** 44 px disc + 22 px icon above a wrapped TEXT_2 message (max 2 lines), centred in {@code area}. */
    static void emptyState(TabletCanvas c, TextRenderer tr, TabletLayout.Rect area, EmptyIcon icon, Text message) {
        emptyState(c, tr, area, icon, message, null);
    }

    /**
     * As above, plus an optional TEXT_3 {@code hint} (max 2 lines) under the message.
     * 同上，另可在消息下方加一段 TEXT_3 提示（最多 2 行）。
     */
    static void emptyState(TabletCanvas c, TextRenderer tr, TabletLayout.Rect area, EmptyIcon icon, Text message,
                           @Nullable Text hint) {
        if (area.width() <= 0 || area.height() <= 0) {
            return;
        }
        int maxWidth = Math.max(0, Math.min(240, area.width() - 16));
        List<String> lines = wrap(tr, message.getString(), maxWidth, 2);
        List<String> hintLines = hint == null ? List.of() : wrap(tr, hint.getString(), maxWidth, 2);
        int textHeight = lines.isEmpty() ? 0 : lines.size() * LINE_STEP - 2;
        int hintTop = textHeight + (textHeight > 0 ? EMPTY_HINT_GAP : 0);
        if (!hintLines.isEmpty()) {
            textHeight = hintTop + hintLines.size() * LINE_STEP - 2;
        }
        int withDisc = EMPTY_DISC + (textHeight > 0 ? EMPTY_GAP : 0) + textHeight;
        boolean showDisc = area.height() >= withDisc && area.width() >= EMPTY_DISC;
        int blockHeight = showDisc ? withDisc : textHeight;
        int top = area.y() + Math.max(0, (area.height() - blockHeight) / 2);
        if (showDisc) {
            float cx = area.x() + area.width() / 2f;
            float cy = top + EMPTY_DISC / 2f;
            c.circle(cx, cy, EMPTY_DISC / 2f, TabletTheme.SURFACE);
            c.ring(cx, cy, EMPTY_DISC / 2f, 1f, TabletTheme.HAIRLINE);
            float half = EMPTY_ICON / 2f;
            switch (icon) {
                case MEMBERS -> TabletIcons.members(c, cx - half, cy - half, EMPTY_ICON, TabletTheme.TEXT_3);
                case CHAT -> TabletIcons.chat(c, cx - half, cy - half, EMPTY_ICON, TabletTheme.TEXT_3);
                case SHIELD -> TabletIcons.shield(c, cx - half, cy - half, EMPTY_ICON, TabletTheme.TEXT_3);
                case DOOR -> TabletIcons.door(c, cx - half, cy - half, EMPTY_ICON, TabletTheme.TEXT_3);
                case DRONE -> TabletIcons.drone(c, cx - half, cy - half, EMPTY_ICON, TabletTheme.TEXT_3);
            }
            top += EMPTY_DISC + EMPTY_GAP;
        }
        int centerX = area.x() + area.width() / 2;
        for (int i = 0; i < lines.size(); i++) {
            c.textCentered(tr, lines.get(i), centerX, top + i * LINE_STEP, TabletTheme.TEXT_2);
        }
        for (int i = 0; i < hintLines.size(); i++) {
            c.textCentered(tr, hintLines.get(i), centerX, top + hintTop + i * LINE_STEP, TabletTheme.TEXT_3);
        }
    }

    // ============================================================================================ connections

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

    /** 2 px thumb on the right edge of {@code track}; nothing when everything fits. */
    static void scrollThumb(TabletCanvas c, TabletLayout.Rect track, int firstRow, int visibleRows, int totalRows) {
        if (visibleRows <= 0 || totalRows <= visibleRows || track.height() <= 0 || track.width() <= 0) {
            return;
        }
        float thumbW = Math.min(2f, track.width());
        float thumbH = Math.max(12f, track.height() * (float) visibleRows / totalRows);
        thumbH = Math.min(thumbH, track.height());
        int maxFirst = totalRows - visibleRows;
        float t = Math.max(0f, Math.min(1f, (float) firstRow / maxFirst));
        float thumbY = track.y() + (track.height() - thumbH) * t;
        c.roundRect(track.right() - thumbW, thumbY, thumbW, thumbH, thumbW / 2f, 0x33FFFFFF);
    }

    // ============================================================================================ meeting

    /** Hero card centred in {@code area}: min(HERO_W, area.w) x min(HERO_H, area.h). */
    private static TabletLayout.Rect heroRect(TabletLayout.Rect area) {
        int w = Math.min(HERO_W, area.width());
        int h = Math.min(HERO_H, area.height());
        return new TabletLayout.Rect(area.x() + (area.width() - w) / 2, area.y() + (area.height() - h) / 2, w, h);
    }

    /**
     * Where the screen must place the call-meeting TabletPressable; kept in lock-step with {@link #meetingHero}.
     * 界面放置“召开会议”按钮的位置；与 meetingHero 的绘制布局严格一致。
     */
    static TabletLayout.Rect heroButtonRect(TabletLayout.Rect area) {
        TabletLayout.Rect hero = heroRect(area);
        boolean full = hero.height() >= HERO_CONTENT_H;
        int top = heroContentTop(hero, full);
        int w = Math.min(HERO_BUTTON_W, Math.max(0, hero.width() - 24));
        int y = top + (full ? HERO_BUTTON_Y : HERO_COMPACT_BUTTON_Y);
        y = Math.max(hero.y(), Math.min(y, hero.bottom() - HERO_BUTTON_H));
        return new TabletLayout.Rect(hero.x() + (hero.width() - w) / 2, y, w, HERO_BUTTON_H);
    }

    private static int heroContentTop(TabletLayout.Rect hero, boolean full) {
        int contentHeight = full ? HERO_CONTENT_H : HERO_COMPACT_H;
        return hero.y() + Math.max(0, (hero.height() - contentHeight) / 2);
    }

    /**
     * Inactive-meeting hero card (the call button itself is a widget painted via {@link #button}).
     * 未开会时的英雄卡片（召开按钮本身是控件，通过 button 绘制）。
     */
    static void meetingHero(TabletCanvas c, TextRenderer tr, TabletLayout.Rect area, HeroState state,
                            int cooldownSeconds, int callsRemaining, long timeMs) {
        TabletLayout.Rect hero = heroRect(area);
        if (hero.width() <= 0 || hero.height() <= 0) {
            return;
        }
        boolean ready = state == HeroState.READY;
        c.shadow(hero.x(), hero.y() + 2, hero.width(), hero.height(), HERO_R, 10f, 0x55000000);
        c.roundRect(hero.x(), hero.y(), hero.width(), hero.height(), HERO_R, TabletTheme.SURFACE_RAISED);
        // Faint top sheen, tinted red while a meeting can be called. 顶部淡光泽，可召开时带红色调。
        int sheen = ready ? TabletTheme.withAlpha(TabletTheme.DANGER, 0x12) : 0x08FFFFFF;
        c.roundRectGradientV(hero.x(), hero.y(), hero.width(), hero.height(), HERO_R, sheen, sheen & 0x00FFFFFF);
        c.roundRectOutline(hero.x(), hero.y(), hero.width(), hero.height(), HERO_R, 1f, TabletTheme.HAIRLINE);

        boolean full = hero.height() >= HERO_CONTENT_H;
        int top = heroContentTop(hero, full);
        int centerX = hero.x() + hero.width() / 2;
        float cx = hero.x() + hero.width() / 2f;
        int textMax = Math.max(0, hero.width() - 24);

        if (full) {
            float cy = top + HERO_DISC_R;
            if (ready) {
                // Slow 2.4 s breathing glow; the only motion in the hero. 2.4 秒缓慢呼吸光晕，英雄卡唯一的动效。
                double phase = (timeMs % 2400L) / 2400.0 * Math.PI * 2.0;
                int glowAlpha = 0x1C + (int) Math.round(0x10 * (0.5 + 0.5 * Math.sin(phase)));
                c.glow(cx, cy, 40f, TabletTheme.withAlpha(TabletTheme.DANGER, glowAlpha));
                c.circle(cx, cy, HERO_DISC_R, TabletTheme.withAlpha(TabletTheme.DANGER, 0x2E));
                c.ring(cx, cy, HERO_DISC_R, 1f, TabletTheme.withAlpha(TabletTheme.DANGER, 0x40));
            } else {
                c.circle(cx, cy, HERO_DISC_R, TabletTheme.FAINT);
            }
            if (state == HeroState.COOLDOWN && cooldownSeconds > 0) {
                float total = Math.max(1f, TabletRules.MEETING_COOLDOWN_TICKS / 20f);
                float fraction = Math.max(0f, Math.min(1f, cooldownSeconds / total));
                float ringR = HERO_DISC_R + 5f;
                c.ring(cx, cy, ringR, 2f, TabletTheme.FAINT);
                c.arc(cx, cy, ringR, 2f, 0f, 360f * fraction, TabletTheme.TEXT_3);
            }
            int iconColor = switch (state) {
                case READY -> TabletTheme.DANGER;
                case COOLDOWN -> TabletTheme.TEXT_2;
                case DISABLED -> TabletTheme.TEXT_3;
            };
            TabletIcons.megaphone(c, cx - 13f, cy - 13f, 26f, iconColor);
        }

        Text statusText = switch (state) {
            case READY -> Text.translatable(KEY + "meeting.ready");
            case COOLDOWN -> Text.translatable(KEY + "meeting.cooldown", cooldownSeconds);
            case DISABLED -> Text.translatable(KEY + "meeting.disabled");
        };
        int statusColor = switch (state) {
            case READY -> TabletTheme.SUCCESS;
            case COOLDOWN -> TabletTheme.TEXT_2;
            case DISABLED -> TabletTheme.TEXT_3;
        };
        int statusY = top + (full ? HERO_STATUS_Y : 0);
        int chancesY = top + (full ? HERO_CHANCES_Y : HERO_COMPACT_CHANCES_Y);
        c.textCentered(tr, TabletCanvas.trim(tr, statusText.getString(), textMax), centerX, statusY, statusColor);
        String chances = Text.translatable(KEY + "meeting.chances", callsRemaining).getString();
        c.textCentered(tr, TabletCanvas.trim(tr, chances, textMax), centerX, chancesY, TabletTheme.TEXT_3);
    }

    /** 3 px remaining-time bar (track + DANGER fill) at the top of the vote list block. */
    static void meetingProgress(TabletCanvas c, int x, int y, int w, int remainingSeconds) {
        if (w <= 0) {
            return;
        }
        float total = Math.max(1f, TabletRules.MEETING_DURATION_TICKS / 20f);
        float fraction = Math.max(0f, Math.min(1f, remainingSeconds / total));
        c.roundRect(x, y, w, 3f, 1.5f, TabletTheme.FAINT);
        if (fraction > 0f) {
            c.roundRect(x, y, Math.max(3f, w * fraction), 3f, 1.5f, TabletTheme.DANGER);
        }
    }

    /**
     * Vote target card. {@code clickable} must already be participant && selectable && !confirmed (screen-side);
     * hover is only shown when clickable, unselectable targets are dimmed. {@code viewOnly} (spectator) draws the
     * names of selectable targets in TEXT_2 so the grid reads as non-interactive.
     * 投票目标卡片。clickable 须由界面预先算好（参与者 && 可选 && 未确认）；仅可点击时显示悬停，不可选目标变暗。
     * viewOnly（旁观者）时可选目标的名字改用 TEXT_2，使网格呈现为只读。
     */
    static void voteCard(TabletCanvas c, TextRenderer tr, TabletPressable card, TabletSnapshot.VoteTarget target,
                         boolean selected, boolean hovered, boolean clickable, boolean viewOnly, TabletTheme.Accent a) {
        int x = card.getX();
        int y = card.getY();
        int w = card.getWidth();
        int h = card.getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        boolean hover = hovered && clickable;
        boolean dim = !target.selectable();
        int base = hover ? TabletTheme.SURFACE_HOVER : TabletTheme.SURFACE;
        // Opaque blend (not a translucent overlay) so the avatar corner mask matches the card exactly.
        // 使用不透明混合色（而非半透明叠加），使头像圆角遮罩与卡片颜色完全一致。
        int fill = selected ? TabletTheme.mix(base, a.base() | 0xFF000000, 0x2E / 255f) : base;
        c.roundRect(x, y, w, h, CARD_R, fill);
        if (selected) {
            c.roundRectOutline(x, y, w, h, CARD_R, 1.5f, a.base());
        }
        focusRing(c, card, CARD_R, a);

        int centerY = y + h / 2;
        int textY = centerY - 4;
        int avatarX = x + 6;
        int avatarY = centerY - VOTE_AVATAR / 2;
        c.avatar(target.uuid(), target.name(), avatarX, avatarY, VOTE_AVATAR, 5f, fill);
        if (dim) {
            c.roundRect(avatarX, avatarY, VOTE_AVATAR, VOTE_AVATAR, 5f, TabletTheme.withAlpha(fill, 0x99));
        }
        if (selected) {
            // 12 px disc on the avatar's top-right, cut out of the card by a 1 px fill-coloured ring; kept clear of
            // the 1.5 px selection outline and of the name column.
            // 头像右上角 12 px 勾选圆点，外圈 1 px 卡片底色描边；不压住 1.5 px 选中描边，也不侵入名字列。
            float discX = avatarX + VOTE_AVATAR - 2f;
            float discY = avatarY + 2.5f;
            c.circle(discX, discY, 7f, fill);
            c.circle(discX, discY, 6f, a.base());
            TabletIcons.check(c, discX - 4.5f, discY - 4.5f, 9f, TabletTheme.WHITE);
        }

        // Vote-count badge on the right. 右侧票数徽章。
        String votes = String.valueOf(Math.max(0, target.votes()));
        int badgeW = Math.max(BADGE_MIN_W, visibleWidth(tr, votes) + 12);
        int badgeX = x + w - 8 - badgeW;
        int badgeY = centerY - BADGE_H / 2;
        boolean hasVotes = target.votes() > 0 && !dim;
        c.roundRect(badgeX, badgeY, badgeW, BADGE_H, BADGE_H / 2f,
                hasVotes ? TabletTheme.withAlpha(a.base(), 0x4D) : TabletTheme.FAINT);
        c.text(tr, votes, badgeX + (badgeW - visibleWidth(tr, votes) + 1) / 2, textY,
                hasVotes ? a.pale() : TabletTheme.TEXT_3);

        int nameX = x + VOTE_TEXT_X;
        String name = TabletCanvas.trim(tr, target.name(), badgeX - 6 - nameX);
        int nameColor = dim ? TabletTheme.TEXT_3 : viewOnly && !selected ? TabletTheme.TEXT_2 : TabletTheme.TEXT;
        c.text(tr, name, nameX, textY, nameColor);
    }

    /** Footer status line (TEXT_2), optionally led by a 12 px lock; trimmed to {@code maxWidth}. */
    static void meetingStatus(TabletCanvas c, TextRenderer tr, int x, int centerY, int maxWidth, Text status,
                              boolean locked) {
        if (maxWidth <= 0) {
            return;
        }
        int textX = x;
        if (locked && maxWidth >= BUTTON_ICON + 5 + 12) {
            TabletIcons.lock(c, x, centerY - BUTTON_ICON / 2f, BUTTON_ICON, TabletTheme.TEXT_2);
            textX += BUTTON_ICON + 5;
        }
        String text = TabletCanvas.trim(tr, status.getString(), maxWidth - (textX - x));
        c.text(tr, text, textX, centerY - 4, TabletTheme.TEXT_2);
    }

    // ============================================================================================ suspects

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

    // ============================================================================================ door log

    /**
     * One door-log card: kind tile, then either "head name predicate" (KEY_OPENED) or the event sentence, then an
     * optional ×N badge and the age, right-aligned in a fixed {@code ageColumn} so badges line up as ages tick.
     * The door name is the sentence's only bright run. Anonymous actors get a "?" disc and "???", never skin or uuid data.
     * 一张房门记录卡片：类型图块，随后是“头像 名字 谓语”（KEY_OPENED）或事件句子，再是可选的 ×N 徽章与时间；
     * 时间在固定宽度 ageColumn 内右对齐，时间变化时徽章仍对齐。门名是句中唯一的高亮部分。
     * 匿名操作者显示“?”圆盘与“???”，绝不绘制皮肤或任何 uuid 数据。
     */
    static void doorLogRow(TabletCanvas c, TextRenderer tr, int x, int y, int w, TabletSnapshot.DoorLogRow row,
                           DoorLogKind kind, String age, int ageColumn) {
        int h = DOOR_ROW_H;
        if (w <= 0) {
            return;
        }
        int centerY = y + h / 2;
        int textY = centerY - 4;
        int tone = doorTone(kind);
        boolean neutral = kind == DoorLogKind.KEY_OPENED;
        // Opaque blend so the avatar corner mask matches the card. 不透明混合，使头像圆角遮罩与卡片一致。
        int fill = doorAlarm(kind) ? TabletTheme.mix(TabletTheme.SURFACE, tone, 0.07f) : TabletTheme.SURFACE;
        c.roundRect(x, y, w, h, CARD_R, fill);

        int tileX = x + DOOR_TILE_INSET;
        int tileY = centerY - DOOR_TILE / 2;
        c.roundRect(tileX, tileY, DOOR_TILE, DOOR_TILE, 5f, TabletTheme.withAlpha(tone, neutral ? 0x1F : 0x2E));
        float iconOffset = (DOOR_TILE - DOOR_ICON) / 2f;
        doorKindIcon(c, kind, tileX + iconOffset, tileY + iconOffset, DOOR_ICON, tone);

        int right = x + w - 8;
        int ageWidth = Math.min(ageColumn, Math.max(0, right - tileX - DOOR_TILE - 16));
        c.textRight(tr, TabletCanvas.trim(tr, age, ageWidth), right, textY, TabletTheme.TEXT_3);
        int contentRight = right - ageWidth - 8;
        int textX = tileX + DOOR_TILE + 8;
        if (row.count() > 1) {
            String count = Text.translatable(KEY + "door_log.count", row.count()).getString();
            int badgeW = visibleWidth(tr, count) + 2 * DOOR_COUNT_PAD;
            if (contentRight - badgeW - 6 - textX >= MIN_NAME_W) {
                int badgeX = contentRight - badgeW;
                c.roundRect(badgeX, centerY - DOOR_COUNT_H / 2, badgeW, DOOR_COUNT_H, DOOR_COUNT_H / 2f,
                        neutral ? TabletTheme.FAINT : TabletTheme.withAlpha(tone, 0x33));
                c.text(tr, count, badgeX + DOOR_COUNT_PAD, textY, neutral ? TabletTheme.TEXT_2 : tone);
                contentRight = badgeX - 6;
            }
        }
        if (contentRight - textX <= 0) {
            return;
        }

        Text sentence = Text.translatable(kind.translationKey(),
                Text.literal(row.doorName()).withColor(TabletTheme.TEXT & 0xFFFFFF));
        if (!kind.namesActor()) {
            c.text(tr, trimStyled(tr, sentence, contentRight - textX), textX, textY, TabletTheme.TEXT_2);
            return;
        }
        boolean anonymous = row.actorUuid() == null;
        if (contentRight - textX >= DOOR_AVATAR + 5 + MIN_NAME_W) {
            int avatarY = centerY - DOOR_AVATAR / 2;
            if (anonymous) {
                anonymousAvatar(c, tr, textX, avatarY, DOOR_AVATAR);
            } else {
                c.avatar(row.actorUuid(), row.actorName(), textX, avatarY, DOOR_AVATAR, 4f, fill);
            }
            textX += DOOR_AVATAR + 5;
        }
        String name = anonymous ? Text.translatable(KEY + "door_log.anonymous").getString() : row.actorName();
        int room = contentRight - textX;
        int nameWidth = tr.getWidth(name);
        int sentenceWidth = tr.getWidth(sentence);
        // The name keeps its full width when everything fits; otherwise it yields down to MIN_NAME_W first.
        // 全部放得下时名字保持完整；否则名字先让出空间，最少保留 MIN_NAME_W。
        int nameRoom = nameWidth + 3 + sentenceWidth <= room ? nameWidth
                : Math.min(nameWidth, Math.max(MIN_NAME_W, room - 3 - sentenceWidth));
        textX += c.text(tr, TabletCanvas.trim(tr, name, Math.min(nameRoom, room)), textX, textY,
                anonymous ? TabletTheme.TEXT_3 : TabletTheme.TEXT) + 3;
        if (contentRight - textX > 0) {
            c.text(tr, trimStyled(tr, sentence, contentRight - textX), textX, textY, TabletTheme.TEXT_2);
        }
    }

    /** Widest age label, so every row reserves the same age column. 最宽的时间文本宽度，所有行预留同宽的时间列。 */
    static int doorAgeColumn(TextRenderer tr) {
        // Advance widths (not inked): TabletCanvas.trim compares advances. 使用字宽（非着墨宽度），与 trim 的比较一致。
        return Math.max(tr.getWidth(doorAge(0)),
                Math.max(tr.getWidth(doorAge(DOOR_AGE_MAX_SECONDS)), tr.getWidth(doorAge(DOOR_AGE_MAX_MINUTES * 60))));
    }

    /** "just now" under 5 s, then seconds under a minute, then whole minutes. 5 秒内为“刚刚”，1 分钟内按秒，其后按分钟。 */
    static String doorAge(int seconds) {
        int age = Math.max(0, seconds);
        if (age < 5) {
            return Text.translatable(KEY + "door_log.age.now").getString();
        }
        if (age < 60) {
            return Text.translatable(KEY + "door_log.age.seconds", age).getString();
        }
        return Text.translatable(KEY + "door_log.age.minutes", age / 60).getString();
    }

    /** Severity tone: break-ins red, jams amber, fixes green, key opens neutral. 严重度色：闯入红、堵门琥珀、修复绿、钥匙开门中性。 */
    static int doorTone(DoorLogKind kind) {
        return switch (kind) {
            case KEY_OPENED -> TabletTheme.TEXT_2;
            case PICKED, BROKEN, FORCED -> TabletTheme.DANGER;
            case JAMMED -> TabletTheme.WARNING;
            case REPAIRED, UNJAMMED -> TabletTheme.SUCCESS;
        };
    }

    private static boolean doorAlarm(DoorLogKind kind) {
        return kind == DoorLogKind.PICKED || kind == DoorLogKind.BROKEN || kind == DoorLogKind.FORCED;
    }

    static void doorKindIcon(TabletCanvas c, DoorLogKind kind, float x, float y, float size, int color) {
        switch (kind) {
            case KEY_OPENED -> TabletIcons.key(c, x, y, size, color);
            case PICKED -> TabletIcons.lockpick(c, x, y, size, color);
            case BROKEN -> TabletIcons.burst(c, x, y, size, color);
            case REPAIRED -> TabletIcons.wrench(c, x, y, size, color);
            case JAMMED -> TabletIcons.lock(c, x, y, size, color);
            case UNJAMMED -> TabletIcons.unlock(c, x, y, size, color);
            case FORCED -> TabletIcons.doorOpen(c, x, y, size, color);
        }
    }

    /** Stand-in for a hidden actor: "?" on a disc, like anonymous chat rows. 隐藏操作者的替代头像：圆盘上的“?”，与匿名聊天一致。 */
    private static void anonymousAvatar(TabletCanvas c, TextRenderer tr, int x, int y, int size) {
        c.circle(x + size / 2f, y + size / 2f, size / 2f, TabletTheme.ANONYMOUS_DISC);
        int glyphWidth = visibleWidth(tr, "?");
        c.text(tr, "?", x + (size - glyphWidth + 1) / 2, y + (size - 7) / 2, TabletTheme.TEXT_2);
    }

    // ============================================================================================ drones

    /**
     * One drone card: kind tile (glyph, payload/bomb badge, glow while airborne), then "name [status]" over
     * "battery % · payload · distance"; the right side stays free for the connect widget ({@link #droneButtonRect}).
     * Meta items that do not fit are dropped from the right (distance first). Only the viewer's own drones ever reach
     * here (server-filtered).
     * 一张无人机卡片：型号图块（图形、挂载/炸弹角标、升空时的光晕），右侧上行“名称 [状态]”，下行“电量 % · 挂载 · 距离”；
     * 最右侧留给连接按钮控件（droneButtonRect）。放不下的信息从右往左依次省略（先省略距离）。这里只会收到查看者自己的
     * 无人机（服务端已过滤）。
     */
    static void droneCard(TabletCanvas c, TextRenderer tr, int x, int y, int w, TabletSnapshot.DroneRow row,
                          DroneKind kind, long now) {
        int h = DRONE_CARD_H;
        if (w <= 0) {
            return;
        }
        DroneChip chip = droneChip(row, kind);
        int tone = droneTone(kind);
        boolean airborne = chip == DroneChip.HOVERING || chip == DroneChip.FLYING || chip == DroneChip.PILOTING;
        boolean falling = chip == DroneChip.FALLING;
        int centerY = y + h / 2;
        // Opaque blend so the badge cut-out ring matches the card. 不透明混合，使角标描边与卡片底色一致。
        int fill = falling ? TabletTheme.mix(TabletTheme.SURFACE, TabletTheme.DANGER, 0.07f) : TabletTheme.SURFACE;
        c.roundRect(x, y, w, h, CARD_R, fill);
        if (airborne || falling) {
            c.roundRectOutline(x, y, w, h, CARD_R, 1f,
                    TabletTheme.withAlpha(falling ? TabletTheme.DANGER : TabletTheme.DRONE.base(), 0x4D));
        }

        int tileX = x + DRONE_TILE_INSET;
        int tileY = centerY - DRONE_TILE / 2;
        float tileCx = tileX + DRONE_TILE / 2f;
        float tileCy = tileY + DRONE_TILE / 2f;
        int glyphColor = falling ? TabletTheme.DANGER : tone;
        if (airborne) {
            // Radius stays inside the card's tile inset. 半径不超出卡片内边距。
            c.glow(tileCx, tileCy, DRONE_TILE * 0.7f, TabletTheme.withAlpha(tone, 0x2E));
        }
        c.roundRect(tileX, tileY, DRONE_TILE, DRONE_TILE, 9f, TabletTheme.withAlpha(glyphColor, row.placed() ? 0x2E : 0x1F));
        TabletIcons.drone(c, tileCx - DRONE_GLYPH / 2f, tileCy - DRONE_GLYPH / 2f, DRONE_GLYPH,
                row.placed() ? glyphColor : TabletTheme.multiplyAlpha(glyphColor, 0.7f));
        // Bottom-right badge: the bomb drone IS the bomb; a grenade drone shows it only while an M67 is bound.
        // 右下角标：炸弹无人机本身就是炸弹；投弹无人机仅在挂载 M67 时显示。
        boolean bombBadge = kind == DroneKind.BOMB;
        if (bombBadge || row.payload()) {
            float badgeCx = tileX + DRONE_TILE - 3f;
            float badgeCy = tileY + DRONE_TILE - 3f;
            c.circle(badgeCx, badgeCy, DRONE_BADGE_R + 1f, fill);
            c.circle(badgeCx, badgeCy, DRONE_BADGE_R, bombBadge ? TabletTheme.DANGER : TabletTheme.DRONE.base());
            float glyph = DRONE_BADGE_R * 1.4f;
            if (bombBadge) {
                TabletIcons.burst(c, badgeCx - glyph / 2f, badgeCy - glyph / 2f, glyph, TabletTheme.WHITE);
            } else {
                TabletIcons.grenade(c, badgeCx - glyph / 2f, badgeCy - glyph / 2f, glyph, TabletTheme.WHITE);
            }
        }

        int textX = tileX + DRONE_TILE + DRONE_TEXT_GAP;
        int contentRight = droneButtonRect(tr, x, y, w).x() - 10;
        int room = contentRight - textX;
        if (room <= 0) {
            return;
        }
        // Two lines centred as a block: name/chip (8) + gap 10 + battery line (10). 两行整体居中：名称行、间距、电量行。
        int nameY = centerY - 13;
        int metaCenterY = centerY + 9;

        String name = Text.translatable(KEY + "drone.kind." + kind.id()).getString();
        String chipText = droneChipText(row, chip);
        boolean chipGlyph = chip != DroneChip.STOWED;
        int chipGlyphSpace = chipGlyph ? DRONE_CHIP_GLYPH + 3 : 0;
        int chipMax = Math.max(0, room - Math.min(tr.getWidth(name), MIN_NAME_W) - DRONE_CHIP_GAP);
        String chipVisible = TabletCanvas.trim(tr, chipText, chipMax - 2 * DRONE_CHIP_PAD - chipGlyphSpace);
        int chipW = chipVisible.isEmpty() ? 0 : 2 * DRONE_CHIP_PAD + chipGlyphSpace + visibleWidth(tr, chipVisible);
        int nameW = c.text(tr, TabletCanvas.trim(tr, name, room - (chipW > 0 ? chipW + DRONE_CHIP_GAP : 0)), textX, nameY,
                TabletTheme.TEXT);
        if (chipW > 0) {
            int chipX = textX + nameW + DRONE_CHIP_GAP - 1;
            droneStatusChip(c, tr, chipX, nameY + 4 - DRONE_CHIP_H / 2, chipW, chip, chipVisible, chipGlyph, now);
        }

        // Battery meter + percent. 电量条与百分比。
        int percent = row.chargePercent();
        boolean charging = chip == DroneChip.CHARGING || chip == DroneChip.COOLDOWN_CHARGING;
        boolean lowBlink = airborne && percent < DRONE_LOW_PERCENT;
        int metaX = textX;
        if (contentRight - metaX < DRONE_BATTERY_W + 3) {
            return;
        }
        batteryMeter(c, metaX, metaCenterY - DRONE_BATTERY_H / 2, percent, charging, lowBlink, now);
        metaX += DRONE_BATTERY_W + 3 + 5;
        String percentText = Text.translatable(KEY + "drone.percent", percent).getString();
        if (contentRight - metaX < tr.getWidth(percentText)) {
            return;
        }
        metaX += c.text(tr, percentText, metaX, metaCenterY - 4,
                percent < DRONE_LOW_PERCENT ? TabletTheme.DANGER : TabletTheme.TEXT_2);

        // Payload: bound M67 for the grenade drone, "single use" for the bomb drone. 挂载：投弹无人机的 M67；炸弹无人机为“一次性”。
        String payloadText;
        int payloadInk;
        int payloadGlyphColor;
        if (kind == DroneKind.BOMB) {
            payloadText = Text.translatable(KEY + "drone.payload.bomb").getString();
            payloadInk = TabletTheme.TEXT_3;
            payloadGlyphColor = TabletTheme.multiplyAlpha(TabletTheme.DANGER, 0.85f);
        } else if (row.payload()) {
            payloadText = Text.translatable(KEY + "drone.payload.loaded").getString();
            payloadInk = TabletTheme.TEXT_2;
            payloadGlyphColor = TabletTheme.DRONE.pale();
        } else {
            payloadText = Text.translatable(KEY + "drone.payload.empty").getString();
            payloadInk = TabletTheme.TEXT_3;
            payloadGlyphColor = TabletTheme.TEXT_3;
        }
        metaX = metaItem(c, tr, metaX, contentRight, metaCenterY, payloadText, payloadInk, payloadGlyphColor,
                kind == DroneKind.BOMB ? MetaGlyph.BURST : MetaGlyph.GRENADE);
        if (metaX < 0 || !row.placed() || row.distance() < 0) {
            return;
        }
        metaItem(c, tr, metaX, contentRight, metaCenterY,
                Text.translatable(KEY + "drone.distance", row.distance()).getString(), TabletTheme.TEXT_3,
                TabletTheme.TEXT_3, MetaGlyph.LOCATE);
    }

    /**
     * Where the screen must place a drone card's connect TabletPressable (right-aligned, vertically centred). Its width
     * fits the widest label the button can ever show, identical on every row, so states never shift the layout.
     * 界面放置无人机卡片“连接”按钮的位置（右对齐、垂直居中）。宽度取按钮可能显示的最宽文字，每行相同，状态变化时布局不动。
     */
    static TabletLayout.Rect droneButtonRect(TextRenderer tr, int x, int y, int w) {
        int labelW = 0;
        for (String key : new String[]{"drone.connect", "drone.linked", "drone.connecting", "drone.cooling"}) {
            labelW = Math.max(labelW, tr.getWidth(Text.translatable(KEY + key)) + BUTTON_ICON + BUTTON_ICON_GAP);
        }
        for (String key : new String[]{"drone.lost", "drone.place_first", "drone.busy"}) {
            labelW = Math.max(labelW, tr.getWidth(Text.translatable(KEY + key)));
        }
        int bw = Math.max(DRONE_BUTTON_MIN_W, labelW + DRONE_BUTTON_TEXT_PAD);
        // Upper clamp never drops below the minimum, like suspectButtonRect. 上限不低于最小宽度，与 suspectButtonRect 相同。
        bw = Math.min(bw, Math.max(DRONE_BUTTON_MIN_W, w / 3));
        bw = Math.min(bw, Math.max(0, w - 2 * DRONE_BUTTON_INSET));
        return new TabletLayout.Rect(x + w - DRONE_BUTTON_INSET - bw, y + (DRONE_CARD_H - DRONE_BUTTON_H) / 2, bw,
                DRONE_BUTTON_H);
    }

    static DroneChip droneChip(TabletSnapshot.DroneRow row, DroneKind kind) {
        if (!row.placed() || row.statusWire() == TabletSnapshot.DroneRow.STATUS_ITEM) {
            // Only a carried grenade drone recharges, and it keeps recharging through the loss cooldown.
            // 只有携带中的投弹无人机会回充，损毁冷却期间也照常回充。
            boolean charging = kind == DroneKind.GRENADE && row.chargePercent() < 100;
            if (row.cooldownSeconds() > 0) {
                return charging ? DroneChip.COOLDOWN_CHARGING : DroneChip.COOLDOWN;
            }
            return charging ? DroneChip.CHARGING : DroneChip.STOWED;
        }
        return switch (row.statusWire()) {
            case TabletSnapshot.DroneRow.STATUS_HOVERING -> DroneChip.HOVERING;
            case TabletSnapshot.DroneRow.STATUS_FLYING -> DroneChip.FLYING;
            case TabletSnapshot.DroneRow.STATUS_PILOTING -> DroneChip.PILOTING;
            case TabletSnapshot.DroneRow.STATUS_FALLING -> DroneChip.FALLING;
            default -> DroneChip.STANDBY;
        };
    }

    /** Grenade drone in the drone accent, bomb drone in danger red. 投弹无人机用无人机强调色，炸弹无人机用危险红。 */
    static int droneTone(DroneKind kind) {
        return kind == DroneKind.BOMB ? TabletTheme.DANGER : TabletTheme.DRONE.base();
    }

    private static String droneChipText(TabletSnapshot.DroneRow row, DroneChip chip) {
        return switch (chip) {
            case COOLDOWN -> Text.translatable(KEY + "drone.status.cooldown", row.cooldownSeconds()).getString();
            case COOLDOWN_CHARGING ->
                    Text.translatable(KEY + "drone.status.cooldown_charging", row.cooldownSeconds()).getString();
            case CHARGING -> Text.translatable(KEY + "drone.status.charging").getString();
            case STOWED -> Text.translatable(KEY + "drone.status.stowed").getString();
            case STANDBY -> Text.translatable(KEY + "drone.status.standby").getString();
            case HOVERING -> Text.translatable(KEY + "drone.status.hovering").getString();
            case FLYING -> Text.translatable(KEY + "drone.status.flying").getString();
            case PILOTING -> Text.translatable(KEY + "drone.status.piloting").getString();
            case FALLING -> Text.translatable(KEY + "drone.status.falling").getString();
        };
    }

    /** Status chip (h=12): optional leading glyph/dot, then text. 状态胶囊（高 12）：可选前置图形/圆点，随后文字。 */
    private static void droneStatusChip(TabletCanvas c, TextRenderer tr, int x, int y, int w, DroneChip chip, String text,
                                        boolean glyph, long now) {
        int ink;
        int fill;
        switch (chip) {
            case CHARGING, PILOTING -> {
                ink = TabletTheme.SUCCESS;
                fill = TabletTheme.withAlpha(TabletTheme.SUCCESS, 0x2E);
            }
            case HOVERING, FLYING -> {
                ink = TabletTheme.DRONE.pale();
                fill = TabletTheme.withAlpha(TabletTheme.DRONE.base(), 0x33);
            }
            case FALLING -> {
                ink = TabletTheme.DANGER;
                fill = TabletTheme.withAlpha(TabletTheme.DANGER, 0x2E);
            }
            case STOWED -> {
                ink = TabletTheme.TEXT_3;
                fill = TabletTheme.FAINT;
            }
            default -> {
                ink = TabletTheme.TEXT_2;
                fill = TabletTheme.FAINT;
            }
        }
        c.roundRect(x, y, w, DRONE_CHIP_H, DRONE_CHIP_H / 2f, fill);
        int textX = x + DRONE_CHIP_PAD;
        if (glyph) {
            float gx = textX;
            float gy = y + (DRONE_CHIP_H - DRONE_CHIP_GLYPH) / 2f;
            float half = DRONE_CHIP_GLYPH / 2f;
            switch (chip) {
                case COOLDOWN, COOLDOWN_CHARGING -> TabletIcons.clock(c, gx, gy, DRONE_CHIP_GLYPH, ink);
                case CHARGING -> TabletIcons.bolt(c, gx, gy, DRONE_CHIP_GLYPH, ink);
                case PILOTING -> TabletIcons.link(c, gx, gy, DRONE_CHIP_GLYPH, ink);
                case HOVERING, FLYING -> pulseDot(c, gx + half, gy + half, 2.2f, TabletTheme.DRONE.base(), now);
                case FALLING -> c.circle(gx + half, gy + half, 2.2f, TabletTheme.DANGER);
                default -> c.circle(gx + half, gy + half, 2.2f, TabletTheme.TEXT_3);
            }
            textX += DRONE_CHIP_GLYPH + 3;
        }
        c.text(tr, text, textX, y + (DRONE_CHIP_H - 8) / 2, ink);
    }

    /**
     * Battery-shaped meter (body + nub, inner fill by level). Charging breathes; a low battery in the air blinks.
     * 电池形电量条（机身 + 正极凸起，内部按电量填充）。充电时呼吸闪烁；空中低电量时闪烁警示。
     */
    private static void batteryMeter(TabletCanvas c, int x, int y, int percent, boolean charging, boolean lowBlink,
                                     long now) {
        int w = DRONE_BATTERY_W;
        int h = DRONE_BATTERY_H;
        c.roundRectOutline(x, y, w, h, 2.5f, 1f, 0x40FFFFFF);
        c.roundRect(x + w + 1, y + 3, 2, h - 6, 0.8f, 0x40FFFFFF);
        int inner = w - 4;
        float filled = inner * Math.max(0, Math.min(100, percent)) / 100f;
        if (filled <= 0f) {
            return;
        }
        int color = percent >= DRONE_MID_PERCENT ? TabletTheme.SUCCESS
                : percent >= DRONE_LOW_PERCENT ? TabletTheme.WARNING : TabletTheme.DANGER;
        if (charging) {
            float phase = (float) (0.5 + 0.5 * Math.sin(2.0 * Math.PI * (now % DRONE_CHARGE_PULSE_MS) / DRONE_CHARGE_PULSE_MS));
            color = TabletTheme.multiplyAlpha(color, 0.6f + 0.4f * phase);
        } else if (lowBlink && (now % DRONE_LOW_BLINK_MS) >= DRONE_LOW_BLINK_MS / 2) {
            color = TabletTheme.multiplyAlpha(color, 0.4f);
        }
        c.roundRect(x + 2, y + 2, Math.max(1.5f, filled), h - 4, 1.2f, color);
    }

    private enum MetaGlyph { GRENADE, BURST, LOCATE }

    /**
     * One "· glyph text" meta item after {@code x}; returns the new x, or -1 when it did not fit (nothing drawn).
     * 在 x 之后绘制一个“· 图形 文字”信息项；返回新的 x，放不下时返回 -1（不绘制）。
     */
    private static int metaItem(TabletCanvas c, TextRenderer tr, int x, int right, int centerY, String text, int ink,
                                int glyphColor, MetaGlyph glyph) {
        int width = DRONE_META_GAP + DRONE_META_GLYPH + 3 + visibleWidth(tr, text);
        if (x + width > right) {
            return -1;
        }
        c.circle(x + DRONE_META_GAP / 2f, centerY, 1f, TabletTheme.TEXT_3);
        float gx = x + DRONE_META_GAP;
        float gy = centerY - DRONE_META_GLYPH / 2f;
        switch (glyph) {
            case GRENADE -> TabletIcons.grenade(c, gx, gy, DRONE_META_GLYPH, glyphColor);
            case BURST -> TabletIcons.burst(c, gx, gy, DRONE_META_GLYPH, glyphColor);
            case LOCATE -> TabletIcons.locate(c, gx, gy, DRONE_META_GLYPH, glyphColor);
        }
        int textX = x + DRONE_META_GAP + DRONE_META_GLYPH + 3;
        return textX + c.text(tr, text, textX, centerY - 4, ink);
    }

    /**
     * Pilot controls cheat-sheet centred in {@code area}: keycaps plus action labels. Key names follow the player's
     * bindings (the same KeyBindings the pilot client reads). When the keycaps do not fit, default bindings switch to
     * their short names (LMB, Shift...); if that still does not fit, one trimmed plain line.
     * 在 area 中居中绘制驾驶操作提示：键帽加动作说明。键名随玩家的按键绑定变化（与驾驶客户端读取的 KeyBinding 相同）。
     * 键帽放不下时，默认绑定改用简称（左键、Shift 等）；仍放不下时退化为一行截断文字。
     */
    static void droneControls(TabletCanvas c, TextRenderer tr, TabletLayout.Rect area, GameOptions options) {
        if (area.width() <= 0 || area.height() < 8) {
            return;
        }
        List<ControlItem> items = controlItems(options, false);
        int total = controlsWidth(tr, items);
        if (total > area.width()) {
            items = controlItems(options, true);
            total = controlsWidth(tr, items);
        }
        int centerY = area.centerY();
        int textY = centerY - 4;
        if (total > area.width() || area.height() < KEYCAP_H) {
            c.textCentered(tr, TabletCanvas.trim(tr, controlsLine(controlItems(options, true)), area.width()),
                    area.centerX(), textY, TabletTheme.TEXT_3);
            return;
        }
        int x = area.x() + (area.width() - total) / 2;
        int capY = centerY - KEYCAP_H / 2;
        for (int i = 0; i < items.size(); i++) {
            ControlItem item = items.get(i);
            if (i > 0) {
                x += KEYCAP_ITEM_GAP;
            }
            for (int k = 0; k < item.keys().size(); k++) {
                if (k > 0) {
                    x += KEYCAP_KEY_GAP;
                    x += c.text(tr, "/", x, textY, TabletTheme.TEXT_3) - 1 + KEYCAP_KEY_GAP;
                }
                String key = item.keys().get(k);
                int capW = visibleWidth(tr, key) + 2 * KEYCAP_PAD;
                // Keycap: raised face over a 1 px darker lip. 键帽：凸起键面下方 1 px 深色边。
                c.roundRect(x, capY + 1, capW, KEYCAP_H, 3f, 0x66000000);
                c.roundRect(x, capY, capW, KEYCAP_H, 3f, TabletTheme.SURFACE_HOVER);
                c.roundRectOutline(x, capY, capW, KEYCAP_H, 3f, 1f, TabletTheme.HAIRLINE);
                c.text(tr, key, x + KEYCAP_PAD, textY, TabletTheme.TEXT_2);
                x += capW;
            }
            x += KEYCAP_ACTION_GAP;
            x += c.text(tr, item.action(), x, textY, TabletTheme.TEXT_3) - 1;
        }
    }

    /** One cheat-sheet entry: keycaps (joined by "/") then the action label. 操作提示的一项：键帽（以“/”分隔）与动作说明。 */
    private record ControlItem(List<String> keys, String action) {
    }

    private static List<ControlItem> controlItems(GameOptions options, boolean compact) {
        return List.of(
                new ControlItem(moveKeys(options), Text.translatable(KEY + "drone.control.move").getString()),
                new ControlItem(List.of(keyName(options.jumpKey, "drone.key.up", compact),
                        keyName(options.sneakKey, "drone.key.down", compact)),
                        Text.translatable(KEY + "drone.control.lift").getString()),
                new ControlItem(List.of(keyName(options.attackKey, "drone.key.fire", compact)),
                        Text.translatable(KEY + "drone.control.fire").getString()),
                new ControlItem(List.of(keyName(options.useKey, "drone.key.exit", compact)),
                        Text.translatable(KEY + "drone.control.exit").getString()));
    }

    /**
     * Forward/left/back/right as one keycap when every name is a single character ("WASD", "ZQSD" on AZERTY, a rebind
     * such as "ESDF"), else four keycaps.
     * 前/左/后/右：每个键名都是单个字符时合为一个键帽（“WASD”、AZERTY 键盘上的“ZQSD”、改键后的“ESDF”等），否则为四个键帽。
     */
    private static List<String> moveKeys(GameOptions options) {
        KeyBinding[] bindings = {options.forwardKey, options.leftKey, options.backKey, options.rightKey};
        List<String> names = new ArrayList<>(bindings.length);
        StringBuilder joined = new StringBuilder();
        boolean single = true;
        for (KeyBinding binding : bindings) {
            String name = binding.getBoundKeyLocalizedText().getString();
            names.add(name);
            joined.append(name);
            single &= name.codePointCount(0, name.length()) == 1;
        }
        return single ? List.of(joined.toString()) : names;
    }

    /**
     * Compact form shows a default binding by its short name (e.g. "LMB" for Left Button); otherwise the bound key's
     * own localized name. 紧凑模式下默认绑定显示简称（如“左键”）；否则显示所绑定按键的本地化名称。
     */
    private static String keyName(KeyBinding binding, String shortKey, boolean compact) {
        return compact && binding.isDefault() ? Text.translatable(KEY + shortKey).getString()
                : binding.getBoundKeyLocalizedText().getString();
    }

    private static int controlsWidth(TextRenderer tr, List<ControlItem> items) {
        int total = 0;
        for (int i = 0; i < items.size(); i++) {
            ControlItem item = items.get(i);
            if (i > 0) {
                total += KEYCAP_ITEM_GAP;
            }
            for (int k = 0; k < item.keys().size(); k++) {
                total += visibleWidth(tr, item.keys().get(k)) + 2 * KEYCAP_PAD
                        + (k > 0 ? 2 * KEYCAP_KEY_GAP + visibleWidth(tr, "/") : 0);
            }
            total += KEYCAP_ACTION_GAP + visibleWidth(tr, item.action());
        }
        return total;
    }

    private static String controlsLine(List<ControlItem> items) {
        StringBuilder plain = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            ControlItem item = items.get(i);
            if (i > 0) {
                plain.append(" · ");
            }
            plain.append(String.join("/", item.keys())).append(' ').append(item.action());
        }
        return plain.toString();
    }

    /** Breathing dot: a fading halo plus a core whose alpha follows the same 1.2 s phase. 呼吸圆点：渐隐光环与同相位核心。 */
    static void pulseDot(TabletCanvas c, float cx, float cy, float radius, int color, long now) {
        float phase = (float) (0.5 + 0.5 * Math.sin(2.0 * Math.PI * (now % PULSE_PERIOD_MS) / PULSE_PERIOD_MS));
        c.circle(cx, cy, radius + 2f * phase, TabletTheme.withAlpha(color, Math.round(0x55 * (1f - phase))));
        c.circle(cx, cy, radius, TabletTheme.multiplyAlpha(color, 0.7f + 0.3f * phase));
    }

    // ============================================================================================ buttons

    /**
     * Paints a TabletPressable. An inactive widget ({@code b.active == false}) or LOCKED style never shows hover;
     * an inactive WARNING_TOGGLED keeps full-strength warning ink on a warning tint so a chosen-but-frozen state
     * (abstained then locked) reads as "chosen", not as disabled.
     * 绘制 TabletPressable。未启用控件或 LOCKED 样式不显示悬停；未启用的 WARNING_TOGGLED 保留完整警示色文字与警示底色，
     * 使“已选择但已冻结”（弃票后锁定）读作“已选中”而非禁用。
     */
    static void button(TabletCanvas c, TextRenderer tr, TabletPressable b, ButtonStyle style, ButtonIcon icon,
                       Text label, boolean hovered, TabletTheme.Accent a) {
        int x = b.getX();
        int y = b.getY();
        int w = b.getWidth();
        int h = b.getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        boolean inactive = !b.active || style == ButtonStyle.LOCKED;
        boolean hover = hovered && !inactive;
        int fill;
        int ink;
        if (inactive) {
            if (style == ButtonStyle.WARNING_TOGGLED) {
                fill = TabletTheme.withAlpha(TabletTheme.WARNING, 0x33);
                ink = TabletTheme.WARNING;
            } else if (style == ButtonStyle.DANGER_SOFT) {
                fill = TabletTheme.withAlpha(TabletTheme.DANGER, 0x2E);
                ink = TabletTheme.DANGER;
            } else {
                fill = TabletTheme.SURFACE_HOVER;
                ink = TabletTheme.TEXT_3;
            }
        } else {
            switch (style) {
                case PRIMARY -> {
                    fill = hover ? TabletTheme.mix(a.base(), a.pale(), 0.2f) : a.base();
                    ink = TabletTheme.WHITE;
                }
                case DANGER -> {
                    fill = hover ? TabletTheme.mix(TabletTheme.DANGER, TabletTheme.WHITE, 0.14f) : TabletTheme.DANGER;
                    ink = TabletTheme.WHITE;
                }
                case DANGER_SOFT -> {
                    fill = TabletTheme.withAlpha(TabletTheme.DANGER, hover ? 0x4D : 0x38);
                    ink = TabletTheme.DANGER;
                }
                case WARNING_TOGGLED -> {
                    fill = TabletTheme.withAlpha(TabletTheme.WARNING, hover ? 0x4D : 0x38);
                    ink = TabletTheme.WARNING;
                }
                default -> {
                    fill = hover ? TabletTheme.mix(TabletTheme.SURFACE_HOVER, TabletTheme.WHITE, 0.07f)
                            : TabletTheme.SURFACE_HOVER;
                    ink = TabletTheme.TEXT;
                }
            }
        }
        float r = Math.min(BUTTON_R, h / 2f);
        c.roundRect(x, y, w, h, r, fill);
        focusRing(c, b, r, a);

        boolean hasIcon = icon != ButtonIcon.NONE && w >= BUTTON_ICON + 2 * BUTTON_PAD;
        int iconSpace = hasIcon ? BUTTON_ICON + BUTTON_ICON_GAP : 0;
        String text = label == null ? "" : TabletCanvas.trim(tr, label.getString(), w - 2 * BUTTON_PAD - iconSpace);
        int textW = visibleWidth(tr, text);
        int contentW = text.isEmpty() ? (hasIcon ? BUTTON_ICON : 0) : iconSpace + textW;
        int left = x + (w - contentW) / 2;
        int centerY = y + h / 2;
        if (hasIcon) {
            float iconY = y + (h - BUTTON_ICON) / 2f;
            switch (icon) {
                case LOCK -> TabletIcons.lock(c, left, iconY, BUTTON_ICON, ink);
                case MEGAPHONE -> TabletIcons.megaphone(c, left, iconY, BUTTON_ICON, ink);
                case LINK -> TabletIcons.link(c, left, iconY, BUTTON_ICON, ink);
                case CLOCK -> TabletIcons.clock(c, left, iconY, BUTTON_ICON, ink);
                case SPINNER -> TabletIcons.spinner(c, left + BUTTON_ICON / 2f, iconY + BUTTON_ICON / 2f,
                        BUTTON_ICON / 2f - 1f, Util.getMeasuringTimeMs(), ink);
                default -> {
                }
            }
        }
        if (!text.isEmpty()) {
            c.text(tr, text, left + iconSpace, centerY - 4, ink);
        }
    }

    // ============================================================================================ helpers

    /** Keyboard-focus ring: 1 px accent.pale outline, 1 px gap outside the widget. 键盘焦点环：控件外 1 px 处的 1 px 描边。 */
    static void focusRing(TabletCanvas c, TabletPressable widget, float radius, TabletTheme.Accent a) {
        if (!widget.keyboardFocused()) {
            return;
        }
        c.roundRectOutline(widget.getX() - 2, widget.getY() - 2, widget.getWidth() + 4, widget.getHeight() + 4,
                radius + 2f, 1f, a.pale());
    }

    /**
     * Inked width: TextRenderer advances include one trailing spacing pixel, which would bias centring/padding.
     * 实际着墨宽度：TextRenderer 的字宽包含末尾 1 px 字距，直接使用会让居中与内边距偏移。
     */
    static int visibleWidth(TextRenderer tr, String s) {
        return s.isEmpty() ? 0 : Math.max(0, tr.getWidth(s) - 1);
    }

    /**
     * {@link TabletCanvas#trim} for styled text: keeps per-run colours and appends "…" (default style) when cut.
     * 带样式文本的截断：保留各段颜色，被截断时追加“…”（默认样式）。
     */
    static OrderedText trimStyled(TextRenderer tr, Text text, int maxWidth) {
        if (tr.getWidth(text) <= maxWidth) {
            return text.asOrderedText();
        }
        int room = maxWidth - tr.getWidth(ELLIPSIS);
        if (room < 0) {
            return OrderedText.EMPTY;
        }
        return Language.getInstance().reorder(StringVisitable.concat(tr.trimToWidth(text, room),
                StringVisitable.plain(ELLIPSIS)));
    }

    /**
     * Width-based (CJK-safe) wrap to at most {@code maxLines}; the last kept line gets "…" when text was cut.
     * 按像素宽度换行（兼容 CJK），最多 maxLines 行；有内容被截掉时末行追加“…”。
     */
    static List<String> wrap(TextRenderer tr, String text, int width, int maxLines) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isBlank() || width <= 0 || maxLines <= 0) {
            return out;
        }
        List<StringVisitable> parts = tr.getTextHandler().wrapLines(text, width, Style.EMPTY);
        int count = Math.min(parts.size(), maxLines);
        for (int i = 0; i < count; i++) {
            String line = parts.get(i).getString().strip();
            // A single glyph wider than the box can still overflow; hard-trim it. 单个字形超宽时强制截断。
            out.add(TabletCanvas.trim(tr, line, width));
        }
        if (parts.size() > maxLines && count > 0) {
            String last = parts.get(count - 1).getString().strip();
            int room = width - tr.getWidth(ELLIPSIS);
            out.set(count - 1, room < 0 ? "" : tr.trimToWidth(last, room).stripTrailing() + ELLIPSIS);
        }
        return out;
    }
}
