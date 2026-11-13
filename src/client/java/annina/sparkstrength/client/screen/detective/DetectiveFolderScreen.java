package annina.sparkstrength.client.screen.detective;

import annina.sparkstrength.component.detective.DetectiveCasePlayerComponent;
import annina.sparkstrength.component.detective.DetectiveCasePlayerComponent.DetectiveCase;
import annina.sparkstrength.role.detective.DetectiveRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;
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
 *
 * <p>This class keeps the screen lifecycle, input routing, tab paging and draw order; the case state and the edit
 * requests live in {@link DetectiveFolderState}, the folder painting in {@link DetectiveFolderRenderer}.
 * 本类负责界面生命周期、输入分发、标签翻页与绘制顺序；案件状态与修改请求在 DetectiveFolderState 中，文件夹绘制在
 * DetectiveFolderRenderer 中。</p>
 */
public final class DetectiveFolderScreen extends Screen {
    private static final int REVOKE_GRACE_TICKS = 10;

    private static final int COLOR_DIM = 0xB0000000;

    private final DetectiveRolePicker rolePicker = new DetectiveRolePicker();
    private final DetectivePlayerPicker playerPicker = new DetectivePlayerPicker();
    private final DetectiveFolderPaint.StickyNoteWidths stickyNoteWidths = new DetectiveFolderPaint.StickyNoteWidths();
    private final DetectiveFolderState state = new DetectiveFolderState();
    private DetectiveFolderLayout layout = DetectiveFolderLayout.forViewport(0, 0);
    // Rebuilt in init() with that layout and font; render() never runs before init().
    // 在 init() 中按当时的布局与字体重建；render() 不会早于 init() 调用。
    private DetectiveFolderRenderer folderRenderer;
    private int lastSyncRevision;
    private int tabPage;
    private int revokedTicks;
    private boolean stateReady;

    public DetectiveFolderScreen() {
        super(Text.translatable("screen.sparkstrength.detective_folder.title"));
    }

    @Override
    protected void init() {
        super.init();
        layout = DetectiveFolderLayout.forViewport(width, height);
        stickyNoteWidths.clear();
        folderRenderer = new DetectiveFolderRenderer(textRenderer, layout, stickyNoteWidths, state);
        state.detachNotesBox();
        DetectiveCasePlayerComponent folder = folder();
        if (folder == null) {
            return;
        }
        if (!stateReady) {
            stateReady = true;
            lastSyncRevision = folder.getSyncRevision();
            showCase(state.resolveView(folder), folder);
        }
        clampTabPage(folder);
        if (state.viewedCase(folder) == null) {
            return;
        }
        DetectiveNotesBox box = new DetectiveNotesBox(textRenderer, layout.notesBox());
        state.attachNotesBox(box);
        addDrawableChild(box);
    }

