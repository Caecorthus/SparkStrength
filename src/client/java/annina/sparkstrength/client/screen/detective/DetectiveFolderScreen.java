package annina.sparkstrength.client.screen.detective;

import annina.sparkstrength.client.ui.common.PlayerNameResolver;
import annina.sparkstrength.component.detective.DetectiveCasePlayerComponent;
import annina.sparkstrength.component.detective.DetectiveCasePlayerComponent.DetectiveCase;
import annina.sparkstrength.component.detective.DetectiveCasePlayerComponent.SuspectClue;
import annina.sparkstrength.network.detective.SelectDetectiveCaseC2SPacket;
import annina.sparkstrength.network.detective.SetDetectiveKillerGuessC2SPacket;
import annina.sparkstrength.network.detective.SetDetectivePresumedKillerC2SPacket;
import annina.sparkstrength.network.detective.UpdateDetectiveCaseNotesC2SPacket;
import annina.sparkstrength.role.detective.DetectiveCaseRules;
import annina.sparkstrength.role.detective.DetectiveRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Detective case folder, drawn as an open manila folder: case tabs, the victim/notes/killer page and the suspects
 * page. Opened by the server after it synced {@link DetectiveCasePlayerComponent}.
 * 侦探文件夹界面，绘制为打开的牛皮纸文件夹：命案标签、受害人/笔记/凶手页与嫌疑人页。服务端先同步案件组件后再通知打开。
 *
 * <p>Authority: every field shown here comes from the owner-only case sync. The client only sends edit requests
 * (select, notes, killer guess, presumed killer) and shows them optimistically until the next sync; the server
 * validates and wins. Suspect names are the synced display names; they are never re-resolved from uuids, so
 * disguises stay as recorded. The presumed killer is a real player the detective picked, so its name and head are
 * resolved from that uuid.
 * 权威：界面数据全部来自仅发给本人的案件同步。客户端只发送修改请求（选择、笔记、凶手身份推测、假定凶手），在下一次
 * 同步前乐观显示；服务端校验并以其为准。嫌疑人名字使用同步的显示名，绝不根据 UUID 重新解析，伪装身份保持记录时的样子。
 * 假定凶手是侦探亲自选择的真实玩家，因此其名字与头像按该 UUID 解析。</p>
 */
public final class DetectiveFolderScreen extends Screen {
    // An unconfirmed optimistic edit falls back to the synced value after this long (e.g. the server rejected it).
    // 乐观修改若在此时间内未被同步确认（例如被服务端拒绝），回退到同步值。
    private static final int PENDING_TIMEOUT_TICKS = 100;
    private static final int REVOKE_GRACE_TICKS = 10;
    // Notes are saved after this long without typing, so a sudden loss of access (swallow, death) keeps most of the
    // draft; the server rejects edits once the gate fails. / 停止输入这么久后自动保存笔记，突然失去权限（被吞、死亡）时
    // 草稿大多已保存；门槛失效后服务端会拒绝编辑。
    private static final int NOTES_AUTOSAVE_TICKS = 20;
    private static final String ANONYMOUS_NAME = "???";

    private static final int COLOR_DIM = 0xB0000000;
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

    private final DetectiveRolePicker rolePicker = new DetectiveRolePicker();
    private final DetectivePlayerPicker playerPicker = new DetectivePlayerPicker();
    // Notes sent for cases no longer in the editor, kept until a sync confirms them. / 已发送但尚未被同步确认的其它案件笔记。
    private final Map<UUID, String> sentNotes = new HashMap<>();
    private final Map<UUID, Pending<Identifier>> pendingGuesses = new HashMap<>();
    private final Map<UUID, Pending<UUID>> pendingPresumed = new HashMap<>();
    private DetectiveFolderLayout layout = DetectiveFolderLayout.forViewport(0, 0);
    private @Nullable DetectiveNotesBox notesBox;
    private @Nullable UUID viewedCaseId;
    private String notesDraft = "";
    // True once the user has typed in the viewed case's notes; only an untouched draft may be replaced by a sync.
    // 用户在当前命案笔记中输入过后为 true；只有未改动的草稿才会被同步覆盖。
    private boolean notesEdited;
    private @Nullable UUID pendingSelection;
    private int pendingSelectionTicks;
    private int lastSyncRevision;
    private int tabPage;
    private int revokedTicks;
    private int notesAutosaveTicks;
    private boolean stateReady;
    private boolean suppressNotesListener;
    private @Nullable Text hoverTooltip;

