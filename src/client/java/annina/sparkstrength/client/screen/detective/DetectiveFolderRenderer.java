package annina.sparkstrength.client.screen.detective;

import annina.sparkstrength.client.ui.common.PlayerNameResolver;
import annina.sparkstrength.client.ui.common.RoleFactionBands;
import annina.sparkstrength.client.ui.common.UiColors;
import annina.sparkstrength.client.ui.common.UiText;
import annina.sparkstrength.component.detective.DetectiveCasePlayerComponent;
import annina.sparkstrength.component.detective.DetectiveCasePlayerComponent.DetectiveCase;
import annina.sparkstrength.component.detective.DetectiveCasePlayerComponent.SuspectClue;
import annina.sparkstrength.role.detective.DetectiveCaseRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Paints {@link DetectiveFolderScreen}'s folder: the closed cover, the case tabs, the victim/notes/killer page and the
 * suspects page, and records the hover tooltip they ask for. It only reads {@link DetectiveFolderState}; the props
 * come from {@link DetectiveFolderPaint}. The screen builds one per {@code init()}, with that layout and font.
 * 绘制 DetectiveFolderScreen 的文件夹：合上的封面、命案标签、受害人/笔记/凶手页与嫌疑人页，并记录它们需要的悬停提示。
 * 只读取 DetectiveFolderState；绘制道具来自 DetectiveFolderPaint。界面每次 init() 时用当时的布局与字体新建一个。
 */
final class DetectiveFolderRenderer {
    private static final String ANONYMOUS_NAME = "???";

    private static final int COLOR_SHADOW = 0x66000000;
    private static final int COLOR_FOLDER = 0xFFD9A95B;
    private static final int COLOR_FOLDER_EDGE = 0xFF8A6331;
    private static final int COLOR_FOLDER_HIGHLIGHT = 0xFFE6BC73;
    private static final int COLOR_FOLDER_CREASE = 0x668A6331;
    private static final int COLOR_TAB = 0xFFB88F4D;
    private static final int COLOR_TAB_HOVER = 0xFFC99D57;
    private static final int COLOR_PAPER = 0xFFF6F1E3;
    private static final int COLOR_PAPER_EDGE = 0xFFD8CFB8;
    private static final int COLOR_PAPER_LINE = 0x5A7FA6C9;
    private static final int COLOR_MARGIN_LINE = 0x80D05A5A;
    private static final int COLOR_RING_HOLE = 0xFF8F6D3E;
    private static final int COLOR_BLUE_PAPER = 0xFFDCEAF7;
    private static final int COLOR_BLUE_PAPER_EDGE = 0xFF9DB9D6;
    private static final int COLOR_BLUE_LINE = 0x3A7FA6C9;
    private static final int COLOR_ROW = 0xFFF7FBFF;
    private static final int COLOR_ROW_EDGE = 0xFF4F7FB8;
    private static final int COLOR_ROW_PLACEHOLDER_EDGE = 0xFFB9CDE2;
    private static final int COLOR_NOTES_PANEL = 0xFF1B2735;
    private static final int COLOR_PILL = 0xFF2B2622;
    private static final int COLOR_PILL_HOVER = 0xFF3D352F;
    private static final int COLOR_PILL_EDGE = 0xFF15120F;
    private static final int COLOR_PILL_UNKNOWN = 0xFFB8B0A4;
    private static final int COLOR_ACTIVE_PIN = 0xFFC8372D;
    private static final int COLOR_SILHOUETTE = 0xFF1C1C1C;
    private static final int COLOR_LABEL_HOVER = 0x268A6331;
    private static final float ENOUGH_NOTE_TILT = 2.0F;
    // Room kept right of the presumed-killer name for the pencil icon. / 假定凶手名字右侧为铅笔图标预留的宽度。
    private static final int PENCIL_ROOM = 12;

    private final TextRenderer textRenderer;
    private final DetectiveFolderLayout layout;
    private final DetectiveFolderPaint.StickyNoteWidths stickyNoteWidths;
    private final DetectiveFolderState state;
    private @Nullable Text hoverTooltip;

