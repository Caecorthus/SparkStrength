package annina.sparkstrength.client.ui.common;

import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/**
 * 玩家头像分页按钮。
 *
 * <p>翻页按钮是独立控件，不继承玩家头像按钮，点击时只会翻页，
 * 不会误触发大嗓门点亮请求。</p>
 */
public class PlayerPageSwitchWidget extends ButtonWidget {
    private final ItemStack iconStack;
    private final Text tooltipText;

    public PlayerPageSwitchWidget(int x, int y, ItemStack iconStack, Text tooltipText, PressAction onPress) {
        super(x, y, 16, 16, tooltipText, onPress, DEFAULT_NARRATION_SUPPLIER);
        this.iconStack = iconStack;
        this.tooltipText = tooltipText;
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        context.drawGuiTexture(ShopEntry.Type.TOOL.getTexture(), getX() - 7, getY() - 7, 30, 30);
        context.drawItem(iconStack, getX(), getY());

        if (isHovered()) {
            drawHighlight(context);
            context.drawTooltip(MinecraftClient.getInstance().textRenderer, tooltipText, mouseX, mouseY);
        }
    }

    private void drawHighlight(DrawContext context) {
        int color = 0x90FFBF49;
        int x = getX();
        int y = getY();
        context.fillGradient(RenderLayer.getGuiOverlay(), x, y, x + 16, y + 14, color, color, 0);
        context.fillGradient(RenderLayer.getGuiOverlay(), x, y + 14, x + 15, y + 15, color, color, 0);
        context.fillGradient(RenderLayer.getGuiOverlay(), x, y + 15, x + 14, y + 16, color, color, 0);
    }
}
