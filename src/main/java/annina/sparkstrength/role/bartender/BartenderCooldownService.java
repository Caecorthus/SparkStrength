package annina.sparkstrength.role.bartender;

import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashSet;
import java.util.Set;

/**
 * Spice A's effect: clears the cooldown of every item the drinker carries (hotbar, backpack, armour, offhand and
 * the cursor). Only item cooldowns: shop cooldowns live in PlayerShopComponent and role abilities in their own
 * components, so neither is touched. Cooldowns SparkStrength holds up every tick while a state lasts (skateboard
 * ride, drone flight, M67 pin) are re-raised by their owners on the next tick.
 * 调料 A 的效果：清空饮用者身上所有物品（快捷栏、背包、盔甲、副手与鼠标）的冷却。只清物品冷却：商店冷却在
 * PlayerShopComponent、职业技能在各自组件中，均不受影响。SparkStrength 在状态持续期间每刻维持的冷却
 * （滑板骑行、无人机飞行、M67 拔销）会在下一刻由其所属逻辑重新抬高。
 */
public final class BartenderCooldownService {
    private BartenderCooldownService() {
    }

    public static void clearInventoryCooldowns(ServerPlayerEntity player) {
        ItemCooldownManager cooldowns = player.getItemCooldownManager();
        Set<Item> carried = new HashSet<>();
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            addItem(carried, inventory.getStack(slot));
        }
        addItem(carried, player.currentScreenHandler.getCursorStack());
        for (Item item : carried) {
            if (cooldowns.isCoolingDown(item)) {
                cooldowns.remove(item);
            }
        }
    }

    private static void addItem(Set<Item> carried, ItemStack stack) {
        if (!stack.isEmpty()) {
            carried.add(stack.getItem());
        }
    }
}
