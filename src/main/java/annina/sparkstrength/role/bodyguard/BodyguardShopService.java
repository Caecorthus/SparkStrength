package annina.sparkstrength.role.bodyguard;

import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.component.bodyguard.BodyguardGearComponent;
import annina.sparkstrength.compat.SparkTraitsCompat;
import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.List;

/**
 * The Bodyguard's shop: the vest (200, chest armour) and the Democracy Shield (100), each once per round.
 * Wathe opens a shop for any role that gets entries, and buys by index, so the list only depends on the synced role.
 * Not gated on being alive: Wathe caches stock(1) from this list before the round turns ACTIVE.
 * 保镖商店：防弹衣（200，买下即穿）与民主盾牌（100），每局各限购一次。Wathe 只要构建出条目就开放商店，并按下标购买，
 * 所以列表只取决于已同步的身份。不检查存活：Wathe 在对局变为 ACTIVE 前就从此列表缓存 stock(1)。
 */
public final class BodyguardShopService {
    private static boolean registered;

    private BodyguardShopService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BuildShopEntries.EVENT.register(BodyguardShopService::buildShopEntries);
    }

    private static void buildShopEntries(PlayerEntity player, BuildShopEntries.ShopContext context) {
        if (!BodyguardRules.isBodyguard(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))) {
            return;
        }
        Item vest = SparkStrengthItems.bodyguardVest();
        context.addEntry(SparkTraitsCompat.discountShopEntryForCharisma(player, new ShopEntry.Builder(
                BodyguardRules.VEST_ENTRY_ID,
                displayStack(vest, "bodyguard_vest"),
                BodyguardRules.VEST_PRICE,
                ShopEntry.Type.TOOL
        ).actualStack(vest.getDefaultStack()).stock(BodyguardRules.ENTRY_STOCK)
                .onBuy(BodyguardShopService::buyVest).build()));
        Item shield = SparkStrengthItems.democracyShield();
        context.addEntry(SparkTraitsCompat.discountShopEntryForCharisma(player, new ShopEntry.Builder(
                BodyguardRules.SHIELD_ENTRY_ID,
                displayStack(shield, "democracy_shield"),
                BodyguardRules.SHIELD_PRICE,
                ShopEntry.Type.TOOL
        ).actualStack(shield.getDefaultStack()).stock(BodyguardRules.ENTRY_STOCK)
                .onBuy(BodyguardShopService::buyShield).build()));
    }

    /**
     * Server purchase handlers (PlayerShopComponent.tryBuy): true charges the price, false charges nothing. The
     * component caps hold even for a mid-round Bodyguard, whose stock was never cached.
     * 服务端购买处理（PlayerShopComponent.tryBuy）：返回 true 扣费，false 不扣费。组件上限对局中才成为保镖、
     * 未缓存库存的玩家同样有效。
     */
    private static boolean buyVest(PlayerEntity buyer) {
        // A real chest piece: it lands in the hotbar and only protects once right-clicked on (owner rule 2026-10-08).
        // 真正的胸甲：放进快捷栏，右键穿上后才生效（所有者规则 2026-10-08）。
        BodyguardGearComponent gear = BodyguardGearComponent.KEY.get(buyer);
        Item vest = SparkStrengthItems.bodyguardVest();
        if (gear.isVestBought() || buyer.getInventory().contains(stack -> stack.isOf(vest))) {
            return false;
        }
        if (!ShopEntry.insertStackInFreeSlot(buyer, vest.getDefaultStack())) {
            return false;
        }
        gear.markVestBought();
        return true;
    }

    private static boolean buyShield(PlayerEntity buyer) {
        BodyguardGearComponent gear = BodyguardGearComponent.KEY.get(buyer);
        Item shield = SparkStrengthItems.democracyShield();
        if (gear.isShieldBought() || buyer.getInventory().contains(stack -> stack.isOf(shield))) {
            return false;
        }
        if (!ShopEntry.insertStackInFreeSlot(buyer, shield.getDefaultStack())) {
            return false;
        }
        gear.markShieldBought();
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
