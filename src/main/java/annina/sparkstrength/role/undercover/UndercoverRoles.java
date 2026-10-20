package annina.sparkstrength.role.undercover;

import annina.sparkstrength.component.undercover.UndercoverPlayerComponent;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Who counts as the Undercover, side-agnostic. Only the real role does: a Coroner wearing an Undercover disguise keeps
 * its own role and gets none of this (same rule as the blackout night vision).
 * 谁算卧底，与端无关。只有真实身份算：伪装成卧底的验尸官仍是验尸官，什么都拿不到（与熄灯夜视同一规则）。
 */
public final class UndercoverRoles {
    private UndercoverRoles() {
    }

    public static boolean isUndercover(@Nullable Role role) {
        return UndercoverRoleRules.isUndercover(role);
    }

    public static boolean isUndercover(@Nullable PlayerEntity player) {
        return player != null && isUndercover(GameWorldComponent.KEY.get(player.getWorld()).getRole(player));
    }

    /** The real Undercover in stage 2 (mask put on at least once this round). / 处于第二阶段的真卧底。 */
    public static boolean isStage2(@Nullable PlayerEntity player) {
        return isUndercover(player) && UndercoverPlayerComponent.KEY.get(player).isStage2();
    }

    /** Stage 2 and alive in a running round. / 第二阶段且在进行中的对局里存活。 */
    public static boolean isLivingStage2(@Nullable PlayerEntity player) {
        return isStage2(player) && GameFunctions.isPlayerPlayingAndAlive(player);
    }
}