    /**
     * Rebuilds widgets (resize-safe order from TabletScreen): drop focus first so a removed notes box stops
     * receiving keystrokes. The draft lives in {@link DetectiveFolderState}, so it survives the rebuild.
     * 重建控件（沿用 TabletScreen 的顺序）：先清除焦点，避免已移除的笔记框继续接收按键。草稿保存在文件夹状态中，重建后保留。
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
            state.reconcile(folder);
        }
        boolean expired = state.tickPending();
        if (synced || expired) {
            boolean switched = showCase(state.resolveView(folder), folder);
            if (switched) {
                refresh();
            } else if (synced) {
                // Same case: keep the notes widget (caret, selection, scroll); only the tab page depends on the sync.
                // 同一命案：保留笔记控件（光标、选区、滚动），只有标签页码依赖同步数据。
                clampTabPage(folder);
                state.adoptUntouchedNotes(folder);
            }
        }
        state.tickNotesAutosave(folder);
    }

    @Override
    public void removed() {
        DetectiveCasePlayerComponent folder = folder();
        if (folder != null) {
            state.commitNotes(folder);
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
        DetectiveCase viewed = folder == null ? null : state.viewedCase(folder);
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
                    if (tabPage < layout.tabPageCount(cases.size()) - 1) {
                        tabPage++;
                        playClick();
                    }
                    return true;
                }
            }
            int perPage = layout.tabsPerPage(cases.size());
            int first = tabPage * perPage;
            for (int slot = 0; slot < perPage && first + slot < cases.size(); slot++) {
                if (layout.tabHitBox(slot).contains(mouseX, mouseY)) {
                    selectCase(folder, cases.get(first + slot).getCaseId());
                    return true;
                }
            }
            if (layout.killerPill().contains(mouseX, mouseY)) {
                setFocused(null);
                playerPicker.close();
                rolePicker.open(layout, textRenderer, state.displayedGuess(viewed));
                playClick();
                return true;
            }
            if (layout.containsPresumed(mouseX, mouseY)) {
                setFocused(null);
                rolePicker.close();
                playerPicker.open(layout, textRenderer, state.displayedPresumed(viewed));
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
            int pages = layout.tabPageCount(folder.getCases().size());
            tabPage = Math.max(0, Math.min(pages - 1, tabPage + (verticalAmount > 0.0D ? -1 : 1)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, COLOR_DIM);
        folderRenderer.clearHoverTooltip();
        DetectiveCasePlayerComponent folder = folder();
        DetectiveCase viewed = folder == null ? null : state.viewedCase(folder);
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
            folderRenderer.renderClosedFolder(context);
            super.render(context, hoverX, hoverY, delta);
            context.disableScissor();
            return;
        }

        folderRenderer.renderTabs(context, folder, tabPage, hoverX, hoverY, false);
        folderRenderer.renderFolderBody(context);
        folderRenderer.renderTabs(context, folder, tabPage, hoverX, hoverY, true);
        folderRenderer.renderLeftPage(context, viewed, hoverX, hoverY);
        folderRenderer.renderRightPage(context, folder, viewed, hoverX, hoverY);
        super.render(context, hoverX, hoverY, delta);
        context.disableScissor();

        if (rolePicker.isOpen()) {
            rolePicker.render(context, textRenderer, layout, mouseX, mouseY, state.displayedGuess(viewed));
        } else if (playerPicker.isOpen()) {
            playerPicker.render(context, textRenderer, layout, mouseX, mouseY, state.displayedPresumed(viewed));
        } else if (folderRenderer.hoverTooltip() != null) {
            context.drawTooltip(textRenderer, folderRenderer.hoverTooltip(), mouseX, mouseY);
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

    // ---- State / 状态 ----

    private @Nullable DetectiveCasePlayerComponent folder() {
        ClientPlayerEntity player = client == null ? null : client.player;
        return player == null ? null : DetectiveCasePlayerComponent.KEY.get(player);
    }

    /**
     * Switches the view to {@code next} (the state saves the outgoing case's notes first), then closes any picker and
     * turns the tab strip to the new case. Returns true if the view changed.
     * 切换到 next（状态先保存离开的命案笔记），随后关闭选择浮层并把标签栏翻到新命案；视图变化时返回 true。
     */
    private boolean showCase(@Nullable UUID next, DetectiveCasePlayerComponent folder) {
        if (!state.switchTo(next, folder)) {
            return false;
        }
        closePickers();
        revealViewedTab(folder);
        return true;
    }

    private void selectCase(DetectiveCasePlayerComponent folder, UUID caseId) {
        if (!state.requestSelection(folder, caseId)) {
            return;
        }
        if (showCase(state.resolveView(folder), folder)) {
            refresh();
        }
        playSound(PositionedSoundInstance.master(SoundEvents.ITEM_BOOK_PAGE_TURN, 1.0F));
    }

    private void pickKiller(DetectiveCasePlayerComponent folder, @Nullable Identifier roleId) {
        rolePicker.close();
        if (!state.requestKillerGuess(folder, roleId)) {
            return;
        }
        playClick();
    }

    private void pickPresumed(DetectiveCasePlayerComponent folder, @Nullable UUID playerUuid) {
        playerPicker.close();
        if (!state.requestPresumedKiller(folder, playerUuid)) {
            return;
        }
        playClick();
    }

    /** Only one picker is ever open; either one makes the screen modal. / 同一时间只会打开一个选择浮层，任一打开即为模态。 */
    private boolean pickerOpen() {
        return rolePicker.isOpen() || playerPicker.isOpen();
    }

    private void closePickers() {
        rolePicker.close();
        playerPicker.close();
    }

    private void revealViewedTab(DetectiveCasePlayerComponent folder) {
        List<DetectiveCase> cases = folder.getCases();
        for (int index = 0; index < cases.size(); index++) {
            if (cases.get(index).getCaseId().equals(state.viewedCaseId())) {
                tabPage = index / layout.tabsPerPage(cases.size());
                return;
            }
        }
    }

    private void clampTabPage(DetectiveCasePlayerComponent folder) {
        tabPage = Math.max(0, Math.min(layout.tabPageCount(folder.getCases().size()) - 1, tabPage));
    }

    private static boolean canKeepOpen(ClientPlayerEntity player) {
        // Wathe's alive check ignores spectator mode (e.g. Taotie-swallowed); the server rejects spectators' edits.
        // Wathe 的存活判断不排除旁观模式（如被饕餮吞下）；服务端会拒绝旁观者的编辑。
        if (!GameFunctions.isPlayerPlayingAndAlive(player) || player.isSpectator()) {
            return false;
        }
        return DetectiveRules.isDetective(GameWorldComponent.KEY.get(player.getWorld()).getRole(player));
    }

    private void playClick() {
        playSound(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private void playSound(PositionedSoundInstance sound) {
        if (client != null) {
            client.getSoundManager().play(sound);
        }
    }
}
