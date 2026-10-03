package annina.sparkstrength.role.perfumer;

import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.component.perfumer.PerfumerScentComponent;
import annina.sparkstrength.compat.SparkTraitsCompat;
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
 * Appends the SparkStrength Perfumer kit to the Perfumer's final shop list (after SparkWitch's own entries).
 * 在调香师最终商店列表（SparkWitch 自身条目之后）追加 SparkStrength 调香师道具。
 */
public final class PerfumerShopService {
    private PerfumerShopService() {
    }

    /**
     * Called from {@code M67ShopEntriesMixin} on both sides; Wathe buys by index, so the answer must only depend on
     * synced state (the real role). Deliberately not gated on {@code isPlayerPlayingAndAlive}: Wathe's
     * {@code initializeShopsForPlayers} caches Zephyr's {@code stock(1)} from this list before the round is ACTIVE.
     * 由 M67ShopEntriesMixin 在两端调用；Wathe 按下标购买，因此结果只能依赖已同步状态（真实身份）。
     * 刻意不检查 isPlayerPlayingAndAlive：Wathe 在对局变为 ACTIVE 前就从此列表缓存晨风香水的 stock(1)。
     */
    public static List<ShopEntry> appendToFinalEntries(PlayerEntity player, List<ShopEntry> entries) {
        if (player == null
                || !PerfumerRules.isPerfumer(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))) {
            return entries;
        }

        List<ShopEntry> result = null;
        Item coolingOil = SparkStrengthItems.coolingOil();
        if (!contains(entries, PerfumerRules.COOLING_OIL_ENTRY_ID, coolingOil)) {
            result = add(result, entries, player, new ShopEntry.Builder(
                    PerfumerRules.COOLING_OIL_ENTRY_ID,
                    displayStack(coolingOil, "cooling_oil"),
                    PerfumerRules.COOLING_OIL_PRICE,
                    ShopEntry.Type.TOOL
            ).actualStack(coolingOil.getDefaultStack())
                    .onBuy(buyer -> giveStackable(buyer, coolingOil))
                    .build());
        }
        Item aromaOrb = SparkStrengthItems.aromaOrb();
        if (!contains(entries, PerfumerRules.AROMA_ORB_ENTRY_ID, aromaOrb)) {
            result = add(result, entries, player, new ShopEntry.Builder(
                    PerfumerRules.AROMA_ORB_ENTRY_ID,
                    displayStack(aromaOrb, "aroma_orb"),
                    PerfumerRules.AROMA_ORB_PRICE,
                    ShopEntry.Type.TOOL
            ).actualStack(aromaOrb.getDefaultStack())
                    .onBuy(buyer -> giveStackable(buyer, aromaOrb))
                    .build());
        }
        Item zephyrPerfume = SparkStrengthItems.zephyrPerfume();
        if (!contains(entries, PerfumerRules.ZEPHYR_PERFUME_ENTRY_ID, zephyrPerfume)) {
            result = add(result, entries, player, new ShopEntry.Builder(
                    PerfumerRules.ZEPHYR_PERFUME_ENTRY_ID,
                    displayStack(zephyrPerfume, "zephyr_perfume"),
                    PerfumerRules.ZEPHYR_PERFUME_PRICE,
                    ShopEntry.Type.TOOL
            ).actualStack(zephyrPerfume.getDefaultStack())
                    .stock(PerfumerRules.ZEPHYR_PERFUME_STOCK)
                    .onBuy(buyer -> buyZephyr(buyer, zephyrPerfume))
                    .build());
        }
        return result == null ? entries : result;
    }

    /**
     * Server-side purchase handler (PlayerShopComponent.tryBuy): true charges the price, false charges nothing.
     * Tops up an existing hotbar stack first, because Wathe's default only fills an empty hotbar slot.
     * 服务端购买处理（PlayerShopComponent.tryBuy）：返回 true 扣费，false 不扣费。
     * 优先补充快捷栏中已有的同类堆叠，因为 Wathe 默认只会放入空的快捷栏槽位。
     */
    private static boolean giveStackable(PlayerEntity player, Item item) {
        ItemStack fresh = item.getDefaultStack();
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < PlayerInventory.getHotbarSize(); slot++) {
            ItemStack held = inventory.getStack(slot);
            if (ItemStack.areItemsAndComponentsEqual(held, fresh)
                    && held.getCount() < Math.min(PerfumerRules.THROWABLE_MAX_STACK, held.getMaxCount())) {
                held.increment(1);
                return true;
            }
        }
        return ShopEntry.insertStackInFreeSlot(player, fresh);
    }

    /**
     * Hard cap behind {@code stock(1)}: stock is cached only for roles held at round start, so a mid-round
     * Perfumer would otherwise have unlimited Zephyr. Returning false charges nothing.
     * stock(1) 之外的硬上限：库存只为开局时的身份缓存，局中成为调香师者否则可无限购买晨风香水。返回 false 不扣费。
     */
    private static boolean buyZephyr(PlayerEntity player, Item zephyrPerfume) {
        if (PerfumerScentComponent.KEY.get(player).isZephyrActive()
                || player.getInventory().contains(stack -> stack.isOf(zephyrPerfume))) {
            return false;
        }
        // The shop lives in the inventory screen, so a bottle may be held on the cursor while buying.
        // 商店位于物品栏界面，购买时香水可能正被鼠标拿起。
        if (player.currentScreenHandler != null && player.currentScreenHandler.getCursorStack().isOf(zephyrPerfume)) {
            return false;
        }
        return ShopEntry.insertStackInFreeSlot(player, zephyrPerfume.getDefaultStack());
    }

    private static boolean contains(List<ShopEntry> entries, String entryId, Item item) {
        for (ShopEntry entry : entries) {
            if (entryId.equals(entry.id()) || entry.stack().isOf(item)) {
                return true;
            }
        }
        return false;
    }

    private static List<ShopEntry> add(
            List<ShopEntry> result, List<ShopEntry> entries, PlayerEntity player, ShopEntry entry
    ) {
        List<ShopEntry> target = result == null ? new ArrayList<>(entries) : result;
        target.add(SparkTraitsCompat.discountShopEntryForCharisma(player, entry));
        return target;
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
