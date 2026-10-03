package annina.sparkstrength.role.toxicologist;

import annina.sparkstrength.SparkStrengthItems;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Adds the capsule, antidote-refresh and blue-poison shop entries for Toxicologists.
 * 给毒理学家追加胶囊、解毒剂冷却刷新与蓝毒道具商店项。
 */
public final class ToxicologistCapsuleShop {
    private static boolean registered;

    private ToxicologistCapsuleShop() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BuildShopEntries.EVENT.register(ToxicologistCapsuleShop::buildShopEntries);
        ToxicologistBlueShop.register();
    }

    private static void buildShopEntries(PlayerEntity player, BuildShopEntries.ShopContext context) {
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (!ToxicologistCapsuleRules.canBuyCapsules(role)) {
            return;
        }
        context.addEntry(new ShopEntry.Builder(
                ToxicologistCapsuleRules.CAPSULE_ENTRY_ID,
                capsuleDisplayStack(),
                ToxicologistCapsuleRules.CAPSULE_PRICE,
                ShopEntry.Type.TOOL
        ).actualStack(SparkStrengthItems.capsule().getDefaultStack()).build());
        context.addEntry(new ShopEntry.Builder(
                ToxicologistCapsuleRules.REFRESH_ANTIDOTE_COOLDOWN_ENTRY_ID,
                refreshAntidoteCooldownDisplayStack(),
                ToxicologistCapsuleRules.REFRESH_ANTIDOTE_COOLDOWN_PRICE,
                ShopEntry.Type.TOOL
        ).onBuy(ToxicologistAntidoteService::refreshAntidoteCooldown).build());
        // Blue items go after the existing entries so earlier purchase indices stay unchanged.
        // 蓝毒道具追加在原有条目之后，保持原有条目的购买下标不变。
        for (ShopEntry entry : ToxicologistBlueShop.entries()) {
            context.addEntry(entry);
        }
    }

    private static ItemStack capsuleDisplayStack() {
        ItemStack stack = SparkStrengthItems.capsule().getDefaultStack();
        stack.set(DataComponentTypes.ITEM_NAME, Text.translatable("shop.sparkstrength.capsule"));
        stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.translatable("shop.sparkstrength.capsule.description")
                        .styled(style -> style.withColor(0x808080).withItalic(false))
        )));
        return stack;
    }

    private static ItemStack refreshAntidoteCooldownDisplayStack() {
        ItemStack stack = Items.CLOCK.getDefaultStack();
        stack.set(DataComponentTypes.ITEM_NAME,
                Text.translatable("shop.sparkstrength.toxicologist.refresh_antidote_cooldown"));
        stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.translatable("shop.sparkstrength.toxicologist.refresh_antidote_cooldown.description")
                        .styled(style -> style.withColor(0x808080).withItalic(false))
        )));
        return stack;
    }
}
