package annina.sparkstrength.client.screen.assassin;

import java.util.ArrayList;
import java.util.List;

/**
 * Geometry of the guess screen, in scaled GUI pixels: no rendering and no game state, only rectangles from the
 * viewport size and a few measured label widths. Two columns between cinematic bars: the target list on the left, the
 * chosen target's role grid on the right, and a footer with the stakes and the buttons.
 * 猜测界面的几何布局（缩放后的 GUI 像素）：不绘制、不读游戏状态，只根据视口大小与几个量好的文字宽度算出矩形。
 * 电影感上下黑边之间分两栏：左边目标列表，右边所选目标的身份网格，底部是风险提示与按钮。
 */
final class AssassinGuessLayout {
    // The native menu's 20 px letterbox, thinner on short windows. / 原生菜单的 20 像素黑边，窗口较矮时变细。
    static final int BAR_HEIGHT = 20;
    static final int BAR_HEIGHT_COMPACT = 14;
    static final int COMPACT_BELOW_HEIGHT = 300;
    static final int MARGIN = 6;
    static final int PANEL_MAX_WIDTH = 800;
    static final int PANEL_MAX_HEIGHT = 440;
    static final int PAD = 5;
    static final int COLUMN_GAP = 6;
    static final int TARGET_COLUMN_PERCENT = 30;
    static final int TARGET_COLUMN_MIN = 112;
    static final int TARGET_COLUMN_MAX = 196;
    static final int TARGET_HEADER_HEIGHT = 14;
    // Room for a 16 px head beside two text lines (name, mark). / 16 像素头像加两行文字（名字、标记）的高度。
    static final int ROLE_HEADER_HEIGHT = 22;
    static final int FOOTER_HEIGHT = 24;
    static final int FOOTER_RULE_GAP = 3;
    static final int BUTTON_HEIGHT = 16;
    static final int BUTTON_GAP = 4;
    static final int BUTTON_MIN_WIDTH = 40;
    static final int BUTTON_TEXT_PAD = 8;
    static final int SCROLLBAR_WIDTH = 3;
    static final int SCROLLBAR_ROOM = SCROLLBAR_WIDTH + 2;

    static final int ROW_HEIGHT = 22;
    static final int ROW_GAP = 1;
    // Baselines of a row's two lines: the name, then the pencilled role. / 行内两行文字的位置：名字，然后是铅笔标记。
    static final int ROW_NAME_Y = 3;
    static final int ROW_MARK_Y = 12;
    static final int HEAD_SIZE = 16;

    static final int CELL_HEIGHT = 14;
    static final int CELL_GAP = 2;
    // Fits "Survival Master"; Chinese names (up to 6 glyphs) fit with room to spare. / 容得下英文长名，中文名（最多 6 字）绰绰有余。
    static final int MIN_CELL_WIDTH = 84;
    static final int MIN_COLUMNS = 2;
    static final int MAX_COLUMNS = 6;
    static final int BAND_HEADER_HEIGHT = 12;
    static final int BAND_GAP = 5;

    private AssassinGuessLayout() {
    }

    record Rect(int x, int y, int width, int height) {
        int right() {
            return x + width;
        }

        int bottom() {
            return y + height;
        }

        boolean contains(double pointX, double pointY) {
            return pointX >= x && pointX < right() && pointY >= y && pointY < bottom();
        }
    }

    /**
     * Every fixed rectangle of one viewport. {@code targetList} and {@code roleGrid} are scroll viewports: their content
     * is laid out by {@link #targetRowY} and {@link #flowRoles} and shifted by the screen's scroll offsets.
     * 一个视口下的所有固定矩形。targetList 与 roleGrid 是滚动视口：内容由 targetRowY 与 flowRoles 排布，再按界面的滚动偏移平移。
     */
    record Frame(
            Rect topBar,
            Rect bottomBar,
            Rect panel,
            Rect targetHeader,
            Rect targetList,
            Rect roleHeader,
            Rect roleGrid,
            Rect footer,
            Rect status,
            Rect eraseButton,
            Rect closeButton,
            Rect guessButton
    ) {
    }

