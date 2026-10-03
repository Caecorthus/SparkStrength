package annina.sparkstrength.client.screen.tablet;

import annina.sparkstrength.network.tablet.TabletSnapshot;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.IntBinaryOperator;

/**
 * Wrapped chat bubbles, newest at the bottom, scrolled in logical pixels from the bottom edge.
 * 自动换行的聊天气泡，最新消息在底部，以距底部的逻辑像素滚动。
 *
 * <p>Anonymous rows (null sender uuid, redacted server-side) are never grouped into runs and never draw a skin or any
 * uuid-derived data: two "???" rows may be different people, and grouping would imply a single sender.
 * 匿名行（发送者 uuid 为空，已由服务端脱敏）从不合并为连续组，也从不绘制皮肤或任何由 uuid 推导的数据：
 * 两条“???”可能来自不同的人，合并会暗示同一发送者。</p>
 */
public final class TabletChatView {
    private static final int PAD_X = 8;
    private static final int PAD_Y = 6;
    private static final int LINE_H = 10;
    private static final int RADIUS = 10;
    private static final int TAIL_RADIUS = 3;
    private static final int MIN_BUBBLE_W = 2 * RADIUS;
    private static final int MAX_BUBBLE_W = 300;
    private static final float MAX_BUBBLE_FRACTION = 0.72f;
    private static final int AVATAR = 20;
    private static final int AVATAR_RADIUS = 5;
    private static final int AVATAR_GAP = 6;
    private static final int NAME_H = 11;
    private static final int GAP_IN_RUN = 3;
    private static final int GAP_BETWEEN_RUNS = 9;
    private static final int TOP_PAD = 6;
    private static final int BOTTOM_PAD = 6;

    private List<TabletSnapshot.ChatRow> cachedRows = List.of();
    private @Nullable UUID cachedSelf;
    private int cachedWidth = -1;
    private @Nullable TextRenderer cachedRenderer;
    private List<Bubble> bubbles = List.of();
    private int contentHeight;
    private IntBinaryOperator backdrop = (x, y) -> TabletTheme.mix(TabletTheme.SCREEN_TOP, TabletTheme.SCREEN_BOTTOM, 0.5f);

    /** Recomputes the layout only when (rows identity/equals, width, selfUuid) changed. 仅在行、宽度或自身 uuid 变化时重排。 */
    public void layout(TextRenderer tr, List<TabletSnapshot.ChatRow> rows, @Nullable UUID self, int width) {
        List<TabletSnapshot.ChatRow> safeRows = rows == null ? List.of() : rows;
        if (tr == cachedRenderer && width == cachedWidth && Objects.equals(self, cachedSelf)) {
            if (safeRows == cachedRows) {
                return;
            }
            if (safeRows.equals(cachedRows)) {
                // Adopt the equal list's identity so later frames take the identity fast path.
                // 内容相同时采用新列表引用，之后的帧走引用相等的快速路径。
                cachedRows = safeRows;
                return;
            }
        }
        cachedRenderer = tr;
        cachedWidth = width;
        cachedSelf = self;
        cachedRows = safeRows;
        rebuild(tr, safeRows, self, Math.max(0, width));
    }

    public int contentHeight() {
        return contentHeight;
    }

    public int maxScroll(int viewportHeight) {
        return Math.max(0, contentHeight - viewportHeight);
    }