    DetectiveFolderRenderer(
            TextRenderer textRenderer,
            DetectiveFolderLayout layout,
            DetectiveFolderPaint.StickyNoteWidths stickyNoteWidths,
            DetectiveFolderState state
    ) {
        this.textRenderer = textRenderer;
        this.layout = layout;
        this.stickyNoteWidths = stickyNoteWidths;
        this.state = state;
    }

    /** Tooltip a hovered, trimmed label asked for during this frame. / 本帧中被截断且悬停的标签请求显示的提示。 */
    @Nullable Text hoverTooltip() {
        return hoverTooltip;
    }

    /** Forgets the previous frame's tooltip; called before painting. / 绘制前清除上一帧的提示。 */
    void clearHoverTooltip() {
        hoverTooltip = null;
    }

    void renderClosedFolder(DrawContext context) {
        DetectiveFolderLayout.Rect body = layout.body();
        String title = Text.translatable("screen.sparkstrength.detective_folder.title").getString();
        int tabWidth = Math.min(
                Math.max(DetectiveFolderLayout.TAB_WIDTH, textRenderer.getWidth(title) + 14),
                Math.max(DetectiveFolderLayout.TAB_WIDTH, body.width() - DetectiveFolderLayout.PAGE_MARGIN * 2)
        );
        int tabX = body.x() + DetectiveFolderLayout.PAGE_MARGIN;
        int tabY = layout.folder().y();
        context.fill(body.x() + 3, body.y() + 3, body.right() + 3, body.bottom() + 3, COLOR_SHADOW);
        context.fill(tabX, tabY, tabX + tabWidth, body.y() + 2, COLOR_FOLDER);
        context.fill(tabX, tabY, tabX + tabWidth, tabY + 1, COLOR_FOLDER_EDGE);
        context.fill(tabX, tabY, tabX + 1, body.y() + 1, COLOR_FOLDER_EDGE);
        context.fill(tabX + tabWidth - 1, tabY, tabX + tabWidth, body.y() + 1, COLOR_FOLDER_EDGE);
        drawFolderSheet(context, body);
        // Re-open the edge under the tab so tab and cover read as one piece. / 擦掉标签下方的边线，使标签与封面连成一体。
        context.fill(tabX + 1, body.y(), tabX + tabWidth - 1, body.y() + 2, COLOR_FOLDER);
        context.drawText(
                textRenderer,
                UiText.trim(textRenderer, title, tabWidth - 10),
                tabX + 5,
                tabY + 3,
                DetectiveFolderPaint.INK,
                false
        );
        context.fill(body.x() + 12, body.y() + 4, body.x() + 13, body.bottom() - 4, COLOR_FOLDER_CREASE);

        Text note = Text.translatable("screen.sparkstrength.detective_folder.no_case");
        int noteWidth = stickyNoteWidths.get(textRenderer, note, Math.min(150, Math.max(40, body.width() - 40)), 4);
        int noteHeight = DetectiveFolderPaint.stickyNoteHeight(textRenderer, note, noteWidth, 4);
        int noteX = body.x() + (body.width() - noteWidth) / 2;
        int noteY = body.y() + (body.height() - noteHeight) / 2;
        DetectiveFolderPaint.rotated(context, noteX + noteWidth / 2.0F, noteY + noteHeight / 2.0F, -3.0F,
                () -> DetectiveFolderPaint.drawStickyNote(context, textRenderer, note, noteX, noteY, noteWidth, 4));
    }

    void renderFolderBody(DrawContext context) {
        DetectiveFolderLayout.Rect body = layout.body();
        context.fill(body.x() + 3, body.y() + 3, body.right() + 3, body.bottom() + 3, COLOR_SHADOW);
        drawFolderSheet(context, body);
        int spineX = body.x() + body.width() / 2;
        context.fill(spineX - 1, body.y() + 4, spineX + 1, body.bottom() - 4, COLOR_FOLDER_CREASE);
    }

