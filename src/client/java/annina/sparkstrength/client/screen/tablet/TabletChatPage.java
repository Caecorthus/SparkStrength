package annina.sparkstrength.client.screen.tablet;

import annina.sparkstrength.client.screen.tablet.TabletSectionPainter.EmptyIcon;
import annina.sparkstrength.network.tablet.SendTabletChatC2SPacket;
import annina.sparkstrength.network.tablet.TabletSnapshot;
import annina.sparkstrength.tablet.TabletChannel;
import annina.sparkstrength.tablet.TabletLayout;
import annina.sparkstrength.tablet.TabletLayout.Rect;
import annina.sparkstrength.tablet.TabletRules;
import annina.sparkstrength.tablet.TabletUiSession;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Element;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

import static annina.sparkstrength.client.screen.tablet.TabletScreen.PILL_GLYPH;
import static annina.sparkstrength.client.screen.tablet.TabletScreen.PILL_GLYPH_GAP;
import static annina.sparkstrength.client.screen.tablet.TabletSectionPainter.KEY;
import static annina.sparkstrength.client.screen.tablet.TabletSectionPainter.PILL_PAD;

/**
 * Chat section: the viewed channel's messages as bubbles, the composer (input pill and send button) and the "new
 * messages" pill. TabletScreen routes Enter, composer clicks and focus repair here.
 * 聊天分区：当前频道的消息气泡、输入栏（输入胶囊与发送按钮）与“新消息”胶囊。TabletScreen 把回车、输入栏点击与焦点修复交给本页。
 */
final class TabletChatPage implements TabletPage {
    private static final int CHAT_SCROLL_STEP = 18;
    // Chat edge mask: band height and column pitch (logical px). 聊天边缘遮罩：带高与列间距（逻辑像素）。
    private static final int CHAT_MASK = 18;
    private static final int CHAT_MASK_STEP = 12;
    private static final int COUNTER_THRESHOLD = 100;

    // Composer. 输入栏。
    private static final int SEND_SIZE = 26;
    private static final int SEND_GAP = 8;
    private static final int INPUT_INSET_X = 12;
    private static final int INPUT_H = 9;
    private static final int NEW_PILL_H = 16;

    private final TabletScreen screen;
    private final TabletChatView chatView = new TabletChatView();

    // Widgets rebuilt by init(); the chat input is one persistent instance so caret/selection survive refresh().
    // init() 重建的控件；聊天输入框为同一个持久实例，使光标/选区在 refresh() 后保留。
    private @Nullable TabletTextInput chatInput;
    private boolean chatInputAttached;
    private @Nullable TabletPressable sendButton;
    private @Nullable TabletPressable newMessagesButton;

    // Chat scroll anchoring. 聊天滚动锚定。
    private List<TabletSnapshot.ChatRow> lastChatRows = List.of();
    // Scratch layout of the kept prefix, used only to measure the anchor offset. 仅用于测量锚点偏移的前缀排版。
    private boolean chatLayoutPrimed;
    private boolean newMessagesPending;

    TabletChatPage(TabletScreen screen) {
        this.screen = screen;
        chatView.setBackdrop(screen::screenBackdropAt);
    }

    // ============================================================================================ page

    @Override
    public void prepare(TabletSnapshot snapshot) {
        ensureChatInput();
        chatInputAttached = false;
        sendButton = null;
        newMessagesButton = null;
    }

