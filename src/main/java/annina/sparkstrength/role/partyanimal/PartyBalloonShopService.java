package annina.sparkstrength.role.partyanimal;

import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.compat.SparkTraitsCompat;
import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Adds the Party Balloon (25) to the real Party Animal's shop, right after Wathe's firecracker. Built on both sides
 * from the synced role alone, because Wathe buys by index; not gated on round state, like the other shops.
 * 在真实派对狂的商店中加入派对气球（25），紧跟在 Wathe 鞭炮之后。两端只依据已同步的身份构建，因为 Wathe 按下标购买；
 * 与其他商店一样不依赖对局状态。
 */
public final class PartyBalloonShopService {
    private static boolean registered;

    private PartyBalloonShopService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BuildShopEntries.EVENT.register(PartyBalloonShopService::buildShopEntries);
    }

    private static void buildShopEntries(PlayerEntity player, BuildShopEntries.ShopContext context) {
        if (player == null
                || !PartyAnimalRules.isPartyAnimal(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))) {
            return;
        }
        List<String> ids = new ArrayList<>(context.size());
        for (int i = 0; i < context.size(); i++) {
            ids.add(context.getEntry(i).id());
        }
        Item balloon = SparkStrengthItems.partyBalloon();
        context.addEntry(PartyBalloonRules.shopInsertIndex(ids),
                SparkTraitsCompat.discountShopEntryForCharisma(player, new ShopEntry.Builder(
                        PartyBalloonRules.ENTRY_ID,
                        displayStack(balloon),
                        PartyBalloonRules.PRICE,
                        ShopEntry.Type.TOOL
                ).actualStack(balloon.getDefaultStack())
                        .onBuy(buyer -> giveStackable(buyer, balloon))
                        .build()));
    }

    /**
     * Server-side purchase: true charges the price, false charges nothing. Tops up an existing hotbar stack first,
     * because Wathe's default only fills an empty hotbar slot.
     * 服务端购买：返回 true 扣费，false 不扣费。优先补充快捷栏中已有的堆叠，因为 Wathe 默认只放入空的快捷栏槽位。
     */
    private static boolean giveStackable(PlayerEntity player, Item item) {
        ItemStack fresh = item.getDefaultStack();
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < PlayerInventory.getHotbarSize(); slot++) {
            ItemStack held = inventory.getStack(slot);
            if (ItemStack.areItemsAndComponentsEqual(held, fresh) && held.getCount() < held.getMaxCount()) {
                held.increment(1);
                return true;
            }
        }
        return ShopEntry.insertStackInFreeSlot(player, fresh);
    }

    // Builder has no name/description API; display components must not enter the purchased stack.
    // Builder 无名称/描述接口；展示组件不应进入实际购买的物品堆。
    private static ItemStack displayStack(Item item) {
        ItemStack display = item.getDefaultStack();
        display.set(DataComponentTypes.ITEM_NAME, Text.translatable("shop.sparkstrength.party_balloon"));
        display.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.translatable("shop.sparkstrength.party_balloon.description")
                        .styled(style -> style.withColor(0x808080).withItalic(false)))));
        return display;
    }
}
