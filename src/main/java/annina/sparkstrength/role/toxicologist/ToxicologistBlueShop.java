package annina.sparkstrength.role.toxicologist;

import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.compat.SparkTraitsBluePoisonCompat;
import dev.doctor4t.wathe.api.event.ShopPurchase;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Blue Vitriol and Blue Belladonna shop entries, shared by the Toxicologist shop and the Coroner's Toxicologist
 * disguise shop so both stay identical.
 * 蓝矾与蓝颠茄商店条目，由毒理学家商店与验尸官的毒理学家伪装商店共用，保证两边完全一致。
 *
 * <p>Wathe buys by list index and rebuilds the list on both sides, so the entries are always listed even when
 * SparkTraits is missing; availability is only checked server-side when buying.
 * Wathe 按下标购买且双端各自重建列表，因此即使缺少 SparkTraits 也始终列出；只在服务端购买时检查是否可用。</p>
 */
public final class ToxicologistBlueShop {
    private static boolean registered;

    private ToxicologistBlueShop() {
    }

    /**
     * Deny-only purchase guard: Wathe shows the deny reason on the action bar, whereas a message sent from a false
     * onBuy would be overwritten by Wathe's generic purchase_failed in the same tick. It never allows, so later
     * listeners still run; onBuy keeps its own check as the fail-closed fallback.
     * 仅拒绝的购买守卫：Wathe 会在动作栏显示拒绝原因；若只在 onBuy 返回 false 时发消息，会在同一 tick 被通用的
     * purchase_failed 覆盖。从不放行，后续监听器照常执行；onBuy 仍保留自身检查作为兜底。
     */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ShopPurchase.BEFORE.register(ToxicologistBlueShop::denyWhenUnavailable);
    }

    public static List<ShopEntry> entries() {
        return List.of(
                new ShopEntry.Builder(
                        ToxicologistBlueRules.BLUE_VITRIOL_ENTRY_ID,
                        displayStack(SparkStrengthItems.blueVitriol(), "shop.sparkstrength.toxicologist.blue_vitriol"),
                        ToxicologistBlueRules.BLUE_VITRIOL_PRICE,
                        ShopEntry.Type.POISON
                ).onBuy(ToxicologistBlueShop::buyBlueVitriol).build(),
                new ShopEntry.Builder(
                        ToxicologistBlueRules.BLUE_BELLADONNA_ENTRY_ID,
                        displayStack(SparkStrengthItems.blueBelladonna(), "shop.sparkstrength.toxicologist.blue_belladonna"),
                        ToxicologistBlueRules.BLUE_BELLADONNA_PRICE,
                        ShopEntry.Type.POISON
                ).onBuy(ToxicologistBlueShop::buyBlueBelladonna).build()
        );
    }

    private static ShopPurchase.PurchaseResult denyWhenUnavailable(ServerPlayerEntity player, ShopEntry entry, int index) {
        if (isBlueEntry(entry) && !SparkTraitsBluePoisonCompat.isBluePoisonApiAvailable()) {
            return ShopPurchase.PurchaseResult.deny(ToxicologistBlueVitriolService.UNAVAILABLE_KEY);
        }
        return null;
    }

    private static boolean isBlueEntry(ShopEntry entry) {
        return ToxicologistBlueRules.BLUE_VITRIOL_ENTRY_ID.equals(entry.id())
                || ToxicologistBlueRules.BLUE_BELLADONNA_ENTRY_ID.equals(entry.id());
    }

    private static boolean buyBlueVitriol(PlayerEntity buyer) {
        if (!SparkTraitsBluePoisonCompat.isBluePoisonApiAvailable()) {
            return false;
        }
        return giveToHotbar(buyer, SparkStrengthItems.blueVitriol().getDefaultStack());
    }

    /** The fruit is stamped with the buyer's blue marker, so whoever eats it is credited to the buyer.
     *  果实写入购买者的蓝毒标记，任何人吃下都记在购买者名下。 */
    private static boolean buyBlueBelladonna(PlayerEntity buyer) {
        if (!SparkTraitsBluePoisonCompat.isBluePoisonApiAvailable()) {
            return false;
        }
        ItemStack fruit = SparkStrengthItems.blueBelladonna().getDefaultStack();
        if (!SparkTraitsBluePoisonCompat.markStackBluePoison(fruit, buyer.getUuid())
                || !buyer.getUuid().equals(SparkTraitsBluePoisonCompat.getStackBluePoisoner(fruit))) {
            return false;
        }
        return giveToHotbar(buyer, fruit);
    }

    /**
     * Hotbar-only delivery like Wathe's default, but stacks onto an equal stack first; false means not charged.
     * 与 Wathe 默认一样只放入快捷栏，但优先叠加到相同物品上；返回 false 表示不扣费。
     */
    static boolean giveToHotbar(PlayerEntity buyer, ItemStack bought) {
        PlayerInventory inventory = buyer.getInventory();
        int slot = ToxicologistBlueItemRules.hotbarSlot(
                ToxicologistBlueItemRules.HOTBAR_SIZE,
                index -> canMerge(inventory.getStack(index), bought),
                index -> inventory.getStack(index).isEmpty()
        );
        if (slot == ToxicologistBlueItemRules.NO_SLOT) {
            return false;
        }
        ItemStack existing = inventory.getStack(slot);
        if (existing.isEmpty()) {
            inventory.setStack(slot, bought);
        } else {
            existing.increment(bought.getCount());
        }
        return true;
    }

    private static boolean canMerge(ItemStack existing, ItemStack bought) {
        return !existing.isEmpty()
                && ItemStack.areItemsAndComponentsEqual(existing, bought)
                && existing.getCount() + bought.getCount() <= existing.getMaxCount();
    }

    private static ItemStack displayStack(Item item, String translationKey) {
        ItemStack stack = item.getDefaultStack();
        stack.set(DataComponentTypes.ITEM_NAME, Text.translatable(translationKey));
        stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.translatable(translationKey + ".description")
                        .styled(style -> style.withColor(0x808080).withItalic(false))
        )));
        return stack;
    }
}
