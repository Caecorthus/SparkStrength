package annina.sparkstrength.role.bomber.drone;

import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.compat.SparkTraitsCompat;
import annina.sparkstrength.role.coroner.CoronerRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Appends the bomb drone to the real Bomber's final shop list (after the Perfumer kit and the M67).
 * 在真实炸弹客的最终商店列表末尾（调香师道具与 M67 之后）追加炸弹无人机。
 */
public final class DroneShopService {
    private DroneShopService() {
    }

    /**
     * Called from {@code M67ShopEntriesMixin} on both sides; Wathe buys by index, so the answer may only depend on
     * owner-synced state: the real round role and the SparkTraits Conscience flag. A Coroner's Bomber disguise never
     * qualifies. Not gated on {@code isPlayerPlayingAndAlive}, like the other appenders (round-start stock caching runs
     * before ACTIVE). The entry has no stock or cooldown, so purchases are unlimited; placement limits live in
     * {@code DroneService}.
     * 由 M67ShopEntriesMixin 在两端调用；Wathe 按下标购买，因此结果只能依赖同步给本人的状态：真实局内身份与 SparkTraits
     * 善良天赋。验尸官的炸弹客伪装不计入。与其他追加器一样不检查 isPlayerPlayingAndAlive（开局库存缓存早于 ACTIVE）。
     * 条目没有库存与冷却，可无限购买；放置上限由 DroneService 负责。
     */
    public static List<ShopEntry> appendToFinalEntries(PlayerEntity player, List<ShopEntry> entries) {
        // An empty list means no shop access; never grant access by appending. / 空列表表示无商店权限，追加不得授予权限。
        if (player == null || entries.isEmpty()
                || !CoronerRules.isBomber(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))) {
            return entries;
        }
        Item bombDrone = SparkStrengthItems.bombDrone();
        for (ShopEntry entry : entries) {
            if (DroneRules.BOMB_DRONE_SHOP_ENTRY_ID.equals(entry.id()) || entry.stack().isOf(bombDrone)) {
                return entries;
            }
        }

        // Builder has no name/description API; display components must not enter the purchased stack.
        // Builder 无名称/描述接口；展示组件不应进入实际购买的物品堆。
        ItemStack display = bombDrone.getDefaultStack();
        display.set(DataComponentTypes.ITEM_NAME, Text.translatable("shop.sparkstrength.bomb_drone"));
        display.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.translatable("shop.sparkstrength.bomb_drone.description")
                        .styled(style -> style.withColor(0x808080).withItalic(false)))));
        ShopEntry entry = new ShopEntry.Builder(
                DroneRules.BOMB_DRONE_SHOP_ENTRY_ID,
                display,
                DroneRules.bombDronePrice(SparkTraitsCompat.hasConscience(player)),
                ShopEntry.Type.WEAPON
        ).actualStack(bombDrone.getDefaultStack()).build();
        List<ShopEntry> result = new ArrayList<>(entries);
        // Charisma and Conscience are both killer traits and never held together, so the discounts never stack.
        // 魅力与善良都是杀手天赋，不会同时持有，因此两种折扣不会叠加。
        result.add(SparkTraitsCompat.discountShopEntryForCharisma(player, entry));
        return result;
    }
}
