package annina.sparkstrength.util;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Round-membership facts about a player that many roles share.
 * 多个职业共用的玩家对局身份事实。
 */
public final class SparkPlayers {
    private SparkPlayers() {
    }

    /**
     * Alive in a running round per Wathe (has a role, not marked dead) and in survival (neither spectator nor
     * creative). False for null.
     * 按 Wathe 判定在进行中的对局里存活（有身份、未标记死亡），且处于生存模式（非旁观、非创造）。null 为 false。
     */
    public static boolean isLivingParticipant(@Nullable PlayerEntity player) {
        return GameFunctions.isPlayerPlayingAndAlive(player) && GameFunctions.isPlayerAliveAndSurvival(player);
    }

    /** Real round role in the player's own world (never a disguise), or null. / 玩家所在世界中的真实局内身份（从不是伪装），或 null。 */
    public static @Nullable Role roleOf(PlayerEntity player) {
        return GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
    }
}
