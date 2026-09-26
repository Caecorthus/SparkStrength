package annina.sparkstrength.client.screen.detective;

/**
 * Pixel layout of the case folder: a 320x200 GUI target, centred and clamped to the viewport.
 * 文件夹界面的像素布局：目标尺寸 320x200，居中并限制在视口内。
 *
 * <p>Everything below the tab strip is placed relative to the two pages, so a narrower viewport only squeezes the
 * pages horizontally; vertical overflow on tiny windows is cut off by the scissor the screen enables over
 * {@link #clip()}.
 * 标签栏以下的元素都相对两页纸定位，视口变窄时只会横向压缩；极小窗口下的纵向溢出由界面在 clip() 区域启用的裁剪截掉。</p>
 */
record DetectiveFolderLayout(
        Rect folder,
        Rect body,
        Rect leftPage,
        Rect rightPage,
        int contentX,
        Rect polaroid,
        Rect notesBox,
        Rect notesPanel,
        Rect killerPill,
        Rect presumedPolaroid,
        Rect presumedLabel,
        int rowsTop
) {
    static final int PREFERRED_WIDTH = 320;
    static final int PREFERRED_HEIGHT = 200;
    static final int TAB_WIDTH = 64;
    static final int TAB_HEIGHT = 14;
    static final int TAB_GAP = 2;
    static final int ARROW_SIZE = 14;
    static final int BODY_TOP = 12;
    static final int PAGE_TOP = 20;
    static final int PAGE_MARGIN = 8;
    static final int PAGE_GUTTER = 8;
    static final int LINE_PITCH = 10;
    static final int ROW_HEIGHT = 22;
    static final int ROW_PITCH = 24;
    static final int ROW_INSET = 4;
    static final int NOTES_COUNTER_STRIP = 13;
    static final int NOTES_SCROLLBAR = 8;
    static final int POLAROID_WIDTH = 38;
    static final int POLAROID_HEIGHT = 44;
    static final int PHOTO_SIZE = 32;
    // The ACTIVE pin rises this far above the folder top; modal scrims and the clip start there.
    // “调查中”图钉高出文件夹顶部的距离；模态遮罩与裁剪区域都从这里开始。
    static final int ACTIVE_PIN_RISE = 14;
    static final int SHADOW = 3;

    // Offsets inside the left page; text baselines sit on the ruled lines (first line at +14, then every 10 px).
    // 左页内偏移；文字基线落在横线上（第一条线在 +14，其后每 10 像素一条）。
    static final int VICTIM_HEADER_Y = 5;
    static final int VICTIM_NAME_Y = 15;
    static final int NOTES_HEADER_Y = 45;
    static final int NOTES_BOX_Y = 56;
    static final int NOTES_BOX_HEIGHT = 44;
    static final int KILLER_HEADER_Y = 116;
    static final int KILLER_PILL_Y = 126;
    static final int PRESUMED_HEADER_Y = 146;
    static final int PRESUMED_NAME_Y = 156;
    static final int PRESUMED_POLAROID_Y = 120;
    // Offsets inside the right page. / 右页内偏移。
    static final int SUSPECTS_TITLE_Y = 4;
    static final int SUSPECTS_SUBTITLE_Y = 15;
    static final int ROWS_TOP = 36;

    static DetectiveFolderLayout forViewport(int screenWidth, int screenHeight) {
        int width = Math.max(0, Math.min(PREFERRED_WIDTH, screenWidth));
        int height = Math.max(0, Math.min(PREFERRED_HEIGHT, screenHeight));
        int x = (screenWidth - width) / 2;
        int y = (screenHeight - height) / 2;
        Rect folder = new Rect(x, y, width, height);
        Rect body = new Rect(x, y + BODY_TOP, width, height - BODY_TOP);

        int pageWidth = Math.max(0, (width - PAGE_MARGIN * 2 - PAGE_GUTTER) / 2);
        int pageTop = y + PAGE_TOP;
        int pageHeight = Math.max(0, y + height - PAGE_MARGIN - pageTop);
        Rect left = new Rect(x + PAGE_MARGIN, pageTop, pageWidth, pageHeight);
        Rect right = new Rect(x + width - PAGE_MARGIN - pageWidth, pageTop, pageWidth, pageHeight);

        int contentX = left.x() + 12;
        int photoX = left.right() - 4 - POLAROID_WIDTH;
        Rect polaroid = new Rect(photoX, pageTop + 3, POLAROID_WIDTH, POLAROID_HEIGHT);
        // Vanilla draws the edit box scrollbar just outside its right edge, so the panel reserves that strip.
        // 原版把输入框滚动条画在右边框外侧，因此底板为其预留宽度。
        Rect notesBox = new Rect(
                contentX,
                pageTop + NOTES_BOX_Y,
                Math.max(20, left.right() - 5 - contentX - NOTES_SCROLLBAR),
                NOTES_BOX_HEIGHT
        );
        Rect notesPanel = new Rect(
                notesBox.x() - 1,
                notesBox.y() - 1,
                notesBox.width() + NOTES_SCROLLBAR + 2,
                notesBox.height() + NOTES_COUNTER_STRIP + 1
        );
        // Killer block: role pill and presumed-killer name on the left, one polaroid on the right that shows the
        // presumed killer's face (or a silhouette tinted with the guessed role).
        // 凶手区：左侧为身份药丸与假定凶手名字，右侧拍立得显示假定凶手头像（未选择时为按推测身份着色的剪影）。
        Rect presumedPolaroid = new Rect(photoX, pageTop + PRESUMED_POLAROID_Y, POLAROID_WIDTH, POLAROID_HEIGHT);
        int leftColumn = Math.max(24, photoX - 4 - contentX);
        Rect killerPill = new Rect(contentX, pageTop + KILLER_PILL_Y, Math.max(24, photoX - 6 - contentX), 16);
        Rect presumedLabel = new Rect(contentX - 2, pageTop + PRESUMED_HEADER_Y - 1, leftColumn + 2, 20);
        return new DetectiveFolderLayout(
                folder,
                body,
                left,
                right,
                contentX,
                polaroid,
                notesBox,
                notesPanel,
                killerPill,
                presumedPolaroid,
                presumedLabel,
                pageTop + ROWS_TOP
        );
    }

    /** Scissor area: the folder plus the raised ACTIVE pin and the drop shadow. / 裁剪区域：文件夹加上图钉与投影。 */
    Rect clip() {
        return new Rect(
                folder.x(),
                folder.y() - ACTIVE_PIN_RISE,
                folder.width() + SHADOW,
                folder.height() + ACTIVE_PIN_RISE + SHADOW
        );
    }

    /** Modal scrim: the folder including the ACTIVE pin above it. / 模态遮罩：文件夹及其上方的图钉。 */
    Rect scrim() {
        return new Rect(folder.x(), folder.y() - ACTIVE_PIN_RISE, folder.width(), folder.height() + ACTIVE_PIN_RISE);
    }

    /** Tabs that fit without paging arrows. / 不需要翻页箭头时能放下的标签数。 */
    int tabsWithoutArrows() {
        return Math.max(1, (folder.width() - PAGE_MARGIN * 2 + TAB_GAP) / (TAB_WIDTH + TAB_GAP));
    }

    /** Tabs per page once the two arrows take the right end of the strip. / 右端放置两个箭头后每页的标签数。 */
    int tabsWithArrows() {
        int available = folder.width() - PAGE_MARGIN * 2 - ARROW_SIZE * 2 - 6;
        return Math.max(1, (available + TAB_GAP) / (TAB_WIDTH + TAB_GAP));
    }

    int tabsPerPage(int caseCount) {
        return caseCount <= tabsWithoutArrows() ? tabsWithoutArrows() : tabsWithArrows();
    }

    boolean needsArrows(int caseCount) {
        return caseCount > tabsWithoutArrows();
    }

    Rect tab(int slot) {
        return new Rect(folder.x() + PAGE_MARGIN + slot * (TAB_WIDTH + TAB_GAP), folder.y(), TAB_WIDTH, TAB_HEIGHT);
    }

    Rect previousArrow() {
        return new Rect(folder.right() - PAGE_MARGIN - ARROW_SIZE * 2 - 2, folder.y(), ARROW_SIZE, ARROW_SIZE);
    }

    Rect nextArrow() {
        return new Rect(folder.right() - PAGE_MARGIN - ARROW_SIZE, folder.y(), ARROW_SIZE, ARROW_SIZE);
    }

    Rect tabStrip() {
        return new Rect(folder.x(), folder.y() - 2, folder.width(), TAB_HEIGHT + 2);
    }

    Rect suspectRow(int index) {
        Rect page = rightPage;
        return new Rect(
                page.x() + ROW_INSET,
                rowsTop + index * ROW_PITCH,
                Math.max(0, page.width() - ROW_INSET * 2),
                ROW_HEIGHT
        );
    }

    /** The pickers sit over the folder, inset so the folder edge stays visible. / 选择浮层覆盖文件夹并留出边缘。 */
    Rect pickerBox() {
        return new Rect(folder.x() + 10, folder.y() + 16, Math.max(0, folder.width() - 20), Math.max(0, folder.height() - 22));
    }

    record Rect(int x, int y, int width, int height) {
        Rect {
            width = Math.max(0, width);
            height = Math.max(0, height);
        }

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
}
