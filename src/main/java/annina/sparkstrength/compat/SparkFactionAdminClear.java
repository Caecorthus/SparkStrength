package annina.sparkstrength.compat;

import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.item.DroneItem;
import annina.sparkstrength.item.m67.M67RoundService;
import annina.sparkstrength.role.bodyguard.BodyguardShieldService;
import annina.sparkstrength.role.bomber.drone.DroneService;
import annina.sparkstrength.role.vulture.VultureSkateboardService;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Admin path only: SparkFactionAPI {@code /sparkfactionapi:clearCooldown} just removed {@code item}'s vanilla cooldown
 * for {@code player} (called from {@code AdminClearCooldownMixin} once the clear took effect). The locks below are
 * re-synced every tick from their own state, so that remove alone comes back on the next tick; the item's owner
 * releases its own lock, for this player only. Role mechanics that remove cooldowns (Timekeeper refresh, Serial Killer
 * reset) never come through here, and other items need nothing.
 * 仅管理员路径：SparkFactionAPI /sparkfactionapi:clearCooldown 刚为该玩家移除了该物品的原版冷却（清除生效后由
 * AdminClearCooldownMixin 调用）。以下锁每刻按自身状态重新同步，仅移除原版冷却会在下一刻恢复；由物品的所属逻辑只为该玩家
 * 解除自己的锁。移除冷却的职业机制（计时员刷新、连环杀手重置）不会经过这里，其他物品无需处理。
 */
public final class SparkFactionAdminClear {
    private SparkFactionAdminClear() {
    }

    public static void onCooldownCleared(@Nullable ServerPlayerEntity player, @Nullable Item item) {
        if (player == null || item == null) {
            return;
        }
        if (item instanceof DroneItem) {
            DroneService.onAdminCooldownCleared(player, item);
        } else if (item == SparkStrengthItems.m67()) {
            M67RoundService.onAdminCooldownCleared(player);
        } else if (item == SparkStrengthItems.skateboard()) {
            VultureSkateboardService.onAdminCooldownCleared(player);
        } else if (item == SparkStrengthItems.democracyShield()) {
            BodyguardShieldService.onAdminCooldownCleared(player);
        }
    }
}
