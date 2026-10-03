package annina.sparkstrength.client.screen.tablet;

import annina.sparkstrength.client.screen.tablet.TabletSectionPainter.ButtonIcon;
import annina.sparkstrength.client.screen.tablet.TabletSectionPainter.ButtonStyle;
import annina.sparkstrength.client.screen.tablet.TabletSectionPainter.EmptyIcon;
import annina.sparkstrength.client.screen.tablet.TabletSectionPainter.HeroState;
import annina.sparkstrength.client.screen.tablet.TabletTheme.Accent;
import annina.sparkstrength.compat.SparkWitchCompat;
import annina.sparkstrength.network.tablet.ApproveSuspectRemovalC2SPacket;
import annina.sparkstrength.network.tablet.CallTabletMeetingC2SPacket;
import annina.sparkstrength.network.tablet.CastTabletVoteC2SPacket;
import annina.sparkstrength.network.tablet.ConfirmTabletVoteC2SPacket;
import annina.sparkstrength.network.tablet.RequestTabletSnapshotC2SPacket;
import annina.sparkstrength.network.tablet.SelectTabletChannelC2SPacket;
import annina.sparkstrength.network.tablet.SendTabletChatC2SPacket;
import annina.sparkstrength.network.tablet.TabletSnapshot;
import annina.sparkstrength.tablet.TabletChannel;
import annina.sparkstrength.tablet.TabletFeature;
import annina.sparkstrength.tablet.TabletIdentityRules;
import annina.sparkstrength.tablet.TabletLayout;
import annina.sparkstrength.tablet.TabletLayout.Rect;
import annina.sparkstrength.tablet.TabletRules;
import annina.sparkstrength.tablet.TabletUiSession;
import annina.sparkstrength.tablet.TabletUiSession.Section;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
 * <p>Feature sections (the Attendant door log) ride along with any channel. A holder whose only content is the door
 * log ("monitor-only": no channel and none to switch to) gets monitor chrome instead of "No Signal": a non-clickable
 * status chip, a monitor badge and the {@link TabletTheme#MONITOR} accent.
 * 功能分区（乘务员房门记录）可与任意频道并存。只有房门记录的持有者（“仅监控”：无频道且无可切换频道）显示监控外观，
 * 而非“无信号”：不可点击的状态胶囊、监控徽标与 MONITOR 强调色。</p>
 */
public final class TabletScreen extends Screen {
    private static final DateTimeFormatter CLOCK_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private static final int SNAPSHOT_POLL_TICKS = 20;
    private static final int PENDING_SWITCH_TICKS = 40;
    private static final int CHAT_SCROLL_STEP = 18;
    // Chat edge mask: band height and column pitch (logical px). 聊天边缘遮罩：带高与列间距（逻辑像素）。
    private static final int CHAT_MASK = 18;
    private static final int CHAT_MASK_STEP = 12;
    private static final int COUNTER_THRESHOLD = 100;
    private static final long PULSE_PERIOD_MS = 1200L;
    private static final long SIGNAL_SWEEP_MS = 220L;

    // Pills (status chip, header chip, switcher chips). 胶囊（状态栏频道、页眉信息、切换浮层标签）。
    private static final int PILL_H = 16;
    // Accent glow over the screen, relative to the content origin; mirrored by screenBackdropAt.
    // 屏幕上的强调色光晕（相对内容区原点）；screenBackdropAt 按相同参数复现。
    private static final int GLOW_DX = 60;
    private static final int GLOW_DY = 30;
    private static final float GLOW_RADIUS = 260f;
    private static final int GLOW_ALPHA = 0x22;
    private static final int GLOW_RINGS = 5;
    private static final int PILL_GLYPH = 8;
    private static final int PILL_GLYPH_GAP = 4;
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

    // Composer. 输入栏。
    private static final int SEND_SIZE = 26;
    private static final int SEND_GAP = 8;
    private static final int INPUT_INSET_X = 12;
    private static final int INPUT_H = 9;
    private static final int NEW_PILL_H = 16;
    // Room kept below the member rows for a hint callout (two text lines). 成员行下方为提示卡片保留的高度（两行文字）。
    private static final int HINT_CALLOUT_MIN_H = 42;

    // Action bar buttons (meeting footer). 会议操作栏按钮。
    private static final int ACTION_PAD = 14;
    private static final int ACTION_GAP = 8;
    private static final int ACTION_ICON = 12;
    private static final int ACTION_ICON_GAP = 5;
    private static final int ACTION_MIN_W = 74;

    // Channel switcher popover. 频道切换浮层。
    private static final int SWITCHER_WIDTH = 220;
    private static final int SWITCHER_OFFSET = 6;
    private static final int SWITCHER_ROWS_TOP = 26;
    private static final int SWITCHER_ROW_H = 32;
    private static final int SWITCHER_ROW_GAP = 4;
    private static final int SWITCHER_COMPACT_ROW_H = 24;
    private static final int SWITCHER_COMPACT_ROW_GAP = 2;
    private static final int SWITCHER_INSET = 6;
    private static final int SWITCHER_BADGE = 20;
    private static final int SWITCHER_CARET = 6;
    private static final int SWITCHER_HINT_PAD = 10;
    private static final int SWITCHER_Z = 200;
    private static final long SWITCHER_FADE_MS = 120L;
    private static final int SWITCHER_SLIDE = 6;

    private final TabletUiSession session = new TabletUiSession(TabletClientState.chatDraft());
    private final TabletChatView chatView = new TabletChatView();

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

    // Widgets rebuilt by init(); the chat input is one persistent instance so caret/selection survive refresh().
    // init() 重建的控件；聊天输入框为同一个持久实例，使光标/选区在 refresh() 后保留。
    private final List<TabletPressable> tabButtons = new ArrayList<>();
    private final List<Section> tabSections = new ArrayList<>();
    private @Nullable TabletTextInput chatInput;
    private boolean chatInputAttached;
    private @Nullable TabletPressable sendButton;
    private @Nullable TabletPressable newMessagesButton;

    private int snapshotRequestTicks;
    private boolean requestedInitialSnapshot;
    private int lastChannelWire;
    private boolean lastCanSend;
    private boolean switcherOpen;
    private long switcherOpenedAtMs;
    private int pendingChannelWire = TabletChannel.NO_CHANNEL_WIRE;
    private int pendingSwitchTicks;

    // Chat scroll anchoring. 聊天滚动锚定。
    private List<TabletSnapshot.ChatRow> lastChatRows = List.of();
    // Scratch layout of the kept prefix, used only to measure the anchor offset. 仅用于测量锚点偏移的前缀排版。
    private boolean chatLayoutPrimed;
    private boolean newMessagesPending;
    // Door-log rows the scroll position refers to; anchors the reader when newer rows arrive. 门记录滚动位置所对应的行。
    private List<TabletSnapshot.DoorLogRow> lastDoorLogRows = List.of();

    // Stable identity of each keyed widget in the current build, so a rebuild can restore keyboard focus.
    // 当前构建中各控件的稳定标识，供重建时恢复键盘焦点。
    private final Map<Element, String> focusKeys = new IdentityHashMap<>();

    public TabletScreen() {
        super(Text.translatable(KEY + "title"));
        TabletSnapshot snapshot = TabletClientState.snapshot();
        lastChannelWire = snapshot.channelWire();
        lastCanSend = snapshot.canSend();
        chatView.setBackdrop(this::screenBackdropAt);
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
        clampRowScrolls(snapshot);
        lastDoorLogRows = doorLogRows(snapshot);
        ensureChatInput();
        focusKeys.clear();
        tabButtons.clear();
        tabSections.clear();
        chatInputAttached = false;
        sendButton = null;
        newMessagesButton = null;

        if (!requestedInitialSnapshot) {
            requestedInitialSnapshot = true;
            requestSnapshot();
        }

        initCloseButton();
        if (visibleSections.isEmpty()) {
            return;
        }
        initTabs();
        switch (session.section()) {
            case CONNECTIONS, DOOR_LOG -> {
                // Painted only; no widgets. 仅绘制，无控件。
            }
            case CHAT -> initChat(snapshot);
            case MEETING -> {
                if (hasMeetingFeatures(snapshot)) {
                    initMeeting(snapshot);
                }
            }
            case SUSPECTS -> {
                if (hasMeetingFeatures(snapshot)) {
                    initSuspects(snapshot);
                }
            }
        }
    }

    public void refresh() {
        if (client == null) {
            return;
        }
        rememberChatDraft();
        String focusKey = switcherOpen ? null : focusKeys.get(getFocused());
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
     * Snapshot rebuilds (Meeting/Suspects refresh every poll) keep keyboard focus on the equivalent new widget;
     * skipped while the switcher is open or when init() already focused something (the chat input).
     * 快照重建（会议/嫌疑人分区每次轮询都会刷新）时把键盘焦点保留在对应的新控件上；
     * 切换浮层打开或 init() 已设置焦点（聊天输入框）时不恢复。
     */
    private void restoreFocus(@Nullable String focusKey) {
        if (focusKey == null || switcherOpen || getFocused() != null) {
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

        if (layout != null) {
            anchorDoorLog(snapshot);
            clampRowScrolls(snapshot);
        }
        if (switcherOpen && monitorOnly(snapshot)) {
            // Became monitor-only (channels lost mid-round): the switcher has nothing to offer. 变为仅监控时关闭切换浮层。
            switcherOpen = false;
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
                || session.section() == Section.MEETING
                || session.section() == Section.SUSPECTS) {
            refresh();
        }
    }

    /**
     * Door log is newest first: a reader scrolled down stays on the same entry when newer ones are prepended.
     * 房门记录最新在前：已向下翻阅的读者在顶部插入新条目时保持停在同一条目。
     */
    private void anchorDoorLog(TabletSnapshot snapshot) {
        List<TabletSnapshot.DoorLogRow> rows = doorLogRows(snapshot);
        int first = session.doorLogFirstRow();
        int anchor = -1;
        if (first > 0 && first < lastDoorLogRows.size()) {
            int anchorId = lastDoorLogRows.get(first).id();
            for (int index = 0; index < rows.size(); index++) {
                if (rows.get(index).id() == anchorId) {
                    anchor = index;
                    break;
                }
            }
        }
        session.applyDoorLogSnapshot(anchor, rows.size(), doorLogGrid().visibleRows());
        lastDoorLogRows = rows;
    }

    @Override
    public void tick() {
        super.tick();
        if (pendingSwitchTicks > 0 && --pendingSwitchTicks == 0) {
            pendingChannelWire = TabletChannel.NO_CHANNEL_WIRE;
        }
        snapshotRequestTicks++;
        if (snapshotRequestTicks >= SNAPSHOT_POLL_TICKS) {
            snapshotRequestTicks = 0;
            requestSnapshot();
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
        rememberChatDraft();
        session.select(section);
        if (section == Section.CHAT) {
            resetChatScroll();
        }
        refresh();
    }

    private void ensureChatInput() {
        if (chatInput != null) {
            return;
        }
        TabletTextInput input = new TabletTextInput(textRenderer, 0, 0, 1, INPUT_H,
                Text.translatable(KEY + "chat.placeholder"));
        // The limit must be set before the draft (default limit is 32). 必须先设长度上限再写入草稿（默认上限为 32）。
        input.setMaxLength(TabletRules.CHAT_MESSAGE_MAX_LENGTH);
        input.setText(session.draft());
        input.setTextColor(TabletTheme.TEXT);
        input.setChangedListener(value -> {
            session.updateDraft(value);
            TabletClientState.setChatDraft(value);
        });
        chatInput = input;
    }

    private void initChat(TabletSnapshot snapshot) {
        Composer composer = composer();
        TabletChannel channel = snapshot.channel();
        if (snapshot.canSend() && channel != null && chatInput != null) {
            Rect pill = composer.pill();
            chatInput.setDimensionsAndPosition(Math.max(1, pill.width() - 2 * INPUT_INSET_X), INPUT_H,
                    pill.x() + INPUT_INSET_X, pill.centerY() - 4);
            chatInput.setPlaceholder(Text.translatable(KEY + "chat.placeholder_channel",
                    Text.translatable(channel.translationKey())));
            chatInput.setCaretColor(frameAccentFor(snapshot).pale());
            chatInput.setSelectionColor(TabletTheme.withAlpha(frameAccentFor(snapshot).base(), 0x66));
            addDrawableChild(chatInput);
            chatInputAttached = true;
            if (!switcherOpen) {
                setInitialFocus(chatInput);
            }
        }
        // Read-only holders (dead) get a painted read-only bar instead of the input; see paintComposer.
        // 只读持有者（死亡）不挂输入框，而是绘制只读栏；见 paintComposer。

        Rect send = composer.send();
        sendButton = addDrawableChild(new TabletPressable(send.x(), send.y(), send.width(), send.height(),
                Text.translatable(KEY + "chat.send"), this::sendChat,
                (context, button, hovered, delta) -> paintSendButton(button, hovered)));
        sendButton.active = canSendChat(snapshot);

        Text newLabel = Text.translatable(KEY + "chat.new");
        Rect list = layout.list();
        int pillWidth = Math.min(list.width(), TabletSectionPainter.visibleWidth(textRenderer, newLabel.getString())
                + 2 * PILL_PAD + PILL_GLYPH + PILL_GLYPH_GAP);
        newMessagesButton = addDrawableChild(new TabletPressable(list.centerX() - pillWidth / 2,
                list.bottom() - NEW_PILL_H - 6, pillWidth, NEW_PILL_H, newLabel, this::resetChatScroll,
                (context, button, hovered, delta) -> paintNewMessagesPill(button, newLabel, hovered)));
        newMessagesButton.visible = false;
    }

    private void initMeeting(TabletSnapshot snapshot) {
        TabletSnapshot.Meeting meeting = snapshot.meeting();
        if (!meeting.active()) {
            Rect call = TabletSectionPainter.heroButtonRect(layout.bodyNoFooter());
            addButton("call", call, Text.translatable(KEY + "meeting.call"), ButtonStyle.DANGER,
                    ButtonIcon.MEGAPHONE,
                    () -> ClientPlayNetworking.send(new CallTabletMeetingC2SPacket()))
                    .active = canCallMeeting(snapshot);
            return;
        }

        boolean participant = snapshot.localMeetingParticipant();
        boolean confirmed = meeting.localConfirmed();
        Grid grid = voteGrid();
        List<TabletSnapshot.VoteTarget> targets = meeting.targets();
        int first = session.meetingFirstRow();
        for (int row = 0; row < grid.visibleRows(); row++) {
            for (int column = 0; column < grid.columns(); column++) {
                int index = (first + row) * grid.columns() + column;
                if (index >= targets.size()) {
                    break;
                }
                TabletSnapshot.VoteTarget target = targets.get(index);
                boolean selected = target.uuid().equals(meeting.localVoteTarget());
                boolean clickable = participant && target.selectable() && !confirmed;
                TabletPressable card = new TabletPressable(grid.cellX(column), grid.rowY(row), grid.cellWidth(),
                        TabletSectionPainter.VOTE_CARD_H, Text.literal(target.name()),
                        () -> ClientPlayNetworking.send(new CastTabletVoteC2SPacket(target.uuid())),
                        (context, button, hovered, delta) -> TabletSectionPainter.voteCard(frameCanvas, textRenderer,
                                button, target, selected, hovered, clickable, !participant, frameAccent));
                card.active = clickable;
                card.setSelectedState(selected);
                keyed("vote:" + target.uuid(), addDrawableChild(card));
            }
        }

        ActionBar bar = actionBar(snapshot);
        addButton("abstain", bar.abstain(), bar.abstainLabel(), bar.abstainStyle(), ButtonIcon.NONE,
                () -> ClientPlayNetworking.send(new CastTabletVoteC2SPacket(null)))
                .active = participant && !confirmed;
        addButton("confirm", bar.confirm(), bar.confirmLabel(), bar.confirmStyle(), bar.confirmIcon(),
                () -> ClientPlayNetworking.send(new ConfirmTabletVoteC2SPacket()))
                .active = participant && !confirmed;
    }

    private void initSuspects(TabletSnapshot snapshot) {
        Grid grid = suspectGrid();
        List<TabletSnapshot.SuspectRow> suspects = snapshot.suspects();
        int first = session.suspectFirstRow();
        for (int row = 0; row < grid.visibleRows() && first + row < suspects.size(); row++) {
            TabletSnapshot.SuspectRow suspect = suspects.get(first + row);
            Rect toggle = TabletSectionPainter.suspectButtonRect(textRenderer, grid.area().x(), grid.rowY(row),
                    grid.area().width());
            addButton("suspect:" + suspect.uuid(), toggle,
                    Text.translatable(KEY + (suspect.localApproved() ? "suspects.cancel" : "suspects.approve")),
                    suspect.localApproved() ? ButtonStyle.WARNING_TOGGLED : ButtonStyle.SECONDARY,
                    ButtonIcon.NONE,
                    () -> ClientPlayNetworking.send(new ApproveSuspectRemovalC2SPacket(suspect.uuid(), !suspect.localApproved())))
                    .active = snapshot.localMeetingParticipant();
        }
    }

    private TabletPressable addButton(String focusKey, Rect rect, Text label, ButtonStyle style, ButtonIcon icon,
                                      Runnable action) {
        return keyed(focusKey, addDrawableChild(new TabletPressable(rect.x(), rect.y(), rect.width(), rect.height(),
                label, action, (context, button, hovered, delta) -> TabletSectionPainter.button(frameCanvas,
                        textRenderer, button, style, icon, label, hovered, frameAccent))));
    }

    private <T extends Element> T keyed(String focusKey, T widget) {
        focusKeys.put(widget, focusKey);
        return widget;
    }

    /** Per-frame widget state that depends on typing/scrolling rather than on the snapshot. 随输入/滚动变化的控件状态。 */
    private void syncChatWidgets(TabletSnapshot snapshot) {
        if (chatInputAttached && chatInput != null) {
            Rect pill = composer().pill();
            int reserve = counterVisible() ? textRenderer.getWidth(counterText(TabletRules.CHAT_MESSAGE_MAX_LENGTH)) + 6 : 0;
            chatInput.setWidth(Math.max(1, pill.width() - 2 * INPUT_INSET_X - reserve));
        }
        if (sendButton != null) {
            sendButton.active = canSendChat(snapshot);
        }
        if (newMessagesButton != null) {
            newMessagesButton.visible = newMessagesPending && session.chatScroll() > 0;
        }
    }

    // ============================================================================================ input

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (switcherOpen) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeSwitcher();
                return true;
            }
            // The overlay is modal: swallow focus traversal (Tab/arrows) and activation (Space/Enter) so no widget
            // underneath can gain focus or be pressed. Other keys are not forwarded to widgets either.
            // 浮层为模态：拦截焦点切换（Tab/方向键）与激活（空格/回车），防止下层控件获得焦点或被触发；其余按键也不转发给控件。
            return isSwitcherBlockedKey(keyCode);
        }
        // Enter sends only from the composer; a focused button activates instead.
        // 回车仅在输入区发送；聚焦的按钮则被激活。
        Element focused = getFocused();
        if (session.section() == Section.CHAT
                && !visibleSections.isEmpty()
                && (focused == null || focused == chatInput || focused == sendButton)
                && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            sendChat();
            return true;
        }
        boolean handled = super.keyPressed(keyCode, scanCode, modifiers);
        if (session.section() == Section.CHAT
                && chatInputAttached
                && chatInput != null
                && chatInput.isFocused()
                && isMovementKey(keyCode, scanCode)) {
            return true;
        }
        return handled;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (switcherOpen) {
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
        if (switcherOpen) {
            SwitcherGeometry switcher = switcherGeometry(snapshot);
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                for (int index = 0; index < switcher.rows().size(); index++) {
                    TabletChannel channel = switcher.channels().get(index);
                    if (switcher.rows().get(index).contains(x, y) && switchState(channel, snapshot) == SwitchState.JOIN) {
                        requestSwitch(channel);
                        return true;
                    }
                }
            }
            // Clicks never fall through the overlay; anything outside it just dismisses it.
            // 点击不会穿透浮层；点击浮层外部仅关闭浮层。
            if (!switcher.box().contains(x, y)) {
                closeSwitcher();
            }
            return true;
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
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && session.section() == Section.CHAT
                && chatInputAttached
                && chatInput != null
                && composer().pill().contains(x, y)) {
            // The whole pill focuses the input and places the caret (the input's own hit box is one text line tall);
            // dragging then extends the selection through ParentElement.mouseDragged.
            // 整个输入胶囊都能聚焦输入框并定位光标（输入框自身的命中区只有一行高）；随后拖动经 ParentElement.mouseDragged 扩展选区。
            setFocused(chatInput);
            chatInput.onClick(x, y);
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
        boolean chatControl = focused == sendButton || focused == newMessagesButton;
        if (detached || chatControl) {
            setFocused(chatInputAttached ? chatInput : null);
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
        if (switcherOpen) {
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
        if (switcherOpen) {
            return true;
        }
        double x = toLogical(mouseX);
        double y = toLogical(mouseY);
        if (layout == null || visibleSections.isEmpty()) {
            return super.mouseScrolled(x, y, horizontalAmount, verticalAmount);
        }
        TabletSnapshot snapshot = TabletClientState.snapshot();
        switch (session.section()) {
            case CHAT -> {
                Rect list = layout.list();
                if (list.contains(x, y)) {
                    session.scrollChat((int) Math.round(verticalAmount * CHAT_SCROLL_STEP), chatView.maxScroll(list.height()));
                    if (session.chatScroll() == 0) {
                        newMessagesPending = false;
                    }
                    return true;
                }
            }
            case CONNECTIONS -> {
                Grid grid = connectionsGrid();
                if (grid.area().contains(x, y)) {
                    // Painted only, so no rebuild is needed. 仅绘制，无需重建控件。
                    session.scrollConnections(verticalAmount, grid.totalRows(snapshot.connections().size()), grid.visibleRows());
                    return true;
                }
            }
            case DOOR_LOG -> {
                Grid grid = doorLogGrid();
                if (grid.area().contains(x, y)) {
                    // Painted only, like CONNECTIONS. 与成员分区相同，仅绘制。
                    List<TabletSnapshot.DoorLogRow> rows = doorLogRows(snapshot);
                    session.scrollDoorLog(verticalAmount, rows.size(), grid.visibleRows());
                    lastDoorLogRows = rows;
                    return true;
                }
            }
            case MEETING -> {
                Grid grid = voteGrid();
                if (hasMeetingFeatures(snapshot) && snapshot.meeting().active() && grid.area().contains(x, y)) {
                    int previous = session.meetingFirstRow();
                    session.scrollMeeting(verticalAmount, grid.totalRows(snapshot.meeting().targets().size()), grid.visibleRows());
                    if (session.meetingFirstRow() != previous) {
                        refresh();
                    }
                    return true;
                }
            }
            case SUSPECTS -> {
                Grid grid = suspectGrid();
                if (hasMeetingFeatures(snapshot) && grid.area().contains(x, y)) {
                    int previous = session.suspectFirstRow();
                    session.scrollSuspects(verticalAmount, grid.totalRows(snapshot.suspects().size()), grid.visibleRows());
                    if (session.suspectFirstRow() != previous) {
                        refresh();
                    }
                    return true;
                }
            }
        }
        return super.mouseScrolled(x, y, horizontalAmount, verticalAmount);
    }

    private static boolean isSwitcherBlockedKey(int keyCode) {
        return keyCode == GLFW.GLFW_KEY_TAB
                || keyCode == GLFW.GLFW_KEY_LEFT
                || keyCode == GLFW.GLFW_KEY_RIGHT
                || keyCode == GLFW.GLFW_KEY_UP
                || keyCode == GLFW.GLFW_KEY_DOWN
                || keyCode == GLFW.GLFW_KEY_SPACE
                || keyCode == GLFW.GLFW_KEY_ENTER
                || keyCode == GLFW.GLFW_KEY_KP_ENTER;
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

    // ============================================================================================ chat actions

    private void sendChat() {
        TabletSnapshot snapshot = TabletClientState.snapshot();
        if (!snapshot.canSend() || snapshot.channel() == null || !chatInputAttached || chatInput == null) {
            return;
        }
        rememberChatDraft();
        // The wire we were viewing travels with the message; the server drops it if its selection moved on.
        // 随消息附带当前查看的频道；若服务端的选择已变化则丢弃该消息。
        session.submitDraft().ifPresent(message ->
                ClientPlayNetworking.send(new SendTabletChatC2SPacket(snapshot.channelWire(), message)));
        TabletClientState.setChatDraft(session.draft());
        chatInput.setText(session.draft());
        resetChatScroll();
    }

    private void rememberChatDraft() {
        if (chatInputAttached && chatInput != null) {
            session.updateDraft(chatInput.getText());
            TabletClientState.setChatDraft(session.draft());
        }
    }

    private void resetChatScroll() {
        session.resetChatScroll();
        newMessagesPending = false;
    }

    private boolean canSendChat(TabletSnapshot snapshot) {
        return snapshot.canSend()
                && snapshot.channel() != null
                && chatInputAttached
                && !session.draft().isBlank();
    }

    private boolean counterVisible() {
        return chatInput != null && chatInput.getText().length() >= COUNTER_THRESHOLD;
    }

    private static String counterText(int length) {
        return length + "/" + TabletRules.CHAT_MESSAGE_MAX_LENGTH;
    }

    // ============================================================================================ switcher state

    private void openSwitcher() {
        rememberChatDraft();
        if (chatInput != null) {
            chatInput.setFocused(false);
        }
        setFocused(null);
        switcherOpen = true;
        switcherOpenedAtMs = Util.getMeasuringTimeMs();
        playClick();
    }

    private void closeSwitcher() {
        switcherOpen = false;
        if (chatInputAttached && chatInput != null && session.section() == Section.CHAT) {
            setFocused(chatInput);
        }
    }

    private void requestSwitch(TabletChannel channel) {
        ClientPlayNetworking.send(new SelectTabletChannelC2SPacket(channel.wire()));
        pendingChannelWire = channel.wire();
        pendingSwitchTicks = PENDING_SWITCH_TICKS;
        playClick();
    }

    private void onChannelChanged(int previousChannelWire) {
        // Wire 0 means "not synced yet"; only a real channel-to-channel change discards what was typed.
        // wire 0 表示尚未同步；只有真正从一个频道切到另一个频道才丢弃已输入内容。
        if (previousChannelWire != TabletChannel.NO_CHANNEL_WIRE) {
            TabletClientState.clearChatDraft();
        }
        session.updateDraft(TabletClientState.chatDraft());
        // Overwrite the persistent input now so refresh() cannot copy the old channel's text back into the draft.
        // 立即覆盖持久输入框，避免 refresh() 把旧频道的文字写回草稿。
        if (chatInput != null) {
            chatInput.setText(session.draft());
        }
        switcherOpen = false;
        pendingChannelWire = TabletChannel.NO_CHANNEL_WIRE;
        pendingSwitchTicks = 0;
        resetChatScroll();
        chatLayoutPrimed = false;
        lastChatRows = List.of();
    }

    /**
     * Presentation-only mirror of {@code TabletChannelRules.canSwitch}; the server re-validates every request.
     * 仅用于展示的 canSwitch 镜像；服务端会重新校验每个请求。
     */
    private SwitchState switchState(TabletChannel channel, TabletSnapshot snapshot) {
        if (channel == snapshot.channel()) {
            return SwitchState.CURRENT;
        }
        if (!snapshot.allowedChannels().contains(channel)) {
            return SwitchState.LOCKED;
        }
        if (channel.wire() == pendingChannelWire) {
            return SwitchState.PENDING;
        }
        if (snapshot.channelLocked()) {
            return SwitchState.MEETING_LOCKED;
        }
        if (snapshot.channelSwitchCooldownSeconds() > 0) {
            return SwitchState.COOLDOWN;
        }
        return SwitchState.JOIN;
    }

    private List<TabletChannel> switcherChannels(TabletSnapshot snapshot) {
        EnumSet<TabletChannel> allowed = snapshot.allowedChannels();
        TabletChannel current = snapshot.channel();
        ArrayList<TabletChannel> channels = new ArrayList<>();
        for (TabletChannel channel : TabletChannel.values()) {
            // The witch network only exists with SparkWitch installed. 只有安装 SparkWitch 时才存在魔女网络。
            if (channel == TabletChannel.WITCH
                    && !SparkWitchCompat.isLoaded()
                    && channel != current
                    && !allowed.contains(channel)) {
                continue;
            }
            channels.add(channel);
        }
        return channels;
    }

    private @Nullable SwitcherHint switcherHint(TabletSnapshot snapshot) {
        EnumSet<TabletChannel> allowed = snapshot.allowedChannels();
        if (snapshot.channelLocked()) {
            return new SwitcherHint(Text.translatable(KEY + "channel.hint.meeting"), true);
        }
        if (allowed.size() > 1 && snapshot.channelSwitchCooldownSeconds() > 0) {
            return new SwitcherHint(Text.translatable(KEY + "channel.hint.cooldown",
                    snapshot.channelSwitchCooldownSeconds()), false);
        }
        if (allowed.size() <= 1) {
            return new SwitcherHint(Text.translatable(KEY + "channel.hint.identity"), false);
        }
        return null;
    }

    private void playClick() {
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    // ============================================================================================ geometry

    /**
     * Status-bar channel chip; the single source for both painting and hit testing. Monitor-only holders get a
     * static chip naming the door monitor instead of "No Signal" and a switcher of locked rows.
     * 状态栏频道胶囊；绘制与命中测试共用。仅监控持有者显示不可点击、写着房门监控的胶囊，而不是“无信号”与全锁定的切换浮层。
     */
    private ChannelChip channelChip(TabletSnapshot snapshot) {
        Rect status = layout.statusBar();
        boolean live = snapshot.localHasTablet();
        boolean monitor = live && monitorOnly(snapshot);
        // Clickable exactly when mouseClicked opens the switcher from it. 与 mouseClicked 打开切换浮层的条件一致。
        boolean clickable = live && !monitor;
        TabletChannel channel = live ? snapshot.channel() : null;
        String name;
        String shortName = null;
        if (!live) {
            name = Text.translatable(KEY + "no_signal.connecting").getString();
        } else if (monitor) {
            name = Text.translatable(Section.DOOR_LOG.translationKey()).getString();
            shortName = Text.translatable(Section.DOOR_LOG.shortTranslationKey()).getString();
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
                channel != null || monitor);
    }

    private Rect signalBox() {
        Rect close = layout.closeButton();
        Rect status = layout.statusBar();
        return new Rect(close.x() - 8 - SIGNAL_SIZE, status.centerY() - SIGNAL_SIZE / 2, SIGNAL_SIZE, SIGNAL_SIZE);
    }

    private Composer composer() {
        Rect footer = layout.footer();
        int padX = layout.mode().padX();
        int size = Math.max(0, Math.min(SEND_SIZE, footer.height() - 6));
        int inset = Math.max(3, (footer.height() - SEND_SIZE) / 2);
        Rect pill = footer.inset(padX, inset, padX + size + SEND_GAP, inset);
        Rect send = new Rect(pill.right() + SEND_GAP, pill.centerY() - size / 2, size, size);
        return new Composer(pill, send);
    }

    /** Meeting footer: [Abstain][Confirm] right-aligned; the status line gets the rest. 会议操作栏：按钮右对齐，其余给状态行。 */
    private ActionBar actionBar(TabletSnapshot snapshot) {
        TabletSnapshot.Meeting meeting = snapshot.meeting();
        Rect footer = layout.footer();
        int padX = layout.mode().padX();
        int height = Math.min(TabletSectionPainter.ACTION_BUTTON_H, footer.height());
        int y = footer.centerY() - height / 2;
        boolean confirmed = meeting.localConfirmed();
        Text confirmLabel = Text.translatable(KEY + (confirmed ? "meeting.locked" : "meeting.confirm"));
        ButtonIcon confirmIcon = confirmed ? ButtonIcon.LOCK : ButtonIcon.NONE;
        ButtonStyle confirmStyle = confirmed ? ButtonStyle.LOCKED : ButtonStyle.PRIMARY;
        Text abstainLabel = Text.translatable(KEY + "meeting.abstain");
        ButtonStyle abstainStyle = meeting.localAbstained() ? ButtonStyle.WARNING_TOGGLED : ButtonStyle.SECONDARY;

        // Widths come from the widest label each button can ever show, so nothing shifts when it locks.
        // 宽度取各按钮可能显示的最宽文字，锁定时布局不会移动。
        int room = Math.max(0, footer.width() - 2 * padX - ACTION_GAP);
        int confirmWidth = Math.min(room / 2, Math.max(
                actionButtonWidth(Text.translatable(KEY + "meeting.confirm"), ButtonIcon.NONE),
                actionButtonWidth(Text.translatable(KEY + "meeting.locked"), ButtonIcon.LOCK)));
        int abstainWidth = Math.min(room / 2, actionButtonWidth(abstainLabel, ButtonIcon.NONE));
        int confirmX = footer.right() - padX - confirmWidth;
        int abstainX = confirmX - ACTION_GAP - abstainWidth;
        return new ActionBar(
                new Rect(abstainX, y, abstainWidth, height), abstainLabel, abstainStyle,
                new Rect(confirmX, y, confirmWidth, height), confirmLabel, confirmStyle, confirmIcon);
    }

    private int actionButtonWidth(Text label, ButtonIcon icon) {
        int iconWidth = icon == ButtonIcon.NONE ? 0 : ACTION_ICON + ACTION_ICON_GAP;
        return Math.max(ACTION_MIN_W, 2 * ACTION_PAD + iconWidth + textRenderer.getWidth(label));
    }

    private Grid connectionsGrid() {
        return Grid.of(layout.bodyNoFooter(), TabletLayout.gridColumns(layout.bodyNoFooter().width(),
                        TabletSectionPainter.MEMBER_MIN_W, TabletSectionPainter.GRID_GAP),
                TabletSectionPainter.MEMBER_CARD_H, TabletSectionPainter.MEMBER_ROW_STEP);
    }

    private Grid voteGrid() {
        Rect area = layout.list().inset(0, TabletSectionPainter.PROGRESS_BLOCK_H, 0, 0);
        return Grid.of(area, TabletLayout.gridColumns(area.width(), TabletSectionPainter.VOTE_MIN_W,
                TabletSectionPainter.GRID_GAP), TabletSectionPainter.VOTE_CARD_H, TabletSectionPainter.VOTE_ROW_STEP);
    }

    private Grid suspectGrid() {
        return Grid.of(layout.bodyNoFooter(), 1, TabletSectionPainter.SUSPECT_CARD_H,
                TabletSectionPainter.SUSPECT_ROW_STEP);
    }

    private Grid doorLogGrid() {
        return Grid.of(layout.bodyNoFooter(), 1, TabletSectionPainter.DOOR_ROW_H, TabletSectionPainter.DOOR_ROW_STEP);
    }

    /** Row scrolls are in grid rows (ceil(n / columns)); clamped on every snapshot and rebuild. 以网格行计，每次快照与重建时限制范围。 */
    private void clampRowScrolls(TabletSnapshot snapshot) {
        Grid members = connectionsGrid();
        session.clampConnections(members.totalRows(snapshot.connections().size()), members.visibleRows());
        Grid votes = voteGrid();
        session.applyMeetingSnapshot(snapshot.meeting().active(),
                votes.totalRows(snapshot.meeting().targets().size()), votes.visibleRows());
        Grid suspects = suspectGrid();
        session.applySuspectSnapshot(suspects.totalRows(snapshot.suspects().size()), suspects.visibleRows());
        session.applyDoorLogSnapshot(-1, doorLogRows(snapshot).size(), doorLogGrid().visibleRows());
    }

    /** 2 px scroll track in the right gutter of a list area. 列表右侧留白中的 2 像素滚动条轨道。 */
    private Rect scrollTrack(Rect area) {
        int offset = Math.max(2, layout.mode().padX() / 3);
        return new Rect(area.right() + offset, area.y(), 2, area.height());
    }

    private Rect tabIconBox(TabletPressable tab) {
        if (layout.mode() == TabletLayout.Mode.REGULAR) {
            return new Rect(tab.getX() + tab.getWidth() / 2 - 7, tab.getY() + 7, 14, 14);
        }
        return new Rect(tab.getX() + tab.getWidth() / 2 - 8, tab.getY() + (tab.getHeight() - 16) / 2, 16, 16);
    }

    /**
     * Switcher popover geometry; the single source for painting and hit testing.
     * 切换浮层几何；绘制与命中测试共用同一来源。
     */
    private SwitcherGeometry switcherGeometry(TabletSnapshot snapshot) {
        Rect screen = layout.screen();
        Rect chip = channelChip(snapshot).rect();
        List<TabletChannel> channels = switcherChannels(snapshot);
        SwitcherHint hint = switcherHint(snapshot);
        int width = Math.min(SWITCHER_WIDTH, Math.max(0, screen.width() - 16));
        int minX = screen.x() + 8;
        int x = Math.max(minX, Math.min(screen.right() - 8 - width, chip.centerX() - width / 2));
        int y = chip.bottom() + SWITCHER_OFFSET;
        List<String> hintLines = hint == null ? List.of()
                : TabletSectionPainter.wrap(textRenderer, hint.text().getString(), width - 2 * SWITCHER_HINT_PAD, 2);

        int rowHeight = SWITCHER_ROW_H;
        int rowGap = SWITCHER_ROW_GAP;
        if (y + switcherHeight(channels.size(), hintLines.size(), rowHeight, rowGap) > screen.bottom() - 4) {
            rowHeight = SWITCHER_COMPACT_ROW_H;
            rowGap = SWITCHER_COMPACT_ROW_GAP;
        }
        ArrayList<Rect> rows = new ArrayList<>(channels.size());
        for (int index = 0; index < channels.size(); index++) {
            rows.add(new Rect(x + SWITCHER_INSET, y + SWITCHER_ROWS_TOP + index * (rowHeight + rowGap),
                    Math.max(0, width - 2 * SWITCHER_INSET), rowHeight));
        }
        int rowsBottom = rows.isEmpty() ? y + SWITCHER_ROWS_TOP : rows.get(rows.size() - 1).bottom();
        int height = switcherHeight(channels.size(), hintLines.size(), rowHeight, rowGap);
        int caretX = Math.max(x + 14, Math.min(x + width - 14, chip.centerX()));
        return new SwitcherGeometry(new Rect(x, y, width, height), caretX, List.copyOf(channels), List.copyOf(rows),
                hintLines, hint != null && hint.warning(), rowsBottom + 8);
    }

    private static int switcherHeight(int rowCount, int hintLines, int rowHeight, int rowGap) {
        int rows = rowCount <= 0 ? 0 : rowCount * rowHeight + (rowCount - 1) * rowGap;
        int hint = hintLines == 0 ? 6 : 8 + hintLines * 10 + SWITCHER_HINT_PAD - 2;
        return SWITCHER_ROWS_TOP + rows + hint;
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
        int hoverX = switcherOpen ? -1 : logicalMouseX;
        int hoverY = switcherOpen ? -1 : logicalMouseY;

        paintFrame();
        paintStatusBar(snapshot, hoverX, hoverY, now);
        boolean hasSections = !visibleSections.isEmpty();
        if (hasSections) {
            paintRail(snapshot, hoverX, hoverY);
            paintHeader(snapshot, now);
            paintSectionBody(snapshot, hoverX, hoverY, now);
        } else {
            paintNoSignal(snapshot, now);
        }

        super.render(context, hoverX, hoverY, delta);

        if (hasSections) {
            paintRailBadges(snapshot, now);
            paintTabTooltip();
        }
        if (switcherOpen) {
            paintSwitcher(snapshot, logicalMouseX, logicalMouseY, now);
        }
        frameCanvas.flush();
        matrices.pop();
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
    private int screenBackdropAt(int x, int y) {
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
        // The door monitor works without a network, so a monitor-only tablet is not shown as signal-less.
        // 房门监控无需网络，仅监控的平板不显示为无信号。
        int lit = connecting ? sweepBars(now) : snapshot.channel() == null && !monitorOnly(snapshot) ? 0 : 4;
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
        boolean hot = chip.clickable() && (switcherOpen || rect.contains(hoverX, hoverY));
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
                    switcherOpen, hot ? TabletTheme.TEXT : TabletTheme.TEXT_2);
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
        if (monitorOnly(snapshot)) {
            // Not a channel: a monitor glyph instead of a monogram, and nothing to click. 不是频道：显示监控图形而非首字，且不可点击。
            c.roundRectGradientV(badge.x(), badge.y(), badge.width(), badge.height(), 9f,
                    frameAccent.base(), frameAccent.deep());
            float glyph = 16f;
            TabletIcons.eye(c, badge.centerX() - glyph / 2f, badge.y() + (badge.height() - glyph) / 2f, glyph,
                    TabletTheme.WHITE);
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
        if (switcherOpen || badge.contains(hoverX, hoverY)) {
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
        }
    }

    /** Badges sit on the top-right of each tab icon, drawn after widgets. 徽标位于标签图标右上角，在控件之后绘制。 */
    private void paintRailBadges(TabletSnapshot snapshot, long now) {
        TabletCanvas c = frameCanvas;
        for (int index = 0; index < tabButtons.size(); index++) {
            // The open tab needs no attention badge. 当前所在标签不显示提醒徽标。
            if (tabButtons.get(index).selectedState()) {
                continue;
            }
            Rect icon = tabIconBox(tabButtons.get(index));
            float cx = icon.right();
            float cy = icon.y() + 1;
            switch (tabSections.get(index)) {
                case CHAT -> {
                    if (TabletClientState.hasUnreadChat(snapshot)) {
                        c.circle(cx, cy, 4f, TabletTheme.mix(screenBackdropAt((int) cx, (int) cy), 0xFF000000, 0.25f));
                        c.circle(cx, cy, 3f, frameAccent.base());
                    }
                }
                case MEETING -> {
                    if (snapshot.meeting().active()) {
                        paintPulseDot(cx, cy, 3f, TabletTheme.DANGER, now);
                    }
                }
                case SUSPECTS -> {
                    int count = snapshot.suspects().size();
                    if (count > 0) {
                        String text = count > 99 ? "99+" : String.valueOf(count);
                        int width = Math.max(11, textRenderer.getWidth(text) + 4);
                        float left = icon.right() - 4;
                        float top = icon.y() - 4;
                        c.roundRect(left, top, width, 10f, 5f, TabletTheme.WARNING);
                        c.text(textRenderer, text, Math.round(left + (width - textRenderer.getWidth(text) + 1) / 2f),
                                Math.round(top + 2), TabletTheme.WARNING_INK);
                    }
                }
                case DOOR_LOG -> {
                    // New entries only ever surface as this red dot (no HUD). 新条目只以此红点提示（无 HUD）。
                    if (TabletClientState.hasUnreadDoorLog(snapshot)) {
                        c.circle(cx, cy, 4f, TabletTheme.mix(screenBackdropAt((int) cx, (int) cy), 0xFF000000, 0.25f));
                        c.circle(cx, cy, 3f, TabletTheme.DANGER);
                    }
                }
                case CONNECTIONS -> {
                }
            }
        }
    }

    /** NARROW tabs are icon-only: show the short label in a pill to the right of the hovered tab. 窄模式悬停提示。 */
    private void paintTabTooltip() {
        if (layout.mode() != TabletLayout.Mode.NARROW || switcherOpen) {
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
        int chipX = paintHeaderChip(snapshot, right, centerY, Math.max(0, (right - left) / 2), now);
        String title = TabletCanvas.trim(textRenderer, Text.translatable(session.section().translationKey()).getString(),
                Math.max(0, chipX - 10 - left));
        frameCanvas.text(textRenderer, title, left, centerY - 4, TabletTheme.TEXT);
    }

    /** Right-aligned detail chip; returns its left edge ({@code right} when none). 右对齐的信息胶囊；返回其左边缘（无胶囊时为 right）。 */
    private int paintHeaderChip(TabletSnapshot snapshot, int right, int centerY, int maxWidth, long now) {
        TabletSnapshot.Meeting meeting = snapshot.meeting();
        return switch (session.section()) {
            // Locked police view: a lock + task progress instead of a misleading "1 connected".
            // 义警身份未解锁：显示锁与任务进度，而非具有误导性的“已接入 1”。
            case CONNECTIONS -> identitiesLocked(snapshot)
                    ? paintPill(right, centerY, maxWidth, Text.translatable(KEY + "channel.tasks_locked",
                            identityTasksDone(snapshot), TabletIdentityRules.POLICE_REVEAL_TASKS).getString(),
                    frameAccent.pale(), TabletTheme.withAlpha(frameAccent.base(), 0x2E), 0, TabletIcons::lock,
                    frameAccent.pale())
                    : paintPill(right, centerY, maxWidth, memberCount(snapshot).getString(),
                    TabletTheme.TEXT_2, TabletTheme.SURFACE, 0, null, 0);
            case CHAT -> paintPill(right, centerY, maxWidth, String.valueOf(snapshot.chat().size()),
                    TabletTheme.TEXT_2, TabletTheme.SURFACE, 0, TabletIcons::chat, TabletTheme.TEXT_2);
            case MEETING -> meeting.active()
                    ? paintPill(right, centerY, maxWidth,
                    Text.translatable(KEY + "meeting.timer", meeting.remainingSeconds()).getString(),
                    TabletTheme.KILLER.pale(), TabletTheme.withAlpha(TabletTheme.DANGER, 0x33), 0,
                    (canvas, x, y, size, color) -> paintPulseDot(x + size / 2f, y + size / 2f, 3f, color, now),
                    TabletTheme.DANGER)
                    // Inactive: the hero card already shows the remaining chances, so no duplicate chip.
                    // 未开会：英雄卡已显示剩余次数，页眉不再重复显示。
                    : right;
            case SUSPECTS -> paintPill(right, centerY, maxWidth, String.valueOf(snapshot.suspects().size()),
                    TabletTheme.TEXT_2, TabletTheme.SURFACE, 0, TabletIcons::target, TabletTheme.TEXT_2);
            // Live indicator in the monitor accent whatever the channel: the feed belongs to the door monitor.
            // 无论频道如何都使用监控强调色的实时指示：该画面属于房门监控。
            case DOOR_LOG -> paintPill(right, centerY, maxWidth, Text.translatable(KEY + "door_log.live").getString(),
                    TabletTheme.MONITOR.pale(), TabletTheme.withAlpha(TabletTheme.MONITOR.base(), 0x2E), 0,
                    (canvas, x, y, size, color) -> paintPulseDot(x + size / 2f, y + size / 2f, 3f, color, now),
                    TabletTheme.MONITOR.base());
        };
    }

    /**
     * Right-aligned pill (h=16) with an optional leading glyph; the text is trimmed to maxWidth. Returns the left x.
     * 右对齐胶囊（高 16），可带前置图形；文字按 maxWidth 截断。返回左边缘 x。
     */
    private int paintPill(int right, int centerY, int maxWidth, String text, int textColor, int fill, int outline,
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

    private void paintPulseDot(float cx, float cy, float radius, int color, long now) {
        float phase = (float) (0.5 + 0.5 * Math.sin(2.0 * Math.PI * (now % PULSE_PERIOD_MS) / PULSE_PERIOD_MS));
        frameCanvas.circle(cx, cy, radius + 2f * phase, TabletTheme.withAlpha(color, Math.round(0x55 * (1f - phase))));
        frameCanvas.circle(cx, cy, radius, TabletTheme.multiplyAlpha(color, 0.7f + 0.3f * phase));
    }

    // ------------------------------------------------------------------------------------------------ sections

    private void paintSectionBody(TabletSnapshot snapshot, int hoverX, int hoverY, long now) {
        switch (session.section()) {
            case CONNECTIONS -> paintConnections(snapshot);
            case CHAT -> paintChat(snapshot, hoverX, hoverY);
            case MEETING -> {
                if (hasMeetingFeatures(snapshot)) {
                    paintMeeting(snapshot, now);
                }
            }
            case SUSPECTS -> {
                if (hasMeetingFeatures(snapshot)) {
                    paintSuspects(snapshot);
                }
            }
            case DOOR_LOG -> paintDoorLog(snapshot);
        }
    }

    private void paintConnections(TabletSnapshot snapshot) {
        TabletCanvas c = frameCanvas;
        Grid grid = connectionsGrid();
        List<TabletSnapshot.PlayerRow> rows = snapshot.connections();
        if (rows.isEmpty()) {
            TabletSectionPainter.emptyState(c, textRenderer, grid.area(), EmptyIcon.MEMBERS,
                    Text.translatable(KEY + "connections.empty"));
            return;
        }
        int totalRows = grid.totalRows(rows.size());
        int first = session.connectionsFirstRow();
        int shownRows = Math.min(grid.visibleRows(), totalRows - first);
        // A locked police viewer was sent only their own row (dead viewers too, so they still learn why); an unlinked
        // live killer was sent only theirs. 未解锁的义警查看者只收到自己的行（死亡者也显示，以便知道原因）；未互认的存活杀手同理。
        boolean taskHint = identitiesLocked(snapshot);
        boolean linkHint = !taskHint && snapshot.canSend() && isAnonymousChannel(snapshot) && linkedPartners(snapshot) == 0;
        if (taskHint || linkHint) {
            // Pin the callout to the bottom by giving up the last row(s) when it would not fit after them.
            // 若放在最后一行之后放不下，就让出末尾的行，把提示卡片固定在列表底部。
            while (shownRows > 0 && grid.area().bottom() - grid.rowY(shownRows) < HINT_CALLOUT_MIN_H) {
                shownRows--;
            }
        }

        UUID self = localUuid();
        for (int row = 0; row < shownRows; row++) {
            for (int column = 0; column < grid.columns(); column++) {
                int index = (first + row) * grid.columns() + column;
                if (index >= rows.size()) {
                    break;
                }
                TabletSnapshot.PlayerRow player = rows.get(index);
                TabletSectionPainter.memberCard(c, textRenderer, grid.cellX(column), grid.rowY(row), grid.cellWidth(),
                        player, player.uuid().equals(self), frameAccent);
            }
        }
        if (taskHint) {
            int top = grid.rowY(shownRows);
            TabletSectionPainter.taskHint(c, textRenderer, grid.area().x(), top, grid.area().width(),
                    grid.area().bottom() - top, frameAccent, identityTasksDone(snapshot),
                    TabletIdentityRules.POLICE_REVEAL_TASKS);
        } else if (linkHint) {
            int top = grid.rowY(shownRows);
            TabletSectionPainter.linkHint(c, textRenderer, grid.area().x(), top, grid.area().width(),
                    grid.area().bottom() - top, frameAccent);
        }
        TabletSectionPainter.scrollThumb(c, scrollTrack(grid.area()), first, grid.visibleRows(), totalRows);
    }

    private void paintChat(TabletSnapshot snapshot, int hoverX, int hoverY) {
        TabletCanvas c = frameCanvas;
        Rect list = layout.list();
        List<TabletSnapshot.ChatRow> rows = snapshot.chat();
        layoutChat(rows, list);
        TabletClientState.markChatSeen(TabletClientState.chatSignature(rows));
        if (rows.isEmpty()) {
            TabletSectionPainter.emptyState(c, textRenderer, list, EmptyIcon.CHAT, Text.translatable(KEY + "chat.empty"));
        } else {
            c.pushClip(list.x(), list.y(), list.width(), list.height());
            chatView.render(c, textRenderer, list.x(), list.y(), list.width(), list.height(), session.chatScroll(), frameAccent);
            paintChatEdgeMasks(list);
            c.popClip();
        }
        paintComposer(hoverX, hoverY);
        syncChatWidgets(snapshot);
    }

    /**
     * Fades scrolled-past chat content into the real screen backdrop at the list edges. Each band's strength grows
     * with how far the content continues past that edge, so the resting ends (newest at scroll 0, oldest at max
     * scroll) stay crisp. Must run inside the list clip and before the "new messages" pill (a drawable child painted
     * later by super.render).
     * 在列表上下边缘把滚出视野的聊天内容渐隐到真实屏幕背景。每条遮罩的强度随内容越过该边缘的距离增加，
     * 因此静止端（滚动为 0 的最新消息、滚到顶的最早消息）保持清晰。必须在列表裁剪内、且早于“新消息”胶囊
     * （由 super.render 稍后绘制的子控件）执行。
     */
    private void paintChatEdgeMasks(Rect list) {
        int maxScroll = chatView.maxScroll(list.height());
        int scroll = Math.max(0, Math.min(session.chatScroll(), maxScroll));
        int band = Math.min(CHAT_MASK, list.height() / 2);
        if (band <= 0) {
            return;
        }
        paintChatMaskBand(list, list.y(), list.y() + band, (maxScroll - scroll) / (float) CHAT_MASK);
        paintChatMaskBand(list, list.bottom(), list.bottom() - band, scroll / (float) CHAT_MASK);
    }

    /** One mask band from {@code outerY} (opaque backdrop × strength) to {@code innerY} (clear). 从外缘到内缘的一条遮罩。 */
    private void paintChatMaskBand(Rect list, int outerY, int innerY, float strength) {
        strength = Math.max(0f, Math.min(1f, strength));
        if (strength < 0.01f) {
            return;
        }
        int outerAlpha = Math.round(255f * strength);
        int top = Math.min(outerY, innerY);
        int bottom = Math.max(outerY, innerY);
        boolean outerIsTop = outerY < innerY;
        for (int x0 = list.x(); x0 < list.right(); x0 += CHAT_MASK_STEP) {
            int x1 = Math.min(x0 + CHAT_MASK_STEP, list.right());
            int topAlpha = outerIsTop ? outerAlpha : 0;
            int bottomAlpha = outerIsTop ? 0 : outerAlpha;
            frameCanvas.gradientQuad(x0, top, x1, bottom,
                    TabletTheme.withAlpha(screenBackdropAt(x0, top), topAlpha),
                    TabletTheme.withAlpha(screenBackdropAt(x1, top), topAlpha),
                    TabletTheme.withAlpha(screenBackdropAt(x0, bottom), bottomAlpha),
                    TabletTheme.withAlpha(screenBackdropAt(x1, bottom), bottomAlpha));
        }
    }

    /**
     * Re-lays out the bubbles. When messages are appended while scrolled up, the previously newest bubble stays put:
     * scroll grows by the height appended below it, not by the net content-height change (at the history cap the
     * server also trims the oldest row, which must not move the view). The "new messages" pill is armed only when at
     * least one row was appended; a re-send that only reveals identities changes no message text and arms nothing.
     * 重新排版气泡。向上翻阅时追加新消息，原最新气泡保持不动：滚动量增加“原最新气泡下方新增的高度”，而非内容总高度差
     * （历史达到上限时服务端还会裁掉最早一行，这不应移动视口）。只有确实追加了至少一行才显示“新消息”胶囊；
     * 仅揭示身份的重发不改变任何消息文本，不会触发。
     */
    private void layoutChat(List<TabletSnapshot.ChatRow> rows, Rect list) {
        chatView.layout(textRenderer, rows, localUuid(), list.width());
        int maxScroll = chatView.maxScroll(list.height());
        if (!rows.equals(lastChatRows)) {
            if (chatLayoutPrimed && session.chatScroll() > 0) {
                int appended = appendedRowCount(lastChatRows, rows);
                if (appended < 0) {
                    // No overlap with the previous backlog: history was replaced, the old position means nothing.
                    // 与之前的记录没有重叠：历史已被替换，旧位置已无意义。
                    resetChatScroll();
                } else if (appended > 0) {
                    // Placement never depends on later rows, so this is exactly the height appended below the old newest bubble.
                    // 行的位置不取决于其后的行，因此这正是原最新气泡下方新增的高度。
                    session.scrollChat(chatView.heightBelow(rows.size() - appended - 1), maxScroll);
                    newMessagesPending = true;
                }
            }
            lastChatRows = rows;
        }
        chatLayoutPrimed = true;
        session.clampChat(maxScroll);
        if (session.chatScroll() == 0) {
            newMessagesPending = false;
        }
    }

    /**
     * Smallest k such that the first (n - k) new rows repeat the last (n - k) old rows by message text (sender-agnostic,
     * like the unread signature), i.e. k rows were appended; -1 when no non-empty overlap exists.
     * 求最小的 k，使新记录的前 (n - k) 行与旧记录的末尾 (n - k) 行消息文本一致（不看发送者，与未读指纹一致），
     * 即追加了 k 行；不存在非空重叠时返回 -1。
     */
    private static int appendedRowCount(List<TabletSnapshot.ChatRow> previous, List<TabletSnapshot.ChatRow> rows) {
        int oldSize = previous.size();
        int newSize = rows.size();
        for (int appended = Math.max(0, newSize - oldSize); appended < newSize; appended++) {
            int kept = newSize - appended;
            boolean match = true;
            for (int index = 0; index < kept && match; index++) {
                match = Objects.equals(rows.get(index).message(), previous.get(oldSize - kept + index).message());
            }
            if (match) {
                return appended;
            }
        }
        return -1;
    }

    private void paintComposer(int hoverX, int hoverY) {
        TabletCanvas c = frameCanvas;
        Rect pill = composer().pill();
        if (pill.width() <= 0 || pill.height() <= 0) {
            return;
        }
        float radius = pill.height() / 2f;
        int textY = pill.centerY() - 4;
        if (!chatInputAttached || chatInput == null) {
            c.roundRect(pill.x(), pill.y(), pill.width(), pill.height(), radius, TabletTheme.SURFACE_SUNKEN);
            int lockX = pill.x() + INPUT_INSET_X;
            TabletIcons.lock(c, lockX, pill.centerY() - 5, 10f, TabletTheme.TEXT_3);
            int textX = lockX + 10 + 6;
            c.text(textRenderer, TabletCanvas.trim(textRenderer, Text.translatable(KEY + "chat.read_only").getString(),
                    Math.max(0, pill.right() - INPUT_INSET_X - textX)), textX, textY, TabletTheme.TEXT_3);
            return;
        }
        boolean focused = chatInput.isFocused();
        int outline = focused
                ? TabletTheme.withAlpha(frameAccent.base(), 0xB3)
                : pill.contains(hoverX, hoverY) ? 0x26FFFFFF : TabletTheme.HAIRLINE;
        c.roundRect(pill.x(), pill.y(), pill.width(), pill.height(), radius, TabletTheme.SURFACE);
        c.roundRectOutline(pill.x(), pill.y(), pill.width(), pill.height(), radius, 1f, outline);
        int length = chatInput.getText().length();
        if (length >= COUNTER_THRESHOLD) {
            c.textRight(textRenderer, counterText(length), pill.right() - INPUT_INSET_X, textY,
                    length >= TabletRules.CHAT_MESSAGE_MAX_LENGTH ? TabletTheme.WARNING : TabletTheme.TEXT_3);
        }
    }

    private void paintSendButton(TabletPressable button, boolean hovered) {
        TabletCanvas c = frameCanvas;
        float radius = button.getWidth() / 2f;
        float cx = button.getX() + radius;
        float cy = button.getY() + button.getHeight() / 2f;
        int fill = !button.active
                ? TabletTheme.SURFACE
                : hovered ? TabletTheme.mix(frameAccent.base(), frameAccent.pale(), 0.25f) : frameAccent.base();
        c.circle(cx, cy, radius, fill);
        TabletIcons.send(c, cx - 6f, cy - 6f, 12f, button.active ? TabletTheme.WHITE : TabletTheme.TEXT_3);
        if (button.keyboardFocused()) {
            c.ring(cx, cy, radius + 2f, 1f, frameAccent.pale());
        }
    }

    private void paintNewMessagesPill(TabletPressable button, Text label, boolean hovered) {
        TabletCanvas c = frameCanvas;
        float x = button.getX();
        float y = button.getY();
        float w = button.getWidth();
        float h = button.getHeight();
        c.shadow(x, y, w, h, h / 2f, 8f, 0x80000000);
        c.roundRect(x, y, w, h, h / 2f,
                hovered ? TabletTheme.mix(frameAccent.base(), frameAccent.pale(), 0.25f) : frameAccent.base());
        c.roundRectOutline(x, y, w, h, h / 2f, 1f, TabletTheme.HAIRLINE);
        String text = TabletCanvas.trim(textRenderer, label.getString(),
                Math.max(0, button.getWidth() - 2 * PILL_PAD - PILL_GLYPH - PILL_GLYPH_GAP));
        int textX = button.getX() + PILL_PAD;
        textX += c.text(textRenderer, text, textX, button.getY() + (button.getHeight() - 8) / 2, TabletTheme.WHITE);
        TabletIcons.caret(c, textX + PILL_GLYPH_GAP, y + (h - PILL_GLYPH) / 2f, PILL_GLYPH, false, TabletTheme.WHITE);
        TabletSectionPainter.focusRing(c, button, h / 2f, frameAccent);
    }

    private void paintMeeting(TabletSnapshot snapshot, long now) {
        TabletCanvas c = frameCanvas;
        TabletSnapshot.Meeting meeting = snapshot.meeting();
        if (!meeting.active()) {
            TabletSectionPainter.meetingHero(c, textRenderer, layout.bodyNoFooter(), heroState(snapshot),
                    snapshot.cooldownSeconds(), snapshot.localMeetingCallsRemaining(), now);
            return;
        }
        Rect list = layout.list();
        TabletSectionPainter.meetingProgress(c, list.x(), list.y(), list.width(), meeting.remainingSeconds());
        Grid grid = voteGrid();
        TabletSectionPainter.scrollThumb(c, scrollTrack(grid.area()), session.meetingFirstRow(), grid.visibleRows(),
                grid.totalRows(meeting.targets().size()));

        ActionBar bar = actionBar(snapshot);
        int statusX = layout.footer().x() + layout.mode().padX();
        TabletSectionPainter.meetingStatus(c, textRenderer, statusX, layout.footer().centerY(),
                Math.max(0, bar.abstain().x() - 10 - statusX), meetingStatus(snapshot), meeting.localConfirmed()
                        && snapshot.localMeetingParticipant());
    }

    private void paintSuspects(TabletSnapshot snapshot) {
        TabletCanvas c = frameCanvas;
        Grid grid = suspectGrid();
        List<TabletSnapshot.SuspectRow> suspects = snapshot.suspects();
        if (suspects.isEmpty()) {
            TabletSectionPainter.emptyState(c, textRenderer, grid.area(), EmptyIcon.SHIELD,
                    Text.translatable(KEY + "suspects.none"));
            return;
        }
        int first = session.suspectFirstRow();
        for (int row = 0; row < grid.visibleRows() && first + row < suspects.size(); row++) {
            TabletSectionPainter.suspectCard(c, textRenderer, grid.area().x(), grid.rowY(row), grid.area().width(),
                    suspects.get(first + row), frameAccent);
        }
        TabletSectionPainter.scrollThumb(c, scrollTrack(grid.area()), first, grid.visibleRows(), suspects.size());
    }

    /** Newest first, painted only (no per-row widgets). 最新在前，仅绘制（每行无控件）。 */
    private void paintDoorLog(TabletSnapshot snapshot) {
        TabletCanvas c = frameCanvas;
        Grid grid = doorLogGrid();
        List<TabletSnapshot.DoorLogRow> rows = doorLogRows(snapshot);
        int first = session.doorLogFirstRow();
        // Only seeing the newest row clears the rail dot: entries that arrive while the reader is scrolled down (the
        // anchor keeps them off-screen) stay unread.
        // 只有看到最新一行才清除侧栏红点：阅读者向下滚动时到达的新记录（锚定使其不在屏幕内）保持未读。
        if (first == 0) {
            TabletClientState.markDoorLogSeen(snapshot);
        }
        if (rows.isEmpty()) {
            TabletSectionPainter.emptyState(c, textRenderer, grid.area(), EmptyIcon.DOOR,
                    Text.translatable(KEY + "door_log.empty"), Text.translatable(KEY + "door_log.empty_hint"));
            return;
        }
        int ageColumn = TabletSectionPainter.doorAgeColumn(textRenderer);
        int elapsed = TabletClientState.secondsSinceSnapshot();
        for (int row = 0; row < grid.visibleRows() && first + row < rows.size(); row++) {
            TabletSnapshot.DoorLogRow entry = rows.get(first + row);
            TabletSectionPainter.doorLogRow(c, textRenderer, grid.area().x(), grid.rowY(row), grid.area().width(), entry,
                    Objects.requireNonNull(entry.kind()), TabletSectionPainter.doorAge(entry.ageSeconds() + elapsed),
                    ageColumn);
        }
        TabletSectionPainter.scrollThumb(c, scrollTrack(grid.area()), first, grid.visibleRows(), rows.size());
    }

    private static HeroState heroState(TabletSnapshot snapshot) {
        if (snapshot.cooldownSeconds() > 0) {
            return HeroState.COOLDOWN;
        }
        return canCallMeeting(snapshot) ? HeroState.READY : HeroState.DISABLED;
    }

    private static Text meetingStatus(TabletSnapshot snapshot) {
        TabletSnapshot.Meeting meeting = snapshot.meeting();
        if (!snapshot.localMeetingParticipant()) {
            return Text.translatable(KEY + "meeting.status.spectator");
        }
        if (meeting.localConfirmed()) {
            return Text.translatable(KEY + "meeting.status.locked");
        }
        if (meeting.localAbstained()) {
            return Text.translatable(KEY + "meeting.status.abstained");
        }
        UUID target = meeting.localVoteTarget();
        if (target != null) {
            String name = "?";
            for (TabletSnapshot.VoteTarget candidate : meeting.targets()) {
                if (candidate.uuid().equals(target)) {
                    name = candidate.name();
                    break;
                }
            }
            return Text.translatable(KEY + "meeting.status.voted", name);
        }
        return Text.translatable(KEY + "meeting.status.none");
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

    // ------------------------------------------------------------------------------------------------ switcher

    private void paintSwitcher(TabletSnapshot snapshot, int mouseX, int mouseY, long now) {
        TabletCanvas c = frameCanvas;
        float fade = Math.min(1f, Math.max(0f, (now - switcherOpenedAtMs) / (float) SWITCHER_FADE_MS));
        float eased = 1f - (1f - fade) * (1f - fade);
        SwitcherGeometry switcher = switcherGeometry(snapshot);
        Rect box = switcher.box();
        Rect screen = layout.screen();

        c.roundRect(screen.x(), screen.y(), screen.width(), screen.height(), layout.mode().screenRadius(),
                TabletTheme.multiplyAlpha(0x66000000, fade));

        MatrixStack matrices = c.context().getMatrices();
        matrices.push();
        // Slide is paint-only and whole-unit, so text stays on integer logical coordinates; hit tests use the final
        // geometry. 滑入仅作用于绘制且取整，文字保持整数逻辑坐标；命中测试使用最终几何。
        matrices.translate(0f, (float) -Math.round((1f - eased) * SWITCHER_SLIDE), (float) SWITCHER_Z);
        c.shadow(box.x(), box.y(), box.width(), box.height(), 12f, 18f, TabletTheme.multiplyAlpha(0xA0000000, fade));
        c.roundRect(box.x(), box.y(), box.width(), box.height(), 12f, TabletTheme.multiplyAlpha(TabletTheme.SURFACE_RAISED, fade));
        c.roundRectOutline(box.x(), box.y(), box.width(), box.height(), 12f, 1f, TabletTheme.multiplyAlpha(TabletTheme.HAIRLINE, fade));
        paintSwitcherCaret(switcher.caretX(), box.y(), fade);

        String title = Text.translatable(KEY + "channel.select").getString().toUpperCase(Locale.ROOT);
        c.text(textRenderer, TabletCanvas.trim(textRenderer, title, box.width() - 24), box.x() + 12, box.y() + 10, TabletTheme.TEXT_3);
        for (int index = 0; index < switcher.rows().size(); index++) {
            paintSwitcherRow(snapshot, switcher.channels().get(index), switcher.rows().get(index), mouseX, mouseY, fade, now);
        }
        int hintY = switcher.hintY();
        int hintColor = switcher.hintWarning() ? TabletTheme.WARNING : TabletTheme.TEXT_3;
        for (String line : switcher.hintLines()) {
            c.text(textRenderer, line, box.x() + SWITCHER_HINT_PAD, hintY, hintColor);
            hintY += 10;
        }
        c.flush();
        matrices.pop();
    }

    /**
     * Caret as part of the popover silhouette: a 45-degree triangle filled with the popover colour down through the top
     * hairline (erasing it under the caret), then its two slanted edges stroked with the same hairline, which runs 1 px
     * inside the silhouette like the box outline, so the border flows around the caret.
     * 把尖角作为浮层轮廓的一部分：45 度三角形以浮层底色填充并向下盖住顶部描边（抹去尖角下方的线），
     * 再用同一描边色描出两条斜边；斜边与盒子描边一样位于轮廓内侧 1 px，使边框连续绕过尖角。
     */
    private void paintSwitcherCaret(float caretX, float top, float fade) {
        TabletCanvas c = frameCanvas;
        float size = SWITCHER_CARET;
        // Silhouette edges: (caretX -/+ (size + d), top + d) for d in [-size, 1]; the base sits under the hairline.
        // 轮廓斜边经过 (caretX ∓ (size + d), top + d)，d ∈ [-size, 1]；底边位于描边之下。
        c.polygon(new float[]{caretX - size - 1f, top + 1f, caretX, top - size, caretX + size + 1f, top + 1f},
                TabletTheme.multiplyAlpha(TabletTheme.SURFACE_RAISED, fade));
        // Stroke centre line sits 0.5 px inside each slanted edge (offset 0.5 * sqrt 2 vertically).
        // 描边中心线位于各斜边内侧 0.5 px（竖直方向偏移 0.5 * √2）。
        float inset = 0.70710677f;
        float tipY = top - size + inset;
        c.polyline(new float[]{caretX - size - 1f + inset, top + 1f, caretX, tipY, caretX + size + 1f - inset, top + 1f},
                1f,
                TabletTheme.multiplyAlpha(TabletTheme.HAIRLINE, fade));
    }

    /**
     * One switcher row. Locked rows deliberately show only the channel name: no member counts or other data.
     * 切换浮层中的一行。锁定行只显示频道名，刻意不显示成员人数等任何信息。
     */
    private void paintSwitcherRow(TabletSnapshot snapshot, TabletChannel channel, Rect row, int mouseX, int mouseY,
                                  float fade, long now) {
        TabletCanvas c = frameCanvas;
        SwitchState state = switchState(channel, snapshot);
        Accent accent = TabletTheme.accentFor(channel);
        boolean hovered = state == SwitchState.JOIN && row.contains(mouseX, mouseY);
        if (state == SwitchState.CURRENT) {
            c.roundRect(row.x(), row.y(), row.width(), row.height(), 8f,
                    TabletTheme.multiplyAlpha(TabletTheme.withAlpha(accent.base(), 0x2E), fade));
        } else if (hovered) {
            c.roundRect(row.x(), row.y(), row.width(), row.height(), 8f, 0x0FFFFFFF);
        }

        int badgeX = row.x() + 6;
        int badgeY = row.centerY() - SWITCHER_BADGE / 2;
        if (state == SwitchState.LOCKED) {
            c.roundRect(badgeX, badgeY, SWITCHER_BADGE, SWITCHER_BADGE, 6f, TabletTheme.LOCKED_BADGE);
            TabletIcons.lock(c, badgeX + 5f, badgeY + 5f, 10f, TabletTheme.TEXT_3);
        } else {
            c.roundRectGradientV(badgeX, badgeY, SWITCHER_BADGE, SWITCHER_BADGE, 6f, accent.base(), accent.deep());
            c.textCentered(textRenderer, monogram(channel), badgeX + SWITCHER_BADGE / 2, badgeY + 6, TabletTheme.WHITE);
        }

        int chipRight = row.right() - 6;
        int maxChip = Math.max(0, row.width() / 2);
        int chipX = switch (state) {
            case CURRENT -> paintPill(chipRight, row.centerY(), maxChip, Text.translatable(KEY + "channel.current").getString(),
                    accent.pale(), TabletTheme.withAlpha(accent.base(), 0x40), 0, TabletIcons::check, accent.pale());
            case JOIN -> hovered
                    ? paintPill(chipRight, row.centerY(), maxChip, Text.translatable(KEY + "channel.join").getString(),
                    TabletTheme.WHITE, accent.base(), 0, null, 0)
                    : paintPill(chipRight, row.centerY(), maxChip, Text.translatable(KEY + "channel.join").getString(),
                    accent.base(), 0, TabletTheme.withAlpha(accent.base(), 0x99), null, 0);
            case PENDING -> paintPill(chipRight, row.centerY(), maxChip, Text.translatable(KEY + "channel.pending").getString(),
                    TabletTheme.TEXT_2, TabletTheme.FAINT, 0,
                    (canvas, x, y, size, color) -> TabletIcons.spinner(canvas, x + size / 2f, y + size / 2f, size / 2f, now, color),
                    accent.pale());
            case COOLDOWN -> paintPill(chipRight, row.centerY(), maxChip,
                    Text.translatable(KEY + "channel.cooldown", snapshot.channelSwitchCooldownSeconds()).getString(),
                    TabletTheme.TEXT_2, TabletTheme.FAINT, 0, TabletIcons::clock, TabletTheme.TEXT_2);
            case MEETING_LOCKED -> paintPill(chipRight, row.centerY(), maxChip,
                    Text.translatable(KEY + "channel.meeting").getString(),
                    TabletTheme.WARNING, TabletTheme.withAlpha(TabletTheme.WARNING, 0x2E), 0, null, 0);
            case LOCKED -> paintPill(chipRight, row.centerY(), maxChip, Text.translatable(KEY + "channel.locked").getString(),
                    TabletTheme.TEXT_3, 0x0AFFFFFF, 0, null, 0);
        };

        int nameColor = switch (state) {
            case CURRENT -> accent.pale();
            case JOIN -> TabletTheme.TEXT;
            case LOCKED -> TabletTheme.TEXT_3;
            default -> TabletTheme.TEXT_2;
        };
        int nameX = badgeX + SWITCHER_BADGE + 8;
        String name = Text.translatable(channel.translationKey()).getString();
        c.text(textRenderer, TabletCanvas.trim(textRenderer, name, Math.max(0, chipX - 6 - nameX)), nameX,
                row.centerY() - 4, nameColor);
    }

    // ============================================================================================ helpers

    private static Accent frameAccentFor(TabletSnapshot snapshot) {
        return monitorOnly(snapshot) ? TabletTheme.MONITOR : TabletTheme.accentFor(snapshot.channel());
    }

    /**
     * The door log is the holder's only content: no channel now and none to switch to (a plain Attendant).
     * 房门记录是持有者唯一的内容：当前无频道且没有可切换的频道（普通乘务员）。
     */
    private static boolean monitorOnly(TabletSnapshot snapshot) {
        return snapshot.localHasTablet()
                && snapshot.channel() == null
                && snapshot.allowedChannels().isEmpty()
                && snapshot.hasFeature(TabletFeature.DOOR_LOG);
    }

    /**
     * Door-log rows this client can show, newest first; empty without the feature. Rows of a kind this client does
     * not know (newer server) are skipped.
     * 本客户端可显示的房门记录行（最新在前）；无此功能时为空。跳过本客户端不认识的类型（更新的服务端）。
     */
    private static List<TabletSnapshot.DoorLogRow> doorLogRows(TabletSnapshot snapshot) {
        if (!snapshot.hasFeature(TabletFeature.DOOR_LOG) || snapshot.doorLog().isEmpty()) {
            return List.of();
        }
        List<TabletSnapshot.DoorLogRow> rows = snapshot.doorLog();
        for (TabletSnapshot.DoorLogRow row : rows) {
            if (row.kind() == null) {
                return rows.stream().filter(candidate -> candidate.kind() != null).toList();
            }
        }
        return rows;
    }

    private @Nullable UUID localUuid() {
        return client != null && client.player != null ? client.player.getUuid() : null;
    }

    /** Upper-cased first code point of the channel's short name. 频道简称的首个字符（大写）。 */
    private static String monogram(TabletChannel channel) {
        String name = Text.translatable(channel.shortTranslationKey()).getString();
        if (name.isEmpty()) {
            return "?";
        }
        return new String(Character.toChars(Character.toUpperCase(name.codePointAt(0))));
    }

    /**
     * Header/chip member count. Anonymous channels list only the viewer plus linked partners (server-filtered), so they
     * count partners rather than channel size. Members may include dead teammates, hence "connected", not "online".
     * 成员计数。匿名频道只列出观看者本人与已互认同伴（服务端过滤），因此计数显示互认人数而非频道人数。
     * 成员可能包含死亡队友，因此显示为“已接入”而非“在线”。
     */
    private static Text memberCount(TabletSnapshot snapshot) {
        if (isAnonymousChannel(snapshot)) {
            return Text.translatable(KEY + "channel.linked", linkedPartners(snapshot));
        }
        return Text.translatable(KEY + "channel.connected", snapshot.connections().size());
    }

    /**
     * The viewed channel hides other members until this viewer completes their tasks (police); the server then sent
     * only the viewer's own member row and sender-less chat rows for everyone else.
     * 当前频道在查看者完成任务前隐藏其他成员（义警）；此时服务端只下发查看者自己的成员行，其他人的聊天行不带发送者。
     */
    private static boolean identitiesLocked(TabletSnapshot snapshot) {
        TabletChannel channel = snapshot.channel();
        return channel != null && channel.revealsAfterTasks() && snapshot.identityTasksRemaining() > 0;
    }

    private static int identityTasksDone(TabletSnapshot snapshot) {
        return Math.max(0, TabletIdentityRules.POLICE_REVEAL_TASKS - snapshot.identityTasksRemaining());
    }

    private static boolean isAnonymousChannel(TabletSnapshot snapshot) {
        TabletChannel channel = snapshot.channel();
        return channel != null && channel.anonymousSenders();
    }

    private static int linkedPartners(TabletSnapshot snapshot) {
        // The viewer is always a member of their own selected channel. 观看者总是其所选频道的成员。
        return Math.max(0, snapshot.connections().size() - 1);
    }

    private static boolean hasMeetingFeatures(TabletSnapshot snapshot) {
        TabletChannel channel = snapshot.channel();
        return channel != null && channel.hasMeetingFeatures();
    }

    private static boolean canCallMeeting(TabletSnapshot snapshot) {
        return snapshot.localMeetingParticipant()
                && snapshot.cooldownSeconds() <= 0
                && snapshot.localMeetingCallsRemaining() > 0;
    }

    // ============================================================================================ types

    private enum SwitchState {
        CURRENT,
        JOIN,
        PENDING,
        COOLDOWN,
        MEETING_LOCKED,
        LOCKED
    }

    @FunctionalInterface
    private interface Glyph {
        void paint(TabletCanvas canvas, float x, float y, float size, int color);
    }

    /** {@code lit}: a live channel or the door monitor (accent dot with glow, bright name). 已接入频道或房门监控（发光圆点、亮色名字）。 */
    private record ChannelChip(Rect rect, String name, boolean clickable, boolean lit) {
    }

    private record Composer(Rect pill, Rect send) {
    }

    private record ActionBar(
            Rect abstain,
            Text abstainLabel,
            ButtonStyle abstainStyle,
            Rect confirm,
            Text confirmLabel,
            ButtonStyle confirmStyle,
            ButtonIcon confirmIcon
    ) {
    }

    private record SwitcherHint(Text text, boolean warning) {
    }

    private record SwitcherGeometry(
            Rect box,
            int caretX,
            List<TabletChannel> channels,
            List<Rect> rows,
            List<String> hintLines,
            boolean hintWarning,
            int hintY
    ) {
    }

    /**
     * Card grid inside a list area; a final row counts as visible when its card (not its trailing gap) fits.
     * 列表区域内的卡片网格；最后一行只要卡片本身放得下（不含行间距）即计为可见。
     */
    private record Grid(Rect area, int columns, int cellWidth, int rowStep, int visibleRows) {
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
