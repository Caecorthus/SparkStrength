package annina.sparkstrength.client.ui.shop;

import annina.sparkstrength.client.ui.common.PlayerPageSwitchWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/**
 * The shop row's page switch. Its own type so that {@code ShopRowOverflowMixin} lays it out with the shop entries
 * and leaves the player-picker page buttons of other roles alone.
 * 商店行的翻页按钮。单独成类，让 {@code ShopRowOverflowMixin} 把它与商品一起排版，而不影响其他职业选人栏的翻页按钮。
 */
public final class ShopPageSwitchWidget extends PlayerPageSwitchWidget {
    public ShopPageSwitchWidget(int x, int y, ItemStack iconStack, Text tooltipText, PressAction onPress) {
        super(x, y, iconStack, tooltipText, onPress);
    }
}
