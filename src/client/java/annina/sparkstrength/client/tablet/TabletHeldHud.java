package annina.sparkstrength.client.tablet;

import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.client.role.bomber.DronePilotClient;
import annina.sparkstrength.client.screen.tablet.TabletCanvas;
import annina.sparkstrength.client.screen.tablet.TabletClientState;
import annina.sparkstrength.client.screen.tablet.TabletIcons;
import annina.sparkstrength.client.screen.tablet.TabletTheme;
import annina.sparkstrength.network.tablet.TabletSnapshot;
import annina.sparkstrength.tablet.TabletChannel;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Small card shown while the local player holds the tablet in the main hand with no screen open: the last messages of
 * the channel the tablet is tuned to (the one last viewed) and, under them, which other networks got messages since
 * they were last on screen. It reads only the snapshot the server already pushes to every holder and the local unread
 * counts, so it reveals nothing the tablet screen would not; other channels show a count, never their text (the
 * snapshot only ever carries the viewed channel). Local only: nobody else sees it.
 * 本地玩家主手拿着平板且没有打开任何界面时显示的小卡片：平板当前接入的频道（即上次查看的频道）的最近几条消息，以及其下方
 * 哪些其他网络在上次显示后收到了消息。它只读取服务端本就推送给每名持有者的快照和本地未读条数，因此不会透露平板界面以外的
 * 信息；其他频道只显示条数，从不显示内容（快照只携带当前查看的频道）。仅本地可见，其他人看不到。
 *
 * <p>Shown for {@link #READ_AFTER_MS} it counts the viewed channel as read, the same as opening its chat; the newest
 * row glows briefly when it arrives while the card is up.
 * 显示满 READ_AFTER_MS 后把当前频道计为已读，与打开其聊天相同；卡片显示期间到达的最新一行会短暂高亮。</p>
 */
public final class TabletHeldHud {
    private static final String KEY = "hud.sparkstrength.tablet.";
    private static final int MAX_ROWS = 4;
    private static final int WIDTH = 150;
    private static final int PAD = 6;
    private static final int HEADER_H = 15;
    private static final int ROW_H = 11;
    private static final int AVATAR = 8;
    private static final int NAME_MAX_W = 46;
    private static final int FOOTER_ROW_H = 11;
    private static final int MARGIN_RIGHT = 8;
    private static final float RADIUS = 5f;
    private static final long FADE_MS = 160L;
    private static final long READ_AFTER_MS = 800L;
    private static final long GLOW_MS = 2200L;
    private static final int CARD = 0xEC12151D;

    private static long shownSinceMs = -1L;
    private static long lastChatSignature;
    private static boolean signaturePrimed;
    private static long glowStartMs = Long.MIN_VALUE / 2;

    private TabletHeldHud() {
    }

    public static void register() {
        HudRenderCallback.EVENT.register(TabletHeldHud::render);
    }

    /** Whether the card is up this frame; package-visible for the dev harness. / 本帧卡片是否显示。 */
    static boolean visible(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || client.options.hudHidden || client.currentScreen != null
                || player.isSpectator() || DronePilotClient.isViewingDrone()) {
            return false;
        }
        if (!player.getMainHandStack().isOf(SparkStrengthItems.tablet())) {
            return false;
        }
        TabletSnapshot snapshot = TabletClientState.snapshot();
        return snapshot.localHasTablet() && snapshot.channel() != null;
    }

    private static void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        long now = Util.getMeasuringTimeMs();
        if (!visible(client)) {
            shownSinceMs = -1L;
            return;
        }
        if (shownSinceMs < 0L) {
            shownSinceMs = now;
        }
        TabletSnapshot snapshot = TabletClientState.snapshot();
        TabletChannel channel = snapshot.channel();
        List<TabletSnapshot.ChatRow> chat = snapshot.chat();
        trackGlow(chat, now);
        if (now - shownSinceMs >= READ_AFTER_MS) {
            TabletClientState.markChannelRead(channel);
        }

        TextRenderer tr = client.textRenderer;
        List<TabletSnapshot.ChatRow> rows = chat.size() <= MAX_ROWS ? chat : chat.subList(chat.size() - MAX_ROWS, chat.size());
        List<Hint> hints = hints(snapshot, channel);
        int bodyH = rows.isEmpty() ? ROW_H + 2 : rows.size() * ROW_H;
        int footerH = hints.isEmpty() ? 0 : 5 + hints.size() * FOOTER_ROW_H;
        int height = HEADER_H + 4 + bodyH + footerH + PAD - 2;

        float fade = Math.min(1f, (now - shownSinceMs) / (float) FADE_MS);
        float ease = 1f - (1f - fade) * (1f - fade);
        int w = context.getScaledWindowWidth();
        int h = context.getScaledWindowHeight();
        int x = w - WIDTH - MARGIN_RIGHT + Math.round((1f - ease) * 10f);
        int y = Math.max(4, h / 2 - height / 2 + 6);

        int scale = Math.max(1, (int) Math.round(client.getWindow().getScaleFactor()));
        TabletCanvas canvas = new TabletCanvas(context, scale, client.getWindow().getFramebufferHeight());
        UUID self = client.player.getUuid();
        canvas.batch(() -> paint(canvas, tr, x, y, height, channel, rows, hints, self, ease, now));
    }

    private static void paint(TabletCanvas c, TextRenderer tr, int x, int y, int height, TabletChannel channel,
                              List<TabletSnapshot.ChatRow> rows, List<Hint> hints, UUID self, float alpha, long now) {
        TabletTheme.Accent accent = TabletTheme.accentFor(channel);
        c.shadow(x, y, WIDTH, height, RADIUS, 10f, fade(0x70000000, alpha));
        c.roundRect(x, y, WIDTH, height, RADIUS, fade(CARD, alpha));
        c.roundRectOutline(x, y, WIDTH, height, RADIUS, 1f, fade(TabletTheme.HAIRLINE, alpha));
        // Header: channel dot + name, the chat glyph on the right. 页眉：频道圆点与名称，右侧为聊天图标。
        c.circle(x + PAD + 2.5f, y + HEADER_H / 2f + 0.5f, 2.5f, fade(accent.base(), alpha));
        String title = TabletCanvas.trim(tr, Text.translatable(channel.translationKey()).getString(), WIDTH - 2 * PAD - 24);
        c.text(tr, title, x + PAD + 9, y + (HEADER_H - 8) / 2 + 1, fade(TabletTheme.TEXT, alpha));
        TabletIcons.chat(c, x + WIDTH - PAD - 9, y + (HEADER_H - 9) / 2f + 0.5f, 9f, fade(TabletTheme.TEXT_3, alpha));
        int lineY = y + HEADER_H;
        c.rect(x + PAD, lineY, WIDTH - 2 * PAD, c.px(), fade(TabletTheme.HAIRLINE, alpha));

        int rowY = lineY + 4;
        if (rows.isEmpty()) {
            c.text(tr, Text.translatable(KEY + "empty").getString(), x + PAD, rowY + 2, fade(TabletTheme.TEXT_3, alpha));
            rowY += ROW_H + 2;
        } else {
            String anonymous = Text.translatable("screen.sparkstrength.tablet.chat.anonymous").getString();
            String you = Text.translatable(KEY + "you").getString();
            float glow = glowFraction(now);
            for (int index = 0; index < rows.size(); index++) {
                TabletSnapshot.ChatRow row = rows.get(index);
                boolean newest = index == rows.size() - 1;
                if (newest && glow > 0f) {
                    c.roundRect(x + PAD - 3, rowY - 1, WIDTH - 2 * PAD + 6, ROW_H, 3f,
                            fade(TabletTheme.withAlpha(accent.base(), Math.round(0x40 * glow)), alpha));
                }
                paintRow(c, tr, x + PAD, rowY, row, self, anonymous, you, accent, alpha);
                rowY += ROW_H;
            }
        }

        if (!hints.isEmpty()) {
            rowY += 1;
            c.rect(x + PAD, rowY, WIDTH - 2 * PAD, c.px(), fade(TabletTheme.HAIRLINE, alpha));
            rowY += 4;
            for (Hint hint : hints) {
                TabletTheme.Accent hintAccent = TabletTheme.accentFor(hint.channel());
                c.circle(x + PAD + 2.5f, rowY + 4f, 2.5f, fade(hintAccent.base(), alpha));
                String count = Text.translatable(KEY + "unread", hint.count()).getString();
                int countW = tr.getWidth(count);
                String name = TabletCanvas.trim(tr, Text.translatable(hint.channel().translationKey()).getString(),
                        WIDTH - 2 * PAD - 9 - countW - 6);
                c.text(tr, name, x + PAD + 9, rowY, fade(TabletTheme.TEXT_2, alpha));
                c.textRight(tr, count, x + WIDTH - PAD, rowY, fade(hintAccent.pale(), alpha));
                rowY += FOOTER_ROW_H;
            }
        }
    }

    private static void paintRow(TabletCanvas c, TextRenderer tr, int x, int y, TabletSnapshot.ChatRow row,
                                 UUID self, String anonymous, String you, TabletTheme.Accent accent, float alpha) {
        boolean hidden = row.isAnonymous() || row.senderUuid() == null;
        boolean own = !hidden && self.equals(row.senderUuid());
        int avatarY = y;
        if (hidden) {
            c.circle(x + AVATAR / 2f, avatarY + AVATAR / 2f, AVATAR / 2f, fade(TabletTheme.ANONYMOUS_DISC, alpha));
            c.text(tr, "?", x + 2, avatarY, fade(TabletTheme.TEXT_2, alpha));
        } else if (alpha >= 0.95f) {
            // A skin is a texture draw: it cannot fade, so it waits for the card to finish fading in.
            // 皮肤是贴图绘制，无法渐隐，因此等卡片淡入结束后再绘制。
            c.avatar(row.senderUuid(), row.senderName(), x, avatarY, AVATAR, 1.5f, CARD);
        }
        int textX = x + AVATAR + 4;
        String name = TabletCanvas.trim(tr, hidden ? anonymous : own ? you : row.senderName(), NAME_MAX_W);
        int nameColor = hidden ? TabletTheme.TEXT_3 : own ? TabletTheme.TEXT_2 : accent.pale();
        int nameW = c.text(tr, name, textX, y, fade(nameColor, alpha));
        int messageX = textX + nameW + 5;
        String message = TabletCanvas.trim(tr, row.message(), Math.max(0, x + WIDTH - 2 * PAD - messageX));
        c.text(tr, message, messageX, y, fade(TabletTheme.TEXT, alpha));
    }

    /** Other networks with messages not seen yet, in wire order (police, killer, witch, public). 其他有未读消息的网络。 */
    private static List<Hint> hints(TabletSnapshot snapshot, TabletChannel viewed) {
        List<Hint> hints = new ArrayList<>(3);
        for (TabletChannel channel : TabletChannel.values()) {
            int count = TabletClientState.unreadCount(channel);
            if (channel != viewed && count > 0 && snapshot.allowedChannels().contains(channel)) {
                hints.add(new Hint(channel, count));
            }
        }
        return hints;
    }

    /** A chat change while the card is up lights the newest row. 卡片显示期间聊天变化会点亮最新一行。 */
    private static void trackGlow(List<TabletSnapshot.ChatRow> chat, long now) {
        long signature = TabletClientState.chatSignature(chat);
        boolean changed = signaturePrimed && signature != lastChatSignature;
        lastChatSignature = signature;
        signaturePrimed = true;
        if (changed && !chat.isEmpty() && now - shownSinceMs > FADE_MS) {
            glowStartMs = now;
        }
    }

    private static float glowFraction(long now) {
        long age = now - glowStartMs;
        if (age < 0L || age >= GLOW_MS) {
            return 0f;
        }
        return 1f - age / (float) GLOW_MS;
    }

    private static int fade(int color, float alpha) {
        return TabletTheme.multiplyAlpha(color, alpha);
    }

    /** Clears per-session state on disconnect. 断开连接时清除会话状态。 */
    public static void reset() {
        shownSinceMs = -1L;
        signaturePrimed = false;
        lastChatSignature = 0L;
        glowStartMs = Long.MIN_VALUE / 2;
    }

    private record Hint(TabletChannel channel, int count) {
    }
}
