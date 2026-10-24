package annina.sparkstrength.sabotage;

import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.compat.SparkTraitsCompat;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

/**
 * Round-start grant of the Impostor's pry tool (owner 2026-10-24): one free per Impostor per round, never sold. Only
 * players who may pry junction boxes get it, so a witch-faction Accomplice that kept the Impostor trait does not.
 * 开局发放内鬼的撬锁工具（负责人 2026-10-24）：每名内鬼每局免费一把，不出售。只发给能撬接线箱的人，
 * 因此保留内鬼词条的魔女阵营共犯拿不到。
 */
public final class ImpostorLockpickService {
    private ImpostorLockpickService() {
    }

    /**
     * From {@code GameEvents.ON_FINISH_INITIALIZE}, like the starter tablet: roles and traits are final, and the round
     * is not ACTIVE yet.
     * 与开局平板一样在 ON_FINISH_INITIALIZE 调用：身份与天赋已确定，对局尚未进入 ACTIVE。
     */
    public static void grantStarterLockpicks(ServerWorld world, GameWorldComponent game) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (game.hasAnyRole(player) && SparkTraitsCompat.hasImpostor(player)
                    && SabotageAccess.hasSabotageIdentity(player) && !hasLockpick(player)
                    && player.giveItemStack(new ItemStack(SparkStrengthItems.impostorLockpick()))) {
                player.sendMessage(Text.translatable("message.sparkstrength.sabotage.impostor_lockpick_granted"), false);
            }
        }
    }

    private static boolean hasLockpick(ServerPlayerEntity player) {
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            if (player.getInventory().getStack(slot).isOf(SparkStrengthItems.impostorLockpick())) {
                return true;
            }
        }
        return false;
    }
}
