package annina.sparkstrength.client.screen.tablet;

import annina.sparkstrength.tablet.TabletLayout;
import net.minecraft.client.font.TextRenderer;
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
 * Stateless painters shared by every tablet section (empty state, scroll thumb, pulse dot, text helpers) and every
 * {@link TabletPressable} button look; each section's own cards live in its painter (TabletConnectionsPainter,
 * TabletMeetingPainter, TabletSuspectsPainter, TabletDoorLogPainter, TabletDronePainter, TabletSabotagePainter).
 * Pure presentation of the server-redacted snapshot: nothing here decides what a player may see or do; the pages pass
 * in already-derived flags (clickable, active, state).
 * 平板各分区共用的无状态绘制器（空状态、滚动条、脉冲圆点、文字工具）与所有 TabletPressable 按钮外观；各分区自己的卡片在
 * 各自的绘制器中（成员、会议、嫌疑、房门记录、无人机、破坏）。仅呈现服务端已脱敏的快照：这里不决定玩家能看到或能做什么，
 * 可点击/启用/状态等标志均由页面预先算好后传入。
 *
 * <p>Package-visible geometry constants are shared with the screen and its pages, which place the widgets and scroll
 * by them; changing one changes hit boxes as well as visuals. Private constants are painter-only; button hit boxes that
 * depend on them are exposed through rect helpers ({@code heroButtonRect}, {@code suspectButtonRect},
 * {@code droneButtonRect}). Text is 8 px high and
 * is always drawn at {@code centreY - 4}.
 * 包可见的几何常量与界面及其页面共享，它们据此放置控件与滚动；修改它们会同时改变点击区域与外观。私有常量仅供绘制器使用，
 * 依赖它们的按钮点击区域通过 heroButtonRect、suspectButtonRect、droneButtonRect 暴露。文字高 8 px，一律绘制在 centreY - 4。</p>
 */
final class TabletSectionPainter {
    static final int GRID_GAP = 8;

    static final String KEY = "screen.sparkstrength.tablet.";
    static final int CARD_R = 8;
    static final int LINE_STEP = 10;
    private static final String ELLIPSIS = "…";

    private static final int EMPTY_DISC = 44;
    private static final int EMPTY_ICON = 22;
    private static final int EMPTY_GAP = 12;
    private static final int EMPTY_HINT_GAP = 5;

    static final int PILL_PAD = 7;
    static final int MIN_NAME_W = 28;

    private static final long PULSE_PERIOD_MS = 1200L;

    private static final int BUTTON_R = 8;
    static final int BUTTON_ICON = 12;
    static final int BUTTON_ICON_GAP = 5;
    private static final int BUTTON_PAD = 8;

    private TabletSectionPainter() {
    }

    enum EmptyIcon { MEMBERS, CHAT, SHIELD, DOOR, DRONE }

    /**
     * DANGER_SOFT: danger tint + danger ink that an inactive button keeps, so an alarm reason (a drone out of control)
     * stays legible on a danger-tinted card while still reading as not clickable.
     * DANGER_SOFT：危险色底纹加危险色文字，未启用时保留，使警报原因（无人机失控）在偏红的卡片上仍清晰可读，同时明显不可点击。
     */
    enum ButtonStyle { PRIMARY, SECONDARY, DANGER, DANGER_SOFT, WARNING_TOGGLED, LOCKED }

    enum ButtonIcon { NONE, LOCK, MEGAPHONE, LINK, CLOCK, SPINNER }

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