    @Override
    public void init(TabletSnapshot snapshot) {
        TextRenderer textRenderer = screen.textRenderer();
        Composer composer = composer();
        TabletChannel channel = snapshot.channel();
        if (snapshot.canSend() && channel != null && chatInput != null) {
            Rect pill = composer.pill();
            chatInput.setDimensionsAndPosition(Math.max(1, pill.width() - 2 * INPUT_INSET_X), INPUT_H,
                    pill.x() + INPUT_INSET_X, pill.centerY() - 4);
            chatInput.setPlaceholder(Text.translatable(KEY + "chat.placeholder_channel",
                    Text.translatable(channel.translationKey())));
            chatInput.setCaretColor(screen.frameAccentFor(snapshot).pale());
            chatInput.setSelectionColor(TabletTheme.withAlpha(screen.frameAccentFor(snapshot).base(), 0x66));
            screen.addChild(chatInput);
            chatInputAttached = true;
            if (!screen.switcherOpen()) {
                screen.focusInitially(chatInput);
            }
        }
        // Read-only holders (dead) get a painted read-only bar instead of the input; see paintComposer.
        // 只读持有者（死亡）不挂输入框，而是绘制只读栏；见 paintComposer。

        Rect send = composer.send();
        sendButton = screen.addChild(new TabletPressable(send.x(), send.y(), send.width(), send.height(),
                Text.translatable(KEY + "chat.send"), this::sendChat,
                (context, button, hovered, delta) -> paintSendButton(button, hovered)));
        sendButton.active = canSendChat(snapshot);

        Text newLabel = Text.translatable(KEY + "chat.new");
        Rect list = screen.layout().list();
        int pillWidth = Math.min(list.width(), TabletSectionPainter.visibleWidth(textRenderer, newLabel.getString())
                + 2 * PILL_PAD + PILL_GLYPH + PILL_GLYPH_GAP);
        newMessagesButton = screen.addChild(new TabletPressable(list.centerX() - pillWidth / 2,
                list.bottom() - NEW_PILL_H - 6, pillWidth, NEW_PILL_H, newLabel, this::resetChatScroll,
                (context, button, hovered, delta) -> paintNewMessagesPill(button, newLabel, hovered)));
        newMessagesButton.visible = false;
    }

    @Override
    public boolean mouseScrolled(TabletSnapshot snapshot, double x, double y, double verticalAmount) {
        TabletUiSession session = screen.session();
        Rect list = screen.layout().list();
        if (list.contains(x, y)) {
            session.scrollChat((int) Math.round(verticalAmount * CHAT_SCROLL_STEP), chatView.maxScroll(list.height()));
            if (session.chatScroll() == 0) {
                newMessagesPending = false;
            }
            return true;
        }
        return false;
    }

    @Override
    public int paintHeaderChip(TabletSnapshot snapshot, int right, int centerY, int maxWidth, long now) {
        return screen.paintPill(right, centerY, maxWidth, String.valueOf(snapshot.chat().size()),
                TabletTheme.TEXT_2, TabletTheme.SURFACE, 0, TabletIcons::chat, TabletTheme.TEXT_2);
    }

    @Override
    public void paintBadge(TabletSnapshot snapshot, Rect icon, long now) {
        if (TabletClientState.hasUnreadChat(snapshot)) {
            screen.paintBadgeDot(icon, screen.accent().base());
        }
    }

    @Override
    public void paintBody(TabletSnapshot snapshot, int hoverX, int hoverY, long now) {
        TabletCanvas c = screen.canvas();
        TextRenderer textRenderer = screen.textRenderer();
        Rect list = screen.layout().list();
        List<TabletSnapshot.ChatRow> rows = snapshot.chat();
        layoutChat(rows, list);
        TabletClientState.markChatSeen(TabletClientState.chatSignature(rows));
        if (rows.isEmpty()) {
            TabletSectionPainter.emptyState(c, textRenderer, list, EmptyIcon.CHAT, Text.translatable(KEY + "chat.empty"));
        } else {
            c.pushClip(list.x(), list.y(), list.width(), list.height());
            chatView.render(c, textRenderer, list.x(), list.y(), list.width(), list.height(), screen.session().chatScroll(),
                    screen.accent());
            paintChatEdgeMasks(list);
            c.popClip();
        }
        paintComposer(hoverX, hoverY);
        syncChatWidgets(snapshot);
    }

    // ============================================================================================ screen hooks

    /** The input when this build attached it (sending holder on the chat page), else null. 本次构建挂上的输入框，否则为 null。 */
    @Nullable TabletTextInput attachedInput() {
        return chatInputAttached ? chatInput : null;
    }

    /** Enter sends from the composer: nothing focused, the input or the send button. 回车在输入区发送：无焦点、输入框或发送按钮。 */
    boolean sendsOnEnter(@Nullable Element focused) {
        return focused == null || focused == chatInput || focused == sendButton;
    }

    boolean inputFocused() {
        return chatInputAttached && chatInput != null && chatInput.isFocused();
    }

