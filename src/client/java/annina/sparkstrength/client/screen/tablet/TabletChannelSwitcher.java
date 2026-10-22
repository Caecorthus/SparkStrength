package annina.sparkstrength.client.screen.tablet;

import annina.sparkstrength.client.screen.tablet.TabletTheme.Accent;
import annina.sparkstrength.compat.SparkWitchCompat;
import annina.sparkstrength.network.tablet.SelectTabletChannelC2SPacket;
import annina.sparkstrength.network.tablet.TabletSnapshot;
import annina.sparkstrength.tablet.TabletChannel;
import annina.sparkstrength.tablet.TabletLayout.Rect;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

import static annina.sparkstrength.client.screen.tablet.TabletSectionPainter.KEY;

/**
 * Channel switcher popover under the status-bar chip. While it is open it is modal: TabletScreen routes every click and
 * key here, and nothing underneath reacts to hover. Joining only sends a request; the server re-validates it.
 * 状态栏胶囊下方的频道切换浮层。打开时为模态：TabletScreen 把所有点击与按键交给它，下层元素不响应悬停。
 * 加入只发送请求，服务端会重新校验。
 */
final class TabletChannelSwitcher {
    private static final int PENDING_SWITCH_TICKS = 40;

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

    private final TabletScreen screen;
    private boolean open;
    private long openedAtMs;
    private int pendingChannelWire = TabletChannel.NO_CHANNEL_WIRE;
    private int pendingSwitchTicks;

    TabletChannelSwitcher(TabletScreen screen) {
        this.screen = screen;
    }

    boolean isOpen() {
        return open;
    }

    /** The screen drops the chat focus first. 界面会先移除聊天输入框的焦点。 */
    void open() {
        open = true;
        openedAtMs = Util.getMeasuringTimeMs();
        playClick();
    }

    void close() {
        open = false;
    }

    void tick() {
        if (pendingSwitchTicks > 0 && --pendingSwitchTicks == 0) {
            pendingChannelWire = TabletChannel.NO_CHANNEL_WIRE;
        }
    }

    void onChannelChanged() {
        open = false;
        pendingChannelWire = TabletChannel.NO_CHANNEL_WIRE;
        pendingSwitchTicks = 0;
    }

    static boolean isBlockedKey(int keyCode) {
        return keyCode == GLFW.GLFW_KEY_TAB
                || keyCode == GLFW.GLFW_KEY_LEFT
                || keyCode == GLFW.GLFW_KEY_RIGHT
                || keyCode == GLFW.GLFW_KEY_UP
                || keyCode == GLFW.GLFW_KEY_DOWN
                || keyCode == GLFW.GLFW_KEY_SPACE
                || keyCode == GLFW.GLFW_KEY_ENTER
                || keyCode == GLFW.GLFW_KEY_KP_ENTER;
    }