    private void drawFolderSheet(DrawContext context, DetectiveFolderLayout.Rect body) {
        context.fill(body.x(), body.y(), body.right(), body.bottom(), COLOR_FOLDER);
        context.drawBorder(body.x(), body.y(), body.width(), body.height(), COLOR_FOLDER_EDGE);
        context.drawBorder(body.x() + 1, body.y() + 1, body.width() - 2, body.height() - 2, COLOR_FOLDER_EDGE);
        context.fill(body.x() + 2, body.y() + 2, body.right() - 2, body.y() + 3, COLOR_FOLDER_HIGHLIGHT);
    }

    /**
     * Case tabs. Unselected tabs are drawn first (behind the folder, darker); the viewed tab is drawn after the body,
     * 2 px taller and in the body colour so it merges, with the ACTIVE pin when it is the investigated case.
     * 命案标签：未选中的先画（位于文件夹后方、颜色更深）；当前查看的标签在主体之后绘制，高 2 像素且与主体同色以融为一体，
     * 若为正在调查的命案则显示“调查中”标记。
     */
    void renderTabs(DrawContext context, DetectiveCasePlayerComponent folder, int tabPage, int mouseX, int mouseY,
                    boolean viewedPass) {
        List<DetectiveCase> cases = folder.getCases();
        int perPage = layout.tabsPerPage(cases.size());
        int first = tabPage * perPage;
        for (int slot = 0; slot < perPage && first + slot < cases.size(); slot++) {
            DetectiveCase detectiveCase = cases.get(first + slot);
            boolean viewed = detectiveCase.getCaseId().equals(state.viewedCaseId());
            if (viewed != viewedPass) {
                continue;
            }
            DetectiveFolderLayout.Rect tab = layout.tab(slot);
            boolean hovered = layout.tabHitBox(slot).contains(mouseX, mouseY);
            if (hovered) {
                hoverTooltip = Text.literal(detectiveCase.getVictimName());
            }
            // The viewed tab's fill reaches 2 px into the body, covering its top outline so the two merge.
            // 当前标签的填充向下伸入主体 2 像素，盖住主体顶部边线，使两者融为一体。
            int top = viewed ? tab.y() - 2 : tab.y();
            int fill = viewed ? COLOR_FOLDER : hovered ? COLOR_TAB_HOVER : COLOR_TAB;
            context.fill(tab.x(), top, tab.right(), tab.bottom(), fill);
            context.fill(tab.x(), top, tab.right(), top + 1, COLOR_FOLDER_EDGE);
            context.fill(tab.x(), top, tab.x() + 1, tab.bottom(), COLOR_FOLDER_EDGE);
            context.fill(tab.right() - 1, top, tab.right(), tab.bottom(), COLOR_FOLDER_EDGE);
            int contentY = top + 2;
            DetectiveFolderPaint.drawHead(context, detectiveCase.getVictimDisplayUuid(), tab.x() + 3, contentY, 8);
            context.drawText(
                    textRenderer,
                    UiText.trim(textRenderer, detectiveCase.getVictimName(), tab.width() - 16),
                    tab.x() + 13,
                    contentY,
                    DetectiveFolderPaint.INK,
                    false
            );
            if (viewed && state.isActive(folder, detectiveCase.getCaseId())) {
                drawActivePin(context, tab, top);
            }
        }
        if (!viewedPass && layout.needsArrows(cases.size())) {
            int pages = layout.tabPageCount(cases.size());
            drawArrowTab(context, layout.previousArrow(), true, tabPage > 0, mouseX, mouseY);
            drawArrowTab(context, layout.nextArrow(), false, tabPage < pages - 1, mouseX, mouseY);
        }
    }

    private void drawActivePin(DrawContext context, DetectiveFolderLayout.Rect tab, int tabTop) {
        String label = Text.translatable("screen.sparkstrength.detective_folder.active").getString();
        int labelWidth = textRenderer.getWidth(label) + 6;
        int labelX = tab.x() + (tab.width() - labelWidth) / 2;
        int labelY = tab.y() - DetectiveFolderLayout.ACTIVE_PIN_RISE;
        context.fill(labelX + 1, labelY + 1, labelX + labelWidth + 1, labelY + 11, COLOR_SHADOW);
        context.fill(labelX, labelY, labelX + labelWidth, labelY + 10, COLOR_ACTIVE_PIN);
        int stemX = tab.x() + tab.width() / 2;
        context.fill(stemX, labelY + 10, stemX + 1, tabTop + 1, COLOR_ACTIVE_PIN);
        context.drawText(textRenderer, label, labelX + 3, labelY + 1, 0xFFFFFFFF, false);
    }

