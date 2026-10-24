package annina.sparkstrength.role.undercover;

import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.component.undercover.UndercoverPlayerComponent;
import annina.sparkstrength.compat.SparkTraitsCompat;
import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.List;

/**
 * The Undercover's shop: the CQCM mask (150), once per round. Wathe opens a shop for any role that gets entries and
 * buys by index, so the list depends only on the synced role. Not gated on being alive: Wathe caches stock(1) from
 * this list before the round turns ACTIVE.
 * 卧底商店：CQCM 面具（150），每局限购一次。Wathe 只要有条目就开放商店并按下标购买，所以列表只取决于已同步的身份。
 * 不检查存活：Wathe 在对局变为 ACTIVE 前就从此列表缓存 stock(1)。
 */
public final class UndercoverShopService {
    public static final String MASK_ENTRY_ID = "sparkstrength_cqcm_mask";
    private static boolean registered;

    private UndercoverShopService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BuildShopEntries.EVENT.register(UndercoverShopService::buildShopEntries);
    }

    private static void buildShopEntries(PlayerEntity player, BuildShopEntries.ShopContext context) {
        if (!UndercoverRoles.isUndercover(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))) {
            return;
        }
        Item mask = SparkStrengthItems.cqcmMask();
        context.addEntry(SparkTraitsCompat.discountShopEntryForCharisma(player, new ShopEntry.Builder(
                MASK_ENTRY_ID,
                displayStack(mask),
                UndercoverRules.MASK_PRICE,
                ShopEntry.Type.TOOL
        ).actualStack(mask.getDefaultStack()).stock(1).onBuy(UndercoverShopService::buyMask).build()));
    }

    /** Server purchase handler: true charges the price, false charges nothing. / 服务端购买处理：true 扣费，false 不扣费。 */
    private static boolean buyMask(PlayerEntity buyer) {
        UndercoverPlayerComponent state = UndercoverPlayerComponent.KEY.get(buyer);
        Item mask = SparkStrengthItems.cqcmMask();
        if (state.isMaskBought() || state.isStage2() || buyer.getInventory().contains(stack -> stack.isOf(mask))
                || buyer.getEquippedStack(EquipmentSlot.HEAD).isOf(mask)) {
            return false;
        }
        if (!ShopEntry.insertStackInFreeSlot(buyer, mask.getDefaultStack())) {
            return false;
        }
        state.markMaskBought();
        return true;
    }

    private static ItemStack displayStack(Item mask) {
        ItemStack stack = mask.getDefaultStack();
        stack.set(DataComponentTypes.ITEM_NAME, Text.translatable("shop.sparkstrength.undercover.cqcm_mask"));
        stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.translatable("shop.sparkstrength.undercover.cqcm_mask.description")
                        .styled(style -> style.withColor(0x808080).withItalic(false))
        )));
        return stack;
    }
}
