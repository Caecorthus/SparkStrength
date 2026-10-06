package annina.sparkstrength.role.pathogen;

import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.component.pathogen.PathogenStrainComponent;
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

import java.util.List;

/**
 * The Pathogen's shop: Virus (50), then T-Virus (150, one per round) for an original Pathogen only. Built on both
 * sides from synced state alone (the real role plus the owner-synced converted flag), because Wathe buys by index.
 * Deliberately not gated on round state: Wathe caches {@code stock(1)} while the round is still STARTING. A converted
 * Pathogen gets its list rebuilt and its stock cached again by the revive. Entries are appended, never cleared, so any
 * other provider's entries stay put.
 * 病原体商店：病毒（50），原生病原体另有 T病毒（150，每局限 1）。两端只依据已同步的状态构建（真实身份与只同步给本人的
 * 转化标记），因为 Wathe 按下标购买。刻意不依赖对局状态：Wathe 在 STARTING 阶段就缓存 {@code stock(1)}。转化而来的病原体
 * 由复活流程重建列表并重新缓存库存。条目只追加不清空，其他提供方的条目保持不变。
 */
public final class PathogenShopService {
    private static boolean registered;

    private PathogenShopService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BuildShopEntries.EVENT.register(PathogenShopService::buildShopEntries);
    }

    private static void buildShopEntries(PlayerEntity player, BuildShopEntries.ShopContext context) {
        if (player == null
                || !PathogenRules.isPathogen(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))) {
            return;
        }
        boolean converted = PathogenStrainComponent.KEY.get(player).isConverted();
        for (String entryId : PathogenRules.shopEntryIds(converted)) {
            context.addEntry(SparkTraitsCompat.discountShopEntryForCharisma(player, entry(entryId)));
        }
    }

    private static ShopEntry entry(String entryId) {
        if (PathogenRules.T_VIRUS_ENTRY_ID.equals(entryId)) {
            Item tVirus = SparkStrengthItems.tVirus();
            return new ShopEntry.Builder(
                    PathogenRules.T_VIRUS_ENTRY_ID,
                    displayStack(tVirus, "t_virus"),
                    PathogenRules.T_VIRUS_PRICE,
                    ShopEntry.Type.POISON
            ).actualStack(tVirus.getDefaultStack())
                    .stock(PathogenRules.T_VIRUS_STOCK)
                    .onBuy(PathogenShopService::buyTVirus)
                    .build();
        }
        Item virus = SparkStrengthItems.virus();
        return new ShopEntry.Builder(
                PathogenRules.VIRUS_ENTRY_ID,
                displayStack(virus, "virus"),
                PathogenRules.VIRUS_PRICE,
                ShopEntry.Type.POISON
        ).actualStack(virus.getDefaultStack())
                .onBuy(buyer -> giveStackable(buyer, virus))
                .build();
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

    /** Hard cap behind {@code stock(1)}; false charges nothing. / stock(1) 之外的硬上限；返回 false 不扣费。 */
    private static boolean buyTVirus(PlayerEntity player) {
        PathogenStrainComponent strain = PathogenStrainComponent.KEY.get(player);
        if (!PathogenRules.canBuyTVirus(strain.isConverted(), strain.getTVirusPurchases())) {
            return false;
        }
        if (!ShopEntry.insertStackInFreeSlot(player, SparkStrengthItems.tVirus().getDefaultStack())) {
            return false;
        }
        strain.recordTVirusPurchase();
        return true;
    }

    // Builder has no name/description API; display components must not enter the purchased stack.
    // Builder 无名称/描述接口；展示组件不应进入实际购买的物品堆。
    private static ItemStack displayStack(Item item, String key) {
        ItemStack display = item.getDefaultStack();
        display.set(DataComponentTypes.ITEM_NAME, Text.translatable("shop.sparkstrength." + key));
        display.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.translatable("shop.sparkstrength." + key + ".description")
                        .styled(style -> style.withColor(0x808080).withItalic(false)))));
        return display;
    }
}
