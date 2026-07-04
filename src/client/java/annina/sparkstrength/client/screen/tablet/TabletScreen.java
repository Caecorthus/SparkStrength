package annina.sparkstrength.client.screen.tablet;

import annina.sparkstrength.network.tablet.ApproveSuspectRemovalC2SPacket;
import annina.sparkstrength.network.tablet.CallTabletMeetingC2SPacket;
import annina.sparkstrength.network.tablet.CastTabletVoteC2SPacket;
import annina.sparkstrength.network.tablet.ConfirmTabletVoteC2SPacket;
import annina.sparkstrength.network.tablet.RequestTabletSnapshotC2SPacket;
import annina.sparkstrength.network.tablet.SendTabletChatC2SPacket;
import annina.sparkstrength.network.tablet.TabletSnapshot;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.UUID;

public final class TabletScreen extends Screen {
    private static final DateTimeFormatter CLOCK_FORMAT = DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM);
    private static final int PANEL_WIDTH = 486;
    private static final int PANEL_HEIGHT = 292;
    private static final int HEADER_HEIGHT = 34;
    private static final int SIDEBAR_WIDTH = 128;
    private static final int CONTENT_X_OFFSET = 146;
    private static final int TAB_WIDTH = 106;
    private static final int ROW_HEIGHT = 24;
    private static final int COLOR_PANEL = 0xF20D1218;
    private static final int COLOR_PANEL_EDGE = 0xFF31485A;
    private static final int COLOR_HEADER = 0xFF132331;
    private static final int COLOR_SIDEBAR = 0xFF121A22;
    private static final int COLOR_CONTENT = 0xA80A0F14;
    private static final int COLOR_DIVIDER = 0xFF243341;
    private static final int COLOR_ACCENT = 0xFF4DB7E8;
    private static final int COLOR_TEXT = 0xFFE8F3FA;
    private static final int COLOR_MUTED = 0xFFA5B1BA;
    private static final int COLOR_ROW = 0xB914202A;
    private static final int COLOR_ROW_ALT = 0x9C182631;
    private static final int COLOR_GREEN = 0xFF46D878;
    private static final int COLOR_ORANGE = 0xFFFF8C00;
    private TabletTab tab = TabletTab.CONNECTIONS;
    private String pendingChat = TabletClientState.chatDraft();
    private TextFieldWidget chatField;
    private int snapshotRequestTicks;
    private boolean requestedInitialSnapshot;

    public TabletScreen() {
        super(Text.translatable("screen.sparkstrength.tablet.title"));
    }

    @Override
    protected void init() {
        super.init();
        if (!requestedInitialSnapshot) {
            requestedInitialSnapshot = true;
            requestSnapshot();
        }

        int panelX = panelX();
        int panelY = panelY();
        int tabY = panelY + HEADER_HEIGHT + 14;
        for (TabletTab value : TabletTab.values()) {
            TabletTab selected = value;
            addDrawableChild(ButtonWidget.builder(value.label(), button -> {
                        rememberChatDraft();
                        tab = selected;
                        refresh();
                    })
                    .dimensions(panelX + 12, tabY, TAB_WIDTH, 20)
                    .build());
            tabY += 26;
        }

        if (tab == TabletTab.CHAT) {
            initChat(panelX, panelY);
        } else if (tab == TabletTab.MEETING) {
            initMeeting(panelX, panelY);
        } else if (tab == TabletTab.SUSPECTS) {
            initSuspects(panelX, panelY);
        }
    }

    public void refresh() {
        if (client == null) {
            return;
        }
        rememberChatDraft();
        chatField = null;
        clearChildren();
        init();
    }

    public void handleSnapshotUpdate() {
        if (tab == TabletTab.MEETING || tab == TabletTab.SUSPECTS) {
            refresh();
        }
    }

    @Override
    public void tick() {
        super.tick();
        snapshotRequestTicks++;
        if (snapshotRequestTicks >= 20) {
            snapshotRequestTicks = 0;
            requestSnapshot();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (tab == TabletTab.CHAT && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            sendChat();
            return true;
        }
        boolean handled = super.keyPressed(keyCode, scanCode, modifiers);
        if (tab == TabletTab.CHAT && chatField != null && chatField.isFocused() && isMovementKey(keyCode, scanCode)) {
            return true;
        }
        return handled;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xB8000000);
        int panelX = panelX();
        int panelY = panelY();

        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        drawTabletFrame(context, renderer, panelX, panelY);

        if (tab == TabletTab.CONNECTIONS) {
            renderConnections(context, renderer, panelX, panelY);
        } else if (tab == TabletTab.CHAT) {
            renderChat(context, renderer, panelX, panelY);
        } else if (tab == TabletTab.MEETING) {
            renderMeeting(context, renderer, panelX, panelY);
        } else if (tab == TabletTab.SUSPECTS) {
            renderSuspects(context, renderer, panelX, panelY);
        }

        super.render(context, mouseX, mouseY, delta);
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

    private void initChat(int panelX, int panelY) {
        int contentX = contentX(panelX);
        int inputY = panelY + PANEL_HEIGHT - 34;
        int fieldX = contentX + 10;
        int fieldWidth = contentWidth() - 86;
        TextFieldWidget field = new TextFieldWidget(
                MinecraftClient.getInstance().textRenderer,
                fieldX,
                inputY,
                fieldWidth,
                20,
                Text.translatable("screen.sparkstrength.tablet.chat.placeholder")
        );
        field.setMaxLength(120);
        pendingChat = TabletClientState.chatDraft();
        field.setText(pendingChat);
        field.setPlaceholder(Text.translatable("screen.sparkstrength.tablet.chat.placeholder"));
        field.setChangedListener(value -> {
            pendingChat = value;
            TabletClientState.setChatDraft(value);
        });
        chatField = field;
        addDrawableChild(field);
        setInitialFocus(field);
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.sparkstrength.tablet.chat.send"), button -> sendChat())
                .dimensions(fieldX + fieldWidth + 6, inputY, 62, 20)
                .build());
    }

    private void initMeeting(int panelX, int panelY) {
        TabletSnapshot snapshot = TabletClientState.snapshot();
        int contentX = contentX(panelX) + 10;
        int y = panelY + 72;
        if (!snapshot.meeting().active()) {
            ButtonWidget call = ButtonWidget.builder(Text.translatable("screen.sparkstrength.tablet.meeting.call"),
                            button -> ClientPlayNetworking.send(new CallTabletMeetingC2SPacket()))
                    .dimensions(contentX, y, 130, 20)
                    .build();
            call.active = snapshot.localMeetingParticipant()
                    && snapshot.cooldownSeconds() <= 0
                    && snapshot.localMeetingCallsRemaining() > 0;
            addDrawableChild(call);
            return;
        }

        ButtonWidget abstain = ButtonWidget.builder(Text.translatable("screen.sparkstrength.tablet.meeting.abstain"),
                        button -> ClientPlayNetworking.send(new CastTabletVoteC2SPacket(null)))
                .dimensions(contentX, y, 74, 20)
                .build();
        abstain.active = snapshot.localMeetingParticipant() && !snapshot.meeting().localConfirmed();
        addDrawableChild(abstain);
        ButtonWidget confirm = ButtonWidget.builder(Text.translatable(
                        snapshot.meeting().localConfirmed()
                                ? "screen.sparkstrength.tablet.meeting.locked"
                                : "screen.sparkstrength.tablet.meeting.confirm"
                ), button -> ClientPlayNetworking.send(new ConfirmTabletVoteC2SPacket()))
                .dimensions(contentX + 82, y, 74, 20)
                .build();
        confirm.active = snapshot.localMeetingParticipant() && !snapshot.meeting().localConfirmed();
        addDrawableChild(confirm);

        int rowY = y + 30;
        for (TabletSnapshot.VoteTarget target : snapshot.meeting().targets()) {
            int voteButtonX = meetingVoteButtonX(panelX);
            ButtonWidget vote = ButtonWidget.builder(Text.translatable("screen.sparkstrength.tablet.meeting.vote"),
                            button -> ClientPlayNetworking.send(new CastTabletVoteC2SPacket(target.uuid())))
                    .dimensions(voteButtonX, rowY + 2, 64, 18)
                    .build();
            vote.active = snapshot.localMeetingParticipant() && target.selectable() && !snapshot.meeting().localConfirmed();
            addDrawableChild(vote);
            rowY += ROW_HEIGHT;
            if (rowY > panelY + PANEL_HEIGHT - 34) {
                break;
            }
        }
    }

    private void initSuspects(int panelX, int panelY) {
        int rowY = panelY + 78;
        for (TabletSnapshot.SuspectRow suspect : TabletClientState.snapshot().suspects()) {
            Text label = Text.translatable(suspect.localApproved()
                    ? "screen.sparkstrength.tablet.suspects.cancel"
                    : "screen.sparkstrength.tablet.suspects.approve");
            ButtonWidget button = ButtonWidget.builder(label, ignored ->
                            ClientPlayNetworking.send(new ApproveSuspectRemovalC2SPacket(suspect.uuid(), !suspect.localApproved())))
                    .dimensions(panelX + PANEL_WIDTH - 96, rowY + 2, 78, 18)
                    .build();
            button.active = TabletClientState.snapshot().localMeetingParticipant();
            addDrawableChild(button);
            rowY += ROW_HEIGHT;
            if (rowY > panelY + PANEL_HEIGHT - 24) {
                break;
            }
        }
    }

    private void renderConnections(DrawContext context, TextRenderer renderer, int panelX, int panelY) {
        TabletSnapshot snapshot = TabletClientState.snapshot();
        int contentX = contentX(panelX);
        drawSectionHeader(context, renderer, contentX, panelY + 48, tab.label(), String.valueOf(snapshot.connections().size()));
        if (snapshot.connections().isEmpty()) {
            drawEmptyState(context, renderer, contentX, panelY + 100, Text.translatable("screen.sparkstrength.tablet.connections.empty"));
            return;
        }

        int y = panelY + 78;
        int rowWidth = contentWidth() - 16;
        for (TabletSnapshot.PlayerRow row : snapshot.connections()) {
            drawPlayerRow(context, renderer, row.uuid(), row.name(), row.inGame() ? COLOR_GREEN : 0xFF777777, contentX + 8, y, rowWidth, 86);
            drawPill(
                    context,
                    renderer,
                    Text.translatable(row.inGame()
                            ? "screen.sparkstrength.tablet.status.ingame"
                            : "screen.sparkstrength.tablet.status.outside"),
                    contentX + 8 + rowWidth - 8,
                    y + 5,
                    row.inGame() ? COLOR_GREEN : COLOR_MUTED,
                    row.inGame() ? 0x2736D36B : 0x24777777
            );
            y += ROW_HEIGHT;
            if (y > panelY + PANEL_HEIGHT - 20) {
                break;
            }
        }
    }

    private void renderChat(DrawContext context, TextRenderer renderer, int panelX, int panelY) {
        int contentX = contentX(panelX);
        var messages = TabletClientState.snapshot().chat();
        drawSectionHeader(context, renderer, contentX, panelY + 48, tab.label(), String.valueOf(messages.size()));
        if (messages.isEmpty()) {
            drawEmptyState(context, renderer, contentX, panelY + 100, Text.translatable("screen.sparkstrength.tablet.chat.empty"));
            return;
        }

        int y = panelY + 78;
        int rowWidth = contentWidth() - 16;
        int start = Math.max(0, messages.size() - 7);
        for (int i = start; i < messages.size(); i++) {
            TabletSnapshot.ChatRow row = messages.get(i);
            context.fill(contentX + 8, y, contentX + 8 + rowWidth, y + 20, i % 2 == 0 ? COLOR_ROW : COLOR_ROW_ALT);
            context.fill(contentX + 8, y, contentX + 11, y + 20, COLOR_ACCENT);
            String sender = trim(renderer, row.senderName(), 78);
            int senderWidth = Math.min(82, renderer.getWidth(sender) + 8);
            context.drawText(renderer, sender, contentX + 16, y + 6, COLOR_ACCENT, false);
            context.drawText(renderer, trim(renderer, row.message(), rowWidth - senderWidth - 22), contentX + 16 + senderWidth, y + 6, COLOR_TEXT, false);
            y += 22;
        }
    }

    private void renderMeeting(DrawContext context, TextRenderer renderer, int panelX, int panelY) {
        TabletSnapshot snapshot = TabletClientState.snapshot();
        int contentX = contentX(panelX);
        String detail = snapshot.meeting().active()
                ? Text.translatable("screen.sparkstrength.tablet.meeting.timer", snapshot.meeting().remainingSeconds()).getString()
                : Text.translatable("screen.sparkstrength.tablet.meeting.chances", snapshot.localMeetingCallsRemaining()).getString();
        drawSectionHeader(context, renderer, contentX, panelY + 48, tab.label(), detail);
        if (!snapshot.meeting().active()) {
            String key = snapshot.cooldownSeconds() > 0
                    ? "screen.sparkstrength.tablet.meeting.cooldown"
                    : "screen.sparkstrength.tablet.meeting.disabled";
            drawInfoLine(context, renderer, contentX + 10, panelY + 104, Text.translatable(key, snapshot.cooldownSeconds()), COLOR_MUTED);
            return;
        }

        int y = panelY + 102;
        int rowX = contentX + 8;
        int rowWidth = meetingVoteButtonX(panelX) - rowX - 8;
        int votesX = meetingVoteButtonX(panelX) - 42;
        for (TabletSnapshot.VoteTarget target : snapshot.meeting().targets()) {
            boolean selected = target.uuid().equals(snapshot.meeting().localVoteTarget());
            drawPlayerRow(context, renderer, target.uuid(), target.name(), target.selectable() ? COLOR_ACCENT : 0xFF666666, rowX, y, rowWidth, 60);
            if (selected) {
                context.drawBorder(rowX, y + 3, rowWidth, ROW_HEIGHT - 4, COLOR_ACCENT);
            }
            context.drawText(renderer, String.valueOf(target.votes()), votesX, y + 7, COLOR_TEXT, false);
            y += ROW_HEIGHT;
            if (y > panelY + PANEL_HEIGHT - 24) {
                break;
            }
        }
    }

    private void renderSuspects(DrawContext context, TextRenderer renderer, int panelX, int panelY) {
        TabletSnapshot snapshot = TabletClientState.snapshot();
        int contentX = contentX(panelX);
        drawSectionHeader(context, renderer, contentX, panelY + 48, tab.label(), String.valueOf(snapshot.suspects().size()));
        if (snapshot.suspects().isEmpty()) {
            drawEmptyState(context, renderer, contentX, panelY + 100, Text.translatable("screen.sparkstrength.tablet.suspects.none"));
            return;
        }

        int y = panelY + 78;
        int rowWidth = contentWidth() - 16;
        for (TabletSnapshot.SuspectRow suspect : snapshot.suspects()) {
            drawPlayerRow(context, renderer, suspect.uuid(), suspect.name(), COLOR_ORANGE, contentX + 8, y, rowWidth, 110);
            context.drawText(
                    renderer,
                    Text.translatable("screen.sparkstrength.tablet.suspects.votes", suspect.approvals(), suspect.requiredApprovals()),
                    panelX + PANEL_WIDTH - 174,
                    y + 7,
                    0xFFFFC062,
                    false
            );
            y += ROW_HEIGHT;
            if (y > panelY + PANEL_HEIGHT - 24) {
                break;
            }
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
        context.fill(x, y + 3, x + 4, y + 23, borderColor);
        context.fill(x + 8, y + 3, x + 28, y + 23, borderColor);
        context.fill(x + 10, y + 5, x + 26, y + 21, 0xFF05080B);
        TabletPlayerRow.drawAvatar(context, uuid, name, x + 10, y + 5);
        context.drawText(renderer, trim(renderer, name, width - reservedRightWidth - 42), x + 36, y + 8, COLOR_TEXT, false);
    }

    private void drawTabletFrame(DrawContext context, TextRenderer renderer, int panelX, int panelY) {
        context.fill(panelX - 3, panelY - 3, panelX + PANEL_WIDTH + 3, panelY + PANEL_HEIGHT + 3, 0x76000000);
        context.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, COLOR_PANEL);
        context.drawBorder(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, COLOR_PANEL_EDGE);
        context.fill(panelX + 1, panelY + 1, panelX + PANEL_WIDTH - 1, panelY + HEADER_HEIGHT, COLOR_HEADER);
        context.fill(panelX + 1, panelY + HEADER_HEIGHT, panelX + SIDEBAR_WIDTH, panelY + PANEL_HEIGHT - 1, COLOR_SIDEBAR);
        context.fill(panelX + SIDEBAR_WIDTH, panelY + HEADER_HEIGHT, panelX + SIDEBAR_WIDTH + 1, panelY + PANEL_HEIGHT - 1, COLOR_DIVIDER);

        context.drawText(renderer, Text.translatable("screen.sparkstrength.tablet.title"), panelX + 12, panelY + 12, COLOR_TEXT, false);
        String clock = LocalTime.now().format(CLOCK_FORMAT);
        context.drawText(renderer, clock, panelX + PANEL_WIDTH - 12 - renderer.getWidth(clock), panelY + 12, 0xFF9DEBFF, false);
        drawTabHighlights(context, panelX, panelY);
        drawContentPanel(context, panelX, panelY);
    }

    private void drawTabHighlights(DrawContext context, int panelX, int panelY) {
        int tabY = panelY + HEADER_HEIGHT + 14;
        for (TabletTab value : TabletTab.values()) {
            if (value == tab) {
                context.fill(panelX + 8, tabY - 2, panelX + 122, tabY + 22, 0x332E6F9E);
                context.fill(panelX + 8, tabY - 2, panelX + 11, tabY + 22, COLOR_ACCENT);
            }
            tabY += 26;
        }
    }

    private void drawContentPanel(DrawContext context, int panelX, int panelY) {
        int contentX = contentX(panelX);
        int contentY = panelY + HEADER_HEIGHT + 10;
        context.fill(contentX - 4, contentY - 4, contentX + contentWidth() + 4, panelY + PANEL_HEIGHT - 12, COLOR_CONTENT);
        context.drawBorder(contentX - 4, contentY - 4, contentWidth() + 8, PANEL_HEIGHT - HEADER_HEIGHT - 18, COLOR_DIVIDER);
    }

    private void drawSectionHeader(DrawContext context, TextRenderer renderer, int x, int y, Text title, String detail) {
        context.drawText(renderer, title, x + 8, y, COLOR_TEXT, false);
        if (!detail.isBlank()) {
            context.drawText(renderer, detail, x + contentWidth() - 8 - renderer.getWidth(detail), y, 0xFFFFC062, false);
        }
        context.fill(x + 8, y + 16, x + contentWidth() - 8, y + 17, COLOR_DIVIDER);
    }

    private void drawEmptyState(DrawContext context, TextRenderer renderer, int x, int y, Text text) {
        int width = contentWidth() - 16;
        context.fill(x + 8, y, x + 8 + width, y + 42, 0x7A121A22);
        context.drawBorder(x + 8, y, width, 42, COLOR_DIVIDER);
        String value = trim(renderer, text.getString(), width - 24);
        context.drawText(renderer, value, x + 20, y + 17, COLOR_MUTED, false);
    }

    private void drawInfoLine(DrawContext context, TextRenderer renderer, int x, int y, Text text, int color) {
        context.fill(x - 8, y - 8, x + contentWidth() - 20, y + 20, 0x7A121A22);
        context.drawText(renderer, trim(renderer, text.getString(), contentWidth() - 44), x, y + 2, color, false);
    }

    private void drawPill(DrawContext context, TextRenderer renderer, Text text, int right, int y, int color, int backgroundColor) {
        String value = trim(renderer, text.getString(), 70);
        int width = Math.min(78, renderer.getWidth(value) + 10);
        context.fill(right - width, y, right, y + 14, backgroundColor);
        context.drawBorder(right - width, y, width, 14, color);
        context.drawText(renderer, value, right - width + 5, y + 3, color, false);
    }

    private int contentX(int panelX) {
        return panelX + CONTENT_X_OFFSET;
    }

    private int contentWidth() {
        return PANEL_WIDTH - CONTENT_X_OFFSET - 18;
    }

    private int meetingVoteButtonX(int panelX) {
        return panelX + PANEL_WIDTH - 116;
    }

    private String trim(TextRenderer renderer, String value, int width) {
        if (renderer.getWidth(value) <= width) {
            return value;
        }
        String ellipsis = "…";
        return renderer.trimToWidth(value, Math.max(0, width - renderer.getWidth(ellipsis))) + ellipsis;
    }

    private void sendChat() {
        rememberChatDraft();
        String message = pendingChat.trim();
        if (message.isEmpty()) {
            pendingChat = "";
            TabletClientState.clearChatDraft();
            if (chatField != null) {
                chatField.setText("");
            }
            refresh();
            return;
        }
        ClientPlayNetworking.send(new SendTabletChatC2SPacket(message));
        pendingChat = "";
        TabletClientState.clearChatDraft();
        if (chatField != null) {
            chatField.setText("");
        }
        refresh();
    }

    private void rememberChatDraft() {
        if (chatField != null) {
            pendingChat = chatField.getText();
            TabletClientState.setChatDraft(pendingChat);
        }
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

    private int panelX() {
        return (width - PANEL_WIDTH) / 2;
    }

    private int panelY() {
        return (height - PANEL_HEIGHT) / 2;
    }

    private void requestSnapshot() {
        ClientPlayNetworking.send(new RequestTabletSnapshotC2SPacket());
    }
}
