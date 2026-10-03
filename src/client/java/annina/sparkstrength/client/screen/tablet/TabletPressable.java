package annina.sparkstrength.client.screen.tablet;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.text.Text;

/**
 * Tablet button whose whole look is supplied by a {@link Painter}; vanilla button sprites and message text are never
 * drawn. Clicks, keyboard activation, focus and the click sound stay vanilla.
 * 平板按钮：外观完全由 {@link Painter} 绘制，不绘制原版按钮贴图与文字；点击、键盘激活、焦点与点击音效沿用原版。
 */
public final class TabletPressable extends PressableWidget {
    private final Runnable action;
    private final Painter painter;
    private boolean selectedState;

    public TabletPressable(int x, int y, int width, int height, Text narration, Runnable action, Painter painter) {
        super(x, y, width, height, narration);
        this.action = action;
        this.painter = painter;
    }

    @Override
    public void onPress() {
        action.run();
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        painter.paint(context, this, isHovered() && active, delta);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        appendDefaultNarrations(builder);
    }

    /**
     * True when focus came from keyboard navigation (draw a focus ring only then).
     * 仅当焦点来自键盘导航时为 true（只在此时绘制焦点环）。
     */
    public boolean keyboardFocused() {
        return isFocused() && MinecraftClient.getInstance().getNavigationType().isKeyboard();
    }

    /**
     * Painter-visible "selected" look (current tab, chosen vote target...). Named selectedState because
     * {@link #isSelected()} is vanilla's hovered-or-focused query and must keep that meaning.
     * 供绘制器读取的“选中”外观状态；不用 isSelected 命名，因为原版 isSelected 表示悬停或聚焦，不能覆盖其含义。
     */
    public boolean selectedState() {
        return selectedState;
    }

    public void setSelectedState(boolean selectedState) {
        this.selectedState = selectedState;
    }

    @FunctionalInterface
    public interface Painter {
        void paint(DrawContext context, TabletPressable widget, boolean hovered, float delta);
    }
}