    private void drawArrowTab(DrawContext context, DetectiveFolderLayout.Rect rect, boolean left, boolean enabled, int mouseX, int mouseY) {
        boolean hovered = enabled && rect.contains(mouseX, mouseY);
        context.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), hovered ? COLOR_TAB_HOVER : COLOR_TAB);
        context.drawBorder(rect.x(), rect.y(), rect.width(), rect.height(), COLOR_FOLDER_EDGE);
        int color = enabled ? DetectiveFolderPaint.INK : UiColors.withAlpha(DetectiveFolderPaint.INK, 0x55);
        DetectiveFolderPaint.drawArrow(context, rect.x() + (rect.width() - 4) / 2, rect.y() + (rect.height() - 7) / 2, left, color);
    }

    void renderLeftPage(DrawContext context, DetectiveCase viewed, int mouseX, int mouseY) {
        DetectiveFolderLayout.Rect page = layout.leftPage();
        context.fill(page.x(), page.y(), page.right(), page.bottom(), COLOR_PAPER);
        context.drawBorder(page.x(), page.y(), page.width(), page.height(), COLOR_PAPER_EDGE);
        for (int lineY = page.y() + 14; lineY < page.bottom() - 2; lineY += DetectiveFolderLayout.LINE_PITCH) {
            context.fill(page.x() + 1, lineY, page.right() - 1, lineY + 1, COLOR_PAPER_LINE);
        }
        context.fill(page.x() + 9, page.y() + 1, page.x() + 10, page.bottom() - 1, COLOR_MARGIN_LINE);
        for (int holeY = page.y() + 6; holeY + 4 < page.bottom(); holeY += 14) {
            int holeX = page.x() + 3;
            context.fill(holeX, holeY + 1, holeX + 4, holeY + 3, COLOR_RING_HOLE);
            context.fill(holeX + 1, holeY, holeX + 3, holeY + 4, COLOR_RING_HOLE);
        }

        int textX = layout.contentX();
        DetectiveFolderLayout.Rect polaroid = layout.polaroid();
        drawHeader(context, "screen.sparkstrength.detective_folder.victim", textX, page.y() + DetectiveFolderLayout.VICTIM_HEADER_Y,
                polaroid.x() - 4 - textX);
        Style bold = Style.EMPTY.withBold(true);
        String victimName = UiText.trimStyled(textRenderer, viewed.getVictimName(), bold, polaroid.x() - 4 - textX);
        context.drawText(
                textRenderer,
                Text.literal(victimName).setStyle(bold),
                textX,
                page.y() + DetectiveFolderLayout.VICTIM_NAME_Y,
                DetectiveFolderPaint.INK,
                false
        );
        DetectiveFolderPaint.rotated(
                context,
                polaroid.x() + polaroid.width() / 2.0F,
                polaroid.y() + polaroid.height() / 2.0F,
                4.0F,
                () -> {
                    DetectiveFolderPaint.drawPolaroid(context, polaroid.x(), polaroid.y(), polaroid.width(), polaroid.height());
                    DetectiveFolderPaint.drawPhotoBackground(context, polaroid.x() + 3, polaroid.y() + 3, DetectiveFolderLayout.PHOTO_SIZE);
                    DetectiveFolderPaint.drawHead(context, viewed.getVictimDisplayUuid(), polaroid.x() + 3, polaroid.y() + 3,
                            DetectiveFolderLayout.PHOTO_SIZE);
                }
        );

        drawHeader(context, "screen.sparkstrength.detective_folder.notes", textX, page.y() + DetectiveFolderLayout.NOTES_HEADER_Y,
                page.right() - 4 - textX);
        DetectiveFolderLayout.Rect panel = layout.notesPanel();
        // Dark backing under the notes box; its bottom strip holds vanilla's "n/256" counter.
        // 笔记框下方的深色底板；底部条带用于容纳原版的“n/256”计数。
        context.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), COLOR_NOTES_PANEL);

        renderKillerBlock(context, viewed, mouseX, mouseY);
    }

    /**
     * Killer block: the role pill (opens the role picker) and the presumed killer, a name label plus the polaroid that
     * shows their face, or a silhouette tinted with the guessed role when nobody is named (opens the player picker).
     * 凶手区：身份药丸（打开身份选择）与假定凶手（名字标签加拍立得，显示其头像；未指认时显示按推测身份着色的剪影，
     * 点击打开玩家选择）。
     */
    private void renderKillerBlock(DrawContext context, DetectiveCase viewed, int mouseX, int mouseY) {
        DetectiveFolderLayout.Rect page = layout.leftPage();
        DetectiveFolderLayout.Rect pill = layout.killerPill();
        DetectiveFolderLayout.Rect photo = layout.presumedPolaroid();
        DetectiveFolderLayout.Rect presumedLabel = layout.presumedLabel();
        int textX = layout.contentX();
        int columnWidth = Math.max(0, photo.x() - 4 - textX);
        Identifier guess = state.displayedGuess(viewed);
        Role role = guess == null ? null : WatheRoles.getRole(guess);

        drawHeader(context, "screen.sparkstrength.detective_folder.killer", textX, page.y() + DetectiveFolderLayout.KILLER_HEADER_Y,
                columnWidth);
        boolean pillHovered = pill.contains(mouseX, mouseY);
        int pillFill = pillHovered ? COLOR_PILL_HOVER : COLOR_PILL;
        context.fill(pill.x() + 1, pill.y() + 1, pill.right() + 1, pill.bottom() + 1, COLOR_SHADOW);
        context.fill(pill.x(), pill.y(), pill.right(), pill.bottom(), pillFill);
        context.drawBorder(pill.x(), pill.y(), pill.width(), pill.height(), COLOR_PILL_EDGE);
        context.fill(pill.x() + 1, pill.y() + 1, pill.right() - 1, pill.y() + 2, 0x22FFFFFF);

        Text label;
        int color;
        if (role != null) {
            label = RoleFactionBands.roleName(role);
            color = DetectiveFolderPaint.readableOn(role.color(), pillFill);
        } else if (guess != null) {
            // A role this client does not know (mod mismatch): show the raw path. / 客户端不认识的角色：显示原始路径。
            label = Text.literal(guess.getPath());
            color = COLOR_PILL_UNKNOWN;
        } else {
            label = Text.translatable("screen.sparkstrength.detective_folder.killer_unknown");
            color = COLOR_PILL_UNKNOWN;
        }
        String full = label.getString();
        String visible = UiText.trim(textRenderer, full, pill.width() - 20);
        context.drawText(textRenderer, visible, pill.x() + 5, pill.y() + 4, color, false);
        DetectiveFolderPaint.drawPencil(context, pill.right() - 12, pill.y() + 4);
        if (pillHovered && !visible.equals(full)) {
            hoverTooltip = label;
        }

        UUID presumed = state.displayedPresumed(viewed);
        boolean presumedHovered = layout.containsPresumed(mouseX, mouseY);
        if (presumedHovered) {
            context.fill(presumedLabel.x(), presumedLabel.y(), presumedLabel.right(), presumedLabel.bottom(), COLOR_LABEL_HOVER);
        }
        drawHeader(context, "screen.sparkstrength.detective_folder.presumed_killer", textX,
                page.y() + DetectiveFolderLayout.PRESUMED_HEADER_Y, columnWidth);
        String presumedName = presumed != null
                ? PlayerNameResolver.playerName(presumed)
                : Text.translatable("screen.sparkstrength.detective_folder.presumed_killer_none").getString();
        Style bold = Style.EMPTY.withBold(true);
        String presumedVisible = UiText.trimStyled(textRenderer, presumedName, bold,
                Math.max(0, columnWidth - PENCIL_ROOM));
        Text presumedText = Text.literal(presumedVisible).setStyle(bold);
        int nameY = page.y() + DetectiveFolderLayout.PRESUMED_NAME_Y;
        context.drawText(textRenderer, presumedText, textX, nameY,
                presumed != null ? DetectiveFolderPaint.INK : DetectiveFolderPaint.INK_MUTED, false);
        DetectiveFolderPaint.drawPencil(context, textX + textRenderer.getWidth(presumedText) + 4, nameY);
        if (presumedHovered && !presumedVisible.equals(presumedName)) {
            hoverTooltip = Text.literal(presumedName);
        }

        int silhouette = role != null ? UiColors.opaque(role.color()) : COLOR_SILHOUETTE;
        int photoX = photo.x() + 3;
        int photoY = photo.y() + 3;
        DetectiveFolderPaint.rotated(
                context,
                photo.x() + photo.width() / 2.0F,
                photo.y() + photo.height() / 2.0F,
                -5.0F,
                () -> {
                    DetectiveFolderPaint.drawPolaroid(context, photo.x(), photo.y(), photo.width(), photo.height());
                    if (presumed != null) {
                        DetectiveFolderPaint.drawPhotoBackground(context, photoX, photoY, DetectiveFolderLayout.PHOTO_SIZE);
                        DetectiveFolderPaint.drawHead(context, presumed, photoX, photoY, DetectiveFolderLayout.PHOTO_SIZE);
                    } else {
                        DetectiveFolderPaint.drawSilhouette(
                                context,
                                textRenderer,
                                photoX,
                                photoY,
                                DetectiveFolderLayout.PHOTO_SIZE,
                                DetectiveFolderLayout.PHOTO_SIZE,
                                silhouette
                        );
                    }
                }
        );
    }

    void renderRightPage(DrawContext context, DetectiveCasePlayerComponent folder, DetectiveCase viewed, int mouseX, int mouseY) {
        DetectiveFolderLayout.Rect page = layout.rightPage();
        context.fill(page.x(), page.y(), page.right(), page.bottom(), COLOR_BLUE_PAPER);
        context.drawBorder(page.x(), page.y(), page.width(), page.height(), COLOR_BLUE_PAPER_EDGE);
        for (int lineY = page.y() + 14; lineY < page.bottom() - 2; lineY += DetectiveFolderLayout.LINE_PITCH) {
            context.fill(page.x() + 1, lineY, page.right() - 1, lineY + 1, COLOR_BLUE_LINE);
        }

        List<SuspectClue> suspects = viewed.getSuspects();
        int limit = DetectiveCaseRules.clampSuspectLimit(folder.getSyncedSuspectLimit());
        int textX = page.x() + 6;
        int textWidth = page.width() - 12;
        // The clue count sits on the title row, which keeps the page bottom free for the "enough" sticky note.
        // 线索计数放在标题行，页面底部留给“线索足够”便利贴。
        String clues = Text.translatable("screen.sparkstrength.detective_folder.clues", suspects.size(), limit).getString();
        int cluesWidth = Math.min(textRenderer.getWidth(clues), textWidth / 2);
        int cluesX = page.right() - 6 - cluesWidth;
        context.drawText(
                textRenderer,
                UiText.trim(textRenderer, clues, cluesWidth),
                cluesX,
                page.y() + DetectiveFolderLayout.SUSPECTS_TITLE_Y,
                DetectiveFolderPaint.INK_NAVY,
                false
        );
        Style bold = Style.EMPTY.withBold(true);
        String title = Text.translatable("screen.sparkstrength.detective_folder.suspects").getString();
        context.drawText(
                textRenderer,
                Text.literal(UiText.trimStyled(textRenderer, title, bold, Math.max(0, cluesX - 6 - textX))).setStyle(bold),
                textX,
                page.y() + DetectiveFolderLayout.SUSPECTS_TITLE_Y,
                DetectiveFolderPaint.INK_NAVY,
                false
        );
        List<OrderedText> subtitle = textRenderer.wrapLines(
                Text.translatable("screen.sparkstrength.detective_folder.suspects_subtitle"),
                Math.max(8, textWidth)
        );
        for (int line = 0; line < Math.min(2, subtitle.size()); line++) {
            context.drawText(
                    textRenderer,
                    subtitle.get(line),
                    textX,
                    page.y() + DetectiveFolderLayout.SUSPECTS_SUBTITLE_Y + line * textRenderer.fontHeight,
                    DetectiveFolderPaint.INK_MUTED,
                    false
            );
        }

        // A lowered limit never hides clues already recorded. / 调低上限后，已记录的线索仍全部显示。
        int rows = Math.min(DetectiveCaseRules.MAX_SUSPECT_LIMIT, Math.max(limit, suspects.size()));
        for (int index = 0; index < rows; index++) {
            DetectiveFolderLayout.Rect row = layout.suspectRow(index);
            if (index < suspects.size()) {
                drawSuspectRow(context, row, index, suspects.get(index), mouseX, mouseY);
            } else {
                drawPlaceholderRow(context, row, index);
            }
        }

        if (suspects.size() >= limit) {
            renderEnoughNote(context, page);
        }
    }

    /**
     * "Enough clues" sticky note, bottom-anchored inside the page. Its tilted bounds (tape and shadow included) stay
     * inside the page and, for every limit 1..4 in the shipped languages, below the last suspect row.
     * “线索足够”便利贴，贴在页面底部。倾斜后的范围（含胶带与阴影）保持在页面内；在已提供的语言中，上限 1 到 4 时均位于
     * 最后一行嫌疑人下方。
     */
    private void renderEnoughNote(DrawContext context, DetectiveFolderLayout.Rect page) {
        Text note = Text.translatable("screen.sparkstrength.detective_folder.enough");
        int noteWidth = stickyNoteWidths.get(textRenderer, note, Math.max(40, page.width() - 12), 3);
        int noteHeight = DetectiveFolderPaint.stickyNoteHeight(textRenderer, note, noteWidth, 3);
        int tiltY = tiltMargin(noteWidth, noteHeight, ENOUGH_NOTE_TILT);
        int tiltX = tiltMargin(noteHeight, noteWidth, ENOUGH_NOTE_TILT);
        // Bottom-anchored with the 2 px drop shadow and one paper row above the page border, so it never leaves the
        // page; only an unusually long translation could then reach up into the last row.
        // 底部对齐（含 2 像素投影，并与页边框之间留一行纸），因此不会超出页面；只有过长的译文才可能向上触及最后一行。
        int noteX = page.right() - 4 - 2 - tiltX - noteWidth;
        int noteY = page.bottom() - 1 - 2 - tiltY - noteHeight;
        DetectiveFolderPaint.rotated(context, noteX + noteWidth / 2.0F, noteY + noteHeight / 2.0F, ENOUGH_NOTE_TILT,
                () -> DetectiveFolderPaint.drawStickyNote(context, textRenderer, note, noteX, noteY, noteWidth, 3));
    }

    /** How far a rotated box's edge moves along the axis {@code across}. / 旋转后矩形在该方向上的外扩量。 */
    private static int tiltMargin(int along, int across, float degrees) {
        double radians = Math.toRadians(Math.abs(degrees));
        return (int) Math.ceil(along / 2.0D * Math.sin(radians) + across / 2.0D * (1.0D - Math.cos(radians)));
    }

    private void drawSuspectRow(DrawContext context, DetectiveFolderLayout.Rect row, int index, SuspectClue clue, int mouseX, int mouseY) {
        context.fill(row.x(), row.y(), row.right(), row.bottom(), COLOR_ROW);
        context.drawBorder(row.x(), row.y(), row.width(), row.height(), COLOR_ROW_EDGE);
        int textY = row.y() + (row.height() - textRenderer.fontHeight) / 2 + 1;
        context.drawText(textRenderer, (index + 1) + ":", row.x() + 3, textY, DetectiveFolderPaint.INK_NAVY, false);

        int headX = row.x() + 13;
        int headY = row.y() + (row.height() - 16) / 2;
        if (clue.anonymous()) {
            DetectiveFolderPaint.drawUnknownFace(context, textRenderer, headX, headY, 16);
        } else {
            DetectiveFolderPaint.drawHead(context, clue.displayUuid(), headX, headY, 16);
        }

        int nameX = headX + 20;
        int distanceRight = row.right() - 4;
        String distance = Text.translatable("screen.sparkstrength.detective_folder.distance", clue.distanceBlocks()).getString();
        String name = clue.anonymous() ? ANONYMOUS_NAME : clue.displayName().isBlank() ? "?" : clue.displayName();
        int distanceWidth = textRenderer.getWidth(distance);
        boolean stacked = false;
        String number = distance;
        String unit = "";
        int split = distance.lastIndexOf(' ');
        if (!clue.anonymous() && split > 0 && textRenderer.getWidth(name) > distanceRight - 4 - distanceWidth - nameX) {
            // Compact distance column: a name that would be cut gets the number and unit stacked on two lines.
            // 紧凑距离列：名字会被截断时，把数字与单位分两行叠放，为名字腾出宽度。
            number = distance.substring(0, split).strip();
            unit = distance.substring(split + 1).strip();
            int stackedWidth = Math.max(textRenderer.getWidth(number), textRenderer.getWidth(unit));
            if (!number.isEmpty() && !unit.isEmpty() && stackedWidth < distanceWidth) {
                stacked = true;
                distanceWidth = stackedWidth;
            }
        }
        if (stacked) {
            int lineGap = textRenderer.fontHeight;
            int topY = row.y() + (row.height() - lineGap * 2) / 2 + 1;
            context.drawText(textRenderer, number, distanceRight - textRenderer.getWidth(number), topY, DetectiveFolderPaint.INK, false);
            context.drawText(textRenderer, unit, distanceRight - textRenderer.getWidth(unit), topY + lineGap,
                    DetectiveFolderPaint.INK, false);
        } else {
            context.drawText(textRenderer, distance, distanceRight - distanceWidth, textY, DetectiveFolderPaint.INK, false);
        }

        int nameWidth = Math.max(0, distanceRight - distanceWidth - 4 - nameX);
        if (clue.anonymous()) {
            // Psycho/obfuscated suspects: no name, no head, just scrambled glyphs. / 匿名嫌疑人：不显示名字与头像，只显示乱码。
            context.drawText(
                    textRenderer,
                    Text.literal(ANONYMOUS_NAME).formatted(Formatting.OBFUSCATED),
                    nameX,
                    textY,
                    DetectiveFolderPaint.INK,
                    false
            );
            return;
        }
        String visible = UiText.trim(textRenderer, name, nameWidth);
        context.drawText(textRenderer, visible, nameX, textY, DetectiveFolderPaint.INK, false);
        if (row.contains(mouseX, mouseY) && !visible.equals(name)) {
            hoverTooltip = Text.literal(name);
        }
    }

    private void drawPlaceholderRow(DrawContext context, DetectiveFolderLayout.Rect row, int index) {
        DetectiveFolderPaint.drawDashedBorder(context, row.x(), row.y(), row.width(), row.height(), COLOR_ROW_PLACEHOLDER_EDGE);
        int textY = row.y() + (row.height() - textRenderer.fontHeight) / 2 + 1;
        context.drawText(textRenderer, (index + 1) + ":", row.x() + 3, textY, DetectiveFolderPaint.INK_FADED, false);
        String placeholder = Text.translatable("screen.sparkstrength.detective_folder.suspect_placeholder", index + 1).getString();
        context.drawText(
                textRenderer,
                UiText.trim(textRenderer, placeholder, row.width() - 17),
                row.x() + 13,
                textY,
                DetectiveFolderPaint.INK_FADED,
                false
        );
    }

    private void drawHeader(DrawContext context, String key, int x, int y, int maxWidth) {
        String header = Text.translatable(key).getString();
        context.drawText(textRenderer, UiText.trim(textRenderer, header, Math.max(0, maxWidth)), x, y,
                DetectiveFolderPaint.INK_RED, false);
    }
}
