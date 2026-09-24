package annina.sparkstrength.client.screen.tablet;

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
import annina.sparkstrength.tablet.TabletLayout;
import annina.sparkstrength.tablet.TabletRules;
import annina.sparkstrength.tablet.TabletUiSession;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Tablet UI. Every view is driven by the server-redacted snapshot; the client never infers channels from roles.
 * 平板界面。所有视图都由服务端裁剪后的快照驱动；客户端从不根据身份推断频道。
 */
public final class TabletScreen extends Screen {
    private static final DateTimeFormatter CLOCK_FORMAT = DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM);
    private static final int ROW_HEIGHT = 24;
    private static final int COLOR_SHELL = 0xF20A0D10;
    private static final int COLOR_SHELL_SHADOW = 0x8A000000;
    private static final int COLOR_METAL_EDGE = 0xFFA87A43;
    private static final int COLOR_METAL_DARK = 0xFF4B3928;
    private static final int COLOR_STATUS = 0xFF151D24;
    private static final int COLOR_NAVIGATION = 0xFF10161C;
    private static final int COLOR_BODY = 0xF20C1217;
    private static final int COLOR_FOOTER = 0xFF111920;
    private static final int COLOR_DIVIDER = 0xFF2A3944;
    private static final int COLOR_TEXT = 0xFFEAF2F6;
    private static final int COLOR_MUTED = 0xFF94A1A9;
    private static final int COLOR_ROW = 0xE6162027;
    private static final int COLOR_ROW_ALT = 0xD719252D;
    private static final int COLOR_GREEN = 0xFF46D878;
    private static final int COLOR_ORANGE = 0xFFFF8C00;
    private static final int COLOR_ORANGE_PALE = 0xFFFFC062;
    private static final int COLOR_INFO_BOX = 0xA1121A20;
    private static final int COLOR_INFO_BOX_HOVER = 0xC61A252D;
    private static final int COLOR_WELL = 0xFF080C0F;
    private static final int COLOR_LOCKED = 0xFF3C464D;
    private static final int COLOR_LOCKED_TEXT = 0xFF66727A;
    private static final int COLOR_LOCKED_ROW = 0xB00D1216;
    private static final int COLOR_OVERLAY = 0xF7111920;
    private static final int COLOR_NO_SIGNAL_CROSS = 0xFFD0675C;

    // Police keeps the original signal palette byte-for-byte; other channels get their own family.
    // 义警频道完全沿用原有信号配色；其它频道使用各自的色系。
    private static final Accent POLICE_ACCENT = new Accent(0xFF3AA8E8, 0xFF9EDFFF, 0x553087B8, COLOR_ORANGE_PALE);
    private static final Accent KILLER_ACCENT = new Accent(0xFFE0504A, 0xFFFFB0A8, 0x55B8352F, 0xFFFFB0A8);
    private static final Accent WITCH_ACCENT = new Accent(0xFFB06EE6, 0xFFD6ABFF, 0x558A46C2, 0xFFD6ABFF);
    private static final Accent NO_CHANNEL_ACCENT = new Accent(0xFF6F7C85, 0xFFB4BEC5, 0x40606A72, COLOR_MUTED);

    private static final int PILL_HEIGHT = 14;
    private static final int PILL_MAX_WIDTH = 78;
    // Left pad, dot, gap, gap, caret, right pad around the label. / 标签两侧的内边距、圆点、间隔与下拉箭头。
    private static final int CHANNEL_PILL_CHROME = 27;
    private static final int SWITCHER_WIDTH = 176;
    private static final int SWITCHER_HEADER_HEIGHT = 22;
    private static final int SWITCHER_ROW_HEIGHT = 22;
    private static final int SWITCHER_ROW_GAP = 2;
    private static final int SWITCHER_HINT_HEIGHT = 14;
    private static final int SWITCHER_Z = 200;
    private static final int PENDING_SWITCH_TICKS = 40;

    private final TabletUiSession session = new TabletUiSession(TabletClientState.chatDraft());
    private TabletLayout layout;
    private List<TabletUiSession.Section> visibleSections = List.of();
    private TextFieldWidget chatField;
    private int snapshotRequestTicks;
    private boolean requestedInitialSnapshot;
    private int lastChannelWire;
    private boolean lastCanSend;
    private boolean switcherOpen;
    private int pendingChannelWire = TabletChannel.NO_CHANNEL_WIRE;
    private int pendingSwitchTicks;

    public TabletScreen() {
        super(Text.translatable("screen.sparkstrength.tablet.title"));
        TabletSnapshot snapshot = TabletClientState.snapshot();
        lastChannelWire = snapshot.channelWire();
        lastCanSend = snapshot.canSend();
    }

    @Override
    protected void init() {
        super.init();
        TabletSnapshot snapshot = TabletClientState.snapshot();
        visibleSections = TabletUiSession.visibleSections(snapshot.channel());
        session.ensureVisible(visibleSections);
        layout = TabletLayout.forViewport(width, height, visibleSections.size());
        chatField = null;

        if (!requestedInitialSnapshot) {
            requestedInitialSnapshot = true;
            requestSnapshot();
        }

        initCloseButton();
        if (visibleSections.isEmpty()) {
            return;
        }
        initNavigation();
        if (session.section() == TabletUiSession.Section.CHAT) {
            initChat();
        } else if (session.section() == TabletUiSession.Section.MEETING && hasMeetingFeatures()) {
            initMeeting();
        } else if (session.section() == TabletUiSession.Section.SUSPECTS && hasMeetingFeatures()) {
            initSuspects();
        }
    }

    public void refresh() {
        if (client == null) {
            return;
        }
        rememberChatDraft();
        // Drop focus before removing widgets (as Screen.clearAndInit does); otherwise a removed chat field keeps
        // receiving keystrokes when the rebuild creates no replacement (read-only holder, no channel).
        // 移除控件前先清除焦点（与 Screen.clearAndInit 一致）；否则重建时若未创建新输入框（只读持有者、无频道），
        // 已移除的旧输入框仍会接收按键输入。
        setFocused(null);
        chatField = null;
        clearChildren();
        init();
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

        TabletSnapshot.Meeting meeting = snapshot.meeting();
        session.applyMeetingSnapshot(
                meeting.active(),
                meeting.targets().size(),
                visibleRows()
        );
        session.applySuspectSnapshot(
                snapshot.suspects().size(),
                visibleRows()
        );
        List<TabletUiSession.Section> nextSections = TabletUiSession.visibleSections(snapshot.channel());
        boolean sectionChanged = session.ensureVisible(nextSections);
        if (channelChanged
                || sendChanged
                || sectionChanged
                || !nextSections.equals(visibleSections)
                || session.section() == TabletUiSession.Section.MEETING
                || session.section() == TabletUiSession.Section.SUSPECTS) {
            refresh();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (pendingSwitchTicks > 0 && --pendingSwitchTicks == 0) {
            pendingChannelWire = TabletChannel.NO_CHANNEL_WIRE;
        }
        snapshotRequestTicks++;
        if (snapshotRequestTicks >= 20) {
            snapshotRequestTicks = 0;
            requestSnapshot();
        }
    }

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
        if (session.section() == TabletUiSession.Section.CHAT
                && !visibleSections.isEmpty()
                && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            sendChat();
            return true;
        }
        boolean handled = super.keyPressed(keyCode, scanCode, modifiers);
        if (session.section() == TabletUiSession.Section.CHAT
                && chatField != null
                && chatField.isFocused()
                && isMovementKey(keyCode, scanCode)) {
            return true;
        }
        return handled;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (switcherOpen) {
            // Text never reaches a field hidden behind the switcher overlay.
            // 文字输入不会传到切换浮层下方的输入框。
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        TabletSnapshot snapshot = TabletClientState.snapshot();
        if (switcherOpen) {
            SwitcherLayout switcher = switcherLayout(renderer, snapshot);
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                for (int index = 0; index < switcher.rows().size(); index++) {
                    TabletChannel channel = switcher.channels().get(index);
                    if (switcher.rows().get(index).contains(mouseX, mouseY)
                            && switchState(channel, snapshot) == SwitchState.JOIN) {
                        requestSwitch(channel);
                        return true;
                    }
                }
            }
            // Clicks never fall through the overlay; anything outside it just dismisses it.
            // 点击不会穿透浮层；点击浮层外部仅关闭浮层。
            if (!switcher.box().contains(mouseX, mouseY)) {
                closeSwitcher();
            }
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && snapshot.localHasTablet()
                && (channelPillRect(renderer, snapshot).contains(mouseX, mouseY)
                || layout.channelCard().contains(mouseX, mouseY))) {
            openSwitcher();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (switcherOpen) {
            return true;
        }
        TabletSnapshot snapshot = TabletClientState.snapshot();
        if (session.section() == TabletUiSession.Section.MEETING
                && hasMeetingFeatures()
                && snapshot.meeting().active()
                && layout.list().contains(mouseX, mouseY)) {
            int previousFirstRow = session.meetingFirstRow();
            session.scrollMeeting(
                    verticalAmount,
                    snapshot.meeting().targets().size(),
                    layout.visibleRows()
            );
            if (session.meetingFirstRow() != previousFirstRow) {
                refresh();
            }
            return true;
        }
        if (session.section() == TabletUiSession.Section.SUSPECTS
                && hasMeetingFeatures()
                && layout.list().contains(mouseX, mouseY)) {
            int previousFirstRow = session.suspectFirstRow();
            session.scrollSuspects(
                    verticalAmount,
                    snapshot.suspects().size(),
                    layout.visibleRows()
            );
            if (session.suspectFirstRow() != previousFirstRow) {
                refresh();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xB8000000);
        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        TabletSnapshot snapshot = TabletClientState.snapshot();
        Accent accent = accentFor(snapshot.channel());
        // While the switcher is open nothing underneath reacts to hover.
        // 切换浮层打开时，下层元素不响应悬停。
        int hoverX = switcherOpen ? -1 : mouseX;
        int hoverY = switcherOpen ? -1 : mouseY;
        drawTabletFrame(context, renderer, snapshot, accent, hoverX, hoverY);

        if (visibleSections.isEmpty()) {
            renderNoSignal(context, renderer, snapshot);
        } else if (session.section() == TabletUiSession.Section.CONNECTIONS) {
            renderConnections(context, renderer, snapshot, accent);
        } else if (session.section() == TabletUiSession.Section.CHAT) {
            renderChat(context, renderer, snapshot, accent);
        } else if (session.section() == TabletUiSession.Section.MEETING && hasMeetingFeatures()) {
            renderMeeting(context, renderer, snapshot, accent);
        } else if (session.section() == TabletUiSession.Section.SUSPECTS && hasMeetingFeatures()) {
            renderSuspects(context, renderer, snapshot, accent);
        }

        super.render(context, hoverX, hoverY, delta);
        if (switcherOpen) {
            renderSwitcher(context, renderer, snapshot, accent, mouseX, mouseY);
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Keep vanilla blur behind the tablet, not over custom content.
        // 将原版背景模糊层留在平板后方，避免盖住自绘内容。
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void initCloseButton() {
        TabletLayout.Rect close = layout.closeButton();
        addDrawableChild(ButtonWidget.builder(Text.literal("×"), button -> close())
                .dimensions(close.x(), close.y(), close.width(), close.height())
                .build());
    }

    private void initNavigation() {
        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        int count = Math.min(visibleSections.size(), layout.tabs().size());
        for (int index = 0; index < count; index++) {
            TabletUiSession.Section section = visibleSections.get(index);
            TabletLayout.Rect tab = layout.tabs().get(index);
            Text label = Text.translatable(section.translationKey());
            Text visibleLabel = Text.literal(trim(renderer, label.getString(), Math.max(0, tab.width() - 10)));
            ButtonWidget button = ButtonWidget.builder(visibleLabel, ignored -> {
                        rememberChatDraft();
                        session.select(section);
                        refresh();
                    })
                    .dimensions(tab.x(), tab.y(), tab.width(), tab.height())
                    .build();
            button.active = section != session.section();
            addDrawableChild(button);
        }
    }

    private void initChat() {
        TabletSnapshot snapshot = TabletClientState.snapshot();
        ChatFooter footer = chatFooter();
        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        Text sendLabel = Text.translatable("screen.sparkstrength.tablet.chat.send");
        ButtonWidget send = ButtonWidget.builder(
                        Text.literal(trim(renderer, sendLabel.getString(), footer.sendWidth() - 8)),
                        button -> sendChat())
                .dimensions(footer.fieldX() + footer.fieldWidth() + 6, footer.inputY(), footer.sendWidth(), 20)
                .build();

        if (!snapshot.canSend() || snapshot.channel() == null) {
            // Read-only holders (dead) get a drawn read-only well instead of an input; see renderChat.
            // 只读持有者（死亡）不创建输入框，而是绘制只读栏；见 renderChat。
            send.active = false;
            addDrawableChild(send);
            return;
        }

        Text placeholder = Text.translatable(
                "screen.sparkstrength.tablet.chat.placeholder_channel",
                Text.translatable(snapshot.channel().translationKey())
        );
        TextFieldWidget field = new TextFieldWidget(
                renderer,
                footer.fieldX(),
                footer.inputY(),
                footer.fieldWidth(),
                20,
                placeholder
        );
        field.setMaxLength(TabletRules.CHAT_MESSAGE_MAX_LENGTH);
        field.setText(session.draft());
        // Vanilla hides placeholders on focused fields, so the "send to <channel>" cue uses the grey suggestion slot,
        // which stays visible while the empty field is focused.
        // 原版输入框获得焦点时不显示占位符，因此“发送至某频道”提示使用灰色建议文本，在空输入框聚焦时仍可见。
        String channelHint = placeholder.getString();
        field.setSuggestion(session.draft().isEmpty() ? channelHint : null);
        field.setChangedListener(value -> {
            session.updateDraft(value);
            TabletClientState.setChatDraft(value);
            field.setSuggestion(value.isEmpty() ? channelHint : null);
        });
        chatField = field;
        addDrawableChild(field);
        if (!switcherOpen) {
            setInitialFocus(field);
        }
        addDrawableChild(send);
    }

    private void initMeeting() {
        TabletSnapshot snapshot = TabletClientState.snapshot();
        TabletLayout.Rect footer = layout.footer();
        int controlX = footer.x() + 10;
        int controlY = footer.y() + Math.max(0, (footer.height() - 20) / 2);
        session.applyMeetingSnapshot(
                snapshot.meeting().active(),
                snapshot.meeting().targets().size(),
                layout.visibleRows()
        );

        if (!snapshot.meeting().active()) {
            ButtonWidget call = ButtonWidget.builder(
                            Text.translatable("screen.sparkstrength.tablet.meeting.call"),
                            button -> ClientPlayNetworking.send(new CallTabletMeetingC2SPacket()))
                    .dimensions(controlX, controlY, Math.min(130, Math.max(1, footer.width() - 20)), 20)
                    .build();
            call.active = canCallMeeting(snapshot);
            addDrawableChild(call);
            return;
        }

        ButtonWidget abstain = ButtonWidget.builder(
                        Text.translatable("screen.sparkstrength.tablet.meeting.abstain"),
                        button -> ClientPlayNetworking.send(new CastTabletVoteC2SPacket(null)))
                .dimensions(controlX, controlY, 74, 20)
                .build();
        abstain.active = snapshot.localMeetingParticipant() && !snapshot.meeting().localConfirmed();
        addDrawableChild(abstain);
        ButtonWidget confirm = ButtonWidget.builder(Text.translatable(
                        snapshot.meeting().localConfirmed()
                                ? "screen.sparkstrength.tablet.meeting.locked"
                                : "screen.sparkstrength.tablet.meeting.confirm"
                ), button -> ClientPlayNetworking.send(new ConfirmTabletVoteC2SPacket()))
                .dimensions(controlX + 82, controlY, 74, 20)
                .build();
        confirm.active = snapshot.localMeetingParticipant() && !snapshot.meeting().localConfirmed();
        addDrawableChild(confirm);

        var targets = snapshot.meeting().targets();
        int lastRow = Math.min(session.meetingFirstRow() + layout.visibleRows(), targets.size());
        int rowY = layout.list().y();
        int voteButtonX = layout.list().right() - 68;
        for (int index = session.meetingFirstRow(); index < lastRow; index++) {
            TabletSnapshot.VoteTarget target = targets.get(index);
            ButtonWidget vote = ButtonWidget.builder(
                            Text.translatable("screen.sparkstrength.tablet.meeting.vote"),
                            button -> ClientPlayNetworking.send(new CastTabletVoteC2SPacket(target.uuid())))
                    .dimensions(voteButtonX, rowY + 2, 64, 18)
                    .build();
            vote.active = snapshot.localMeetingParticipant()
                    && target.selectable()
                    && !snapshot.meeting().localConfirmed();
            addDrawableChild(vote);
            rowY += ROW_HEIGHT;
        }
    }

    private void initSuspects() {
        TabletSnapshot snapshot = TabletClientState.snapshot();
        int rowY = layout.list().y();
        int buttonX = layout.list().right() - 82;
        int lastRow = Math.min(session.suspectFirstRow() + layout.visibleRows(), snapshot.suspects().size());
        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        for (int index = session.suspectFirstRow(); index < lastRow; index++) {
            TabletSnapshot.SuspectRow suspect = snapshot.suspects().get(index);
            Text label = Text.translatable(suspect.localApproved()
                    ? "screen.sparkstrength.tablet.suspects.cancel"
                    : "screen.sparkstrength.tablet.suspects.approve");
            ButtonWidget button = ButtonWidget.builder(
                            Text.literal(trim(renderer, label.getString(), 70)),
                            ignored -> ClientPlayNetworking.send(new ApproveSuspectRemovalC2SPacket(
                                    suspect.uuid(),
                                    !suspect.localApproved()
                            )))
                    .dimensions(buttonX, rowY + 2, 78, 18)
                    .build();
            button.active = snapshot.localMeetingParticipant();
            addDrawableChild(button);
            rowY += ROW_HEIGHT;
        }
    }

    private void renderConnections(DrawContext context, TextRenderer renderer, TabletSnapshot snapshot, Accent accent) {
        drawSectionHeader(context, renderer, memberCount(snapshot).getString(), accent);
        if (snapshot.connections().isEmpty()) {
            drawEmptyState(context, renderer, Text.translatable("screen.sparkstrength.tablet.connections.empty"));
            return;
        }

        TabletLayout.Rect list = layout.list();
        context.enableScissor(list.x(), list.y(), list.right(), list.bottom());
        int rows = Math.min(layout.visibleRows(), snapshot.connections().size());
        int y = list.y();
        for (int index = 0; index < rows; index++) {
            TabletSnapshot.PlayerRow row = snapshot.connections().get(index);
            drawPlayerRow(
                    context,
                    renderer,
                    row.uuid(),
                    row.name(),
                    row.inGame() ? COLOR_GREEN : 0xFF6F787D,
                    list.x(),
                    y,
                    list.width(),
                    92
            );
            drawPill(
                    context,
                    renderer,
                    Text.translatable(row.inGame()
                            ? "screen.sparkstrength.tablet.status.ingame"
                            : "screen.sparkstrength.tablet.status.outside"),
                    list.right() - 6,
                    y + 4,
                    row.inGame() ? COLOR_GREEN : COLOR_MUTED,
                    row.inGame() ? 0x2736D36B : 0x24777777
            );
            y += ROW_HEIGHT;
        }
        if (snapshot.canSend() && isAnonymousChannel(snapshot) && linkedPartners(snapshot) == 0) {
            drawLinkHint(context, renderer, accent, y);
        }
        context.disableScissor();
    }

    private void renderChat(DrawContext context, TextRenderer renderer, TabletSnapshot snapshot, Accent accent) {
        if (!snapshot.canSend()) {
            drawReadOnlyWell(context, renderer);
        }
        var messages = snapshot.chat();
        drawSectionHeader(context, renderer, String.valueOf(messages.size()), accent);
        if (messages.isEmpty()) {
            drawEmptyState(context, renderer, Text.translatable("screen.sparkstrength.tablet.chat.empty"));
            return;
        }

        TabletLayout.Rect list = layout.list();
        context.enableScissor(list.x(), list.y(), list.right(), list.bottom());
        int rows = Math.min(layout.visibleRows(), messages.size());
        int start = messages.size() - rows;
        int y = list.y();
        for (int index = start; index < messages.size(); index++) {
            TabletSnapshot.ChatRow row = messages.get(index);
            context.fill(list.x(), y, list.right(), y + ROW_HEIGHT - 3, index % 2 == 0 ? COLOR_ROW : COLOR_ROW_ALT);
            context.fill(list.x(), y, list.x() + 3, y + ROW_HEIGHT - 3, accent.signal());
            // Anonymous rows arrive without sender uuid/name (redacted server-side), so there is nothing to reveal here.
            // 匿名行到达时已无发送者 UUID 与名字（服务端已脱敏），客户端无从还原。
            String senderName = row.isAnonymous()
                    ? Text.translatable("screen.sparkstrength.tablet.chat.anonymous").getString()
                    : row.senderName();
            String sender = trim(renderer, senderName, Math.min(78, Math.max(0, list.width() / 3)));
            int senderWidth = Math.min(82, renderer.getWidth(sender) + 8);
            context.drawText(renderer, sender, list.x() + 9, y + 7, row.isAnonymous() ? COLOR_MUTED : accent.pale(), false);
            context.drawText(
                    renderer,
                    trim(renderer, row.message(), Math.max(0, list.width() - senderWidth - 20)),
                    list.x() + 9 + senderWidth,
                    y + 7,
                    COLOR_TEXT,
                    false
            );
            y += ROW_HEIGHT;
        }
        context.disableScissor();
    }

    private void renderMeeting(DrawContext context, TextRenderer renderer, TabletSnapshot snapshot, Accent accent) {
        String detail = snapshot.meeting().active()
                ? Text.translatable("screen.sparkstrength.tablet.meeting.timer", snapshot.meeting().remainingSeconds()).getString()
                : Text.translatable("screen.sparkstrength.tablet.meeting.chances", snapshot.localMeetingCallsRemaining()).getString();
        drawSectionHeader(context, renderer, detail, accent);
        if (!snapshot.meeting().active()) {
            if (snapshot.cooldownSeconds() > 0) {
                drawInfoLine(context, renderer, Text.translatable(
                        "screen.sparkstrength.tablet.meeting.cooldown",
                        snapshot.cooldownSeconds()
                ), COLOR_MUTED);
            } else if (canCallMeeting(snapshot)) {
                drawInfoLine(context, renderer, Text.translatable("screen.sparkstrength.tablet.meeting.ready"), accent.pale());
            } else {
                drawInfoLine(context, renderer, Text.translatable("screen.sparkstrength.tablet.meeting.disabled"), COLOR_MUTED);
            }
            return;
        }

        var targets = snapshot.meeting().targets();
        TabletLayout.Rect list = layout.list();
        context.enableScissor(list.x(), list.y(), list.right(), list.bottom());
        int lastRow = Math.min(session.meetingFirstRow() + layout.visibleRows(), targets.size());
        int y = list.y();
        int voteButtonX = list.right() - 68;
        int votesX = voteButtonX - 28;
        for (int index = session.meetingFirstRow(); index < lastRow; index++) {
            TabletSnapshot.VoteTarget target = targets.get(index);
            boolean selected = target.uuid().equals(snapshot.meeting().localVoteTarget());
            drawPlayerRow(
                    context,
                    renderer,
                    target.uuid(),
                    target.name(),
                    target.selectable() ? accent.signal() : 0xFF626C72,
                    list.x(),
                    y,
                    list.width(),
                    118
            );
            if (selected) {
                context.drawBorder(list.x(), y + 2, list.width(), ROW_HEIGHT - 3, accent.signal());
                context.fill(list.x(), y + 2, list.x() + 4, y + ROW_HEIGHT - 1, accent.signal());
            }
            context.drawText(renderer, String.valueOf(target.votes()), votesX, y + 7, COLOR_TEXT, false);
            y += ROW_HEIGHT;
        }
        context.disableScissor();
    }

    private void renderSuspects(DrawContext context, TextRenderer renderer, TabletSnapshot snapshot, Accent accent) {
        drawSectionHeader(context, renderer, String.valueOf(snapshot.suspects().size()), accent);
        if (snapshot.suspects().isEmpty()) {
            drawEmptyState(context, renderer, Text.translatable("screen.sparkstrength.tablet.suspects.none"));
            return;
        }

        TabletLayout.Rect list = layout.list();
        context.enableScissor(list.x(), list.y(), list.right(), list.bottom());
        int lastRow = Math.min(session.suspectFirstRow() + layout.visibleRows(), snapshot.suspects().size());
        int y = list.y();
        int buttonX = list.right() - 82;
        for (int index = session.suspectFirstRow(); index < lastRow; index++) {
            TabletSnapshot.SuspectRow suspect = snapshot.suspects().get(index);
            drawPlayerRow(context, renderer, suspect.uuid(), suspect.name(), COLOR_ORANGE, list.x(), y, list.width(), 158);
            Text votes = Text.translatable(
                    "screen.sparkstrength.tablet.suspects.votes",
                    suspect.approvals(),
                    suspect.requiredApprovals()
            );
            String visibleVotes = trim(renderer, votes.getString(), 70);
            context.drawText(
                    renderer,
                    visibleVotes,
                    buttonX - 8 - renderer.getWidth(visibleVotes),
                    y + 7,
                    COLOR_ORANGE_PALE,
                    false
            );
            y += ROW_HEIGHT;
        }
        context.disableScissor();
    }

    /**
     * Body for holders without a channel (or before the first snapshot lands): a centred card, no tabs.
     * 无频道持有者（或首个快照到达前）的主体：居中卡片，不显示标签页。
     */
    private void renderNoSignal(DrawContext context, TextRenderer renderer, TabletSnapshot snapshot) {
        TabletLayout.Rect body = layout.body();
        boolean connecting = !snapshot.localHasTablet();
        String title = Text.translatable(connecting
                        ? "screen.sparkstrength.tablet.no_signal.connecting"
                        : "screen.sparkstrength.tablet.channel.none")
                .getString()
                .toUpperCase(Locale.ROOT);
        int maxTextWidth = Math.max(40, Math.min(240, body.width() - 48));
        List<OrderedText> lines = connecting
                ? List.of()
                : renderer.wrapLines(Text.translatable("screen.sparkstrength.tablet.no_signal.body"), maxTextWidth);
        if (lines.size() > 3) {
            lines = lines.subList(0, 3);
        }

        int glyphWidth = 26;
        int contentWidth = Math.max(glyphWidth, renderer.getWidth(title));
        for (OrderedText line : lines) {
            contentWidth = Math.max(contentWidth, renderer.getWidth(line));
        }
        int contentHeight = 13 + 7 + 9 + (lines.isEmpty() ? 0 : 5 + lines.size() * 10 - 1);
        int boxWidth = Math.min(Math.max(0, body.width() - 24), contentWidth + 32);
        int boxHeight = Math.min(Math.max(0, body.height() - 12), contentHeight + 22);
        // Centre between the signal spine (body.x + 6) and the right edge. / 在信号脊线与右边缘之间居中。
        int centerX = (body.x() + 6 + body.right()) / 2;
        int boxX = centerX - boxWidth / 2;
        int boxY = body.y() + Math.max(0, (body.height() - boxHeight) / 2);
        context.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, COLOR_INFO_BOX);
        context.drawBorder(boxX, boxY, boxWidth, boxHeight, COLOR_DIVIDER);

        int y = boxY + Math.max(4, (boxHeight - contentHeight) / 2);
        int glyphX = centerX - glyphWidth / 2;
        int glyphBottom = y + 13;
        int litBars = connecting ? (int) ((Util.getMeasuringTimeMs() / 220L) % 5L) : 0;
        int[] heights = {4, 7, 10, 13};
        for (int index = 0; index < heights.length; index++) {
            int barX = glyphX + index * 5;
            int color = index < litBars ? NO_CHANNEL_ACCENT.pale() : COLOR_LOCKED;
            context.fill(barX, glyphBottom - heights[index], barX + 3, glyphBottom, color);
        }
        if (!connecting) {
            drawCross(context, glyphX + 21, glyphBottom - 13, COLOR_NO_SIGNAL_CROSS);
        }

        y = glyphBottom + 7;
        context.drawText(renderer, title, centerX - renderer.getWidth(title) / 2, y, COLOR_TEXT, false);
        y += 9 + 5;
        for (OrderedText line : lines) {
            context.drawText(renderer, line, centerX - renderer.getWidth(line) / 2, y, COLOR_MUTED, false);
            y += 10;
        }
    }

    private void drawPlayerRow(
            DrawContext context,
            TextRenderer renderer,
            UUID uuid,
            String name,
            int borderColor,
            int x,
            int y,
            int width,
            int reservedRightWidth
    ) {
        context.fill(x, y, x + width, y + ROW_HEIGHT - 3, COLOR_ROW);
        context.fill(x, y + 2, x + 4, y + ROW_HEIGHT - 1, borderColor);
        context.fill(x + 8, y + 3, x + 28, y + 23, borderColor);
        context.fill(x + 10, y + 5, x + 26, y + 21, 0xFF05080B);
        TabletPlayerRow.drawAvatar(context, uuid, name, x + 10, y + 5);
        context.drawText(
                renderer,
                trim(renderer, name, Math.max(0, width - reservedRightWidth - 42)),
                x + 36,
                y + 7,
                COLOR_TEXT,
                false
        );
    }

    private void drawTabletFrame(
            DrawContext context,
            TextRenderer renderer,
            TabletSnapshot snapshot,
            Accent accent,
            int mouseX,
            int mouseY
    ) {
        TabletLayout.Rect panel = layout.panel();
        TabletLayout.Rect status = layout.statusBar();
        TabletLayout.Rect navigation = layout.navigation();
        TabletLayout.Rect body = layout.body();
        TabletLayout.Rect footer = layout.footer();

        context.fill(panel.x() - 4, panel.y() - 4, panel.right() + 4, panel.bottom() + 4, COLOR_SHELL_SHADOW);
        context.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), COLOR_SHELL);
        context.fill(status.x() + 3, status.y() + 3, status.right() - 3, status.bottom(), COLOR_STATUS);
        context.fill(navigation.x() + 3, navigation.y(), navigation.right() - 3, navigation.bottom(), COLOR_NAVIGATION);
        context.fill(body.x(), body.y(), body.right(), body.bottom(), COLOR_BODY);
        context.fill(footer.x(), footer.y(), footer.right(), footer.bottom() - 3, COLOR_FOOTER);
        context.fill(footer.x(), footer.y(), footer.right(), footer.y() + 1, COLOR_DIVIDER);

        if (layout.mode() == TabletLayout.Mode.WIDE) {
            context.fill(navigation.right() - 1, navigation.y(), navigation.right(), navigation.bottom(), COLOR_DIVIDER);
        } else {
            context.fill(navigation.x(), navigation.bottom() - 1, navigation.right(), navigation.bottom(), COLOR_DIVIDER);
        }
        if (layout.tabs().isEmpty()) {
            drawDeadAir(context);
        }

        drawStatus(context, renderer, snapshot, accent, mouseX, mouseY);
        drawSignalSpine(context, accent);
        drawChannelCard(context, renderer, snapshot, accent, mouseX, mouseY);
        context.drawBorder(panel.x(), panel.y(), panel.width(), panel.height(), COLOR_METAL_EDGE);
        context.drawBorder(panel.x() + 2, panel.y() + 2, Math.max(0, panel.width() - 4), Math.max(0, panel.height() - 4), COLOR_METAL_DARK);
    }

    private void drawStatus(
            DrawContext context,
            TextRenderer renderer,
            TabletSnapshot snapshot,
            Accent accent,
            int mouseX,
            int mouseY
    ) {
        TabletLayout.Rect status = layout.statusBar();
        int glyphX = status.x() + 12;
        int glyphBottom = status.y() + 22;
        context.fill(glyphX, glyphBottom - 4, glyphX + 2, glyphBottom, accent.signal());
        context.fill(glyphX + 4, glyphBottom - 8, glyphX + 6, glyphBottom, accent.signal());
        context.fill(glyphX + 8, glyphBottom - 12, glyphX + 10, glyphBottom, accent.pale());

        String clock = LocalTime.now().format(CLOCK_FORMAT);
        int clockX = clockX(renderer, clock);
        context.drawText(renderer, clock, clockX, status.y() + 13, accent.pale(), false);

        TabletLayout.Rect pill = channelPillRect(renderer, snapshot, clockX);
        drawChannelPill(context, renderer, snapshot, accent, pill, switcherOpen || pill.contains(mouseX, mouseY));

        String titleValue = Text.translatable("screen.sparkstrength.tablet.title")
                .getString()
                .toUpperCase(Locale.ROOT);
        int titleX = glyphX + 18;
        context.drawText(
                renderer,
                trim(renderer, titleValue, Math.max(0, pill.x() - titleX - 8)),
                titleX,
                status.y() + 13,
                COLOR_TEXT,
                false
        );
    }

    /**
     * Status-bar channel pill: short channel name plus a caret; it is the switcher's anchor and hit target.
     * 状态栏频道胶囊：频道简称加下拉箭头；它是切换浮层的锚点与点击区域。
     */
    private void drawChannelPill(
            DrawContext context,
            TextRenderer renderer,
            TabletSnapshot snapshot,
            Accent accent,
            TabletLayout.Rect pill,
            boolean highlighted
    ) {
        if (pill.width() <= 0) {
            return;
        }
        String label = trim(renderer, channelShortLabel(snapshot), Math.max(0, pill.width() - CHANNEL_PILL_CHROME));
        context.fill(pill.x(), pill.y(), pill.right(), pill.bottom(), highlighted ? withAlpha(accent.soft(), 0x99) : accent.soft());
        context.drawBorder(pill.x(), pill.y(), pill.width(), pill.height(), highlighted ? accent.pale() : accent.signal());
        context.fill(pill.x() + 5, pill.y() + 5, pill.x() + 9, pill.y() + 9, snapshot.channel() == null ? COLOR_LOCKED : accent.pale());
        context.drawText(renderer, label, pill.x() + 13, pill.y() + 3, accent.pale(), false);
        drawCaret(context, pill.right() - 10, pill.y() + 6, switcherOpen, highlighted ? accent.pale() : accent.signal());
    }

    /**
     * WIDE-only rail card: full channel name and connected count, also opens the switcher.
     * 仅 WIDE 模式的侧栏卡片：显示完整频道名与接入人数，同样可以打开切换浮层。
     */
    private void drawChannelCard(
            DrawContext context,
            TextRenderer renderer,
            TabletSnapshot snapshot,
            Accent accent,
            int mouseX,
            int mouseY
    ) {
        TabletLayout.Rect card = layout.channelCard();
        if (card.width() <= 0 || card.height() <= 0) {
            return;
        }
        boolean hovered = switcherOpen || card.contains(mouseX, mouseY);
        TabletChannel channel = snapshot.channel();
        String caption = Text.translatable("screen.sparkstrength.tablet.channel.label").getString().toUpperCase(Locale.ROOT);
        context.drawText(renderer, trim(renderer, caption, card.width()), card.x(), card.y() - 11, COLOR_MUTED, false);
        context.fill(card.x(), card.y(), card.right(), card.bottom(), hovered ? COLOR_INFO_BOX_HOVER : COLOR_INFO_BOX);
        context.drawBorder(card.x(), card.y(), card.width(), card.height(), hovered ? accent.signal() : COLOR_DIVIDER);
        context.fill(card.x(), card.y(), card.x() + 3, card.bottom(), channel == null ? COLOR_LOCKED : accent.signal());

        int textX = card.x() + 9;
        int textWidth = Math.max(0, card.right() - 14 - textX);
        String name = channel == null
                ? Text.translatable("screen.sparkstrength.tablet.channel.none").getString()
                : Text.translatable(channel.translationKey()).getString();
        if (channel != null && renderer.getWidth(name) > textWidth) {
            name = Text.translatable(channel.shortTranslationKey()).getString();
        }
        int nameY = channel == null ? card.y() + (card.height() - 8) / 2 : card.y() + 9;
        context.drawText(renderer, trim(renderer, name, textWidth), textX, nameY, accent.pale(), false);
        if (channel != null) {
            String members = memberCount(snapshot).getString();
            context.drawText(renderer, trim(renderer, members, textWidth), textX, card.y() + 23, COLOR_MUTED, false);
        }
        drawCaret(context, card.right() - 11, card.y() + 18, switcherOpen, hovered ? accent.pale() : COLOR_MUTED);
    }

    private void drawSignalSpine(DrawContext context, Accent accent) {
        TabletLayout.Rect body = layout.body();
        TabletLayout.Rect footer = layout.footer();
        // Index into the visible tab list; ordinal() would point past the end once tabs are hidden.
        // 使用可见标签列表中的下标；隐藏标签后 ordinal() 会越界。
        int selectedIndex = visibleSections.indexOf(session.section());
        if (selectedIndex >= 0 && selectedIndex < layout.tabs().size()) {
            TabletLayout.Rect selectedTab = layout.tabs().get(selectedIndex);
            context.fill(
                    selectedTab.x() - 2,
                    selectedTab.y() - 2,
                    selectedTab.right() + 2,
                    selectedTab.bottom() + 2,
                    accent.soft()
            );

            if (layout.mode() == TabletLayout.Mode.WIDE) {
                int centerY = selectedTab.y() + selectedTab.height() / 2;
                context.fill(selectedTab.x() - 4, selectedTab.y() - 2, selectedTab.x() - 1, selectedTab.bottom() + 2, accent.signal());
                context.fill(selectedTab.right(), centerY - 1, body.x() + 5, centerY + 1, accent.signal());
            } else {
                int centerX = selectedTab.x() + selectedTab.width() / 2;
                context.fill(selectedTab.x(), selectedTab.bottom(), selectedTab.right(), selectedTab.bottom() + 2, accent.signal());
                context.fill(centerX - 1, selectedTab.bottom(), centerX + 1, layout.navigation().bottom(), accent.signal());
                fillHorizontal(context, centerX, body.x() + 5, layout.navigation().bottom() - 2, layout.navigation().bottom(), accent.signal());
            }
        }
        context.fill(body.x() + 3, body.y(), body.x() + 6, footer.y(), accent.signal());
    }

    /** Faint dashed line across an empty navigation strip (no-signal state). / 无信号时导航栏中的淡虚线。 */
    private void drawDeadAir(DrawContext context) {
        TabletLayout.Rect navigation = layout.navigation();
        if (layout.mode() == TabletLayout.Mode.WIDE) {
            return;
        }
        int centerY = navigation.y() + navigation.height() / 2;
        for (int x = navigation.x() + 14; x + 3 <= navigation.right() - 14; x += 7) {
            context.fill(x, centerY, x + 3, centerY + 1, COLOR_DIVIDER);
        }
    }

    private void drawSectionHeader(DrawContext context, TextRenderer renderer, String detail, Accent accent) {
        TabletLayout.Rect body = layout.body();
        Text title = Text.translatable(session.section().translationKey());
        int left = body.x() + 12;
        int right = body.right() - 12;
        String visibleDetail = trim(renderer, detail, Math.max(0, body.width() / 3));
        int detailWidth = renderer.getWidth(visibleDetail);
        String visibleTitle = trim(renderer, title.getString(), Math.max(0, right - left - detailWidth - 12));
        context.drawText(renderer, visibleTitle, left, body.y() + 10, COLOR_TEXT, false);
        if (!visibleDetail.isBlank()) {
            context.drawText(renderer, visibleDetail, right - detailWidth, body.y() + 10, accent.detail(), false);
        }
        context.fill(left, body.y() + 25, right, body.y() + 26, COLOR_DIVIDER);
        context.fill(left, body.y() + 25, Math.min(right, left + 24), body.y() + 26, accent.signal());
    }

    private void drawEmptyState(DrawContext context, TextRenderer renderer, Text text) {
        TabletLayout.Rect list = layout.list();
        int boxHeight = Math.min(42, list.height());
        context.fill(list.x(), list.y(), list.right(), list.y() + boxHeight, COLOR_INFO_BOX);
        context.drawBorder(list.x(), list.y(), list.width(), boxHeight, COLOR_DIVIDER);
        String value = trim(renderer, text.getString(), Math.max(0, list.width() - 24));
        context.drawText(renderer, value, list.x() + 12, list.y() + Math.max(4, (boxHeight - 8) / 2), COLOR_MUTED, false);
    }

    /**
     * How-to-link card drawn in the next row slot while an anonymous channel lists no linked partner currently holding a
     * tablet; only for viewers who can send (dead/read-only viewers cannot link). Lines that do not fit the list are
     * dropped and the last visible one is ellipsized.
     * 匿名频道当前没有持有平板的已互认同伴时，在下一行位置绘制的互认说明卡片；仅对可发言的观看者显示（死亡或只读观看者
     * 无法互认）。列表放不下的行会被省略，最后一行加省略号。
     */
    private void drawLinkHint(DrawContext context, TextRenderer renderer, Accent accent, int top) {
        TabletLayout.Rect list = layout.list();
        int glyphX = list.x() + 9;
        int textX = glyphX + 17;
        int textWidth = list.right() - 10 - textX;
        int maxLines = (list.bottom() - top - 10) / 10;
        if (textWidth < 40 || maxLines <= 0) {
            return;
        }
        String hint = Text.translatable("screen.sparkstrength.tablet.connections.link_hint").getString();
        List<String> lines = new ArrayList<>();
        for (StringVisitable line : renderer.getTextHandler().wrapLines(hint, textWidth, Style.EMPTY)) {
            lines.add(line.getString());
        }
        if (lines.size() > maxLines) {
            lines = new ArrayList<>(lines.subList(0, maxLines));
            lines.set(maxLines - 1, trim(renderer, lines.get(maxLines - 1) + "…", textWidth));
        }

        int boxHeight = lines.size() * 10 + 10;
        context.fill(list.x(), top, list.right(), top + boxHeight, COLOR_INFO_BOX);
        context.drawBorder(list.x(), top, list.width(), boxHeight, COLOR_DIVIDER);
        drawLinkGlyph(context, glyphX, top + 6, accent.signal());
        int y = top + 6;
        for (String line : lines) {
            context.drawText(renderer, line, textX, y, COLOR_MUTED, false);
            y += 10;
        }
    }

    private void drawInfoLine(DrawContext context, TextRenderer renderer, Text text, int color) {
        TabletLayout.Rect list = layout.list();
        int boxHeight = Math.min(30, list.height());
        context.fill(list.x(), list.y(), list.right(), list.y() + boxHeight, COLOR_INFO_BOX);
        context.drawText(
                renderer,
                trim(renderer, text.getString(), Math.max(0, list.width() - 24)),
                list.x() + 12,
                list.y() + Math.max(4, (boxHeight - 8) / 2),
                color,
                false
        );
    }

    /** Stand-in for the chat input when the holder may only read (dead). / 只读（死亡）时替代聊天输入框的只读栏。 */
    private void drawReadOnlyWell(DrawContext context, TextRenderer renderer) {
        ChatFooter footer = chatFooter();
        int x = footer.fieldX();
        int y = footer.inputY();
        int right = x + footer.fieldWidth();
        context.fill(x, y, right, y + 20, COLOR_WELL);
        context.drawBorder(x, y, footer.fieldWidth(), 20, COLOR_DIVIDER);
        drawLock(context, x + 6, y + 6, COLOR_MUTED);
        String hint = Text.translatable("screen.sparkstrength.tablet.chat.read_only").getString();
        context.drawText(renderer, trim(renderer, hint, Math.max(0, right - x - 24)), x + 18, y + 6, COLOR_MUTED, false);
    }

    private int drawPill(
            DrawContext context,
            TextRenderer renderer,
            Text text,
            int right,
            int y,
            int color,
            int backgroundColor
    ) {
        String value = trim(renderer, text.getString(), 70);
        int width = Math.min(PILL_MAX_WIDTH, renderer.getWidth(value) + 10);
        context.fill(right - width, y, right, y + PILL_HEIGHT, backgroundColor);
        context.drawBorder(right - width, y, width, PILL_HEIGHT, color);
        context.drawText(renderer, value, right - width + 5, y + 3, color, false);
        return right - width;
    }

    private void renderSwitcher(
            DrawContext context,
            TextRenderer renderer,
            TabletSnapshot snapshot,
            Accent accent,
            int mouseX,
            int mouseY
    ) {
        SwitcherLayout switcher = switcherLayout(renderer, snapshot);
        TabletLayout.Rect box = switcher.box();
        TabletLayout.Rect pill = channelPillRect(renderer, snapshot, clockX(renderer, LocalTime.now().format(CLOCK_FORMAT)));

        context.getMatrices().push();
        // Lift above vanilla widgets (buttons/text field) that were drawn first. / 抬高到先绘制的原版控件之上。
        context.getMatrices().translate(0.0F, 0.0F, SWITCHER_Z);
        context.fill(box.x() - 3, box.y() - 3, box.right() + 3, box.bottom() + 3, COLOR_SHELL_SHADOW);
        context.fill(box.x(), box.y(), box.right(), box.bottom(), COLOR_OVERLAY);
        context.drawBorder(box.x(), box.y(), box.width(), box.height(), COLOR_METAL_DARK);
        context.fill(box.x() + 1, box.y() + 1, box.right() - 1, box.y() + 3, accent.signal());
        int stemX = Math.max(box.x() + 4, Math.min(box.right() - 6, pill.x() + pill.width() / 2 - 1));
        context.fill(stemX, pill.bottom(), stemX + 2, box.y() + 1, accent.signal());

        String title = Text.translatable("screen.sparkstrength.tablet.channel.select").getString().toUpperCase(Locale.ROOT);
        context.drawText(renderer, trim(renderer, title, box.width() - 16), box.x() + 8, box.y() + 8, COLOR_MUTED, false);
        context.fill(box.x() + 8, box.y() + 19, box.right() - 8, box.y() + 20, COLOR_DIVIDER);

        for (int index = 0; index < switcher.rows().size(); index++) {
            drawSwitcherRow(
                    context,
                    renderer,
                    snapshot,
                    switcher.channels().get(index),
                    switcher.rows().get(index),
                    mouseX,
                    mouseY
            );
        }

        SwitcherHint hint = switcher.hint();
        if (hint != null) {
            context.drawText(
                    renderer,
                    trim(renderer, hint.text().getString(), box.width() - 16),
                    box.x() + 8,
                    switcher.hintY(),
                    hint.color(),
                    false
            );
        }
        context.getMatrices().pop();
    }

    /**
     * One switcher row. Locked rows deliberately show only the channel name: no member counts or other data.
     * 切换浮层中的一行。锁定行只显示频道名，刻意不显示成员人数等任何信息。
     */
    private void drawSwitcherRow(
            DrawContext context,
            TextRenderer renderer,
            TabletSnapshot snapshot,
            TabletChannel channel,
            TabletLayout.Rect row,
            int mouseX,
            int mouseY
    ) {
        SwitchState state = switchState(channel, snapshot);
        Accent rowAccent = accentFor(channel);
        boolean hovered = state == SwitchState.JOIN && row.contains(mouseX, mouseY);

        int background = switch (state) {
            case CURRENT -> rowAccent.soft();
            case LOCKED -> COLOR_LOCKED_ROW;
            default -> hovered ? withAlpha(rowAccent.soft(), 0x80) : COLOR_ROW;
        };
        context.fill(row.x(), row.y(), row.right(), row.bottom(), background);
        if (state == SwitchState.CURRENT || hovered) {
            context.drawBorder(row.x(), row.y(), row.width(), row.height(), rowAccent.signal());
        }
        context.fill(row.x(), row.y(), row.x() + 3, row.bottom(), state == SwitchState.LOCKED ? COLOR_LOCKED : rowAccent.signal());

        int iconX = row.x() + 9;
        if (state == SwitchState.LOCKED) {
            drawLock(context, iconX, row.y() + 7, COLOR_LOCKED_TEXT);
        } else {
            int lit = state == SwitchState.CURRENT ? 3 : 1;
            drawMiniSignal(context, iconX, row.y() + 15, rowAccent.pale(), COLOR_LOCKED, lit);
        }

        Text chipText;
        int chipColor;
        int chipBackground;
        switch (state) {
            case CURRENT -> {
                chipText = Text.translatable("screen.sparkstrength.tablet.channel.current");
                chipColor = rowAccent.pale();
                chipBackground = withAlpha(rowAccent.signal(), 0x40);
            }
            case JOIN -> {
                chipText = Text.translatable("screen.sparkstrength.tablet.channel.join");
                chipColor = hovered ? rowAccent.pale() : rowAccent.signal();
                chipBackground = hovered ? withAlpha(rowAccent.signal(), 0x40) : 0;
            }
            case PENDING -> {
                chipText = Text.translatable("screen.sparkstrength.tablet.channel.pending");
                chipColor = rowAccent.pale();
                chipBackground = 0;
            }
            case COOLDOWN -> {
                chipText = Text.translatable(
                        "screen.sparkstrength.tablet.channel.cooldown",
                        snapshot.channelSwitchCooldownSeconds()
                );
                chipColor = COLOR_MUTED;
                chipBackground = 0;
            }
            case MEETING_LOCKED -> {
                chipText = Text.translatable("screen.sparkstrength.tablet.channel.meeting");
                chipColor = COLOR_ORANGE_PALE;
                chipBackground = 0x24FF8C00;
            }
            default -> {
                chipText = Text.translatable("screen.sparkstrength.tablet.channel.locked");
                chipColor = COLOR_LOCKED_TEXT;
                chipBackground = 0;
            }
        }
        int chipX = drawPill(context, renderer, chipText, row.right() - 4, row.y() + 4, chipColor, chipBackground);

        int nameColor = switch (state) {
            case CURRENT -> rowAccent.pale();
            case JOIN -> COLOR_TEXT;
            case LOCKED -> COLOR_LOCKED_TEXT;
            default -> COLOR_MUTED;
        };
        int nameX = row.x() + 22;
        String name = Text.translatable(channel.translationKey()).getString();
        context.drawText(renderer, trim(renderer, name, Math.max(0, chipX - 6 - nameX)), nameX, row.y() + 7, nameColor, false);
    }

    private SwitcherLayout switcherLayout(TextRenderer renderer, TabletSnapshot snapshot) {
        List<TabletChannel> channels = switcherChannels(snapshot);
        SwitcherHint hint = switcherHint(snapshot);
        TabletLayout.Rect panel = layout.panel();
        TabletLayout.Rect pill = channelPillRect(renderer, snapshot, clockX(renderer, LocalTime.now().format(CLOCK_FORMAT)));
        int boxWidth = Math.min(SWITCHER_WIDTH, Math.max(0, panel.width() - 16));
        int minX = panel.x() + 8;
        int maxX = Math.max(minX, panel.right() - 8 - boxWidth);
        int boxX = Math.max(minX, Math.min(maxX, pill.right() - boxWidth));
        int boxY = pill.bottom() + 5;
        int rowsTop = boxY + SWITCHER_HEADER_HEIGHT;
        ArrayList<TabletLayout.Rect> rows = new ArrayList<>(channels.size());
        for (int index = 0; index < channels.size(); index++) {
            rows.add(new TabletLayout.Rect(
                    boxX + 4,
                    rowsTop + index * (SWITCHER_ROW_HEIGHT + SWITCHER_ROW_GAP),
                    Math.max(0, boxWidth - 8),
                    SWITCHER_ROW_HEIGHT
            ));
        }
        int rowsBottom = rows.isEmpty() ? rowsTop : rows.get(rows.size() - 1).bottom();
        int hintY = rowsBottom + 5;
        int boxHeight = rowsBottom - boxY + 4 + (hint == null ? 0 : SWITCHER_HINT_HEIGHT);
        return new SwitcherLayout(
                new TabletLayout.Rect(boxX, boxY, boxWidth, boxHeight),
                List.copyOf(channels),
                List.copyOf(rows),
                hint,
                hintY
        );
    }

    private List<TabletChannel> switcherChannels(TabletSnapshot snapshot) {
        EnumSet<TabletChannel> allowed = snapshot.allowedChannels();
        TabletChannel current = snapshot.channel();
        ArrayList<TabletChannel> channels = new ArrayList<>();
        for (TabletChannel channel : TabletChannel.values()) {
            // The witch network only exists with SparkWitch installed. / 只有安装 SparkWitch 时才存在魔女网络。
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
            return new SwitcherHint(Text.translatable("screen.sparkstrength.tablet.channel.hint.meeting"), COLOR_ORANGE_PALE);
        }
        if (allowed.size() > 1 && snapshot.channelSwitchCooldownSeconds() > 0) {
            return new SwitcherHint(Text.translatable(
                    "screen.sparkstrength.tablet.channel.hint.cooldown",
                    snapshot.channelSwitchCooldownSeconds()
            ), COLOR_MUTED);
        }
        if (allowed.size() <= 1) {
            return new SwitcherHint(Text.translatable("screen.sparkstrength.tablet.channel.hint.identity"), COLOR_MUTED);
        }
        return null;
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

    private void openSwitcher() {
        rememberChatDraft();
        if (chatField != null) {
            chatField.setFocused(false);
        }
        setFocused(null);
        switcherOpen = true;
        playClick();
    }

    private void closeSwitcher() {
        switcherOpen = false;
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
        // Drop the field first so refresh() cannot copy the old channel's text back into the draft.
        // 先丢弃输入框，避免 refresh() 把旧频道的文字写回草稿。
        chatField = null;
        session.updateDraft(TabletClientState.chatDraft());
        switcherOpen = false;
        pendingChannelWire = TabletChannel.NO_CHANNEL_WIRE;
        pendingSwitchTicks = 0;
    }

    private TabletLayout.Rect channelPillRect(TextRenderer renderer, TabletSnapshot snapshot) {
        return channelPillRect(renderer, snapshot, clockX(renderer, LocalTime.now().format(CLOCK_FORMAT)));
    }

    private TabletLayout.Rect channelPillRect(TextRenderer renderer, TabletSnapshot snapshot, int clockX) {
        TabletLayout.Rect status = layout.statusBar();
        int textWidth = Math.min(renderer.getWidth(channelShortLabel(snapshot)), PILL_MAX_WIDTH - CHANNEL_PILL_CHROME);
        int right = clockX - 8;
        int minX = status.x() + 30;
        int width = Math.max(0, Math.min(textWidth + CHANNEL_PILL_CHROME, right - minX));
        return new TabletLayout.Rect(right - width, status.y() + 10, width, PILL_HEIGHT);
    }

    private int clockX(TextRenderer renderer, String clock) {
        return layout.closeButton().x() - 8 - renderer.getWidth(clock);
    }

    private String channelShortLabel(TabletSnapshot snapshot) {
        TabletChannel channel = snapshot.channel();
        return Text.translatable(channel == null
                ? "screen.sparkstrength.tablet.channel.none"
                : channel.shortTranslationKey()).getString();
    }

    /**
     * Header/card member count. Anonymous channels list only the viewer plus linked partners (server-filtered), so they
     * count partners rather than channel size. Members may include dead teammates, hence "connected", not "online".
     * Counts linked partners currently holding a tablet; links themselves persist for the round.
     * 标题与卡片的成员计数。匿名频道只列出观看者本人与已互认同伴（服务端过滤），因此计数显示互认人数而非频道人数。
     * 成员可能包含死亡队友，因此显示为“已接入”而非“在线”。仅统计当前持有平板的已互认同伴；互认关系本身整局有效。
     */
    private static Text memberCount(TabletSnapshot snapshot) {
        if (isAnonymousChannel(snapshot)) {
            return Text.translatable("screen.sparkstrength.tablet.channel.linked", linkedPartners(snapshot));
        }
        return Text.translatable("screen.sparkstrength.tablet.channel.connected", snapshot.connections().size());
    }

    private static boolean isAnonymousChannel(TabletSnapshot snapshot) {
        TabletChannel channel = snapshot.channel();
        return channel != null && channel.anonymousSenders();
    }

    private static int linkedPartners(TabletSnapshot snapshot) {
        // The viewer is always a member of their own selected channel. / 观看者总是其所选频道的成员。
        return Math.max(0, snapshot.connections().size() - 1);
    }

    private ChatFooter chatFooter() {
        TabletLayout.Rect footer = layout.footer();
        int fieldX = footer.x() + 10;
        int inputY = footer.y() + Math.max(0, (footer.height() - 20) / 2);
        int availableWidth = Math.max(0, footer.width() - 20);
        int sendWidth = Math.min(62, Math.max(44, availableWidth / 4));
        int fieldWidth = Math.max(1, availableWidth - sendWidth - 6);
        return new ChatFooter(fieldX, inputY, fieldWidth, sendWidth);
    }

    private static Accent accentFor(@Nullable TabletChannel channel) {
        if (channel == null) {
            return NO_CHANNEL_ACCENT;
        }
        return switch (channel) {
            case POLICE -> POLICE_ACCENT;
            case KILLER -> KILLER_ACCENT;
            case WITCH -> WITCH_ACCENT;
        };
    }

    private boolean hasMeetingFeatures() {
        TabletChannel channel = TabletClientState.snapshot().channel();
        return channel != null && channel.hasMeetingFeatures();
    }

    private static boolean canCallMeeting(TabletSnapshot snapshot) {
        return snapshot.localMeetingParticipant()
                && snapshot.cooldownSeconds() <= 0
                && snapshot.localMeetingCallsRemaining() > 0;
    }

    private void drawMiniSignal(DrawContext context, int x, int bottom, int litColor, int dimColor, int litBars) {
        int[] heights = {3, 5, 7};
        for (int index = 0; index < heights.length; index++) {
            int barX = x + index * 3;
            context.fill(barX, bottom - heights[index], barX + 2, bottom, index < litBars ? litColor : dimColor);
        }
    }

    private void drawLock(DrawContext context, int x, int y, int color) {
        context.fill(x + 1, y, x + 6, y + 1, color);
        context.fill(x + 1, y, x + 2, y + 3, color);
        context.fill(x + 5, y, x + 6, y + 3, color);
        context.fill(x, y + 3, x + 7, y + 8, color);
        context.fill(x + 3, y + 5, x + 4, y + 7, COLOR_WELL);
    }

    /** Two interlocked chain links, 11x8. / 两个相扣的链环，11x8。 */
    private void drawLinkGlyph(DrawContext context, int x, int y, int color) {
        context.drawBorder(x, y, 7, 5, color);
        context.drawBorder(x + 4, y + 3, 7, 5, color);
    }

    private void drawCaret(DrawContext context, int x, int y, boolean pointingUp, int color) {
        for (int row = 0; row < 3; row++) {
            int inset = pointingUp ? 2 - row : row;
            context.fill(x + inset, y + row, x + 5 - inset, y + row + 1, color);
        }
    }

    private void drawCross(DrawContext context, int x, int y, int color) {
        for (int step = 0; step < 5; step++) {
            context.fill(x + step, y + step, x + step + 1, y + step + 1, color);
            context.fill(x + 4 - step, y + step, x + 5 - step, y + step + 1, color);
        }
    }

    private void fillHorizontal(DrawContext context, int firstX, int secondX, int y, int bottom, int color) {
        context.fill(Math.min(firstX, secondX), y, Math.max(firstX, secondX), bottom, color);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    private int visibleRows() {
        return layout == null ? 0 : layout.visibleRows();
    }

    private String trim(TextRenderer renderer, String value, int width) {
        if (renderer.getWidth(value) <= width) {
            return value;
        }
        String ellipsis = "…";
        return renderer.trimToWidth(value, Math.max(0, width - renderer.getWidth(ellipsis))) + ellipsis;
    }

    private void sendChat() {
        TabletSnapshot snapshot = TabletClientState.snapshot();
        if (!snapshot.canSend() || snapshot.channel() == null || chatField == null) {
            return;
        }
        rememberChatDraft();
        // The wire we were viewing travels with the message; the server drops it if its selection moved on.
        // 随消息附带当前查看的频道；若服务端的选择已变化则丢弃该消息。
        session.submitDraft().ifPresent(message ->
                ClientPlayNetworking.send(new SendTabletChatC2SPacket(snapshot.channelWire(), message)));
        TabletClientState.setChatDraft(session.draft());
        chatField.setText(session.draft());
        refresh();
    }

    private void rememberChatDraft() {
        if (chatField != null) {
            session.updateDraft(chatField.getText());
            TabletClientState.setChatDraft(session.draft());
        }
    }

    private void playClick() {
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
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

    private void requestSnapshot() {
        ClientPlayNetworking.send(new RequestTabletSnapshotC2SPacket());
    }

    private enum SwitchState {
        CURRENT,
        JOIN,
        PENDING,
        COOLDOWN,
        MEETING_LOCKED,
        LOCKED
    }

    /** Channel accent family: signal (lines/bars), pale (text/glow), soft (halo fill), detail (header detail). */
    private record Accent(int signal, int pale, int soft, int detail) {
    }

    private record ChatFooter(int fieldX, int inputY, int fieldWidth, int sendWidth) {
    }

    private record SwitcherHint(Text text, int color) {
    }

    private record SwitcherLayout(
            TabletLayout.Rect box,
            List<TabletChannel> channels,
            List<TabletLayout.Rect> rows,
            @Nullable SwitcherHint hint,
            int hintY
    ) {
    }
}