    /** Logical coordinates; always consumes the click. 逻辑坐标；总是消耗本次点击。 */
    boolean mouseClicked(TabletSnapshot snapshot, double x, double y, int button) {
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
            screen.closeSwitcher();
        }
        return true;
    }

    private void requestSwitch(TabletChannel channel) {
        ClientPlayNetworking.send(new SelectTabletChannelC2SPacket(channel.wire()));
        pendingChannelWire = channel.wire();
        pendingSwitchTicks = PENDING_SWITCH_TICKS;
        playClick();
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

    private static void playClick() {
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    /**
     * Switcher popover geometry; the single source for painting and hit testing.
     * 切换浮层几何；绘制与命中测试共用同一来源。
     */
    private SwitcherGeometry switcherGeometry(TabletSnapshot snapshot) {
        TextRenderer textRenderer = screen.textRenderer();
        Rect screenRect = screen.layout().screen();
        Rect chip = screen.channelChip(snapshot).rect();
        List<TabletChannel> channels = switcherChannels(snapshot);
        SwitcherHint hint = switcherHint(snapshot);
        int width = Math.min(SWITCHER_WIDTH, Math.max(0, screenRect.width() - 16));
        int minX = screenRect.x() + 8;
        int x = Math.max(minX, Math.min(screenRect.right() - 8 - width, chip.centerX() - width / 2));
        int y = chip.bottom() + SWITCHER_OFFSET;
        List<String> hintLines = hint == null ? List.of()
                : TabletSectionPainter.wrap(textRenderer, hint.text().getString(), width - 2 * SWITCHER_HINT_PAD, 2);

        int rowHeight = SWITCHER_ROW_H;
        int rowGap = SWITCHER_ROW_GAP;
        if (y + switcherHeight(channels.size(), hintLines.size(), rowHeight, rowGap) > screenRect.bottom() - 4) {
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

    // ============================================================================================ paint

    /**
     * Last in the frame, over everything else; {@code mouseX/Y} are the real mouse (hover is suppressed only underneath).
     * 整帧最后绘制，位于一切之上；mouseX/Y 为真实鼠标位置（只有下层元素不响应悬停）。
     */
    void paint(TabletSnapshot snapshot, int mouseX, int mouseY, long now) {
        TabletCanvas c = screen.canvas();
        TextRenderer textRenderer = screen.textRenderer();
        float fade = Math.min(1f, Math.max(0f, (now - openedAtMs) / (float) SWITCHER_FADE_MS));
        float eased = 1f - (1f - fade) * (1f - fade);
        SwitcherGeometry switcher = switcherGeometry(snapshot);
        Rect box = switcher.box();
        Rect screenRect = screen.layout().screen();

        c.roundRect(screenRect.x(), screenRect.y(), screenRect.width(), screenRect.height(),
                screen.layout().mode().screenRadius(), TabletTheme.multiplyAlpha(0x66000000, fade));

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
        TabletCanvas c = screen.canvas();
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
        TabletCanvas c = screen.canvas();
        TextRenderer textRenderer = screen.textRenderer();
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
            c.textCentered(textRenderer, TabletScreen.monogram(channel), badgeX + SWITCHER_BADGE / 2, badgeY + 6,
                    TabletTheme.WHITE);
        }

        int chipRight = row.right() - 6;
        int maxChip = Math.max(0, row.width() / 2);
        int chipX = switch (state) {
            case CURRENT -> screen.paintPill(chipRight, row.centerY(), maxChip, Text.translatable(KEY + "channel.current").getString(),
                    accent.pale(), TabletTheme.withAlpha(accent.base(), 0x40), 0, TabletIcons::check, accent.pale());
            case JOIN -> hovered
                    ? screen.paintPill(chipRight, row.centerY(), maxChip, Text.translatable(KEY + "channel.join").getString(),
                    TabletTheme.WHITE, accent.base(), 0, null, 0)
                    : screen.paintPill(chipRight, row.centerY(), maxChip, Text.translatable(KEY + "channel.join").getString(),
                    accent.base(), 0, TabletTheme.withAlpha(accent.base(), 0x99), null, 0);
            case PENDING -> screen.paintPill(chipRight, row.centerY(), maxChip, Text.translatable(KEY + "channel.pending").getString(),
                    TabletTheme.TEXT_2, TabletTheme.FAINT, 0,
                    (canvas, x, y, size, color) -> TabletIcons.spinner(canvas, x + size / 2f, y + size / 2f, size / 2f, now, color),
                    accent.pale());
            case COOLDOWN -> screen.paintPill(chipRight, row.centerY(), maxChip,
                    Text.translatable(KEY + "channel.cooldown", snapshot.channelSwitchCooldownSeconds()).getString(),
                    TabletTheme.TEXT_2, TabletTheme.FAINT, 0, TabletIcons::clock, TabletTheme.TEXT_2);
            case MEETING_LOCKED -> screen.paintPill(chipRight, row.centerY(), maxChip,
                    Text.translatable(KEY + "channel.meeting").getString(),
                    TabletTheme.WARNING, TabletTheme.withAlpha(TabletTheme.WARNING, 0x2E), 0, null, 0);
            case LOCKED -> screen.paintPill(chipRight, row.centerY(), maxChip, Text.translatable(KEY + "channel.locked").getString(),
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

    // ============================================================================================ types

    private enum SwitchState {
        CURRENT,
        JOIN,
        PENDING,
        COOLDOWN,
        MEETING_LOCKED,
        LOCKED
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
}