    public DetectiveFolderScreen() {
        super(Text.translatable("screen.sparkstrength.detective_folder.title"));
    }

    @Override
    protected void init() {
        super.init();
        layout = DetectiveFolderLayout.forViewport(width, height);
        notesBox = null;
        DetectiveCasePlayerComponent folder = folder();
        if (folder == null) {
            return;
        }
        if (!stateReady) {
            stateReady = true;
            lastSyncRevision = folder.getSyncRevision();
            showCase(resolveView(folder), folder);
        }
        clampTabPage(folder);
        if (viewedCase(folder) == null) {
            return;
        }
        DetectiveNotesBox box = new DetectiveNotesBox(textRenderer, layout.notesBox());
        box.setText(notesDraft);
        notesDraft = box.getText();
        box.setChangeListener(this::onNotesEdited);
        notesBox = addDrawableChild(box);
    }

    /**
     * Rebuilds widgets (resize-safe order from TabletScreen): drop focus first so a removed notes box stops
     * receiving keystrokes. The draft lives in {@link #notesDraft}, so it survives the rebuild.
     * 重建控件（沿用 TabletScreen 的顺序）：先清除焦点，避免已移除的笔记框继续接收按键。草稿保存在字段中，重建后保留。
     */
    private void refresh() {
        if (client == null) {
            return;
        }
        setFocused(null);
        clearChildren();
        init();
    }

    @Override
    public void tick() {
        super.tick();
        ClientPlayerEntity player = client == null ? null : client.player;
        DetectiveCasePlayerComponent folder = folder();
        if (player == null || folder == null) {
            close();
            return;
        }
        // Mirror the server gate (alive, non-spectator detective); edits would be ignored anyway once it fails.
        // 与服务端门槛一致（存活且非旁观的侦探）；不满足时编辑本就会被忽略。
        if (!canKeepOpen(player)) {
            if (++revokedTicks >= REVOKE_GRACE_TICKS) {
                close();
                return;
            }
        } else {
            revokedTicks = 0;
        }

        int revision = folder.getSyncRevision();
        boolean synced = revision != lastSyncRevision;
        if (synced) {
            lastSyncRevision = revision;
            reconcile(folder);
        }
        boolean expired = tickPending();
        if (synced || expired) {
            boolean switched = showCase(resolveView(folder), folder);
            if (switched) {
                refresh();
            } else if (synced) {
                // Same case: keep the notes widget (caret, selection, scroll); only the tab page depends on the sync.
                // 同一命案：保留笔记控件（光标、选区、滚动），只有标签页码依赖同步数据。
                clampTabPage(folder);
                adoptUntouchedNotes(folder);
            }
        }
        if (notesAutosaveTicks > 0 && --notesAutosaveTicks == 0) {
            commitNotes(folder);
        }
    }

