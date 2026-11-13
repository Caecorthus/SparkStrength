package annina.sparkstrength.client.screen.tablet;

import annina.sparkstrength.client.screen.tablet.TabletChromePainter.ChannelChip;
import annina.sparkstrength.client.screen.tablet.TabletSectionPainter.ButtonIcon;
import annina.sparkstrength.client.screen.tablet.TabletSectionPainter.ButtonStyle;
import annina.sparkstrength.client.screen.tablet.TabletTheme.Accent;
import annina.sparkstrength.network.drone.DronePilotStartC2SPacket;
import annina.sparkstrength.network.tablet.RequestTabletSnapshotC2SPacket;
import annina.sparkstrength.network.tablet.TabletSnapshot;
import annina.sparkstrength.tablet.TabletChannel;
import annina.sparkstrength.tablet.TabletFeature;
import annina.sparkstrength.tablet.TabletLayout;
import annina.sparkstrength.tablet.TabletLayout.Rect;
import annina.sparkstrength.tablet.TabletUiSession;
import annina.sparkstrength.tablet.TabletUiSession.Section;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static annina.sparkstrength.client.screen.tablet.TabletFeatureOnly.featureOnly;
import static annina.sparkstrength.client.screen.tablet.TabletFeatureOnly.frameAccentFor;
import static annina.sparkstrength.client.screen.tablet.TabletSectionPainter.KEY;

/**
 * Tablet UI. Every view is driven by the server-redacted snapshot; the client never infers channels from roles.
 * 平板界面。所有视图都由服务端裁剪后的快照驱动；客户端从不根据身份推断频道。
 *
 * <p>The screen paints in its own logical canvas ({@link TabletViewport}, {@link TabletLayout#effectiveScale}): render()
 * scales the matrix by {@code logicalScale} and every mouse event is divided by it before any hit test, so widgets,
 * layout and painting all share logical units. Clipping goes through {@link TabletCanvas#pushClip} (physical pixels),
 * never DrawContext.enableScissor, which ignores the matrix.
 * 界面在独立的逻辑画布中绘制（见 TabletViewport 与 effectiveScale）：render() 以 logicalScale 缩放矩阵，所有鼠标事件在命中测试前
 * 都先除以它，因此控件、布局与绘制都使用逻辑单位。裁剪只通过 TabletCanvas.pushClip（物理像素），从不使用会忽略矩阵的
 * DrawContext.enableScissor。</p>
 *
 * <p>Each section is a {@link TabletPage} created once with the screen; the screen keeps the frame (device, status bar,
 * rail, header title, painted by {@link TabletChromePainter}), the {@link TabletChannelSwitcher} and input routing, and
 * asks the open page for its widgets, scrolling, header chip and body.
 * 每个分区是随界面创建一次的 TabletPage；界面自己负责外框（设备、状态栏、侧栏、页眉标题，由 TabletChromePainter 绘制）、
 * 频道切换浮层与输入分发，并向当前页面获取其控件、滚动、页眉胶囊与主体。</p>
 *
 * <p>Feature sections (the Attendant door log, the Bomber drone link) ride along with any channel. A holder whose only
 * content is feature sections ("feature-only": no channel and none to switch to, e.g. a plain Attendant or a SparkTraits
 * Conscience Bomber; see {@link TabletFeatureOnly}) gets that feature's chrome instead of "No Signal": a non-clickable
 * status chip naming it, its badge glyph and its accent ({@link TabletTheme#accentFor(TabletFeature)}).
 * 功能分区（乘务员房门记录、炸弹客无人机链路）可与任意频道并存。只有功能分区的持有者（“仅功能”：无频道且无可切换频道，
 * 例如普通乘务员或 SparkTraits 善良炸弹客）显示该功能的外观，而非“无信号”：写着功能名的不可点击状态胶囊、
 * 功能徽标图形与功能强调色。</p>
 *
 * <p>The drone section only asks to connect ({@link DronePilotStartC2SPacket}); the server re-validates ownership,
 * state and the pilot's body, and the pilot client owns everything after that (camera, input, closing this screen).
 * 无人机分区只发出连接请求；服务端重新校验归属、状态与驾驶者本体，此后的一切（视角、输入、关闭本界面）由驾驶客户端负责。</p>
 */