    /**
     * Distance (logical px) from the bottom of row {@code rowIndex}'s bubble to the end of the content, excluding the
     * bottom padding; 0 for an out-of-range index. Used to keep a scrolled-up reader anchored when rows are appended.
     * 第 rowIndex 行气泡底部到内容末尾（不含底部留白）的距离；索引越界时为 0。用于追加新消息时保持已上翻读者的位置。
     */
    public int heightBelow(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= bubbles.size()) {
            return 0;
        }
        Bubble bubble = bubbles.get(rowIndex);
        return Math.max(0, contentHeight - BOTTOM_PAD - (bubble.bubbleTop() + bubble.height()));
    }

    /**
     * Colour behind the chat list at a logical canvas point, used to mask the avatar corners so they blend with the
     * screen gradient. Defaults to the middle of the screen gradient.
     * 聊天列表背后某逻辑画布点的颜色，用于遮罩头像圆角使其与屏幕渐变融合。默认取屏幕渐变中间色。
     */
    public void setBackdrop(IntBinaryOperator colorAt) {
        this.backdrop = Objects.requireNonNull(colorAt, "colorAt");
    }

    /**
     * Draws inside [x,y,w,h] (caller clips), scrollFromBottom px from the bottom (0 = newest visible). Everything is
     * opaque; the caller softens the scrolled edges with a backdrop-coloured mask painted on top.
     * 在 [x,y,w,h] 内绘制（调用方负责裁剪），距底部 scrollFromBottom 像素（0 = 最新可见）。全部不透明绘制；
     * 滚动边缘由调用方在上层叠加背景色遮罩来柔化。
     */
    public void render(TabletCanvas canvas, TextRenderer tr, int x, int y, int w, int h, int scrollFromBottom,
                       TabletTheme.Accent accent) {
        if (bubbles.isEmpty() || w <= 0 || h <= 0) {
            return;
        }
        int scroll = Math.max(0, Math.min(scrollFromBottom, maxScroll(h)));
        // Short histories stick to the bottom edge; scroll 0 always puts the newest bubble there.
        // 消息较少时贴底显示；scroll 为 0 时最新气泡总在底边。
        int originY = y + h - contentHeight + scroll;
        int viewTop = y;
        int viewBottom = y + h;
        String anonymousLabel = null;
        for (int index = bubbles.size() - 1; index >= 0; index--) {
            Bubble bubble = bubbles.get(index);
            int blockTop = originY + bubble.blockTop();
            int bubbleTop = originY + bubble.bubbleTop();
            int bubbleBottom = bubbleTop + bubble.height();
            if (bubbleBottom <= viewTop) {
                break;
            }
            if (blockTop >= viewBottom) {
                continue;
            }
            if (bubble.own()) {
                drawOwn(canvas, tr, x + w - bubble.width(), bubbleTop, bubble, accent);
                continue;
            }
            int bubbleX = x + AVATAR + AVATAR_GAP;
            if (bubble.header()) {
                String name;
                int nameColor;
                if (bubble.anonymous()) {
                    if (anonymousLabel == null) {
                        anonymousLabel = Text.translatable("screen.sparkstrength.tablet.chat.anonymous").getString();
                    }
                    name = anonymousLabel;
                    nameColor = TabletTheme.TEXT_3;
                } else {
                    name = bubble.name();
                    nameColor = accent.pale();
                }
                canvas.text(tr, name, bubbleX + 3, blockTop, nameColor);
            }
            drawOther(canvas, tr, bubbleX, bubbleTop, bubble);
            if (bubble.avatar()) {
                int avatarY = bubbleBottom - AVATAR;
                if (bubble.anonymous() || bubble.uuid() == null) {
                    drawAnonymousAvatar(canvas, tr, x, avatarY);
                } else {
                    int surface = backdrop.applyAsInt(x + AVATAR / 2, avatarY + AVATAR / 2);
                    canvas.avatar(bubble.uuid(), bubble.skinName(), x, avatarY, AVATAR, AVATAR_RADIUS, surface);
                }
            }
        }
    }

    private void rebuild(TextRenderer tr, List<TabletSnapshot.ChatRow> rows, @Nullable UUID self, int width) {
        if (rows.isEmpty()) {
            bubbles = List.of();
            contentHeight = 0;
            return;
        }
        int maxBubble = Math.min((int) (width * MAX_BUBBLE_FRACTION), MAX_BUBBLE_W);
        int ownMax = Math.max(MIN_BUBBLE_W, Math.min(maxBubble, width));
        int otherMax = Math.max(MIN_BUBBLE_W, Math.min(maxBubble, width - AVATAR - AVATAR_GAP));
        List<Bubble> built = new ArrayList<>(rows.size());
        int cursor = TOP_PAD;
        for (int index = 0; index < rows.size(); index++) {
            TabletSnapshot.ChatRow row = rows.get(index);
            TabletSnapshot.ChatRow previous = index > 0 ? rows.get(index - 1) : null;
            TabletSnapshot.ChatRow next = index + 1 < rows.size() ? rows.get(index + 1) : null;
            boolean startsRun = !sameRun(previous, row);
            boolean endsRun = !sameRun(row, next);
            boolean own = row.senderUuid() != null && row.senderUuid().equals(self);
            boolean anonymous = row.isAnonymous();
            if (index > 0) {
                cursor += startsRun ? GAP_BETWEEN_RUNS : GAP_IN_RUN;
            }

            int maxWidth = own ? ownMax : otherMax;
            String message = row.message() == null ? "" : row.message();
            List<OrderedText> lines = tr.wrapLines(StringVisitable.plain(message), Math.max(1, maxWidth - 2 * PAD_X));
            if (lines.isEmpty()) {
                lines = List.of(OrderedText.EMPTY);
            }
            int textWidth = 0;
            for (OrderedText line : lines) {
                textWidth = Math.max(textWidth, tr.getWidth(line));
            }
            int bubbleWidth = Math.max(MIN_BUBBLE_W, Math.min(maxWidth, textWidth + 2 * PAD_X));
            int bubbleHeight = lines.size() * LINE_H + 2 * PAD_Y - 2;

            boolean header = !own && startsRun;
            int blockTop = cursor;
            if (header) {
                cursor += NAME_H;
            }
            int bubbleTop = cursor;
            cursor += bubbleHeight;

            // Anonymous rows carry no uuid/name by server redaction; keep them that way here.
            // 匿名行经服务端脱敏不含 uuid/名字，此处保持不含。
            UUID uuid = anonymous ? null : row.senderUuid();
            String skinName = anonymous ? "" : row.senderName();
            String name = anonymous ? "" : TabletCanvas.trim(tr, row.senderName(), Math.max(0, otherMax - 3));
            built.add(new Bubble(blockTop, bubbleTop, bubbleWidth, bubbleHeight, List.copyOf(lines), own, anonymous,
                    header, !own && endsRun, endsRun, uuid, skinName, name));
        }
        cursor += BOTTOM_PAD;
        bubbles = List.copyOf(built);
        contentHeight = cursor;
    }

    /** Runs group only consecutive rows with the same NON-NULL sender. 只有相同且非空发送者的连续行才成组。 */
    private static boolean sameRun(@Nullable TabletSnapshot.ChatRow a, @Nullable TabletSnapshot.ChatRow b) {
        if (a == null || b == null || a.senderUuid() == null || b.senderUuid() == null) {
            return false;
        }
        return a.senderUuid().equals(b.senderUuid());
    }

    private static void drawOwn(TabletCanvas canvas, TextRenderer tr, int bx, int by, Bubble bubble,
                                TabletTheme.Accent accent) {
        fillBubble(canvas, bx, by, bubble.width(), bubble.height(), bubble.tail(), true, accent.deep(), accent.base());
        drawLines(canvas, tr, bx, by, bubble, TabletTheme.WHITE);
    }

    private static void drawOther(TabletCanvas canvas, TextRenderer tr, int bx, int by, Bubble bubble) {
        fillBubble(canvas, bx, by, bubble.width(), bubble.height(), bubble.tail(), false, TabletTheme.SURFACE,
                TabletTheme.SURFACE);
        drawLines(canvas, tr, bx, by, bubble, TabletTheme.TEXT);
    }

    /**
     * Opaque bubble body; the tail overdraws the bottom corner on the sender's side with a TAIL_RADIUS square whose
     * vertical gradient continues the body's seamlessly.
     * 不透明气泡主体；尾巴在发送方一侧下角叠画一个 TAIL_RADIUS 小圆角方块，其纵向渐变与主体无缝衔接。
     */
    private static void fillBubble(TabletCanvas canvas, int bx, int by, int w, int h, boolean tail, boolean tailRight,
                                   int topColor, int bottomColor) {
        canvas.roundRectGradientV(bx, by, w, h, RADIUS, topColor, bottomColor);
        if (!tail) {
            return;
        }
        int cornerX = tailRight ? bx + w - RADIUS : bx;
        int cornerY = by + h - RADIUS;
        int tailTop = TabletTheme.mix(topColor, bottomColor, (h - RADIUS) / (float) h);
        canvas.roundRectGradientV(cornerX, cornerY, RADIUS, RADIUS, TAIL_RADIUS, tailTop, bottomColor);
    }

    private static void drawLines(TabletCanvas canvas, TextRenderer tr, int bx, int by, Bubble bubble, int color) {
        int lineY = by + PAD_Y;
        for (OrderedText line : bubble.lines()) {
            canvas.text(tr, line, bx + PAD_X, lineY, color);
            lineY += LINE_H;
        }
    }

    private static void drawAnonymousAvatar(TabletCanvas canvas, TextRenderer tr, int ax, int ay) {
        canvas.circle(ax + AVATAR / 2f, ay + AVATAR / 2f, AVATAR / 2f, TabletTheme.ANONYMOUS_DISC);
        int glyphWidth = Math.max(0, tr.getWidth("?") - 1);
        canvas.text(tr, "?", ax + (AVATAR - glyphWidth + 1) / 2, ay + (AVATAR - 7) / 2, TabletTheme.TEXT_2);
    }

    /**
     * One laid-out message. Y values are relative to the content top. {@code uuid}/{@code skinName} are null/empty
     * for anonymous rows.
     * 一条已排版的消息；Y 相对内容顶部。匿名行的 uuid/skinName 为空。
     */
    private record Bubble(
            int blockTop,
            int bubbleTop,
            int width,
            int height,
            List<OrderedText> lines,
            boolean own,
            boolean anonymous,
            boolean header,
            boolean avatar,
            boolean tail,
            @Nullable UUID uuid,
            String skinName,
            String name
    ) {
    }
}
