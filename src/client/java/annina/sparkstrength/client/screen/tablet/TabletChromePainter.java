package annina.sparkstrength.client.screen.tablet;

import annina.sparkstrength.client.screen.tablet.TabletTheme.Accent;
import annina.sparkstrength.network.tablet.TabletSnapshot;
import annina.sparkstrength.tablet.TabletChannel;
import annina.sparkstrength.tablet.TabletLayout;
import annina.sparkstrength.tablet.TabletLayout.Rect;
import annina.sparkstrength.tablet.TabletUiSession.Section;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static annina.sparkstrength.client.screen.tablet.TabletFeatureOnly.featureOnly;
import static annina.sparkstrength.client.screen.tablet.TabletFeatureOnly.featureOnlySection;
import static annina.sparkstrength.client.screen.tablet.TabletSectionPainter.KEY;
import static annina.sparkstrength.client.screen.tablet.TabletSectionPainter.PILL_PAD;

/**
 * Paints the tablet frame around the open page (device, accent glow, close button, status bar, rail with tabs and
 * badges, NARROW tab tooltip, header title, no-signal body) and the frame-bound helpers pages and the
 * {@link TabletChannelSwitcher} draw with (pills, badge and pulse dots, the backdrop colour under the glow). It also
 * owns the chrome geometry that painting and hit testing share, such as the status-bar channel chip. Everything draws
 * on {@link TabletScreen#canvas()} with {@link TabletScreen#accent()}, inside the screen's single frame batch.
 * 绘制当前页面周围的平板外框（设备、强调色光晕、关闭按钮、状态栏、带标签与徽标的侧栏、窄模式标签提示、页眉标题、无信号主体），
 * 以及页面与频道切换浮层使用的外框绘制工具（胶囊、徽标与脉冲圆点、光晕下的背景色）。绘制与命中测试共用的外框几何
 * （如状态栏频道胶囊）也在这里。所有内容都在界面唯一的整帧批次内，以当前帧的画布与强调色绘制。
 */
final class TabletChromePainter {
    private static final DateTimeFormatter CLOCK_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

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

    private final TabletScreen screen;
    // The screen's rail widgets (rebuilt by its init()) and its pages, read for rail badges and the NARROW tooltip.
    // 界面的侧栏控件（由其 init() 重建）与页面，用于绘制侧栏徽标与窄模式提示。
    private final List<TabletPressable> tabButtons;
    private final List<Section> tabSections;
    private final Map<Section, TabletPage> pages;

    TabletChromePainter(TabletScreen screen, List<TabletPressable> tabButtons, List<Section> tabSections,
                        Map<Section, TabletPage> pages) {
        this.screen = screen;
        this.tabButtons = tabButtons;
        this.tabSections = tabSections;
        this.pages = pages;
    }

    // ============================================================================================ geometry

    /**
     * Status-bar channel chip; the single source for both painting and hit testing. Feature-only holders get a
     * static chip naming their feature (door monitor, drones) instead of "No Signal" and a switcher of locked rows.
     * 状态栏频道胶囊；绘制与命中测试共用。仅功能持有者显示不可点击、写着功能名（房门监控、无人机）的胶囊，
     * 而不是“无信号”与全锁定的切换浮层。
     */
    ChannelChip channelChip(TabletSnapshot snapshot) {
        TabletLayout layout = screen.layout();
        TextRenderer textRenderer = screen.textRenderer();
        Rect status = layout.statusBar();
        boolean live = snapshot.localHasTablet();
        Section feature = live ? featureOnlySection(snapshot, screen.session().section()) : null;
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
        TabletLayout layout = screen.layout();
        Rect close = layout.closeButton();
        Rect status = layout.statusBar();
        return new Rect(close.x() - 8 - SIGNAL_SIZE, status.centerY() - SIGNAL_SIZE / 2, SIGNAL_SIZE, SIGNAL_SIZE);
    }

    /** 2 px scroll track in the right gutter of a list area. 列表右侧留白中的 2 像素滚动条轨道。 */
    Rect scrollTrack(Rect area) {
        int offset = Math.max(2, screen.layout().mode().padX() / 3);
        return new Rect(area.right() + offset, area.y(), 2, area.height());
    }

    private Rect tabIconBox(TabletPressable tab) {
        if (screen.layout().mode() == TabletLayout.Mode.REGULAR) {
            return new Rect(tab.getX() + tab.getWidth() / 2 - 7, tab.getY() + 7, 14, 14);
        }
        return new Rect(tab.getX() + tab.getWidth() / 2 - 8, tab.getY() + (tab.getHeight() - 16) / 2, 16, 16);
    }

    // ============================================================================================ frame