public final class TabletScreen extends Screen {
    private final TabletUiSession session = new TabletUiSession(TabletClientState.chatDraft());
    // One page per section, created once; init() only rebuilds their widgets. 每个分区一个页面，只创建一次；init() 只重建其控件。
    private final Map<Section, TabletPage> pages = new EnumMap<>(Section.class);
    private final TabletChatPage chat;
    private final TabletChannelSwitcher switcher;
    private final TabletChromePainter chrome;

    private final TabletViewport viewport = new TabletViewport();
    private TabletLayout layout;
    private List<Section> visibleSections = List.of();
    // Tab count the layout mode is chosen for (see TabletUiSession.referenceSectionCount). 布局模式所依据的标签数。
    private int referenceTabs;

    // Per-frame paint state; widget painters read it. 每帧绘制状态；控件绘制器读取它。
    private TabletCanvas frameCanvas;
    private Accent frameAccent = TabletTheme.NONE;

    // Rail widgets rebuilt by init(). init() 重建的侧栏控件。
    private final List<TabletPressable> tabButtons = new ArrayList<>();
    private final List<Section> tabSections = new ArrayList<>();

    private boolean requestedInitialSnapshot;
    private int lastChannelWire;
    private boolean lastCanSend;
    // Which v10 countdowns were running when the widgets were last built (see countdownState).
    // 上次构建控件时哪些 v10 倒计时仍在进行（见 countdownState）。
    private int builtCountdownState;

    // Stable identity of each keyed widget in the current build, so a rebuild can restore keyboard focus.
    // 当前构建中各控件的稳定标识，供重建时恢复键盘焦点。
    private final Map<Element, String> focusKeys = new IdentityHashMap<>();

    public TabletScreen() {
        super(Text.translatable(KEY + "title"));
        TabletSnapshot snapshot = TabletClientState.snapshot();
        lastChannelWire = snapshot.channelWire();
        lastCanSend = snapshot.canSend();
        // Before the chat page, which binds the painter's backdrop colour. 先于聊天页面创建，聊天页面会绑定其背景色方法。
        chrome = new TabletChromePainter(this, tabButtons, tabSections, pages);
        chat = new TabletChatPage(this);
        switcher = new TabletChannelSwitcher(this);
        pages.put(Section.CONNECTIONS, new TabletConnectionsPage(this));
        pages.put(Section.CHAT, chat);
        pages.put(Section.MEETING, new TabletMeetingPage(this));
        pages.put(Section.SUSPECTS, new TabletSuspectsPage(this));
        pages.put(Section.DOOR_LOG, new TabletDoorLogPage(this));
        pages.put(Section.DRONE, new TabletDronePage(this));
        pages.put(Section.SABOTAGE, new TabletSabotagePage(this));
        Section resume = TabletClientState.consumeResumeSection();
        if (resume != null) {
            // init() falls back to the first visible section if it is gone. 若该分区已不可见，init() 会回退到第一个可见分区。
            session.select(resume);
        }
    }

    // ============================================================================================ lifecycle

    @Override
    protected void init() {
        super.init();
        viewport.update(client.getWindow());
        TabletSnapshot snapshot = TabletClientState.snapshot();
        builtCountdownState = countdownState(snapshot);
        EnumSet<TabletFeature> features = snapshot.features();
        visibleSections = TabletUiSession.visibleSections(snapshot.channel(), features);
        referenceTabs = TabletUiSession.referenceSectionCount(snapshot.channel(), snapshot.allowedChannels(), features);
        session.ensureVisible(visibleSections);
        layout = TabletLayout.forViewport(viewport.canvasWidth(), viewport.canvasHeight(), visibleSections.size(),
                referenceTabs);
        for (TabletPage page : pages.values()) {
            page.prepare(snapshot);
        }
        focusKeys.clear();
        tabButtons.clear();
        tabSections.clear();

        // One request per open; afterwards the server pushes every change to holders, open screen or not.
        // 每次打开只请求一次；之后服务端会把每次变化推送给持有者，无论界面是否打开。
        if (!requestedInitialSnapshot) {
            requestedInitialSnapshot = true;
            requestSnapshot();
        }

        initCloseButton();
        if (visibleSections.isEmpty()) {
            return;
        }
        initTabs();
        page().init(snapshot);
    }

