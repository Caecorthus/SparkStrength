package annina.sparkstrength.tablet;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure tablet geometry in logical canvas units (see {@link #effectiveScale}); no Minecraft types, so it runs in plain
 * unit tests. Every rect is non-negative and nests inside viewport → device → screen; the status bar, rail and content
 * split the screen without overlap, and header/body/footer split the content.
 * 纯平板几何（逻辑画布单位，见 effectiveScale）；不依赖 Minecraft 类型，可直接单元测试。
 * 所有矩形尺寸非负并逐级嵌套在 viewport → device → screen 内；状态栏、侧栏与内容区互不重叠地划分屏幕，
 * 页眉/正文/页脚再划分内容区。
 */
public record TabletLayout(
        Rect viewport,
        Rect device,
        Rect screen,
        Rect statusBar,
        Rect rail,
        Rect channelBadge,
        Rect content,
        Rect header,
        Rect footer,
        Rect body,
        Rect list,
        Rect bodyNoFooter,
        Rect closeButton,
        List<Rect> tabs,
        Mode mode
) {
    private static final int DEFAULT_TAB_COUNT = 4;
    private static final int MAX_DEVICE_WIDTH = 760;
    private static final int MAX_DEVICE_HEIGHT = 430;
    private static final int REGULAR_MIN_SCREEN_WIDTH = 440;
    private static final int REGULAR_MIN_SCREEN_HEIGHT = 250;
    private static final int BADGE_GAP = 10;
    private static final int MIN_TAB_GAP = 2;
    private static final int CLOSE_SIZE = 16;
    private static final int CLOSE_RIGHT_INSET = 10;
    private static final int LIST_BOTTOM_INSET = 10;
    private static final int NO_FOOTER_BOTTOM_INSET = 12;
    private static final double REFERENCE_CANVAS_WIDTH = 640.0D;
    private static final double REFERENCE_CANVAS_HEIGHT = 360.0D;

    public TabletLayout {
        tabs = List.copyOf(tabs);
    }

    public static TabletLayout forViewport(int viewportWidth, int viewportHeight) {
        return forViewport(viewportWidth, viewportHeight, DEFAULT_TAB_COUNT);
    }

    /**
     * Lays out the tablet for the channel's visible section count; 0 tabs is the no-signal screen (no rail).
     * 按频道可见分区数量布局平板；0 个标签页即无信号界面（无侧栏）。
     */
    public static TabletLayout forViewport(int viewportWidth, int viewportHeight, int tabCount) {
        return forViewport(viewportWidth, viewportHeight, tabCount, tabCount);
    }

    /**
     * Like {@link #forViewport(int, int, int)}, but the mode is chosen for {@code referenceTabCount} (the most tabs the
     * holder can see on any of its channels, see {@code TabletUiSession.referenceSectionCount}) when that is larger.
     * 与三参数版本相同，但模式按 referenceTabCount（持有者在任一可用频道下的最多标签数）选择（取较大者）。
     */
    public static TabletLayout forViewport(int viewportWidth, int viewportHeight, int tabCount, int referenceTabCount) {
        int tabs = Math.max(0, tabCount);
        Rect viewport = new Rect(0, 0, Math.max(0, viewportWidth), Math.max(0, viewportHeight));
        // The mode is chosen for the full section set (never fewer than the four channel sections, and at least the
        // holder's reference count), not the live tab count, so switching channels never resizes the device frame;
        // only the rail/tabs react to the count.
        // 模式按完整分区数（不少于四个频道分区，且不少于持有者的参考数量）而非当前标签数决定，切换频道时设备外框不会变化；
        // 只有侧栏/标签随数量变化。
        Mode mode = modeFor(viewport, Math.max(Math.max(tabs, referenceTabCount), DEFAULT_TAB_COUNT));
        return build(viewport, mode, tabs);
    }

    /**
     * Logical scale factor (physical pixels per logical unit) the tablet renders at. It targets a ~640x360 canvas,
     * never exceeds the GUI scale, and is rounded down to an even value above 1 so the unifont (CJK glyphs,
     * oversample 2) stays pixel-exact. Logical canvas = framebuffer / result (int division).
     * 平板渲染所用的逻辑缩放（每逻辑单位的物理像素数）。目标约 640x360 画布，不超过 GUI 缩放，
     * 大于 1 时向下取偶数，使 unifont（中日韩字形，过采样 2）保持像素对齐。逻辑画布 = 帧缓冲 / 结果（整除）。
     */
    public static int effectiveScale(int framebufferWidth, int framebufferHeight, int guiScale) {
        int gui = Math.max(1, guiScale);
        double fit = Math.min(framebufferWidth / REFERENCE_CANVAS_WIDTH, framebufferHeight / REFERENCE_CANVAS_HEIGHT);
        int scale = Math.min(gui, Math.max(1, (int) Math.round(fit)));
        if (scale > 1 && (scale & 1) == 1) {
            scale -= 1;
        }
        return Math.max(1, scale);
    }

    public static int rowsFitting(int areaHeight, int rowStep) {
        if (areaHeight <= 0 || rowStep <= 0) {
            return 0;
        }
        return areaHeight / rowStep;
    }

    public static int gridColumns(int areaWidth, int minCellWidth, int gap) {
        int cellStep = minCellWidth + Math.max(0, gap);
        if (areaWidth <= 0 || cellStep <= 0) {
            return 1;
        }
        return Math.max(1, (areaWidth + Math.max(0, gap)) / cellStep);
    }

    private static Mode modeFor(Rect viewport, int referenceTabs) {
        Rect screen = screenRect(deviceRect(viewport, Mode.REGULAR), Mode.REGULAR);
        if (screen.width() < REGULAR_MIN_SCREEN_WIDTH || screen.height() < REGULAR_MIN_SCREEN_HEIGHT) {
            return Mode.NARROW;
        }
        int railHeight = screen.height() - Math.min(Mode.REGULAR.statusHeight, screen.height());
        int needed = Mode.REGULAR.tabLead() + minimalTabSpan(Mode.REGULAR, referenceTabs) + Mode.REGULAR.railPad;
        return railHeight >= needed ? Mode.REGULAR : Mode.NARROW;
    }

    private static TabletLayout build(Rect viewport, Mode mode, int tabCount) {
        Rect device = deviceRect(viewport, mode);
        Rect screen = screenRect(device, mode);
        Rect statusBar = new Rect(screen.x(), screen.y(), screen.width(), Math.min(mode.statusHeight, screen.height()));
        int belowStatus = screen.bottom() - statusBar.bottom();
        int railWidth = tabCount == 0 ? 0 : Math.min(mode.railWidth, screen.width());
        Rect rail = new Rect(screen.x(), statusBar.bottom(), railWidth, belowStatus);
        Rect content = new Rect(rail.right(), statusBar.bottom(), screen.right() - rail.right(), belowStatus);

        Rect header = new Rect(content.x(), content.y(), content.width(), Math.min(mode.headerHeight, content.height()));
        int footerY = Math.max(header.bottom(), content.bottom() - mode.footerHeight);
        Rect footer = new Rect(content.x(), footerY, content.width(), content.bottom() - footerY);
        Rect body = new Rect(content.x(), header.bottom(), content.width(), footerY - header.bottom());
        Rect list = body.inset(mode.padX, 0, mode.padX, LIST_BOTTOM_INSET);
        Rect bodyNoFooter = new Rect(content.x(), header.bottom(), content.width(), content.bottom() - header.bottom())
                .inset(mode.padX, 0, mode.padX, NO_FOOTER_BOTTOM_INSET);

        int closeSize = Math.min(CLOSE_SIZE, Math.min(statusBar.width(), statusBar.height()));
        Rect closeButton = new Rect(
                Math.max(statusBar.x(), statusBar.right() - CLOSE_RIGHT_INSET - closeSize),
                statusBar.y() + (statusBar.height() - closeSize) / 2,
                closeSize,
                closeSize
        );

        return new TabletLayout(viewport, device, screen, statusBar, rail, channelBadge(rail, mode), content, header,
                footer, body, list, bodyNoFooter, closeButton, railTabs(rail, mode, tabCount), mode);
    }

    private static Rect deviceRect(Rect viewport, Mode mode) {
        int width = Math.max(0, Math.min(MAX_DEVICE_WIDTH, viewport.width() - mode.margin * 2));
        int height = Math.max(0, Math.min(MAX_DEVICE_HEIGHT, viewport.height() - mode.margin * 2));
        return new Rect((viewport.width() - width) / 2, (viewport.height() - height) / 2, width, height);
    }

    private static Rect screenRect(Rect device, Mode mode) {
        return device.inset(mode.bezel, mode.bezel, mode.bezel, mode.bezel);
    }

    private static Rect channelBadge(Rect rail, Mode mode) {
        int size = mode.badgeSize;
        if (size <= 0 || rail.width() < size || rail.height() < mode.railPad + size) {
            return new Rect(rail.x(), rail.y(), 0, 0);
        }
        return new Rect(rail.x() + (rail.width() - size) / 2, rail.y() + mode.railPad, size, size);
    }

    private static int minimalTabSpan(Mode mode, int tabCount) {
        return tabCount <= 0 ? 0 : (tabCount - 1) * (mode.tabHeight + MIN_TAB_GAP) + mode.tabHeight;
    }

    private static List<Rect> railTabs(Rect rail, Mode mode, int tabCount) {
        if (tabCount <= 0) {
            return List.of();
        }
        int width = Math.min(mode.tabWidth, rail.width());
        int x = rail.x() + (rail.width() - width) / 2;
        int height = mode.tabHeight;
        int span = minimalTabSpan(mode, tabCount);
        int lead = mode.tabLead();
        int step;
        int firstY;
        if (rail.height() >= lead + span + mode.railPad) {
            // Nominal step, tightened towards tabHeight + 2 when the rail is short. REGULAR always lands here.
            // 标准步长；侧栏较矮时向 tabHeight + 2 收紧。REGULAR 模式必定走此分支。
            int room = rail.height() - lead - mode.railPad - height;
            step = tabCount == 1 ? mode.tabStep : Math.max(height + MIN_TAB_GAP, Math.min(mode.tabStep, room / (tabCount - 1)));
            firstY = rail.y() + lead;
        } else if (rail.height() >= span) {
            // NARROW only: give up the rail padding, keep full-size tabs centred in the rail.
            // 仅 NARROW：放弃侧栏内边距，保持标签原尺寸并在侧栏中居中。
            step = height + MIN_TAB_GAP;
            firstY = rail.y() + (rail.height() - span) / 2;
        } else {
            // Degenerate canvas: tabs shrink (down to zero height) but never leave the rail or overlap.
            // 退化画布：标签缩小（可至零高度），但绝不超出侧栏或互相重叠。
            height = Math.max(0, (rail.height() - MIN_TAB_GAP * (tabCount - 1)) / tabCount);
            step = tabCount == 1 ? 0 : Math.min(height + MIN_TAB_GAP, (rail.height() - height) / (tabCount - 1));
            firstY = rail.y();
        }
        ArrayList<Rect> tabs = new ArrayList<>(tabCount);
        for (int index = 0; index < tabCount; index++) {
            tabs.add(new Rect(x, firstY + index * step, width, height));
        }
        return List.copyOf(tabs);
    }

    /**
     * REGULAR has a channel badge and labelled tabs; NARROW (small canvases) drops the badge and tab labels.
     * REGULAR 带频道徽标与带文字的标签；NARROW（小画布）去掉徽标与标签文字。
     */
    public enum Mode {
        REGULAR(16, 9, 18, 10, 22, 64, 10, 30, 52, 42, 48, 36, 44, 18),
        NARROW(6, 5, 10, 6, 18, 40, 6, 0, 32, 30, 34, 26, 32, 8);

        private final int margin;
        private final int bezel;
        private final int deviceRadius;
        private final int screenRadius;
        private final int statusHeight;
        private final int railWidth;
        private final int railPad;
        private final int badgeSize;
        private final int tabWidth;
        private final int tabHeight;
        private final int tabStep;
        private final int headerHeight;
        private final int footerHeight;
        private final int padX;

        Mode(
                int margin,
                int bezel,
                int deviceRadius,
                int screenRadius,
                int statusHeight,
                int railWidth,
                int railPad,
                int badgeSize,
                int tabWidth,
                int tabHeight,
                int tabStep,
                int headerHeight,
                int footerHeight,
                int padX
        ) {
            this.margin = margin;
            this.bezel = bezel;
            this.deviceRadius = deviceRadius;
            this.screenRadius = screenRadius;
            this.statusHeight = statusHeight;
            this.railWidth = railWidth;
            this.railPad = railPad;
            this.badgeSize = badgeSize;
            this.tabWidth = tabWidth;
            this.tabHeight = tabHeight;
            this.tabStep = tabStep;
            this.headerHeight = headerHeight;
            this.footerHeight = footerHeight;
            this.padX = padX;
        }

        public int bezel() {
            return bezel;
        }

        public int deviceRadius() {
            return deviceRadius;
        }

        public int screenRadius() {
            return screenRadius;
        }

        public int padX() {
            return padX;
        }

        private int tabLead() {
            return badgeSize > 0 ? railPad + badgeSize + BADGE_GAP : railPad;
        }
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

        public int centerX() {
            return x + width / 2;
        }

        public int centerY() {
            return y + height / 2;
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
