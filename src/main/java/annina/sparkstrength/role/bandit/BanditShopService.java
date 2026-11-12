package annina.sparkstrength.role.bandit;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.compat.SparkTraitsCompat;
import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.util.ShopEntry;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.Noellesroles;

/**
 * The Bandit's shop (owner 2026-11-12): Wathe's revolver is gone (the Bandit starts with the moddable one), and the
 * five parts (one each per round) and two rounds are added at the end; the client shows those on a second shop page.
 * 强盗商店（负责人 2026-11-12）：移除 Wathe 左轮（强盗开局自带改装左轮），在末尾加入五种配件（每局各限购一个）与两种子弹；
 * 客户端把它们放在商店第二页。
 *
 * <p>Registered in a phase after the default one: NoellesRoles' own Bandit handler looks for the {@code revolver}
 * entry to insert the throwing axe behind it, so the entry may only be removed once every default listener has run.
 * Wathe builds the list on both sides and buys by index, and this depends only on the synced role.</p>
 * <p>注册在默认阶段之后：NoellesRoles 自己的强盗处理器要找到 {@code revolver} 条目才能把飞斧插在其后，所以必须等所有默认
 * 监听器跑完才能移除它。Wathe 在两端各自构建列表并按下标购买，这里只取决于已同步的身份。</p>
 */
public final class BanditShopService {
    private static final Identifier LATE_PHASE = SparkStrength.id("bandit_shop_late");
    private static boolean registered;

    private BanditShopService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BuildShopEntries.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, LATE_PHASE);
        BuildShopEntries.EVENT.register(LATE_PHASE, BanditShopService::buildShopEntries);
    }

    private static void buildShopEntries(PlayerEntity player, BuildShopEntries.ShopContext context) {
        if (!GameWorldComponent.KEY.get(player.getWorld()).isRole(player, Noellesroles.BANDIT)) {
            return;
        }
        context.getEntries().removeIf(entry -> BanditRevolverRules.REMOVED_SHOP_ENTRY_ID.equals(entry.id()));
        for (BanditAmmo ammo : BanditAmmo.values()) {
            context.addEntry(SparkTraitsCompat.discountShopEntryForCharisma(player, new ShopEntry.Builder(
                    ammo.shopEntryId(),
                    new ItemStack(SparkStrengthItems.banditAmmo(ammo)),
                    ammo.price(),
                    ShopEntry.Type.WEAPON
            ).onBuy(buyer -> BanditRevolverService.giveAmmo(buyer, ammo)).build()));
        }
        for (BanditAttachment part : BanditAttachment.values()) {
            context.addEntry(SparkTraitsCompat.discountShopEntryForCharisma(player, new ShopEntry.Builder(
                    part.shopEntryId(),
                    new ItemStack(SparkStrengthItems.banditAttachment(part)),
                    part.price(),
                    ShopEntry.Type.TOOL
            ).stock(1).build()));
        }
    }
}