    public void refresh() {
        if (client == null) {
            return;
        }
        chat.rememberChatDraft();
        String focusKey = switcher.isOpen() ? null : focusKeys.get(getFocused());
        // Drop focus before removing widgets (as Screen.clearAndInit does); otherwise a detached chat input keeps
        // receiving keystrokes when the rebuild does not re-attach it (read-only holder, other section).
        // 移除控件前先清除焦点（与 Screen.clearAndInit 一致）；否则重建时若未重新挂上输入框（只读持有者、其它分区），
        // 已脱离的输入框仍会接收按键输入。
        setFocused(null);
        clearChildren();
        init();
        restoreFocus(focusKey);
    }

    /**
     * Snapshot rebuilds (Meeting/Suspects refresh on every snapshot) keep keyboard focus on the equivalent new widget;
     * skipped while the switcher is open or when init() already focused something (the chat input).
     * 快照重建（会议/嫌疑人分区每次收到快照都会刷新）时把键盘焦点保留在对应的新控件上；
     * 切换浮层打开或 init() 已设置焦点（聊天输入框）时不恢复。
     */
    private void restoreFocus(@Nullable String focusKey) {
        if (focusKey == null || switcher.isOpen() || getFocused() != null) {
            return;
        }
        for (Element child : children()) {
            if (focusKey.equals(focusKeys.get(child))
                    && child instanceof TabletPressable pressable
                    && pressable.active
                    && pressable.visible) {
                setFocused(pressable);
                return;
            }
        }
    }

    public void handleSnapshotUpdate() {
        TabletSnapshot snapshot = TabletClientState.snapshot();
        if (!snapshot.localHasTablet()) {
            // The server revokes with an empty snapshot (tablet left the hotbar, round reset); nothing left to show.
            // 服务端以空快照撤销访问（平板离开快捷栏、回合重置），界面已无内容可显示。
            close();
            return;
        }

        int previousChannelWire = lastChannelWire;
        boolean channelChanged = snapshot.channelWire() != previousChannelWire;
        boolean sendChanged = snapshot.canSend() != lastCanSend;
        lastChannelWire = snapshot.channelWire();
        lastCanSend = snapshot.canSend();
        if (channelChanged) {
            onChannelChanged(previousChannelWire);
        }

        for (TabletPage page : pages.values()) {
            page.onSnapshot(snapshot, layout != null);
        }
        if (switcher.isOpen() && featureOnly(snapshot)) {
            // Became feature-only (channels lost mid-round): the switcher has nothing to offer. 变为仅功能时关闭切换浮层。
            switcher.close();
        }
        // Features can change mid-round (Coroner disguise ends), so the section set is re-derived on every snapshot.
        // 功能可能在回合中变化（验尸官伪装结束），因此每次快照都重新计算分区集合。
        EnumSet<TabletFeature> features = snapshot.features();
        List<Section> nextSections = TabletUiSession.visibleSections(snapshot.channel(), features);
        int nextReference = TabletUiSession.referenceSectionCount(snapshot.channel(), snapshot.allowedChannels(), features);
        boolean sectionChanged = session.ensureVisible(nextSections);
        if (channelChanged
                || sendChanged
                || sectionChanged
                || !nextSections.equals(visibleSections)
                || nextReference != referenceTabs
                || page().needsRebuild(snapshot)) {
            refresh();
        }
    }

    private void onChannelChanged(int previousChannelWire) {
        chat.onChannelChanged(previousChannelWire);
        switcher.onChannelChanged();
    }

    @Override
    public void tick() {
        super.tick();
        switcher.tick();
        for (TabletPage page : pages.values()) {
            page.tick(page == page());
        }
        // v10 countdowns run on the client and an ending one changes no snapshot, so the widgets that depend on it
        // (the call-meeting button, the cooldown hero card) are rebuilt here once, as the per-second snapshot did before.
        // v10 倒计时在客户端运行，结束时不会产生新快照，因此依赖它的控件（召开会议按钮、冷却英雄卡）在此重建一次，与以前每秒快照的效果相同。
        if (countdownState(TabletClientState.snapshot()) != builtCountdownState) {
            refresh();
        }
    }

