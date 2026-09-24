package annina.sparkstrength.client.mixin.wathe;

import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedHandledScreen;
import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedInventoryScreen;
import net.minecraft.client.gui.Element;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps every Wathe shop entry on-screen when Wathe's single shop row is wider than the screen.
 * 当 Wathe 的单行商店宽于屏幕时，保证每个商品都留在屏幕内且可点击。
 *
 * <p>Wathe centres all entries in one 38px row with no wrapping, so a 14-entry killer shop already
 * starts off-screen at the automatic 1080p GUI width (480). This runs after Wathe has built the row
 * and only moves the existing buttons. Icon, frame, price, stock, highlight and click hitbox all
 * derive from {@code getX()/getY()}, so they move together. When the original row fits, nothing is
 * touched. Extra rows stack upward because the hovered-item name tooltip and the hotbar sit below.</p>
 * <p>Wathe 把所有商品居中排成一行（间距 38px，不换行），14 个商品的杀手商店在 1080p 自动界面宽度
 * （480）下首个商品就会出屏。这里在 Wathe 建好按钮后只移动已有按钮：图标、边框、价格、库存、高亮和
 * 点击范围都来自 {@code getX()/getY()}，因此同步移动。原单行放得下时不做任何改动。新增行只向上堆叠，
 * 因为下方是悬停物品名称提示和快捷栏。</p>
 */
@Mixin(LimitedInventoryScreen.class)
public abstract class ShopRowOverflowMixin extends LimitedHandledScreen<PlayerScreenHandler> {
    // Geometry mirrored from Wathe 1.5.6 LimitedInventoryScreen / StoreItemWidget.
    // 以下几何常量对应 Wathe 1.5.6 的 LimitedInventoryScreen / StoreItemWidget。
    @Unique private static final int SLOT_PITCH = 38;
    @Unique private static final int FRAME_INSET = 7;
    @Unique private static final int FRAME_SIZE = 30;
    // Price text top above the button: drawn at y-9 and lifted 12 by the tooltip positioner; its box adds a 4px border.
    // 价格文字顶部相对按钮的高度：绘制于 y-9，定位器再上移 12；提示框另有 4px 边框。
    @Unique private static final int PRICE_TEXT_TOP = 21;
    @Unique private static final int PRICE_TOP = PRICE_TEXT_TOP + 4;
    // An upper row's frame ends exactly where the lower row's price tooltip begins.
    // 上一行边框底部恰好接到下一行价格提示框顶部，互不遮挡。
    @Unique private static final int ROW_PITCH = FRAME_SIZE - FRAME_INSET + PRICE_TOP;
    @Unique private static final int EDGE_MARGIN = 2;

    protected ShopRowOverflowMixin(PlayerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void sparkstrength$keepShopEntriesOnScreen(CallbackInfo ci) {
        List<LimitedInventoryScreen.StoreItemWidget> entries = new ArrayList<>();
        for (Element child : children()) {
            if (child instanceof LimitedInventoryScreen.StoreItemWidget entry) {
                entries.add(entry);
            }
        }
        int count = entries.size();
        if (count < 2) {
            return;
        }

        int rowY = entries.get(0).getY();
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        for (LimitedInventoryScreen.StoreItemWidget entry : entries) {
            if (entry.getY() != rowY) {
                // Not Wathe's single row any more; leave whichever layout produced it alone.
                // 已不是 Wathe 的单行布局，交由产生该布局的一方处理。
                return;
            }
            minX = Math.min(minX, entry.getX());
            maxX = Math.max(maxX, entry.getX());
        }
        if (minX - FRAME_INSET >= 0 && maxX - FRAME_INSET + FRAME_SIZE <= width) {
            return;
        }

        int usableWidth = Math.max(FRAME_SIZE, width - 2 * EDGE_MARGIN);
        int perRowAtFullPitch = (usableWidth - FRAME_SIZE) / SLOT_PITCH + 1;
        // Only add rows whose price text stays on-screen; otherwise tighten the pitch instead.
        // 只增加价格文字仍在屏幕内的行，放不下时改为收紧间距。
        int maxRows = 1 + Math.max(0, (rowY - PRICE_TEXT_TOP) / ROW_PITCH);
        int rows = Math.min(maxRows, Math.ceilDiv(count, perRowAtFullPitch));
        int widestRow = Math.ceilDiv(count, rows);
        int pitch = widestRow > 1
                ? Math.max(1, Math.min(SLOT_PITCH, (usableWidth - FRAME_SIZE) / (widestRow - 1)))
                : SLOT_PITCH;

        // Reading order is top-left first; upper rows take the shorter share so they stay narrower.
        // 按从上到下、从左到右排列；较短的行放在上方，使上方行更窄。
        int shortRowSize = count / rows;
        int longRows = count % rows;
        int index = 0;
        for (int row = 0; row < rows; row++) {
            int rowSize = shortRowSize + (row >= rows - longRows ? 1 : 0);
            int span = (rowSize - 1) * pitch + FRAME_SIZE;
            int firstX = (width - span) / 2 + FRAME_INSET;
            int y = rowY - (rows - 1 - row) * ROW_PITCH;
            for (int column = 0; column < rowSize; column++) {
                LimitedInventoryScreen.StoreItemWidget entry = entries.get(index++);
                entry.setX(firstX + column * pitch);
                entry.setY(y);
            }
        }
    }
}
