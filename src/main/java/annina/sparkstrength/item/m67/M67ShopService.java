package annina.sparkstrength.item.m67;

import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.compat.SparkTraitsCompat;
import annina.sparkstrength.role.coroner.CoronerRules;
import annina.sparkstrength.role.coroner.CoronerService;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public final class M67ShopService {
    public static final String ENTRY_ID = "sparkstrength_m67";

    private M67ShopService() {
    }

    /** The completed shop is the permission boundary; never rebuild/query the shop here.
     *  最终商店列表是权限边界；此处绝不重建或再次查询商店。 */
    public static List<ShopEntry> appendToFinalEntries(PlayerEntity player, List<ShopEntry> entries) {
        boolean hasGrenade = false;
        for (ShopEntry entry : entries) {
            if (ENTRY_ID.equals(entry.id()) || entry.stack().isOf(SparkStrengthItems.m67())) {
                return entries;
            }
            hasGrenade |= entry.stack().isOf(WatheItems.GRENADE);
        }
        if (!hasGrenade) {
            return entries;
        }

        // Builder has no name/description API; display components must not enter the purchased stack.
        // Builder 无名称/描述接口；展示组件不应进入实际购买的物品堆。
        ItemStack display = SparkStrengthItems.m67().getDefaultStack();
        display.set(DataComponentTypes.ITEM_NAME, Text.translatable("shop.sparkstrength.m67"));
        display.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.translatable("shop.sparkstrength.m67.description")
                        .styled(style -> style.withColor(0x808080).withItalic(false)))));
        // Match M67 cooldown eligibility; stale disguises on other roles do not qualify.
        // 与 M67 冷却资格一致；其他职业残留的伪装不享受优惠。
        int price = CoronerRules.isBomber(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))
                || (CoronerService.isActualCoroner(player) && CoronerService.hasBomberDisguise(player))
                ? 75 : 100;
        ShopEntry m67 = new ShopEntry.Builder(ENTRY_ID, display, price, ShopEntry.Type.WEAPON)
                .actualStack(SparkStrengthItems.m67().getDefaultStack())
                .build();
        List<ShopEntry> result = new ArrayList<>(entries);
        result.add(SparkTraitsCompat.discountShopEntryForCharisma(player, m67));
        return result;
    }
}
