package annina.sparkstrength.client.mixin.wathe;

import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.client.ui.shop.ShopPageSwitchWidget;
import annina.sparkstrength.client.ui.shop.ShopPages;
import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedHandledScreen;
import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedInventoryScreen;
import dev.doctor4t.wathe.index.WatheItems;
import net.minecraft.client.gui.Element;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
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
 * Splits a shop that has second-page entries ({@link ShopPages}) into two pages: entries of the other page are hidden,
 * the rest are re-centred in Wathe's row and a switch button is appended. Priority 900 so this runs before
 * {@link ShopRowOverflowMixin} (default 1000), which then wraps whatever is still too wide. Shops without second-page
 * entries are not touched.
 * 把含有第二页商品（见 {@link ShopPages}）的商店分成两页：隐藏另一页的商品，其余在 Wathe 的行内重新居中，并在末尾加上
 * 翻页按钮。优先级 900，先于 {@link ShopRowOverflowMixin}（默认 1000）执行，后者再对仍然过宽的行换行。没有第二页商品的
 * 商店不受影响。
 */
@Mixin(value = LimitedInventoryScreen.class, priority = 900)
public abstract class ShopPageMixin extends LimitedHandledScreen<PlayerScreenHandler> {
    /** Wathe 1.5.6 row geometry (LimitedInventoryScreen#init). / Wathe 1.5.6 的行几何。 */
    @Unique private static final int SLOT_PITCH = 38;
    @Unique private static final int ROW_X_OFFSET = 9;

    protected ShopPageMixin(PlayerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void sparkstrength$splitShopPages(CallbackInfo ci) {
        List<LimitedInventoryScreen.StoreItemWidget> entries = new ArrayList<>();
        boolean secondPage = false;
        for (Element child : children()) {
            if (child instanceof LimitedInventoryScreen.StoreItemWidget entry) {
                entries.add(entry);
                secondPage |= ShopPages.pageOf(entry.entry) == 1;
            }
        }
        if (!secondPage) {
            return;
        }
        int page = ShopPages.current();
        int rowY = entries.get(0).getY();
        List<LimitedInventoryScreen.StoreItemWidget> shown = new ArrayList<>();
        for (LimitedInventoryScreen.StoreItemWidget entry : entries) {
            boolean onPage = ShopPages.pageOf(entry.entry) == page;
            entry.visible = onPage;
            entry.active = onPage;
            if (onPage) {
                shown.add(entry);
            }
        }
        int slots = shown.size() + 1;
        int firstX = width / 2 - slots * SLOT_PITCH / 2 + ROW_X_OFFSET;
        for (int index = 0; index < shown.size(); index++) {
            shown.get(index).setX(firstX + index * SLOT_PITCH);
            shown.get(index).setY(rowY);
        }
        // The button shows where it leads: the revolver for the workbench page, the knife for the regular shop.
        // 按钮图标表示去向：左轮代表改装页，刀代表常规商店。
        addDrawableChild(new ShopPageSwitchWidget(firstX + shown.size() * SLOT_PITCH, rowY,
                page == 0 ? new ItemStack(SparkStrengthItems.banditRevolver()) : new ItemStack(WatheItems.KNIFE),
                Text.translatable(page == 0 ? "shop.sparkstrength.page.workbench" : "shop.sparkstrength.page.regular"),
                button -> {
                    ShopPages.toggle();
                    clearAndInit();
                }));
    }
}