    /**
     * Lays out a {@code width} x {@code height} viewport. The label widths are the widest text each button may show,
     * so the buttons do not shift as the cooldown counts down.
     * 排布 width x height 的视口。按钮文字宽度取各按钮可能显示的最宽文字，冷却倒计时时按钮不会跳动。
     */
    static Frame frame(int width, int height, int eraseLabelWidth, int closeLabelWidth, int guessLabelWidth) {
        int bar = height < COMPACT_BELOW_HEIGHT ? BAR_HEIGHT_COMPACT : BAR_HEIGHT;
        Rect topBar = new Rect(0, 0, width, bar);
        Rect bottomBar = new Rect(0, height - bar, width, bar);

        int panelWidth = Math.max(0, Math.min(PANEL_MAX_WIDTH, width - MARGIN * 2));
        int roomHeight = Math.max(0, height - (bar + MARGIN) * 2);
        int panelHeight = Math.min(PANEL_MAX_HEIGHT, roomHeight);
        Rect panel = new Rect(
                (width - panelWidth) / 2,
                bar + MARGIN + (roomHeight - panelHeight) / 2,
                panelWidth,
                panelHeight
        );

        int innerX = panel.x() + PAD;
        int innerWidth = Math.max(0, panel.width() - PAD * 2);
        Rect footer = new Rect(innerX, panel.bottom() - PAD - FOOTER_HEIGHT, innerWidth, FOOTER_HEIGHT);
        int columnsTop = panel.y() + PAD;
        int columnsHeight = Math.max(0, footer.y() - FOOTER_RULE_GAP - columnsTop);

        int targetWidth = clamp(innerWidth * TARGET_COLUMN_PERCENT / 100, TARGET_COLUMN_MIN, TARGET_COLUMN_MAX);
        targetWidth = Math.min(targetWidth, Math.max(0, innerWidth / 2));
        Rect targetHeader = new Rect(innerX, columnsTop, targetWidth, TARGET_HEADER_HEIGHT);
        Rect targetList = new Rect(innerX, targetHeader.bottom(), targetWidth,
                Math.max(0, columnsHeight - TARGET_HEADER_HEIGHT));

        int roleX = innerX + targetWidth + COLUMN_GAP;
        int roleWidth = Math.max(0, innerX + innerWidth - roleX);
        Rect roleHeader = new Rect(roleX, columnsTop, roleWidth, ROLE_HEADER_HEIGHT);
        Rect roleGrid = new Rect(roleX, roleHeader.bottom() + 2, roleWidth,
                Math.max(0, columnsHeight - ROLE_HEADER_HEIGHT - 2));

        int buttonY = footer.y() + (FOOTER_HEIGHT - BUTTON_HEIGHT) / 2;
        Rect guess = new Rect(footer.right() - buttonWidth(guessLabelWidth), buttonY, buttonWidth(guessLabelWidth),
                BUTTON_HEIGHT);
        Rect close = new Rect(guess.x() - BUTTON_GAP - buttonWidth(closeLabelWidth), buttonY,
                buttonWidth(closeLabelWidth), BUTTON_HEIGHT);
        Rect erase = new Rect(close.x() - BUTTON_GAP - buttonWidth(eraseLabelWidth), buttonY,
                buttonWidth(eraseLabelWidth), BUTTON_HEIGHT);
        Rect status = new Rect(footer.x(), footer.y(), Math.max(0, erase.x() - BUTTON_GAP - footer.x()), FOOTER_HEIGHT);

        return new Frame(topBar, bottomBar, panel, targetHeader, targetList, roleHeader, roleGrid, footer, status,
                erase, close, guess);
    }

    static int buttonWidth(int labelWidth) {
        return Math.max(BUTTON_MIN_WIDTH, labelWidth + BUTTON_TEXT_PAD * 2);
    }

    /** Top of target row {@code index} in list content space. / 第 index 行目标在列表内容坐标中的顶部。 */
    static int targetRowY(int index) {
        return index * (ROW_HEIGHT + ROW_GAP);
    }

    static int targetListHeight(int rows) {
        return rows <= 0 ? 0 : targetRowY(rows) - ROW_GAP;
    }

