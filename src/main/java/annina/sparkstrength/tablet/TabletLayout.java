package annina.sparkstrength.tablet;

import java.util.ArrayList;
import java.util.List;

public record TabletLayout(
        Rect viewport,
        Rect panel,
        Rect statusBar,
        Rect navigation,
        Rect body,
        Rect footer,
        Rect list,
        Rect closeButton,
        List<Rect> tabs,
        Mode mode,
        int visibleRows
) {
    private static final int WIDE_MIN_WIDTH = 500;
    private static final int NARROW_MAX_WIDTH = 359;
    private static final int NARROW_MAX_HEIGHT = 239;
    private static final int OUTER_INSET = 12;
    private static final int NARROW_INSET = 4;
    private static final int PREFERRED_WIDTH = 560;
    private static final int PREFERRED_HEIGHT = 320;
    private static final int STATUS_HEIGHT = 34;
    private static final int RAIL_WIDTH = 118;
    private static final int TOP_NAV_HEIGHT = 30;
    private static final int FOOTER_HEIGHT = 38;
    private static final int ROW_HEIGHT = 24;
    private static final int DEFAULT_TAB_COUNT = 4;
    private static final int RAIL_TAB_STEP = 28;
    private static final int RAIL_TAB_HEIGHT = 22;
    private static final int CHANNEL_CARD_HEIGHT = 40;
    private static final int CHANNEL_CARD_BOTTOM_GAP = 12;

    public static TabletLayout forViewport(int viewportWidth, int viewportHeight) {
        return forViewport(viewportWidth, viewportHeight, DEFAULT_TAB_COUNT);
    }

    /**
     * Lays out the tablet for the channel's visible section count; 0 tabs is the no-signal screen.
     * 按频道可见分区数量布局平板；0 个标签页即无信号界面。
     */
    public static TabletLayout forViewport(int viewportWidth, int viewportHeight, int tabCount) {
        int tabs = Math.max(0, tabCount);
        Rect viewport = new Rect(0, 0, Math.max(0, viewportWidth), Math.max(0, viewportHeight));
        Mode mode = modeFor(viewport.width(), viewport.height());
        int inset = mode == Mode.NARROW ? NARROW_INSET : OUTER_INSET;
        int panelWidth = Math.max(0, Math.min(PREFERRED_WIDTH, viewport.width() - inset * 2));
        int panelHeight = Math.max(0, Math.min(PREFERRED_HEIGHT, viewport.height() - inset * 2));
        int panelX = Math.max(0, (viewport.width() - panelWidth) / 2);
        int panelY = Math.max(0, (viewport.height() - panelHeight) / 2);
        Rect panel = new Rect(panelX, panelY, panelWidth, panelHeight);
        Rect status = new Rect(panel.x(), panel.y(), panel.width(), Math.min(STATUS_HEIGHT, panel.height()));
        Rect close = new Rect(Math.max(panel.x(), panel.right() - 28), panel.y() + 6, Math.min(22, panel.width()), Math.min(22, status.height()));

        return mode == Mode.WIDE
                ? wideLayout(viewport, panel, status, close, tabs)
                : topNavigationLayout(viewport, panel, status, close, mode, tabs);
    }

    /**
     * WIDE-only channel card pinned to the bottom of the rail; empty in other modes or when it would hit a tab.
     * 仅 WIDE 模式在侧栏底部显示的频道卡片；其它模式或会与标签页重叠时为空。
     */
    public Rect channelCard() {
        Rect empty = new Rect(navigation.x(), navigation.y(), 0, 0);
        if (mode != Mode.WIDE) {
            return empty;
        }
        Rect card = new Rect(
                navigation.x() + 8,
                navigation.bottom() - CHANNEL_CARD_BOTTOM_GAP - CHANNEL_CARD_HEIGHT,
                Math.max(0, navigation.width() - 16),
                CHANNEL_CARD_HEIGHT
        );
        int tabsBottom = tabs.isEmpty() ? navigation.y() : tabs.get(tabs.size() - 1).bottom();
        // Leave room for the MUTED caption drawn above the card.
        // 为卡片上方的说明文字预留空间。
        return card.y() - 14 < tabsBottom || !navigation.contains(card) ? empty : card;
    }

    private static TabletLayout wideLayout(Rect viewport, Rect panel, Rect status, Rect close, int tabCount) {
        Rect navigation = new Rect(panel.x(), status.bottom(), Math.min(RAIL_WIDTH, panel.width()), Math.max(0, panel.height() - status.height()));
        int contentX = navigation.right();
        int contentWidth = Math.max(0, panel.right() - contentX);
        Rect footer = footer(contentX, contentWidth, status.bottom(), panel.bottom());
        Rect body = new Rect(contentX, status.bottom(), contentWidth, Math.max(0, footer.y() - status.bottom()));
        Rect list = body.inset(10, 34, 10, 8);
        return new TabletLayout(viewport, panel, status, navigation, body, footer, list, close,
                railTabs(navigation, tabCount), Mode.WIDE, list.height() / ROW_HEIGHT);
    }

    private static TabletLayout topNavigationLayout(
            Rect viewport,
            Rect panel,
            Rect status,
            Rect close,
            Mode mode,
            int tabCount
    ) {
        Rect navigation = new Rect(panel.x(), status.bottom(), panel.width(), Math.min(TOP_NAV_HEIGHT, Math.max(0, panel.bottom() - status.bottom())));
        int contentTop = navigation.bottom();
        Rect footer = footer(panel.x(), panel.width(), contentTop, panel.bottom());
        Rect body = new Rect(panel.x(), contentTop, panel.width(), Math.max(0, footer.y() - contentTop));
        Rect list = body.inset(10, 30, 10, 6);
        return new TabletLayout(viewport, panel, status, navigation, body, footer, list, close,
                topTabs(navigation, tabCount), mode, list.height() / ROW_HEIGHT);
    }

    private static Rect footer(int x, int width, int minimumY, int panelBottom) {
        int y = Math.max(minimumY, panelBottom - FOOTER_HEIGHT);
        return new Rect(x, y, width, Math.min(FOOTER_HEIGHT, Math.max(0, panelBottom - minimumY)));
    }

    private static Mode modeFor(int width, int height) {
        if (width <= NARROW_MAX_WIDTH || height <= NARROW_MAX_HEIGHT) {
            return Mode.NARROW;
        }
        return width >= WIDE_MIN_WIDTH && height >= 310 ? Mode.WIDE : Mode.COMPACT;
    }

    private static List<Rect> railTabs(Rect navigation, int tabCount) {
        int x = navigation.x() + 8;
        int width = Math.max(0, navigation.width() - 16);
        int firstY = navigation.y() + 14;
        ArrayList<Rect> tabs = new ArrayList<>(tabCount);
        for (int index = 0; index < tabCount; index++) {
            tabs.add(new Rect(x, firstY + index * RAIL_TAB_STEP, width, RAIL_TAB_HEIGHT));
        }
        return List.copyOf(tabs);
    }

    private static List<Rect> topTabs(Rect navigation, int tabCount) {
        if (tabCount <= 0) {
            return List.of();
        }
        int gap = 4;
        int horizontalInset = 8;
        int availableWidth = Math.max(0, navigation.width() - horizontalInset * 2 - gap * (tabCount - 1));
        int tabWidth = availableWidth / tabCount;
        int remainder = availableWidth - tabWidth * tabCount;
        int x = navigation.x() + horizontalInset;
        int y = navigation.y() + 4;
        int height = Math.max(0, navigation.height() - 8);
        ArrayList<Rect> tabs = new ArrayList<>(tabCount);
        for (int index = 0; index < tabCount; index++) {
            // The last tab absorbs the rounding remainder so the row stays flush with the inset.
            // 最后一个标签吸收取整余数，使整行与内边距对齐。
            int width = index == tabCount - 1 ? tabWidth + remainder : tabWidth;
            tabs.add(new Rect(x, y, width, height));
            x += width + gap;
        }
        return List.copyOf(tabs);
    }

    public enum Mode {
        WIDE,
        COMPACT,
        NARROW
    }

    public record Rect(int x, int y, int width, int height) {
        public Rect {
            width = Math.max(0, width);
            height = Math.max(0, height);
        }

        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }

        public boolean contains(Rect other) {
            return other.x >= x && other.y >= y && other.right() <= right() && other.bottom() <= bottom();
        }

        public boolean contains(double pointX, double pointY) {
            return pointX >= x && pointX < right() && pointY >= y && pointY < bottom();
        }

        public boolean overlaps(Rect other) {
            return x < other.right() && right() > other.x && y < other.bottom() && bottom() > other.y;
        }

        public Rect inset(int leftInset, int topInset, int rightInset, int bottomInset) {
            int nextX = Math.min(right(), x + Math.max(0, leftInset));
            int nextY = Math.min(bottom(), y + Math.max(0, topInset));
            int nextRight = Math.max(nextX, right() - Math.max(0, rightInset));
            int nextBottom = Math.max(nextY, bottom() - Math.max(0, bottomInset));
            return new Rect(nextX, nextY, nextRight - nextX, nextBottom - nextY);
        }
    }
}