    void paintFrame() {
        TabletCanvas c = screen.canvas();
        TabletLayout layout = screen.layout();
        TabletLayout.Mode mode = layout.mode();
        float logicalScale = screen.viewport().logicalScale();
        // Cover the whole GUI area: canvasWidth/Height are floored, so fb % e leftover pixels would stay undimmed.
        // 覆盖整个 GUI 区域：画布尺寸向下取整，否则 fb % e 的剩余像素不会被遮暗。
        c.rect(0, 0, (float) Math.ceil(screen.width / logicalScale), (float) Math.ceil(screen.height / logicalScale),
                TabletTheme.BACKDROP);

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

        Rect screenRect = layout.screen();
        c.roundRectGradientV(screenRect.x(), screenRect.y(), screenRect.width(), screenRect.height(), mode.screenRadius(),
                TabletTheme.SCREEN_TOP, TabletTheme.SCREEN_BOTTOM);
        Rect content = layout.content();
        c.pushClip(screenRect.x(), screenRect.y(), screenRect.width(), screenRect.height());
        c.glow(content.x() + GLOW_DX, content.y() + GLOW_DY, GLOW_RADIUS,
                TabletTheme.withAlpha(screen.accent().base(), GLOW_ALPHA));
        c.popClip();
    }

    /** Screen gradient colour at a canvas y, without the accent glow. 屏幕渐变在某画布 y 处的颜色（不含强调色光晕）。 */
    private int screenColorAt(int y) {
        TabletLayout layout = screen.layout();
        if (layout == null) {
            return TabletTheme.mix(TabletTheme.SCREEN_TOP, TabletTheme.SCREEN_BOTTOM, 0.5f);
        }
        Rect screenRect = layout.screen();
        float t = screenRect.height() <= 0 ? 0f : (y - screenRect.y()) / (float) screenRect.height();
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
        TabletLayout layout = screen.layout();
        Accent frameAccent = screen.accent();
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

    /** The close button's look; TabletScreen builds the widget. 关闭按钮的外观；控件由 TabletScreen 构建。 */
    void paintCloseButton(TabletPressable button, boolean hovered) {
        TabletCanvas c = screen.canvas();
        float cx = button.getX() + button.getWidth() / 2f;
        float cy = button.getY() + button.getHeight() / 2f;
        float radius = button.getWidth() / 2f;
        c.circle(cx, cy, radius, hovered ? TabletTheme.withAlpha(TabletTheme.DANGER, 0x59) : 0x33FFFFFF);
        TabletIcons.close(c, cx - 3.5f, cy - 3.5f, 7f, hovered ? TabletTheme.TEXT : TabletTheme.TEXT_2);
        if (button.keyboardFocused()) {
            c.ring(cx, cy, radius + 2f, 1f, screen.accent().pale());
        }
    }

    // ============================================================================================ status bar

    void paintStatusBar(TabletSnapshot snapshot, int hoverX, int hoverY, long now) {
        TabletCanvas c = screen.canvas();
        TabletLayout layout = screen.layout();
        Rect status = layout.statusBar();
        int textY = status.centerY() - 4;
        c.text(screen.textRenderer(), LocalTime.now().format(CLOCK_FORMAT), layout.screen().x() + CLOCK_X, textY,
                TabletTheme.TEXT_2);
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
        TabletCanvas c = screen.canvas();
        Accent frameAccent = screen.accent();
        ChannelChip chip = channelChip(snapshot);
        Rect rect = chip.rect();
        if (rect.width() <= 0) {
            return;
        }
        boolean lit = chip.lit();
        boolean hot = chip.clickable() && (screen.switcherOpen() || rect.contains(hoverX, hoverY));
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
        c.text(screen.textRenderer(), chip.name(), textX, textY, lit ? TabletTheme.TEXT : TabletTheme.TEXT_2);
        if (chip.clickable()) {
            TabletIcons.caret(c, rect.right() - PILL_PAD - CHIP_CARET, centerY - CHIP_CARET / 2f, CHIP_CARET,
                    screen.switcherOpen(), hot ? TabletTheme.TEXT : TabletTheme.TEXT_2);
            // Another network has unread messages: a dot in that network's colour. 其他网络有未读消息：以该网络颜色显示圆点。
            TabletChannel waiting = TabletClientState.firstUnreadOtherThan(snapshot.channel());
            if (waiting != null) {
                paintBadgeDot(new Rect(rect.x(), rect.y(), rect.width() - 3, rect.height()),
                        TabletTheme.accentFor(waiting).base());
            }
        }
    }

    private static int sweepBars(long now) {
        return (int) ((now / SIGNAL_SWEEP_MS) % 5L);
    }

    // ============================================================================================ rail

    void paintRail(TabletSnapshot snapshot, int hoverX, int hoverY) {
        TabletCanvas c = screen.canvas();
        TabletLayout layout = screen.layout();
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
        TabletCanvas c = screen.canvas();
        Accent frameAccent = screen.accent();
        TextRenderer textRenderer = screen.textRenderer();
        Rect badge = screen.layout().channelBadge();
        if (badge.width() <= 0 || badge.height() <= 0) {
            return;
        }
        TabletChannel channel = snapshot.channel();
        int textY = badge.y() + (badge.height() - 8) / 2;
        Section feature = featureOnlySection(snapshot, screen.session().section());
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
        if (screen.switcherOpen() || badge.contains(hoverX, hoverY)) {
            c.roundRectOutline(badge.x() - 2, badge.y() - 2, badge.width() + 4, badge.height() + 4, 11f, 1f,
                    TabletTheme.withAlpha(frameAccent.pale(), 0x80));
        }
    }

    void paintTab(TabletPressable tab, Section section, boolean hovered) {
        TabletCanvas c = screen.canvas();
        Accent frameAccent = screen.accent();
        TabletLayout layout = screen.layout();
        TextRenderer textRenderer = screen.textRenderer();
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
        TabletCanvas c = screen.canvas();
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
    void paintRailBadges(TabletSnapshot snapshot, long now) {
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
        screen.canvas().circle(cx, cy, 4f, TabletTheme.mix(screenBackdropAt((int) cx, (int) cy), 0xFF000000, 0.25f));
        screen.canvas().circle(cx, cy, 3f, color);
    }

    /** Breathing badge dot on a tab icon's top-right. 标签图标右上角的呼吸徽标圆点。 */
    void paintBadgePulse(Rect icon, int color, long now) {
        paintPulseDot(icon.right(), icon.y() + 1, 3f, color, now);
    }

    /** NARROW tabs are icon-only: show the short label in a pill to the right of the hovered tab. 窄模式悬停提示。 */
    void paintTabTooltip() {
        TabletLayout layout = screen.layout();
        if (layout.mode() != TabletLayout.Mode.NARROW || screen.switcherOpen()) {
            return;
        }
        TabletCanvas c = screen.canvas();
        TextRenderer textRenderer = screen.textRenderer();
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

    // ============================================================================================ header

    void paintHeader(TabletSnapshot snapshot, long now) {
        TabletLayout layout = screen.layout();
        TextRenderer textRenderer = screen.textRenderer();
        Rect header = layout.header();
        int padX = layout.mode().padX();
        int left = header.x() + padX;
        int right = header.right() - padX;
        int centerY = header.centerY();
        int chipX = screen.page().paintHeaderChip(snapshot, right, centerY, Math.max(0, (right - left) / 2), now);
        String title = TabletCanvas.trim(textRenderer,
                Text.translatable(screen.session().section().translationKey()).getString(), Math.max(0, chipX - 10 - left));
        screen.canvas().text(textRenderer, title, left, centerY - 4, TabletTheme.TEXT);
    }

    /**
     * Right-aligned pill (h=16) with an optional leading glyph; the text is trimmed to maxWidth. Returns the left x.
     * 右对齐胶囊（高 16），可带前置图形；文字按 maxWidth 截断。返回左边缘 x。
     */
    int paintPill(int right, int centerY, int maxWidth, String text, int textColor, int fill, int outline,
                  @Nullable Glyph glyph, int glyphColor) {
        TabletCanvas c = screen.canvas();
        TextRenderer textRenderer = screen.textRenderer();
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
        TabletSectionPainter.pulseDot(screen.canvas(), cx, cy, radius, color, now);
    }

    // ============================================================================================ no signal

    /**
     * Body for holders without a channel (or before the first snapshot lands): a centred column, no rail or header.
     * 无频道持有者（或首个快照到达前）的主体：居中内容，无侧栏与页眉。
     */
    void paintNoSignal(TabletSnapshot snapshot, long now) {
        TabletCanvas c = screen.canvas();
        TextRenderer textRenderer = screen.textRenderer();
        Rect content = screen.layout().content();
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

    /** Upper-cased first code point of the channel's short name. 频道简称的首个字符（大写）。 */
    static String monogram(TabletChannel channel) {
        String name = Text.translatable(channel.shortTranslationKey()).getString();
        if (name.isEmpty()) {
            return "?";
        }
        return new String(Character.toChars(Character.toUpperCase(name.codePointAt(0))));
    }

    // ============================================================================================ types

    @FunctionalInterface
    interface Glyph {
        void paint(TabletCanvas canvas, float x, float y, float size, int color);
    }

    /** {@code lit}: a live channel or a feature-only tablet (accent dot with glow, bright name). 已接入频道或仅功能平板（发光圆点、亮色名字）。 */
    record ChannelChip(Rect rect, String name, boolean clickable, boolean lit) {
    }
}
