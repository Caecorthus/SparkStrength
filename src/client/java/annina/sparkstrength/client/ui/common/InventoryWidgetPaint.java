package annina.sparkstrength.client.ui.common;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;

/**
 * Paint shared by the 16x16 inventory slot buttons (player heads, serums, page switches). Each widget keeps its own
 * colour, trigger condition (hover, first choice, cooldown) and click handling; only the drawing lives here.
 * 背包 16x16 槽位按钮（玩家头像、试剂、翻页）共用的绘制。颜色、触发条件（悬停、首选、冷却）与点击逻辑仍由各控件自己决定，
 * 这里只负责绘制。
 */
public final class InventoryWidgetPaint {
    private InventoryWidgetPaint() {
    }

    /**
     * Slot highlight on the GUI overlay layer, with the bottom-right corner stepped off by one pixel per row.
     * GUI 覆盖层上的槽位高亮，右下角逐行阶梯状裁掉一像素。
     */
    public static void highlight(DrawContext context, int x, int y, int color) {
        context.fillGradient(RenderLayer.getGuiOverlay(), x, y, x + 16, y + 14, color, color, 0);
        context.fillGradient(RenderLayer.getGuiOverlay(), x, y + 14, x + 15, y + 15, color, color, 0);
        context.fillGradient(RenderLayer.getGuiOverlay(), x, y + 15, x + 14, y + 16, color, color, 0);
    }

    /**
     * Red whole-second countdown centred on the slot; any remaining tick shows at least "1s".
     * 槽位中央的红色整秒倒计时；只要还剩冷却 tick 就至少显示“1s”。
     */
    public static void cooldownSeconds(DrawContext context, int x, int y, int cooldownTicks) {
        int remainingSeconds = Math.max(1, (int) Math.ceil(cooldownTicks / 20.0));
        String timeText = remainingSeconds + "s";
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        int textX = x + 8 - textRenderer.getWidth(timeText) / 2;
        int textY = y + 4;
        context.drawText(textRenderer, timeText, textX, textY, 0xFF5555, true);
    }
}