    @Override
    public void removed() {
        DetectiveCasePlayerComponent folder = folder();
        if (folder != null) {
            commitNotes(folder);
        }
        super.removed();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (pickerOpen()) {
            // Modal: Escape only closes the picker; nothing reaches the widgets underneath.
            // 模态浮层：Esc 只关闭浮层，其余按键不传给下层控件。
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closePickers();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (pickerOpen()) {
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        DetectiveCasePlayerComponent folder = folder();
        if (rolePicker.isOpen()) {
            DetectiveRolePicker.Choice choice = rolePicker.click(layout, textRenderer, mouseX, mouseY, button);
            if (choice != null && folder != null) {
                pickKiller(folder, choice.roleId());
            }
            return true;
        }
        if (playerPicker.isOpen()) {
            DetectivePlayerPicker.Choice choice = playerPicker.click(layout, textRenderer, mouseX, mouseY, button);
            if (choice != null && folder != null) {
                pickPresumed(folder, choice.playerUuid());
            }
            return true;
        }
        DetectiveCase viewed = folder == null ? null : viewedCase(folder);
        // Parts clipped away on tiny windows are not clickable. / 极小窗口下被裁掉的部分不可点击。
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && folder != null && viewed != null
                && layout.clip().contains(mouseX, mouseY)) {
            List<DetectiveCase> cases = folder.getCases();
            if (layout.needsArrows(cases.size())) {
                if (layout.previousArrow().contains(mouseX, mouseY)) {
                    if (tabPage > 0) {
                        tabPage--;
                        playClick();
                    }
                    return true;
                }
                if (layout.nextArrow().contains(mouseX, mouseY)) {
                    if (tabPage < pageCount(cases.size()) - 1) {
                        tabPage++;
                        playClick();
                    }
                    return true;
                }
            }
            int perPage = layout.tabsPerPage(cases.size());
            int first = tabPage * perPage;
            for (int slot = 0; slot < perPage && first + slot < cases.size(); slot++) {
                if (tabHitBox(slot).contains(mouseX, mouseY)) {
                    selectCase(folder, cases.get(first + slot).getCaseId());
                    return true;
                }
            }
            if (layout.killerPill().contains(mouseX, mouseY)) {
                setFocused(null);
                playerPicker.close();
                rolePicker.open(layout, textRenderer, displayedGuess(viewed));
                playClick();
                return true;
            }
            if (hoversPresumed(mouseX, mouseY)) {
                setFocused(null);
                rolePicker.close();
                playerPicker.open(layout, textRenderer, displayedPresumed(viewed));
                playClick();
                return true;
            }
        }
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (!handled && getFocused() != null) {
            // Clicking the paper blurs the notes box. / 点击纸面时让笔记框失去焦点。
            setFocused(null);
        }
        return handled;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (rolePicker.isOpen()) {
            rolePicker.scroll(layout, textRenderer, verticalAmount);
            return true;
        }
        if (playerPicker.isOpen()) {
            playerPicker.scroll(layout, textRenderer, verticalAmount);
            return true;
        }
        DetectiveCasePlayerComponent folder = folder();
        if (folder != null && layout.tabStrip().contains(mouseX, mouseY)
                && layout.needsArrows(folder.getCases().size()) && verticalAmount != 0.0D) {
            int pages = pageCount(folder.getCases().size());
            tabPage = Math.max(0, Math.min(pages - 1, tabPage + (verticalAmount > 0.0D ? -1 : 1)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, COLOR_DIM);
        hoverTooltip = null;
        DetectiveCasePlayerComponent folder = folder();
        DetectiveCase viewed = folder == null ? null : viewedCase(folder);
        boolean modal = pickerOpen();
        // While a picker is open nothing underneath reacts to hover. / 选择浮层打开时下层不响应悬停。
        int hoverX = modal ? -1 : mouseX;
        int hoverY = modal ? -1 : mouseY;
        // Clip the folder so a window smaller than 320x200 cuts off overflow instead of drawing over the world.
        // Pickers and tooltips are drawn after the clip ends. / 裁剪文件夹区域，窗口小于 320x200 时截掉溢出部分；
        // 选择浮层与提示框在裁剪结束后绘制。
        DetectiveFolderLayout.Rect clip = layout.clip();
        context.enableScissor(clip.x(), clip.y(), clip.right(), clip.bottom());
        if (folder == null || viewed == null) {
            renderClosedFolder(context);
            super.render(context, hoverX, hoverY, delta);
            context.disableScissor();
            return;
        }

        renderTabs(context, folder, hoverX, hoverY, false);
        renderFolderBody(context);
        renderTabs(context, folder, hoverX, hoverY, true);
        renderLeftPage(context, viewed, hoverX, hoverY);
        renderRightPage(context, folder, viewed, hoverX, hoverY);
        super.render(context, hoverX, hoverY, delta);
        context.disableScissor();

        if (rolePicker.isOpen()) {
            rolePicker.render(context, textRenderer, layout, mouseX, mouseY, displayedGuess(viewed));
        } else if (playerPicker.isOpen()) {
            playerPicker.render(context, textRenderer, layout, mouseX, mouseY, displayedPresumed(viewed));
        } else if (hoverTooltip != null) {
            context.drawTooltip(textRenderer, hoverTooltip, mouseX, mouseY);
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // No vanilla blur/darkening; render() draws its own dim fill behind the folder.
        // 不使用原版模糊/变暗；render() 自行在文件夹后方绘制暗色遮罩。
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ---- Rendering / 绘制 ----

    private void renderClosedFolder(DrawContext context) {
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
                DetectiveFolderPaint.trim(textRenderer, title, tabWidth - 10),
                tabX + 5,
                tabY + 3,
                DetectiveFolderPaint.INK,
                false
        );
        context.fill(body.x() + 12, body.y() + 4, body.x() + 13, body.bottom() - 4, COLOR_FOLDER_CREASE);

        Text note = Text.translatable("screen.sparkstrength.detective_folder.no_case");
        int noteWidth = DetectiveFolderPaint.stickyNoteWidth(textRenderer, note, Math.min(150, Math.max(40, body.width() - 40)), 4);
        int noteHeight = DetectiveFolderPaint.stickyNoteHeight(textRenderer, note, noteWidth, 4);
        int noteX = body.x() + (body.width() - noteWidth) / 2;
        int noteY = body.y() + (body.height() - noteHeight) / 2;
        DetectiveFolderPaint.rotated(context, noteX + noteWidth / 2.0F, noteY + noteHeight / 2.0F, -3.0F,
                () -> DetectiveFolderPaint.drawStickyNote(context, textRenderer, note, noteX, noteY, noteWidth, 4));
    }

    private void renderFolderBody(DrawContext context) {
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
    private void renderTabs(DrawContext context, DetectiveCasePlayerComponent folder, int mouseX, int mouseY, boolean viewedPass) {
        List<DetectiveCase> cases = folder.getCases();
        int perPage = layout.tabsPerPage(cases.size());
        int first = tabPage * perPage;
        for (int slot = 0; slot < perPage && first + slot < cases.size(); slot++) {
            DetectiveCase detectiveCase = cases.get(first + slot);
            boolean viewed = detectiveCase.getCaseId().equals(viewedCaseId);
            if (viewed != viewedPass) {
                continue;
            }
            DetectiveFolderLayout.Rect tab = layout.tab(slot);
            boolean hovered = tabHitBox(slot).contains(mouseX, mouseY);
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
                    DetectiveFolderPaint.trim(textRenderer, detectiveCase.getVictimName(), tab.width() - 16),
                    tab.x() + 13,
                    contentY,
                    DetectiveFolderPaint.INK,
                    false
            );
            if (viewed && isActive(folder, detectiveCase.getCaseId())) {
                drawActivePin(context, tab, top);
            }
        }
        if (!viewedPass && layout.needsArrows(cases.size())) {
            int pages = pageCount(cases.size());
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
        int color = enabled ? DetectiveFolderPaint.INK : DetectiveFolderPaint.withAlpha(DetectiveFolderPaint.INK, 0x55);
        DetectiveFolderPaint.drawArrow(context, rect.x() + (rect.width() - 4) / 2, rect.y() + (rect.height() - 7) / 2, left, color);
    }

    private void renderLeftPage(DrawContext context, DetectiveCase viewed, int mouseX, int mouseY) {
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
        String victimName = trimStyled(viewed.getVictimName(), bold, polaroid.x() - 4 - textX);
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
        Identifier guess = displayedGuess(viewed);
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
            label = DetectiveRolePicker.roleName(role);
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
        String visible = DetectiveFolderPaint.trim(textRenderer, full, pill.width() - 20);
        context.drawText(textRenderer, visible, pill.x() + 5, pill.y() + 4, color, false);
        DetectiveFolderPaint.drawPencil(context, pill.right() - 12, pill.y() + 4);
        if (pillHovered && !visible.equals(full)) {
            hoverTooltip = label;
        }

        UUID presumed = displayedPresumed(viewed);
        boolean presumedHovered = hoversPresumed(mouseX, mouseY);
        if (presumedHovered) {
            context.fill(presumedLabel.x(), presumedLabel.y(), presumedLabel.right(), presumedLabel.bottom(), COLOR_LABEL_HOVER);
        }
        drawHeader(context, "screen.sparkstrength.detective_folder.presumed_killer", textX,
                page.y() + DetectiveFolderLayout.PRESUMED_HEADER_Y, columnWidth);
        String presumedName = presumed != null
                ? PlayerNameResolver.playerName(presumed)
                : Text.translatable("screen.sparkstrength.detective_folder.presumed_killer_none").getString();
        Style bold = Style.EMPTY.withBold(true);
        String presumedVisible = trimStyled(presumedName, bold, Math.max(0, columnWidth - PENCIL_ROOM));
        Text presumedText = Text.literal(presumedVisible).setStyle(bold);
        int nameY = page.y() + DetectiveFolderLayout.PRESUMED_NAME_Y;
        context.drawText(textRenderer, presumedText, textX, nameY,
                presumed != null ? DetectiveFolderPaint.INK : DetectiveFolderPaint.INK_MUTED, false);
        DetectiveFolderPaint.drawPencil(context, textX + textRenderer.getWidth(presumedText) + 4, nameY);
        if (presumedHovered && !presumedVisible.equals(presumedName)) {
            hoverTooltip = Text.literal(presumedName);
        }

        int silhouette = role != null ? DetectiveFolderPaint.opaque(role.color()) : COLOR_SILHOUETTE;
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

    private boolean hoversPresumed(double mouseX, double mouseY) {
        return layout.presumedLabel().contains(mouseX, mouseY) || layout.presumedPolaroid().contains(mouseX, mouseY);
    }

    private void renderRightPage(DrawContext context, DetectiveCasePlayerComponent folder, DetectiveCase viewed, int mouseX, int mouseY) {
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
                DetectiveFolderPaint.trim(textRenderer, clues, cluesWidth),
                cluesX,
                page.y() + DetectiveFolderLayout.SUSPECTS_TITLE_Y,
                DetectiveFolderPaint.INK_NAVY,
                false
        );
        Style bold = Style.EMPTY.withBold(true);
        String title = Text.translatable("screen.sparkstrength.detective_folder.suspects").getString();
        context.drawText(
                textRenderer,
                Text.literal(trimStyled(title, bold, Math.max(0, cluesX - 6 - textX))).setStyle(bold),
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
        int noteWidth = DetectiveFolderPaint.stickyNoteWidth(textRenderer, note, Math.max(40, page.width() - 12), 3);
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
        String visible = DetectiveFolderPaint.trim(textRenderer, name, nameWidth);
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
                DetectiveFolderPaint.trim(textRenderer, placeholder, row.width() - 17),
                row.x() + 13,
                textY,
                DetectiveFolderPaint.INK_FADED,
                false
        );
    }

    private void drawHeader(DrawContext context, String key, int x, int y, int maxWidth) {
        String header = Text.translatable(key).getString();
        context.drawText(textRenderer, DetectiveFolderPaint.trim(textRenderer, header, Math.max(0, maxWidth)), x, y,
                DetectiveFolderPaint.INK_RED, false);
    }

    private String trimStyled(String value, Style style, int width) {
        if (textRenderer.getWidth(StringVisitable.styled(value, style)) <= width) {
            return value;
        }
        String ellipsis = "…";
        int budget = Math.max(0, width - textRenderer.getWidth(StringVisitable.styled(ellipsis, style)));
        return textRenderer.trimToWidth(StringVisitable.styled(value, style), budget).getString() + ellipsis;
    }

    // ---- State / 状态 ----

    private @Nullable DetectiveCasePlayerComponent folder() {
        ClientPlayerEntity player = client == null ? null : client.player;
        return player == null ? null : DetectiveCasePlayerComponent.KEY.get(player);
    }

    private @Nullable DetectiveCase viewedCase(DetectiveCasePlayerComponent folder) {
        return viewedCaseId == null ? null : folder.findCase(viewedCaseId);
    }

    /**
     * Case to show: an unconfirmed tab click, else the server's selected case, else keep the current view, else the
     * newest case.
     * 要显示的命案：尚未确认的标签点击 > 服务端选中的命案 > 保持当前视图 > 最新的命案。
     */
    private @Nullable UUID resolveView(DetectiveCasePlayerComponent folder) {
        if (pendingSelection != null && folder.findCase(pendingSelection) != null) {
            return pendingSelection;
        }
        UUID selected = folder.getSelectedCaseId();
        if (selected != null && folder.findCase(selected) != null) {
            return selected;
        }
        if (viewedCaseId != null && folder.findCase(viewedCaseId) != null) {
            return viewedCaseId;
        }
        List<DetectiveCase> cases = folder.getCases();
        return cases.isEmpty() ? null : cases.get(cases.size() - 1).getCaseId();
    }

    /** The case the magnifier works on, as far as this client knows. / 本客户端所知的放大镜当前调查命案。 */
    private boolean isActive(DetectiveCasePlayerComponent folder, UUID caseId) {
        UUID active = pendingSelection != null ? pendingSelection : folder.getSelectedCaseId();
        return caseId.equals(active);
    }

    /**
     * Switches the editor to {@code next}, saving the outgoing case's notes first. Returns true if the view changed.
     * 将编辑器切换到 next，切换前先保存离开的命案笔记；视图变化时返回 true。
     */
    private boolean showCase(@Nullable UUID next, DetectiveCasePlayerComponent folder) {
        if (Objects.equals(next, viewedCaseId)) {
            return false;
        }
        commitNotes(folder);
        viewedCaseId = next;
        DetectiveCase detectiveCase = next == null ? null : folder.findCase(next);
        notesDraft = detectiveCase == null ? "" : sentNotes.getOrDefault(next, detectiveCase.getNotes());
        notesEdited = false;
        closePickers();
        revealViewedTab(folder);
        return true;
    }

    /**
     * Sends the viewed case's notes if they differ from the latest text the server has or is about to have.
     * 若当前命案笔记与服务端已有或即将收到的文本不同，则发送保存。
     */
    private void commitNotes(DetectiveCasePlayerComponent folder) {
        if (viewedCaseId == null) {
            return;
        }
        DetectiveCase detectiveCase = folder.findCase(viewedCaseId);
        if (detectiveCase == null) {
            return;
        }
        String sanitized = DetectiveCaseRules.sanitizeNotes(notesDraft);
        String known = sentNotes.getOrDefault(viewedCaseId, detectiveCase.getNotes());
        if (sanitized.equals(known)) {
            return;
        }
        if (send(new UpdateDetectiveCaseNotesC2SPacket(viewedCaseId, sanitized))) {
            sentNotes.put(viewedCaseId, sanitized);
        }
    }

    /**
     * A sync for the viewed case replaces an untouched draft, so a screen opened on stale data never re-saves it.
     * 同步到当前命案时替换未改动的草稿，避免以旧数据打开的界面把旧文本重新保存。
     */
    private void adoptUntouchedNotes(DetectiveCasePlayerComponent folder) {
        if (viewedCaseId == null || notesEdited || sentNotes.containsKey(viewedCaseId)) {
            return;
        }
        DetectiveCase detectiveCase = folder.findCase(viewedCaseId);
        if (detectiveCase == null || detectiveCase.getNotes().equals(notesDraft)) {
            return;
        }
        notesDraft = detectiveCase.getNotes();
        if (notesBox != null) {
            suppressNotesListener = true;
            try {
                notesBox.setText(notesDraft);
            } finally {
                suppressNotesListener = false;
            }
        }
    }

    /** Drops optimistic state that the new sync confirmed or made moot. / 丢弃已被新同步确认或已失效的乐观状态。 */
    private void reconcile(DetectiveCasePlayerComponent folder) {
        if (pendingSelection != null
                && (pendingSelection.equals(folder.getSelectedCaseId()) || folder.findCase(pendingSelection) == null)) {
            pendingSelection = null;
        }
        sentNotes.entrySet().removeIf(entry -> {
            DetectiveCase detectiveCase = folder.findCase(entry.getKey());
            return detectiveCase == null || detectiveCase.getNotes().equals(entry.getValue());
        });
        pendingGuesses.entrySet().removeIf(entry -> {
            DetectiveCase detectiveCase = folder.findCase(entry.getKey());
            return detectiveCase == null || Objects.equals(detectiveCase.getKillerGuess(), entry.getValue().value);
        });
        pendingPresumed.entrySet().removeIf(entry -> {
            DetectiveCase detectiveCase = folder.findCase(entry.getKey());
            return detectiveCase == null || Objects.equals(detectiveCase.getPresumedKillerUuid(), entry.getValue().value);
        });
    }

    /** Ages optimistic state; returns true when the pending tab selection expired. / 推进乐观状态计时，标签选择过期时返回 true。 */
    private boolean tickPending() {
        boolean expired = false;
        if (pendingSelection != null && --pendingSelectionTicks <= 0) {
            pendingSelection = null;
            expired = true;
        }
        pendingGuesses.values().removeIf(pending -> --pending.ticksLeft <= 0);
        pendingPresumed.values().removeIf(pending -> --pending.ticksLeft <= 0);
        return expired;
    }

    private void selectCase(DetectiveCasePlayerComponent folder, UUID caseId) {
        if (caseId.equals(viewedCaseId) && isActive(folder, caseId)) {
            return;
        }
        // Save first, then select, then switch the view (showCase finds nothing left to save).
        // 先保存笔记，再发送选择，最后切换视图（showCase 不会重复保存）。
        commitNotes(folder);
        if (send(new SelectDetectiveCaseC2SPacket(caseId))) {
            pendingSelection = caseId;
            pendingSelectionTicks = PENDING_TIMEOUT_TICKS;
        }
        if (showCase(resolveView(folder), folder)) {
            refresh();
        }
        playSound(PositionedSoundInstance.master(SoundEvents.ITEM_BOOK_PAGE_TURN, 1.0F));
    }

    private void pickKiller(DetectiveCasePlayerComponent folder, @Nullable Identifier roleId) {
        rolePicker.close();
        if (viewedCaseId == null || folder.findCase(viewedCaseId) == null) {
            return;
        }
        if (send(new SetDetectiveKillerGuessC2SPacket(viewedCaseId, Optional.ofNullable(roleId)))) {
            pendingGuesses.put(viewedCaseId, new Pending<>(roleId));
        }
        playClick();
    }

    private void pickPresumed(DetectiveCasePlayerComponent folder, @Nullable UUID playerUuid) {
        playerPicker.close();
        if (viewedCaseId == null || folder.findCase(viewedCaseId) == null) {
            return;
        }
        if (send(new SetDetectivePresumedKillerC2SPacket(viewedCaseId, Optional.ofNullable(playerUuid)))) {
            pendingPresumed.put(viewedCaseId, new Pending<>(playerUuid));
        }
        playClick();
    }

    private @Nullable Identifier displayedGuess(DetectiveCase viewed) {
        Pending<Identifier> pending = pendingGuesses.get(viewed.getCaseId());
        return pending != null ? pending.value : viewed.getKillerGuess();
    }

    private @Nullable UUID displayedPresumed(DetectiveCase viewed) {
        Pending<UUID> pending = pendingPresumed.get(viewed.getCaseId());
        return pending != null ? pending.value : viewed.getPresumedKillerUuid();
    }

    /** Only one picker is ever open; either one makes the screen modal. / 同一时间只会打开一个选择浮层，任一打开即为模态。 */
    private boolean pickerOpen() {
        return rolePicker.isOpen() || playerPicker.isOpen();
    }

    private void closePickers() {
        rolePicker.close();
        playerPicker.close();
    }

    private void onNotesEdited(String value) {
        if (suppressNotesListener) {
            return;
        }
        if (lineCount(value) > DetectiveCaseRules.NOTES_MAX_LINES && notesBox != null) {
            // Same line cap the server applies; reject the edit instead of silently losing lines on save.
            // 与服务端相同的行数上限；直接拒绝本次编辑，避免保存时静默丢行。
            suppressNotesListener = true;
            try {
                notesBox.setText(notesDraft);
            } finally {
                suppressNotesListener = false;
            }
            return;
        }
        notesDraft = value;
        notesEdited = true;
        notesAutosaveTicks = NOTES_AUTOSAVE_TICKS;
    }

    private static int lineCount(String value) {
        int lines = 1;
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) == '\n') {
                lines++;
            }
        }
        return lines;
    }

    private void revealViewedTab(DetectiveCasePlayerComponent folder) {
        List<DetectiveCase> cases = folder.getCases();
        for (int index = 0; index < cases.size(); index++) {
            if (cases.get(index).getCaseId().equals(viewedCaseId)) {
                tabPage = index / layout.tabsPerPage(cases.size());
                return;
            }
        }
    }

    private void clampTabPage(DetectiveCasePlayerComponent folder) {
        tabPage = Math.max(0, Math.min(pageCount(folder.getCases().size()) - 1, tabPage));
    }

    private int pageCount(int caseCount) {
        int perPage = layout.tabsPerPage(caseCount);
        return Math.max(1, (caseCount + perPage - 1) / perPage);
    }

    /** Tab click area; the viewed tab also covers its raised 2 px. / 标签点击区域；当前标签包含抬高的 2 像素。 */
    private DetectiveFolderLayout.Rect tabHitBox(int slot) {
        DetectiveFolderLayout.Rect tab = layout.tab(slot);
        return new DetectiveFolderLayout.Rect(tab.x(), tab.y() - 2, tab.width(), tab.height());
    }

    private static boolean canKeepOpen(ClientPlayerEntity player) {
        // Wathe's alive check ignores spectator mode (e.g. Taotie-swallowed); the server rejects spectators' edits.
        // Wathe 的存活判断不排除旁观模式（如被饕餮吞下）；服务端会拒绝旁观者的编辑。
        if (!GameFunctions.isPlayerPlayingAndAlive(player) || player.isSpectator()) {
            return false;
        }
        return DetectiveRules.isDetective(GameWorldComponent.KEY.get(player.getWorld()).getRole(player));
    }

    /** Sends only when the server registered the channel, so an older server never disconnects us. / 服务端注册了通道才发送。 */
    private static boolean send(CustomPayload payload) {
        if (!ClientPlayNetworking.canSend(payload.getId())) {
            return false;
        }
        ClientPlayNetworking.send(payload);
        return true;
    }

    private void playClick() {
        playSound(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private void playSound(PositionedSoundInstance sound) {
        if (client != null) {
            client.getSoundManager().play(sound);
        }
    }

    /** Optimistic value shown until a sync confirms it or it times out; null means "cleared". / 乐观显示值，null 表示已清除。 */
    private static final class Pending<T> {
        private final @Nullable T value;
        private int ticksLeft = PENDING_TIMEOUT_TICKS;

        private Pending(@Nullable T value) {
            this.value = value;
        }
    }
}