    /** Bit 0: switch cooldown running, bit 1: meeting cooldown running. / 位 0：切换冷却进行中；位 1：会议冷却进行中。 */
    private static int countdownState(TabletSnapshot snapshot) {
        long worldTime = TabletClientState.worldTime();
        return (snapshot.channelSwitchCooldownSeconds(worldTime) > 0 ? 1 : 0)
                | (snapshot.meetingCooldownSeconds(worldTime) > 0 ? 2 : 0);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // No vanilla blur/darkening: the tablet paints its own backdrop. 不使用原版模糊/变暗：平板自行绘制背景遮罩。
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void requestSnapshot() {
        ClientPlayNetworking.send(new RequestTabletSnapshotC2SPacket());
    }

    /**
     * Widgets live in logical canvas units, so the screen's navigation rect must be logical too (arrow navigation
     * with nothing focused starts from its border).
     * 控件位于逻辑画布坐标，屏幕导航矩形也须使用逻辑坐标（无焦点时方向键导航从其边缘开始）。
     */
    @Override
    public ScreenRect getNavigationFocus() {
        return new ScreenRect(0, 0, Math.max(1, viewport.canvasWidth()), Math.max(1, viewport.canvasHeight()));
    }

    TabletPage page() {
        return pages.get(session.section());
    }

    // ============================================================================================ widgets

    private void initCloseButton() {
        Rect close = layout.closeButton();
        keyed("close", addDrawableChild(new TabletPressable(close.x(), close.y(), close.width(), close.height(),
                Text.translatable(KEY + "close"), this::close,
                (context, button, hovered, delta) -> chrome.paintCloseButton(button, hovered))));
    }

    private void initTabs() {
        int count = Math.min(visibleSections.size(), layout.tabs().size());
        for (int index = 0; index < count; index++) {
            Section section = visibleSections.get(index);
            Rect rect = layout.tabs().get(index);
            TabletPressable tab = new TabletPressable(rect.x(), rect.y(), rect.width(), rect.height(),
                    Text.translatable(section.translationKey()), () -> selectSection(section),
                    (context, button, hovered, delta) -> chrome.paintTab(button, section, hovered));
            // The selected tab is inactive (like before) but still painted selected. 选中的标签不可点击，但仍绘制为选中态。
            tab.active = section != session.section();
            tab.setSelectedState(section == session.section());
            tabButtons.add(keyed("tab:" + section.name(), addDrawableChild(tab)));
            tabSections.add(section);
        }
    }

    private void selectSection(Section section) {
        chat.rememberChatDraft();
        session.select(section);
        if (section == Section.CHAT) {
            chat.resetChatScroll();
        }
        refresh();
    }

    TabletPressable addButton(String focusKey, Rect rect, Text label, ButtonStyle style, ButtonIcon icon,
                              Runnable action) {
        return addButton(focusKey, rect, label, style, icon, null, action);
    }

    /** {@code accent} null = the frame accent of the frame being painted. accent 为 null 时使用当前帧的外框强调色。 */
    TabletPressable addButton(String focusKey, Rect rect, Text label, ButtonStyle style, ButtonIcon icon,
                              @Nullable Accent accent, Runnable action) {
        return keyed(focusKey, addDrawableChild(new TabletPressable(rect.x(), rect.y(), rect.width(), rect.height(),
                label, action, (context, button, hovered, delta) -> TabletSectionPainter.button(frameCanvas,
                        textRenderer, button, style, icon, label, hovered, accent != null ? accent : frameAccent))));
    }

    <T extends Element> T keyed(String focusKey, T widget) {
        focusKeys.put(widget, focusKey);
        return widget;
    }

    /** An unkeyed widget (the chat composer's). 不参与焦点恢复的控件（聊天输入栏）。 */
    <T extends Element & Drawable & Selectable> T addChild(T widget) {
        return addDrawableChild(widget);
    }

    void focusInitially(Element widget) {
        setInitialFocus(widget);
    }

    // ============================================================================================ input

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (switcher.isOpen()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeSwitcher();
                return true;
            }
            // The overlay is modal: swallow focus traversal (Tab/arrows) and activation (Space/Enter) so no widget
            // underneath can gain focus or be pressed. Other keys are not forwarded to widgets either.
            // 浮层为模态：拦截焦点切换（Tab/方向键）与激活（空格/回车），防止下层控件获得焦点或被触发；其余按键也不转发给控件。
            return TabletChannelSwitcher.isBlockedKey(keyCode);
        }
        // Enter sends only from the composer; a focused button activates instead.
        // 回车仅在输入区发送；聚焦的按钮则被激活。
        Element focused = getFocused();
        if (session.section() == Section.CHAT
                && !visibleSections.isEmpty()
                && chat.sendsOnEnter(focused)
                && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            chat.sendChat();
            return true;
        }
        boolean handled = super.keyPressed(keyCode, scanCode, modifiers);
        if (session.section() == Section.CHAT
                && chat.inputFocused()
                && isMovementKey(keyCode, scanCode)) {
            return true;
        }
        return handled;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (switcher.isOpen()) {
            // Text never reaches an input hidden behind the switcher overlay. 文字输入不会传到切换浮层下方的输入框。
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double x = viewport.toLogical(mouseX);
        double y = viewport.toLogical(mouseY);
        TabletSnapshot snapshot = TabletClientState.snapshot();
        if (switcher.isOpen()) {
            return switcher.mouseClicked(snapshot, x, y, button);
        }
        if (layout == null) {
            return super.mouseClicked(x, y, button);
        }
        ChannelChip chip = chrome.channelChip(snapshot);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && chip.clickable()
                && (chip.rect().contains(x, y) || layout.channelBadge().contains(x, y))) {
            openSwitcher();
            return true;
        }
        TabletTextInput input = chat.attachedInput();
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && session.section() == Section.CHAT
                && input != null
                && chat.composerPill().contains(x, y)) {
            // The whole pill focuses the input and places the caret (the input's own hit box is one text line tall);
            // dragging then extends the selection through ParentElement.mouseDragged.
            // 整个输入胶囊都能聚焦输入框并定位光标（输入框自身的命中区只有一行高）；随后拖动经 ParentElement.mouseDragged 扩展选区。
            setFocused(input);
            input.onClick(x, y);
            setDragging(true);
            return true;
        }
        boolean handled = super.mouseClicked(x, y, button);
        if (handled) {
            repairFocusAfterClick();
        }
        return handled;
    }

    /**
     * ParentElement.mouseClicked focuses the clicked child AFTER its action ran. A tab click rebuilds the widgets, so
     * that child is already detached; send / "new messages" should leave typing in the input.
     * ParentElement.mouseClicked 在控件动作执行之后才聚焦被点击的控件。点击标签会重建控件，此时该控件已被移除；
     * 点击发送或“新消息”后应继续在输入框中输入。
     */
    private void repairFocusAfterClick() {
        Element focused = getFocused();
        if (focused == null) {
            return;
        }
        boolean detached = !children().contains(focused);
        boolean chatControl = chat.isComposerControl(focused);
        if (detached || chatControl) {
            setFocused(chat.attachedInput());
            // Repaired focus must not inherit the click's drag, or a held-button jitter would select the draft.
            // 修复后的焦点不能继承本次点击的拖动状态，否则按住鼠标时的微小移动会选中草稿。
            setDragging(false);
        }
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return super.mouseReleased(viewport.toLogical(mouseX), viewport.toLogical(mouseY), button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (switcher.isOpen()) {
            return true;
        }
        return super.mouseDragged(viewport.toLogical(mouseX), viewport.toLogical(mouseY), button,
                viewport.toLogical(deltaX), viewport.toLogical(deltaY));
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(viewport.toLogical(mouseX), viewport.toLogical(mouseY));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (switcher.isOpen()) {
            return true;
        }
        double x = viewport.toLogical(mouseX);
        double y = viewport.toLogical(mouseY);
        if (layout == null || visibleSections.isEmpty()) {
            return super.mouseScrolled(x, y, horizontalAmount, verticalAmount);
        }
        TabletSnapshot snapshot = TabletClientState.snapshot();
        if (page().mouseScrolled(snapshot, x, y, verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(x, y, horizontalAmount, verticalAmount);
    }

    private boolean isMovementKey(int keyCode, int scanCode) {
        if (client == null) {
            return false;
        }
        return client.options.forwardKey.matchesKey(keyCode, scanCode)
                || client.options.backKey.matchesKey(keyCode, scanCode)
                || client.options.leftKey.matchesKey(keyCode, scanCode)
                || client.options.rightKey.matchesKey(keyCode, scanCode)
                || client.options.jumpKey.matchesKey(keyCode, scanCode)
                || client.options.sneakKey.matchesKey(keyCode, scanCode)
                || client.options.sprintKey.matchesKey(keyCode, scanCode);
    }

    // ============================================================================================ switcher

    private void openSwitcher() {
        chat.rememberChatDraft();
        chat.blurInput();
        setFocused(null);
        switcher.open();
    }

    void closeSwitcher() {
        switcher.close();
        TabletTextInput input = chat.attachedInput();
        if (input != null && session.section() == Section.CHAT) {
            setFocused(input);
        }
    }

    // ============================================================================================ render

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (layout == null) {
            return;
        }
        TabletSnapshot snapshot = TabletClientState.snapshot();
        long now = Util.getMeasuringTimeMs();
        frameCanvas = new TabletCanvas(context, viewport.pixelScale(), viewport.framebufferHeight());
        frameAccent = frameAccentFor(snapshot, session.section());

        MatrixStack matrices = context.getMatrices();
        matrices.push();
        float logicalScale = viewport.logicalScale();
        matrices.scale(logicalScale, logicalScale, 1f);
        int logicalMouseX = (int) Math.floor(viewport.toLogical(mouseX));
        int logicalMouseY = (int) Math.floor(viewport.toLogical(mouseY));
        // While the switcher is open nothing underneath reacts to hover. 切换浮层打开时，下层元素不响应悬停。
        int hoverX = switcher.isOpen() ? -1 : logicalMouseX;
        int hoverY = switcher.isOpen() ? -1 : logicalMouseY;
        // The whole frame, widgets included, is one draw batch; avatars and clips flush inside it.
        // 整帧（含控件）是一个绘制批次；头像与裁剪会在批次内提交。
        frameCanvas.batch(() -> paintScreen(context, snapshot, logicalMouseX, logicalMouseY, hoverX, hoverY, delta, now));
        matrices.pop();
    }

    private void paintScreen(DrawContext context, TabletSnapshot snapshot, int logicalMouseX, int logicalMouseY,
                             int hoverX, int hoverY, float delta, long now) {
        chrome.paintFrame();
        chrome.paintStatusBar(snapshot, hoverX, hoverY, now);
        boolean hasSections = !visibleSections.isEmpty();
        if (hasSections) {
            chrome.paintRail(snapshot, hoverX, hoverY);
            chrome.paintHeader(snapshot, now);
            page().paintBody(snapshot, hoverX, hoverY, now);
        } else {
            chrome.paintNoSignal(snapshot, now);
        }

        super.render(context, hoverX, hoverY, delta);

        if (hasSections) {
            chrome.paintRailBadges(snapshot, now);
            chrome.paintTabTooltip();
        }
        if (switcher.isOpen()) {
            switcher.paint(snapshot, logicalMouseX, logicalMouseY, now);
        }
    }

    // ============================================================================================ helpers

    /**
     * Whether {@code channel}'s chat is on screen right now, so a new message there needs no buzz or unread mark.
     * 该频道的聊天此刻是否显示在屏幕上；若是，新消息无需振动或未读标记。
     */
    public boolean showsChatOf(@Nullable TabletChannel channel) {
        TabletSnapshot snapshot = TabletClientState.snapshot();
        return channel != null
                && snapshot.localHasTablet()
                && snapshot.channel() == channel
                && session.section() == Section.CHAT
                && !visibleSections.isEmpty();
    }

    @Nullable UUID localUuid() {
        return client != null && client.player != null ? client.player.getUuid() : null;
    }

    // ------------------------------------------------------------------------------------------------ page support

    /** The canvas of the frame being painted. 当前绘制帧的画布。 */
    TabletCanvas canvas() {
        return frameCanvas;
    }

    /** The frame accent of the frame being painted. 当前绘制帧的外框强调色。 */
    Accent accent() {
        return frameAccent;
    }

    /** Frame chrome painter and the frame-bound pill/badge/backdrop helpers. 外框绘制器及外框相关的胶囊/徽标/背景色工具。 */
    TabletChromePainter chrome() {
        return chrome;
    }

    TabletViewport viewport() {
        return viewport;
    }

    TabletLayout layout() {
        return layout;
    }

    TabletUiSession session() {
        return session;
    }

    TextRenderer textRenderer() {
        return textRenderer;
    }

    MinecraftClient client() {
        return client;
    }

    boolean switcherOpen() {
        return switcher.isOpen();
    }
}
