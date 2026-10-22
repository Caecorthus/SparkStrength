package annina.sparkstrength.client.screen.tablet;

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
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static annina.sparkstrength.client.screen.tablet.TabletSectionPainter.KEY;
import static annina.sparkstrength.client.screen.tablet.TabletSectionPainter.PILL_PAD;

/**
 * Tablet UI. Every view is driven by the server-redacted snapshot; the client never infers channels from roles.
 * 平板界面。所有视图都由服务端裁剪后的快照驱动；客户端从不根据身份推断频道。
 *
 * <p>The screen paints in its own logical canvas ({@link TabletLayout#effectiveScale}): render() scales the matrix by
 * {@code logicalScale} and every mouse event is divided by it before any hit test, so widgets, layout and painting all
 * share logical units. Clipping goes through {@link TabletCanvas#pushClip} (physical pixels), never
 * DrawContext.enableScissor, which ignores the matrix.
 * 界面在独立的逻辑画布中绘制（见 effectiveScale）：render() 以 logicalScale 缩放矩阵，所有鼠标事件在命中测试前都先除以它，
 * 因此控件、布局与绘制都使用逻辑单位。裁剪只通过 TabletCanvas.pushClip（物理像素），从不使用会忽略矩阵的
 * DrawContext.enableScissor。</p>
 *
 * <p>Each section is a {@link TabletPage} created once with the screen; the screen keeps the frame (device, status bar,
 * rail, header title), the {@link TabletChannelSwitcher} and input routing, and asks the open page for its widgets,
 * scrolling, header chip and body.
 * 每个分区是随界面创建一次的 TabletPage；界面自己负责外框（设备、状态栏、侧栏、页眉标题）、频道切换浮层与输入分发，
 * 并向当前页面获取其控件、滚动、页眉胶囊与主体。</p>
 *
 * <p>Feature sections (the Attendant door log, the Bomber drone link) ride along with any channel. A holder whose only
 * content is feature sections ("feature-only": no channel and none to switch to, e.g. a plain Attendant or a SparkTraits
 * Conscience Bomber) gets that feature's chrome instead of "No Signal": a non-clickable status chip naming it, its badge
 * glyph and its accent ({@link TabletTheme#accentFor(TabletFeature)}).
 * 功能分区（乘务员房门记录、炸弹客无人机链路）可与任意频道并存。只有功能分区的持有者（“仅功能”：无频道且无可切换频道，
 * 例如普通乘务员或 SparkTraits 善良炸弹客）显示该功能的外观，而非“无信号”：写着功能名的不可点击状态胶囊、
 * 功能徽标图形与功能强调色。</p>
 *
 * <p>The drone section only asks to connect ({@link DronePilotStartC2SPacket}); the server re-validates ownership,
 * state and the pilot's body, and the pilot client owns everything after that (camera, input, closing this screen).
 * 无人机分区只发出连接请求；服务端重新校验归属、状态与驾驶者本体，此后的一切（视角、输入、关闭本界面）由驾驶客户端负责。</p>
 */
public final class TabletScreen extends Screen {
    private static final DateTimeFormatter CLOCK_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private static final long SIGNAL_SWEEP_MS = 220L;

    // Pills (status chip, header chip, switcher chips). 胶囊（状态栏频道、页眉信息、切换浮层标签）。
    static final int PILL_H = 16;
    // Accent glow over the screen, relative to the content origin; mirrored by screenBackdropAt.
    // 屏幕上的强调色光晕（相对内容区原点）；screenBackdropAt 按相同参数复现。
    private static final int GLOW_DX = 60;
    private static final int GLOW_DY = 30;
    private static final float GLOW_RADIUS = 260f;
    private static final int GLOW_ALPHA = 0x22;
    private static final int GLOW_RINGS = 5;
    static final int PILL_GLYPH = 8;
    static final int PILL_GLYPH_GAP = 4;
    // Status bar channel chip: pad, dot, gap … gap, caret, pad. 状态栏频道胶囊的内边距、圆点、间隔与下拉箭头。
    private static final int CHIP_DOT = 6;
    private static final int CHIP_GAP = 5;
    private static final int CHIP_CARET = 7;
    private static final int CHIP_CHROME = PILL_PAD + CHIP_DOT + CHIP_GAP + CHIP_GAP + CHIP_CARET + PILL_PAD;
    // While connecting the chip is not clickable and has no caret. 连接中胶囊不可点击，也不显示下拉箭头。
    private static final int CHIP_CHROME_STATIC = PILL_PAD + CHIP_DOT + CHIP_GAP + PILL_PAD;
    private static final int CLOCK_X = 14;
    private static final int SIGNAL_SIZE = 12;
    private static final int SIGNAL_DIM = 0x33FFFFFF;

