package annina.sparkstrength.role.professor;

import annina.sparkstrength.component.professor.ProfessorSerumUserComponent;
import annina.sparkstrength.util.ShopStacks;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * 教授专属商店。
 *
 * <p>Wathe 商店是打开时动态构建条目，所以这里不保存任何商店状态；
 * 每次构建时只根据当前玩家职业追加四种试剂和“背包技能刷新冷却”。</p>
 */
public final class ProfessorSerumShopService {
    private static boolean registered;

    private ProfessorSerumShopService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BuildShopEntries.EVENT.register(ProfessorSerumShopService::buildShopEntries);
    }

    private static void buildShopEntries(PlayerEntity player, BuildShopEntries.ShopContext context) {
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (!ProfessorSerumRules.isProfessor(role)) {
            return;
        }

        // 只追加教授增强条目，不清空其它 mod 未来可能给教授追加的商店内容。
        for (ProfessorSerumType type : ProfessorSerumType.BACKPACK_ORDER) {
            context.addEntry(new ShopEntry.Builder(
                    ProfessorSerumRules.shopEntryId(type),
                    serumDisplayStack(type),
                    ProfessorSerumRules.price(type),
                    ShopEntry.Type.TOOL
            ).actualStack(type.defaultStack()).build());
        }
        context.addEntry(new ShopEntry.Builder(
                ProfessorSerumRules.REFRESH_COOLDOWN_ENTRY_ID,
                refreshDisplayStack(),
                ProfessorSerumRules.REFRESH_COOLDOWN_PRICE,
                ShopEntry.Type.TOOL
        ).onBuy(ProfessorSerumShopService::refreshCooldown).build());
    }

    public static boolean refreshCooldown(PlayerEntity buyer) {
        ProfessorSerumUserComponent component = ProfessorSerumUserComponent.KEY.get(buyer);
        if (!component.isOnCooldown()) {
            return false;
        }
        component.reset();
        return true;
    }

    private static ItemStack serumDisplayStack(ProfessorSerumType type) {
        return ShopStacks.display(type.defaultStack(), "shop.sparkstrength.professor." + type.id());
    }

    private static ItemStack refreshDisplayStack() {
        return ShopStacks.display(Items.CLOCK.getDefaultStack(), "shop.sparkstrength.professor.refresh_cooldown");
    }
}