    /** Width a row's content may use, the scrollbar strip kept clear. / 行内容可用宽度，留出滚动条位置。 */
    static int rowWidth(Rect list) {
        return Math.max(0, list.width() - SCROLLBAR_ROOM);
    }

    /** A band header and the band's cells, in grid content space. / 分组标题与其单元格，使用网格内容坐标。 */
    record BandSpot(int band, int headerY, int bottom) {
    }

    /** {@code x} is relative to the grid's left edge, {@code y} to the top of its content. / x 相对网格左边，y 相对内容顶部。 */
    record CellSpot(int band, int member, int x, int y, int width) {
    }

    record RoleGrid(List<BandSpot> bands, List<CellSpot> cells, int columns, int contentHeight) {
    }

    /**
     * Flows bands of the given sizes down a grid {@code gridWidth} wide (scrollbar strip excluded): each band is a
     * header line followed by its cells, row by row; empty bands take no space. Columns follow the width, between
     * {@link #MIN_COLUMNS} and {@link #MAX_COLUMNS}, and every band uses the same column grid so cells line up.
     * 把给定大小的各组依次排进 gridWidth 宽（不含滚动条）的网格：每组一行标题，下面逐行排单元格；空组不占位置。
     * 列数随宽度变化（MIN_COLUMNS 到 MAX_COLUMNS），所有组共用同一套列，单元格上下对齐。
     */
    static RoleGrid flowRoles(List<Integer> bandSizes, int gridWidth) {
        int columns = clamp((gridWidth + CELL_GAP) / (MIN_CELL_WIDTH + CELL_GAP), MIN_COLUMNS, MAX_COLUMNS);
        int cellWidth = Math.max(1, (gridWidth - (columns - 1) * CELL_GAP) / columns);
        List<BandSpot> bands = new ArrayList<>();
        List<CellSpot> cells = new ArrayList<>();
        int y = 0;
        for (int band = 0; band < bandSizes.size(); band++) {
            int size = bandSizes.get(band);
            if (size <= 0) {
                continue;
            }
            if (!bands.isEmpty()) {
                y += BAND_GAP;
            }
            int headerY = y;
            y += BAND_HEADER_HEIGHT;
            for (int member = 0; member < size; member++) {
                int column = member % columns;
                int row = member / columns;
                cells.add(new CellSpot(band, member, column * (cellWidth + CELL_GAP), y + row * (CELL_HEIGHT + CELL_GAP),
                        cellWidth));
            }
            int rows = (size + columns - 1) / columns;
            y += rows * CELL_HEIGHT + (rows - 1) * CELL_GAP;
            bands.add(new BandSpot(band, headerY, y));
        }
        return new RoleGrid(List.copyOf(bands), List.copyOf(cells), columns, y);
    }

    /** Largest scroll offset that still fills the viewport. / 仍能填满视口的最大滚动偏移。 */
    static double maxScroll(int contentHeight, int viewportHeight) {
        return Math.max(0, contentHeight - viewportHeight);
    }

    /**
     * Scroll offset that brings [top, top + height) into the viewport, moving as little as possible.
     * 让 [top, top + height) 进入视口的滚动偏移，移动尽量少。
     */
    static double scrollToShow(double scroll, int top, int height, int viewportHeight) {
        if (top < scroll) {
            return top;
        }
        if (top + height > scroll + viewportHeight) {
            return top + height - viewportHeight;
        }
        return scroll;
    }

    /**
     * Scrollbar thumb {@code [y, height]} along a track of {@code trackHeight}, or null when everything fits.
     * 长度为 trackHeight 的滚动条上滑块的位置与长度；内容放得下时返回 null。
     */
    static int[] scrollThumb(int contentHeight, int trackHeight, double scroll) {
        if (contentHeight <= trackHeight || trackHeight <= 0) {
            return null;
        }
        int thumb = Math.max(8, trackHeight * trackHeight / contentHeight);
        double range = Math.max(1.0D, contentHeight - trackHeight);
        int offset = (int) Math.round((trackHeight - thumb) * Math.min(1.0D, scroll / range));
        return new int[]{offset, thumb};
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
