package annina.sparkstrength.role.bodyguard;

import annina.sparkstrength.SparkStrengthItems;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * The Bodyguard's vest is real chest armour: it only protects while worn in the chest slot (owner rule 2026-10-08),
 * and everyone sees it on the body. Buying it puts it in the hotbar; right-click equips it like any vanilla armour.
 * 保镖的防弹衣是真正的胸甲：只有穿在胸甲栏时才生效（所有者规则 2026-10-08），所有人都能看到穿在身上的样子。
 * 购买后放进快捷栏，像原版护甲一样右键穿上。
 */
public final class BodyguardVestService {
    private BodyguardVestService() {
    }

    public static boolean isWorn(PlayerEntity player) {
        return player.getEquippedStack(EquipmentSlot.CHEST).isOf(SparkStrengthItems.bodyguardVest());
    }

    /**
     * Breaks the worn vest: vanilla's equipment-break sound and particles for everyone nearby, then the chest slot
     * empties. / 报废穿着的防弹衣：附近所有人都能看到/听到原版装备破损的音效与粒子，随后胸甲栏清空。
     */
    public static void breakWorn(ServerPlayerEntity player) {
        ItemStack worn = player.getEquippedStack(EquipmentSlot.CHEST);
        if (!worn.isOf(SparkStrengthItems.bodyguardVest())) {
            return;
        }
        player.sendEquipmentBreakStatus(worn.getItem(), EquipmentSlot.CHEST);
        player.equipStack(EquipmentSlot.CHEST, ItemStack.EMPTY);
    }
}