    private final TabletUiSession session = new TabletUiSession(TabletClientState.chatDraft());
    // One page per section, created once; init() only rebuilds their widgets. 每个分区一个页面，只创建一次；init() 只重建其控件。
    private final Map<Section, TabletPage> pages = new EnumMap<>(Section.class);
    private final TabletChatPage chat;
    private final TabletChannelSwitcher switcher;

    // Logical canvas (recomputed in init(), which vanilla also runs on resize). 逻辑画布（在 init() 中重算，原版调整窗口时也会调用）。
    private int pixelScale = 1;
    private float logicalScale = 1f;
    private int canvasWidth;
    private int canvasHeight;
    private int framebufferHeight;
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

    // Stable identity of each keyed widget in the current build, so a rebuild can restore keyboard focus.
    // 当前构建中各控件的稳定标识，供重建时恢复键盘焦点。
    private final Map<Element, String> focusKeys = new IdentityHashMap<>();

    public TabletScreen() {
        super(Text.translatable(KEY + "title"));
        TabletSnapshot snapshot = TabletClientState.snapshot();
        lastChannelWire = snapshot.channelWire();
        lastCanSend = snapshot.canSend();
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
        updateScale();
        TabletSnapshot snapshot = TabletClientState.snapshot();
        EnumSet<TabletFeature> features = snapshot.features();
        visibleSections = TabletUiSession.visibleSections(snapshot.channel(), features);
        referenceTabs = TabletUiSession.referenceSectionCount(snapshot.channel(), snapshot.allowedChannels(), features);
        session.ensureVisible(visibleSections);
        layout = TabletLayout.forViewport(canvasWidth, canvasHeight, visibleSections.size(), referenceTabs);
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
     * Logical canvas = framebuffer / e; the GUI matrix is scaled by e / guiScale so one logical unit is e physical
     * pixels. Mouse events arrive in GUI units and are divided by the same factor.
     * 逻辑画布 = 帧缓冲 / e；GUI 矩阵按 e / guiScale 缩放，使 1 逻辑单位 = e 物理像素。鼠标事件以 GUI 单位到达，除以同一系数。
     */
    private void updateScale() {
        Window window = client.getWindow();
        int framebufferWidth = Math.max(1, window.getFramebufferWidth());
        framebufferHeight = Math.max(1, window.getFramebufferHeight());
        int guiScale = Math.max(1, (int) window.getScaleFactor());
        pixelScale = TabletLayout.effectiveScale(framebufferWidth, framebufferHeight, guiScale);
        logicalScale = pixelScale / (float) guiScale;
        canvasWidth = framebufferWidth / pixelScale;
        canvasHeight = framebufferHeight / pixelScale;
    }

    private double toLogical(double guiCoordinate) {
        return guiCoordinate / logicalScale;
    }

    /**
     * Widgets live in logical canvas units, so the screen's navigation rect must be logical too (arrow navigation
     * with nothing focused starts from its border).
     * 控件位于逻辑画布坐标，屏幕导航矩形也须使用逻辑坐标（无焦点时方向键导航从其边缘开始）。
     */
    @Override
    public ScreenRect getNavigationFocus() {
        return new ScreenRect(0, 0, Math.max(1, canvasWidth), Math.max(1, canvasHeight));
    }

    private TabletPage page() {
        return pages.get(session.section());
    }

    // ============================================================================================ widgets

    private void initCloseButton() {
        Rect close = layout.closeButton();
        keyed("close", addDrawableChild(new TabletPressable(close.x(), close.y(), close.width(), close.height(),
                Text.translatable(KEY + "close"), this::close, (context, button, hovered, delta) -> {
                    float cx = button.getX() + button.getWidth() / 2f;
                    float cy = button.getY() + button.getHeight() / 2f;
                    float radius = button.getWidth() / 2f;
                    frameCanvas.circle(cx, cy, radius, hovered ? TabletTheme.withAlpha(TabletTheme.DANGER, 0x59) : 0x33FFFFFF);
                    TabletIcons.close(frameCanvas, cx - 3.5f, cy - 3.5f, 7f, hovered ? TabletTheme.TEXT : TabletTheme.TEXT_2);
                    if (button.keyboardFocused()) {
                        frameCanvas.ring(cx, cy, radius + 2f, 1f, frameAccent.pale());
                    }
                })));
    }

    private void initTabs() {
        int count = Math.min(visibleSections.size(), layout.tabs().size());
        for (int index = 0; index < count; index++) {
            Section section = visibleSections.get(index);
            Rect rect = layout.tabs().get(index);
            TabletPressable tab = new TabletPressable(rect.x(), rect.y(), rect.width(), rect.height(),
                    Text.translatable(section.translationKey()), () -> selectSection(section),
                    (context, button, hovered, delta) -> paintTab(button, section, hovered));
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
        double x = toLogical(mouseX);
        double y = toLogical(mouseY);
        TabletSnapshot snapshot = TabletClientState.snapshot();
        if (switcher.isOpen()) {
            return switcher.mouseClicked(snapshot, x, y, button);
        }
        if (layout == null) {
            return super.mouseClicked(x, y, button);
        }
        ChannelChip chip = channelChip(snapshot);
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
        return super.mouseReleased(toLogical(mouseX), toLogical(mouseY), button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (switcher.isOpen()) {
            return true;
        }
        return super.mouseDragged(toLogical(mouseX), toLogical(mouseY), button, toLogical(deltaX), toLogical(deltaY));
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(toLogical(mouseX), toLogical(mouseY));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (switcher.isOpen()) {
            return true;
        }
        double x = toLogical(mouseX);
        double y = toLogical(mouseY);
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

    // ============================================================================================ geometry

    /**
     * Status-bar channel chip; the single source for both painting and hit testing. Feature-only holders get a
     * static chip naming their feature (door monitor, drones) instead of "No Signal" and a switcher of locked rows.
     * 状态栏频道胶囊；绘制与命中测试共用。仅功能持有者显示不可点击、写着功能名（房门监控、无人机）的胶囊，
     * 而不是“无信号”与全锁定的切换浮层。
     */
    ChannelChip channelChip(TabletSnapshot snapshot) {
        Rect status = layout.statusBar();
        boolean live = snapshot.localHasTablet();
        Section feature = live ? featureOnlySection(snapshot) : null;
        // Clickable exactly when mouseClicked opens the switcher from it. 与 mouseClicked 打开切换浮层的条件一致。
        boolean clickable = live && !featureOnly(snapshot);
        TabletChannel channel = live ? snapshot.channel() : null;
        String name;
        String shortName = null;
        if (!live) {
            name = Text.translatable(KEY + "no_signal.connecting").getString();
        } else if (feature != null) {
            name = Text.translatable(feature.translationKey()).getString();
            shortName = Text.translatable(feature.shortTranslationKey()).getString();
        } else if (channel == null) {
            name = Text.translatable(KEY + "channel.none").getString();
        } else {
            name = Text.translatable(channel.translationKey()).getString();
            shortName = Text.translatable(channel.shortTranslationKey()).getString();
        }
        int chrome = clickable ? CHIP_CHROME : CHIP_CHROME_STATIC;
        int minX = layout.screen().x() + CLOCK_X + textRenderer.getWidth("00:00") + 8;
        int maxX = signalBox().x() - 8;
        int available = Math.max(0, maxX - minX);

        if (shortName != null && chrome + TabletSectionPainter.visibleWidth(textRenderer, name) > available) {
            name = shortName;
        }
        name = TabletCanvas.trim(textRenderer, name, Math.max(0, available - chrome));
        int width = Math.min(available, chrome + TabletSectionPainter.visibleWidth(textRenderer, name));
        int x = Math.max(minX, Math.min(maxX - width, status.centerX() - width / 2));
        int height = Math.min(PILL_H, status.height());
        return new ChannelChip(new Rect(x, status.y() + (status.height() - height) / 2, width, height), name, clickable,
                channel != null || feature != null);
    }

    private Rect signalBox() {
        Rect close = layout.closeButton();
        Rect status = layout.statusBar();
        return new Rect(close.x() - 8 - SIGNAL_SIZE, status.centerY() - SIGNAL_SIZE / 2, SIGNAL_SIZE, SIGNAL_SIZE);
    }

    /** 2 px scroll track in the right gutter of a list area. 列表右侧留白中的 2 像素滚动条轨道。 */
    Rect scrollTrack(Rect area) {
        int offset = Math.max(2, layout.mode().padX() / 3);
        return new Rect(area.right() + offset, area.y(), 2, area.height());
    }

    private Rect tabIconBox(TabletPressable tab) {
        if (layout.mode() == TabletLayout.Mode.REGULAR) {
            return new Rect(tab.getX() + tab.getWidth() / 2 - 7, tab.getY() + 7, 14, 14);
        }
        return new Rect(tab.getX() + tab.getWidth() / 2 - 8, tab.getY() + (tab.getHeight() - 16) / 2, 16, 16);
    }

    // ============================================================================================ render

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (layout == null) {
            return;
        }
        TabletSnapshot snapshot = TabletClientState.snapshot();
        long now = Util.getMeasuringTimeMs();
        frameCanvas = new TabletCanvas(context, pixelScale, framebufferHeight);
        frameAccent = frameAccentFor(snapshot);

        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.scale(logicalScale, logicalScale, 1f);
        int logicalMouseX = (int) Math.floor(toLogical(mouseX));
        int logicalMouseY = (int) Math.floor(toLogical(mouseY));
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
        paintFrame();
        paintStatusBar(snapshot, hoverX, hoverY, now);
        boolean hasSections = !visibleSections.isEmpty();
        if (hasSections) {
            paintRail(snapshot, hoverX, hoverY);
            paintHeader(snapshot, now);
            page().paintBody(snapshot, hoverX, hoverY, now);
        } else {
            paintNoSignal(snapshot, now);
        }

        super.render(context, hoverX, hoverY, delta);

        if (hasSections) {
            paintRailBadges(snapshot, now);
            paintTabTooltip();
        }
        if (switcher.isOpen()) {
            switcher.paint(snapshot, logicalMouseX, logicalMouseY, now);
        }
    }

    // ------------------------------------------------------------------------------------------------ frame

    private void paintFrame() {
        TabletCanvas c = frameCanvas;
        TabletLayout.Mode mode = layout.mode();
        // Cover the whole GUI area: canvasWidth/Height are floored, so fb % e leftover pixels would stay undimmed.
        // 覆盖整个 GUI 区域：画布尺寸向下取整，否则 fb % e 的剩余像素不会被遮暗。
        c.rect(0, 0, (float) Math.ceil(width / logicalScale), (float) Math.ceil(height / logicalScale), TabletTheme.BACKDROP);

        Rect device = layout.device();
        float deviceRadius = mode.deviceRadius();
        c.shadow(device.x(), device.y(), device.width(), device.height(), deviceRadius, 24f, 0x99000000);
        c.roundRect(device.x(), device.y(), device.width(), device.height(), deviceRadius, TabletTheme.DEVICE);
        c.roundRectOutline(device.x(), device.y(), device.width(), device.height(), deviceRadius, 1f,
                TabletTheme.withAlpha(TabletTheme.DEVICE_RIM_TOP, 0x99));
        float lensX = device.x() + mode.bezel() / 2f;
        float lensY = device.y() + device.height() / 2f;
        c.circle(lensX, lensY, 2.5f, TabletTheme.CAMERA);
        c.circle(lensX - 0.8f, lensY - 0.8f, 0.8f, TabletTheme.CAMERA_HIGHLIGHT);

        Rect screen = layout.screen();
        c.roundRectGradientV(screen.x(), screen.y(), screen.width(), screen.height(), mode.screenRadius(),
                TabletTheme.SCREEN_TOP, TabletTheme.SCREEN_BOTTOM);
        Rect content = layout.content();
        c.pushClip(screen.x(), screen.y(), screen.width(), screen.height());
        c.glow(content.x() + GLOW_DX, content.y() + GLOW_DY, GLOW_RADIUS,
                TabletTheme.withAlpha(frameAccent.base(), GLOW_ALPHA));
        c.popClip();
    }

    /** Screen gradient colour at a canvas y, without the accent glow. 屏幕渐变在某画布 y 处的颜色（不含强调色光晕）。 */
    private int screenColorAt(int y) {
        if (layout == null) {
            return TabletTheme.mix(TabletTheme.SCREEN_TOP, TabletTheme.SCREEN_BOTTOM, 0.5f);
        }
        Rect screen = layout.screen();
        float t = screen.height() <= 0 ? 0f : (y - screen.y()) / (float) screen.height();
        return TabletTheme.mix(TabletTheme.SCREEN_TOP, TabletTheme.SCREEN_BOTTOM, t);
    }

    /**
     * Opaque colour actually painted behind a canvas point: the screen gradient plus the accent glow, reproducing
     * TabletCanvas.glow's per-ring vertex-alpha interpolation. Opaque masks (avatar corners, badge rings) must match
     * it, or they show as dark notches inside the glow.
     * 画布某点背后实际绘制的不透明颜色：屏幕渐变叠加强调色光晕，复现 TabletCanvas.glow 按环插值的顶点透明度。
     * 不透明遮罩（头像圆角、徽标描边）必须与之一致，否则会在光晕区域内显出暗色缺口。
     */
    int screenBackdropAt(int x, int y) {
        int base = screenColorAt(y);
        if (layout == null || frameAccent == null) {
            return base;
        }
        Rect content = layout.content();
        float f = (float) Math.hypot(x - (content.x() + GLOW_DX), y - (content.y() + GLOW_DY)) / GLOW_RADIUS;
        if (!(f < 1f)) {
            return base;
        }
        float inner = 1f / GLOW_RINGS;
        float g;
        if (f < inner) {
            g = lerp(1f, glowFalloff(inner), f / inner);
        } else {
            int k = Math.min(GLOW_RINGS - 1, (int) (f * GLOW_RINGS));
            float f0 = (float) k / GLOW_RINGS;
            float f1 = (float) (k + 1) / GLOW_RINGS;
            g = lerp(glowFalloff(f0), glowFalloff(f1), f * GLOW_RINGS - k);
        }
        return TabletTheme.mix(base, TabletTheme.withAlpha(frameAccent.base(), 0xFF), (GLOW_ALPHA / 255f) * g);
    }

    private static float glowFalloff(float f) {
        float inverse = 1f - f;
        return inverse * inverse;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    // ------------------------------------------------------------------------------------------------ status bar

    private void paintStatusBar(TabletSnapshot snapshot, int hoverX, int hoverY, long now) {
        TabletCanvas c = frameCanvas;
        Rect status = layout.statusBar();
        int textY = status.centerY() - 4;
        c.text(textRenderer, LocalTime.now().format(CLOCK_FORMAT), layout.screen().x() + CLOCK_X, textY, TabletTheme.TEXT_2);
        paintChannelChip(snapshot, hoverX, hoverY);

        Rect signal = signalBox();
        boolean connecting = !snapshot.localHasTablet();
        // Features (door monitor, drone link) work without a network, so a feature-only tablet is not shown as
        // signal-less. 功能（房门监控、无人机链路）无需网络，仅功能的平板不显示为无信号。
        int lit = connecting ? sweepBars(now) : snapshot.channel() == null && !featureOnly(snapshot) ? 0 : 4;
        TabletIcons.signal(c, signal.x(), signal.y(), signal.width(), lit,
                connecting ? TabletTheme.NONE.pale() : TabletTheme.TEXT_2, SIGNAL_DIM);
    }

    private void paintChannelChip(TabletSnapshot snapshot, int hoverX, int hoverY) {
        TabletCanvas c = frameCanvas;
        ChannelChip chip = channelChip(snapshot);
        Rect rect = chip.rect();
        if (rect.width() <= 0) {
            return;
        }
        boolean lit = chip.lit();
        boolean hot = chip.clickable() && (switcher.isOpen() || rect.contains(hoverX, hoverY));
        float radius = rect.height() / 2f;
        c.roundRect(rect.x(), rect.y(), rect.width(), rect.height(), radius,
                hot ? TabletTheme.SURFACE_HOVER : TabletTheme.withAlpha(TabletTheme.SURFACE, 0xB3));
        c.roundRectOutline(rect.x(), rect.y(), rect.width(), rect.height(), radius, 1f,
                hot ? TabletTheme.withAlpha(frameAccent.base(), 0x99) : TabletTheme.HAIRLINE);

        float dotX = rect.x() + PILL_PAD + CHIP_DOT / 2f;
        float centerY = rect.y() + rect.height() / 2f;
        if (lit) {
            c.glow(dotX, centerY, 7f, TabletTheme.withAlpha(frameAccent.base(), 0x66));
        }
        c.circle(dotX, centerY, CHIP_DOT / 2f, lit ? frameAccent.base() : TabletTheme.TEXT_3);
        int textX = rect.x() + PILL_PAD + CHIP_DOT + CHIP_GAP;
        int textY = rect.y() + (rect.height() - 8) / 2;
        c.text(textRenderer, chip.name(), textX, textY, lit ? TabletTheme.TEXT : TabletTheme.TEXT_2);
        if (chip.clickable()) {
            TabletIcons.caret(c, rect.right() - PILL_PAD - CHIP_CARET, centerY - CHIP_CARET / 2f, CHIP_CARET,
                    switcher.isOpen(), hot ? TabletTheme.TEXT : TabletTheme.TEXT_2);
        }
    }

    private static int sweepBars(long now) {
        return (int) ((now / SIGNAL_SWEEP_MS) % 5L);
    }

    // ------------------------------------------------------------------------------------------------ rail

    private void paintRail(TabletSnapshot snapshot, int hoverX, int hoverY) {
        TabletCanvas c = frameCanvas;
        Rect rail = layout.rail();
        if (rail.width() <= 0) {
            return;
        }
        // Only the rail's bottom-left corner is a screen corner: over-extend up/right and clip to the rail.
        // 侧栏只有左下角是屏幕圆角：向上/向右多画一段再裁剪到侧栏范围。
        float radius = layout.mode().screenRadius();
        c.pushClip(rail.x(), rail.y(), rail.width(), rail.height());
        c.roundRect(rail.x(), rail.y() - radius, rail.width() + radius, rail.height() + radius, radius, TabletTheme.RAIL_TINT);
        c.popClip();
        c.rect(rail.right() - 1, rail.y(), 1, rail.height(), TabletTheme.HAIRLINE);
        paintChannelBadge(snapshot, hoverX, hoverY);
    }

    private void paintChannelBadge(TabletSnapshot snapshot, int hoverX, int hoverY) {
        TabletCanvas c = frameCanvas;
        Rect badge = layout.channelBadge();
        if (badge.width() <= 0 || badge.height() <= 0) {
            return;
        }
        TabletChannel channel = snapshot.channel();
        int textY = badge.y() + (badge.height() - 8) / 2;
        Section feature = featureOnlySection(snapshot);
        if (feature != null) {
            // Not a channel: the feature's glyph instead of a monogram, and nothing to click. 不是频道：显示功能图形而非首字，且不可点击。
            c.roundRectGradientV(badge.x(), badge.y(), badge.width(), badge.height(), 9f,
                    frameAccent.base(), frameAccent.deep());
            float glyph = 16f;
            float glyphX = badge.centerX() - glyph / 2f;
            float glyphY = badge.y() + (badge.height() - glyph) / 2f;
            if (feature == Section.DRONE) {
                TabletIcons.drone(c, glyphX, glyphY, glyph, TabletTheme.WHITE);
            } else if (feature == Section.SABOTAGE) {
                TabletIcons.bolt(c, glyphX, glyphY, glyph, TabletTheme.WHITE);
            } else {
                TabletIcons.eye(c, glyphX, glyphY, glyph, TabletTheme.WHITE);
            }
            return;
        }
        if (channel == null) {
            c.roundRect(badge.x(), badge.y(), badge.width(), badge.height(), 9f, TabletTheme.SURFACE);
            c.textCentered(textRenderer, "–", badge.centerX(), textY, TabletTheme.TEXT_3);
        } else {
            c.roundRectGradientV(badge.x(), badge.y(), badge.width(), badge.height(), 9f,
                    frameAccent.base(), frameAccent.deep());
            c.textCentered(textRenderer, monogram(channel), badge.centerX(), textY, TabletTheme.WHITE);
        }
        if (switcher.isOpen() || badge.contains(hoverX, hoverY)) {
            c.roundRectOutline(badge.x() - 2, badge.y() - 2, badge.width() + 4, badge.height() + 4, 11f, 1f,
                    TabletTheme.withAlpha(frameAccent.pale(), 0x80));
        }
    }

    private void paintTab(TabletPressable tab, Section section, boolean hovered) {
        TabletCanvas c = frameCanvas;
        boolean selected = tab.selectedState();
        float x = tab.getX();
        float y = tab.getY();
        float w = tab.getWidth();
        float h = tab.getHeight();
        if (selected) {
            c.roundRect(x, y, w, h, 8f, TabletTheme.withAlpha(frameAccent.base(), 0x2E));
            c.roundRect(layout.rail().x(), y + h / 2f - 8f, 3f, 16f, 1.5f, frameAccent.base());
        } else if (hovered) {
            c.roundRect(x, y, w, h, 8f, 0x0FFFFFFF);
        }
        int color = selected ? frameAccent.pale() : hovered ? TabletTheme.TEXT_2 : TabletTheme.TEXT_3;
        Rect icon = tabIconBox(tab);
        paintSectionIcon(section, icon, color);
        if (layout.mode() == TabletLayout.Mode.REGULAR) {
            String label = TabletCanvas.trim(textRenderer, Text.translatable(section.shortTranslationKey()).getString(),
                    Math.max(0, tab.getWidth() - 4));
            c.textCentered(textRenderer, label, tab.getX() + tab.getWidth() / 2, tab.getY() + 25, color);
        }
        TabletSectionPainter.focusRing(c, tab, 8f, frameAccent);
    }

    private void paintSectionIcon(Section section, Rect box, int color) {
        TabletCanvas c = frameCanvas;
        switch (section) {
            case CONNECTIONS -> TabletIcons.members(c, box.x(), box.y(), box.width(), color);
            case CHAT -> TabletIcons.chat(c, box.x(), box.y(), box.width(), color);
            case MEETING -> TabletIcons.megaphone(c, box.x(), box.y(), box.width(), color);
            case SUSPECTS -> TabletIcons.target(c, box.x(), box.y(), box.width(), color);
            case DOOR_LOG -> TabletIcons.door(c, box.x(), box.y(), box.width(), color);
            case DRONE -> TabletIcons.drone(c, box.x(), box.y(), box.width(), color);
            case SABOTAGE -> TabletIcons.bolt(c, box.x(), box.y(), box.width(), color);
        }
    }

    /** Badges sit on the top-right of each tab icon, drawn after widgets. 徽标位于标签图标右上角，在控件之后绘制。 */
    private void paintRailBadges(TabletSnapshot snapshot, long now) {
        for (int index = 0; index < tabButtons.size(); index++) {
            // The open tab needs no attention badge. 当前所在标签不显示提醒徽标。
            if (tabButtons.get(index).selectedState()) {
                continue;
            }
            pages.get(tabSections.get(index)).paintBadge(snapshot, tabIconBox(tabButtons.get(index)), now);
        }
    }

    /** Solid badge dot on a tab icon's top-right, ringed in the darkened backdrop. 标签图标右上角的实心徽标圆点，外圈为加深的背景色。 */
    void paintBadgeDot(Rect icon, int color) {
        float cx = icon.right();
        float cy = icon.y() + 1;
        frameCanvas.circle(cx, cy, 4f, TabletTheme.mix(screenBackdropAt((int) cx, (int) cy), 0xFF000000, 0.25f));
        frameCanvas.circle(cx, cy, 3f, color);
    }

    /** Breathing badge dot on a tab icon's top-right. 标签图标右上角的呼吸徽标圆点。 */
    void paintBadgePulse(Rect icon, int color, long now) {
        paintPulseDot(icon.right(), icon.y() + 1, 3f, color, now);
    }

    /** NARROW tabs are icon-only: show the short label in a pill to the right of the hovered tab. 窄模式悬停提示。 */
    private void paintTabTooltip() {
        if (layout.mode() != TabletLayout.Mode.NARROW || switcher.isOpen()) {
            return;
        }
        TabletCanvas c = frameCanvas;
        for (int index = 0; index < tabButtons.size(); index++) {
            TabletPressable tab = tabButtons.get(index);
            if (!tab.isHovered()) {
                continue;
            }
            String label = Text.translatable(tabSections.get(index).shortTranslationKey()).getString();
            int width = TabletSectionPainter.visibleWidth(textRenderer, label) + 2 * PILL_PAD;
            int x = layout.rail().right() + 4;
            int y = tab.getY() + tab.getHeight() / 2 - PILL_H / 2;
            c.shadow(x, y, width, PILL_H, PILL_H / 2f, 6f, 0x66000000);
            c.roundRect(x, y, width, PILL_H, PILL_H / 2f, TabletTheme.SURFACE_RAISED);
            c.roundRectOutline(x, y, width, PILL_H, PILL_H / 2f, 1f, TabletTheme.HAIRLINE);
            c.text(textRenderer, label, x + PILL_PAD, y + 4, TabletTheme.TEXT);
            return;
        }
    }

    // ------------------------------------------------------------------------------------------------ header

    private void paintHeader(TabletSnapshot snapshot, long now) {
        Rect header = layout.header();
        int padX = layout.mode().padX();
        int left = header.x() + padX;
        int right = header.right() - padX;
        int centerY = header.centerY();
        int chipX = page().paintHeaderChip(snapshot, right, centerY, Math.max(0, (right - left) / 2), now);
        String title = TabletCanvas.trim(textRenderer, Text.translatable(session.section().translationKey()).getString(),
                Math.max(0, chipX - 10 - left));
        frameCanvas.text(textRenderer, title, left, centerY - 4, TabletTheme.TEXT);
    }

    /**
     * Right-aligned pill (h=16) with an optional leading glyph; the text is trimmed to maxWidth. Returns the left x.
     * 右对齐胶囊（高 16），可带前置图形；文字按 maxWidth 截断。返回左边缘 x。
     */
    int paintPill(int right, int centerY, int maxWidth, String text, int textColor, int fill, int outline,
                  @Nullable Glyph glyph, int glyphColor) {
        TabletCanvas c = frameCanvas;
        int glyphWidth = glyph == null ? 0 : PILL_GLYPH + PILL_GLYPH_GAP;
        String visible = TabletCanvas.trim(textRenderer, text, Math.max(0, maxWidth - 2 * PILL_PAD - glyphWidth));
        int width = 2 * PILL_PAD + glyphWidth + TabletSectionPainter.visibleWidth(textRenderer, visible);
        int x = right - width;
        int y = centerY - PILL_H / 2;
        float radius = PILL_H / 2f;
        c.roundRect(x, y, width, PILL_H, radius, fill);
        if (outline != 0) {
            c.roundRectOutline(x, y, width, PILL_H, radius, 1f, outline);
        }
        int textX = x + PILL_PAD;
        if (glyph != null) {
            glyph.paint(c, textX, centerY - PILL_GLYPH / 2f, PILL_GLYPH, glyphColor);
            textX += glyphWidth;
        }
        c.text(textRenderer, visible, textX, centerY - 4, textColor);
        return x;
    }

    void paintPulseDot(float cx, float cy, float radius, int color, long now) {
        TabletSectionPainter.pulseDot(frameCanvas, cx, cy, radius, color, now);
    }

    // ------------------------------------------------------------------------------------------------ no signal

    /**
     * Body for holders without a channel (or before the first snapshot lands): a centred column, no rail or header.
     * 无频道持有者（或首个快照到达前）的主体：居中内容，无侧栏与页眉。
     */
    private void paintNoSignal(TabletSnapshot snapshot, long now) {
        TabletCanvas c = frameCanvas;
        Rect content = layout.content();
        boolean connecting = !snapshot.localHasTablet();
        String title = Text.translatable(KEY + (connecting ? "no_signal.connecting" : "channel.none")).getString();
        List<OrderedText> lines = connecting
                ? List.of()
                : textRenderer.wrapLines(Text.translatable(KEY + "no_signal.body"), Math.max(40, Math.min(260, content.width() - 32)));
        if (lines.size() > 3) {
            lines = lines.subList(0, 3);
        }
        int blockHeight = 64 + 12 + 9 + (lines.isEmpty() ? 0 : 6 + lines.size() * 10 - 1);
        int top = content.y() + Math.max(0, (content.height() - blockHeight) / 2);
        int centerX = content.centerX();

        c.circle(centerX, top + 32, 32f, TabletTheme.SURFACE);
        c.ring(centerX, top + 32, 32f, 1f, TabletTheme.HAIRLINE);
        float glyphX = centerX - 14f;
        float glyphY = top + 18f;
        TabletIcons.signal(c, glyphX, glyphY, 28f, connecting ? sweepBars(now) : 0, TabletTheme.NONE.pale(), SIGNAL_DIM);
        if (!connecting) {
            TabletIcons.close(c, glyphX + 20f, glyphY - 2f, 10f, TabletTheme.DANGER);
        }

        int y = top + 64 + 12;
        c.textCentered(textRenderer, TabletCanvas.trim(textRenderer, title, content.width() - 16), centerX, y, TabletTheme.TEXT);
        y += 9 + 6;
        for (OrderedText line : lines) {
            c.text(textRenderer, line, centerX - textRenderer.getWidth(line) / 2, y, TabletTheme.TEXT_2);
            y += 10;
        }
    }

    // ============================================================================================ helpers

    Accent frameAccentFor(TabletSnapshot snapshot) {
        Section feature = featureOnlySection(snapshot);
        TabletFeature required = feature == null ? null : feature.requiredFeature();
        return required != null ? TabletTheme.accentFor(required) : TabletTheme.accentFor(snapshot.channel());
    }

    /**
     * Feature sections are the holder's only content: no channel now and none to switch to (a plain Attendant, a
     * SparkTraits Conscience Bomber).
     * 功能分区是持有者唯一的内容：当前无频道且没有可切换的频道（普通乘务员、SparkTraits 善良炸弹客）。
     */
    private static boolean featureOnly(TabletSnapshot snapshot) {
        return snapshot.localHasTablet()
                && snapshot.channel() == null
                && snapshot.allowedChannels().isEmpty()
                && !snapshot.features().isEmpty();
    }

    /**
     * For a feature-only holder, the feature section that names the tablet (chip, badge, accent): the open section when
     * it is a granted feature section, else the first granted one. Null when the holder is not feature-only.
     * 仅功能持有者用于命名平板（胶囊、徽标、强调色）的功能分区：当前打开的分区若为已授予的功能分区则用它，否则用第一个已授予的；
     * 非仅功能持有者为 null。
     */
    private @Nullable Section featureOnlySection(TabletSnapshot snapshot) {
        if (!featureOnly(snapshot)) {
            return null;
        }
        TabletFeature open = session.section().requiredFeature();
        if (open != null && snapshot.hasFeature(open)) {
            return session.section();
        }
        for (Section section : Section.values()) {
            TabletFeature required = section.requiredFeature();
            if (required != null && snapshot.hasFeature(required)) {
                return section;
            }
        }
        return null;
    }

    @Nullable UUID localUuid() {
        return client != null && client.player != null ? client.player.getUuid() : null;
    }

    /** Upper-cased first code point of the channel's short name. 频道简称的首个字符（大写）。 */
    static String monogram(TabletChannel channel) {
        String name = Text.translatable(channel.shortTranslationKey()).getString();
        if (name.isEmpty()) {
            return "?";
        }
        return new String(Character.toChars(Character.toUpperCase(name.codePointAt(0))));
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

    // ============================================================================================ types

    @FunctionalInterface
    interface Glyph {
        void paint(TabletCanvas canvas, float x, float y, float size, int color);
    }

    /** {@code lit}: a live channel or a feature-only tablet (accent dot with glow, bright name). 已接入频道或仅功能平板（发光圆点、亮色名字）。 */
    record ChannelChip(Rect rect, String name, boolean clickable, boolean lit) {
    }

    /**
     * Card grid inside a list area; a final row counts as visible when its card (not its trailing gap) fits.
     * 列表区域内的卡片网格；最后一行只要卡片本身放得下（不含行间距）即计为可见。
     */
    record Grid(Rect area, int columns, int cellWidth, int rowStep, int visibleRows) {
        static Grid of(Rect area, int columns, int cardHeight, int rowStep) {
            int safeColumns = Math.max(1, columns);
            int cellWidth = Math.max(0, (area.width() - (safeColumns - 1) * TabletSectionPainter.GRID_GAP) / safeColumns);
            int visibleRows = TabletLayout.rowsFitting(area.height() + rowStep - cardHeight, rowStep);
            return new Grid(area, safeColumns, cellWidth, rowStep, visibleRows);
        }

        int totalRows(int items) {
            return (items + columns - 1) / columns;
        }

        int cellX(int column) {
            return area.x() + column * (cellWidth + TabletSectionPainter.GRID_GAP);
        }

        int rowY(int visibleRow) {
            return area.y() + visibleRow * rowStep;
        }
    }
}