    /** Send / "new messages": clicking them keeps typing in the input. 发送与“新消息”：点击后继续在输入框中输入。 */
    boolean isComposerControl(Element focused) {
        return focused == sendButton || focused == newMessagesButton;
    }

    Rect composerPill() {
        return composer().pill();
    }

    void blurInput() {
        if (chatInput != null) {
            chatInput.setFocused(false);
        }
    }

    void onChannelChanged(int previousChannelWire) {
        TabletUiSession session = screen.session();
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
        resetChatScroll();
        chatLayoutPrimed = false;
        lastChatRows = List.of();
    }

    // ============================================================================================ chat actions

    private void ensureChatInput() {
        if (chatInput != null) {
            return;
        }
        TabletUiSession session = screen.session();
        TabletTextInput input = new TabletTextInput(screen.textRenderer(), 0, 0, 1, INPUT_H,
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

    void sendChat() {
        TabletUiSession session = screen.session();
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

    void rememberChatDraft() {
        if (chatInputAttached && chatInput != null) {
            screen.session().updateDraft(chatInput.getText());
            TabletClientState.setChatDraft(screen.session().draft());
        }
    }

    void resetChatScroll() {
        screen.session().resetChatScroll();
        newMessagesPending = false;
    }

    private boolean canSendChat(TabletSnapshot snapshot) {
        return snapshot.canSend()
                && snapshot.channel() != null
                && chatInputAttached
                && !screen.session().draft().isBlank();
    }

    private boolean counterVisible() {
        return chatInput != null && chatInput.getText().length() >= COUNTER_THRESHOLD;
    }

    private static String counterText(int length) {
        return length + "/" + TabletRules.CHAT_MESSAGE_MAX_LENGTH;
    }

    /** Per-frame widget state that depends on typing/scrolling rather than on the snapshot. 随输入/滚动变化的控件状态。 */
    private void syncChatWidgets(TabletSnapshot snapshot) {
        if (chatInputAttached && chatInput != null) {
            Rect pill = composer().pill();
            int reserve = counterVisible()
                    ? screen.textRenderer().getWidth(counterText(TabletRules.CHAT_MESSAGE_MAX_LENGTH)) + 6 : 0;
            chatInput.setWidth(Math.max(1, pill.width() - 2 * INPUT_INSET_X - reserve));
        }
        if (sendButton != null) {
            sendButton.active = canSendChat(snapshot);
        }
        if (newMessagesButton != null) {
            newMessagesButton.visible = newMessagesPending && screen.session().chatScroll() > 0;
        }
    }

    private Composer composer() {
        TabletLayout layout = screen.layout();
        Rect footer = layout.footer();
        int padX = layout.mode().padX();
        int size = Math.max(0, Math.min(SEND_SIZE, footer.height() - 6));
        int inset = Math.max(3, (footer.height() - SEND_SIZE) / 2);
        Rect pill = footer.inset(padX, inset, padX + size + SEND_GAP, inset);
        Rect send = new Rect(pill.right() + SEND_GAP, pill.centerY() - size / 2, size, size);
        return new Composer(pill, send);
    }

    // ============================================================================================ paint

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
        int scroll = Math.max(0, Math.min(screen.session().chatScroll(), maxScroll));
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
            screen.canvas().gradientQuad(x0, top, x1, bottom,
                    TabletTheme.withAlpha(screen.screenBackdropAt(x0, top), topAlpha),
                    TabletTheme.withAlpha(screen.screenBackdropAt(x1, top), topAlpha),
                    TabletTheme.withAlpha(screen.screenBackdropAt(x0, bottom), bottomAlpha),
                    TabletTheme.withAlpha(screen.screenBackdropAt(x1, bottom), bottomAlpha));
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
        TabletUiSession session = screen.session();
        chatView.layout(screen.textRenderer(), rows, screen.localUuid(), list.width());
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
        TabletCanvas c = screen.canvas();
        TextRenderer textRenderer = screen.textRenderer();
        TabletTheme.Accent frameAccent = screen.accent();
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
        TabletCanvas c = screen.canvas();
        TabletTheme.Accent frameAccent = screen.accent();
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
        TabletCanvas c = screen.canvas();
        TextRenderer textRenderer = screen.textRenderer();
        TabletTheme.Accent frameAccent = screen.accent();
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

    private record Composer(Rect pill, Rect send) {
    }
}
