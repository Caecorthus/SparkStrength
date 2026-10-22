package annina.sparkstrength.sabotage;

import annina.sparkstrength.role.undercover.UndercoverRoles;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Who sees through a blackout the way killers do.
 * 谁能像杀手一样在熄灯中看清周围。
 *
 * <p>The NoellesRoles Undercover poses as a killer teammate, so it ignores the blackout like one: Wathe's killer
 * branch gives it the same particle-free Night Vision instead of Blindness. Real round role only, like its tablet.
 * NoellesRoles 卧底伪装成杀手队友，因此也像杀手一样无视熄灯：走 Wathe 的杀手分支，获得同样没有粒子的夜视而不是失明。
 * 与卧底的平板一样只看真实身份。</p>
 */
public final class BlackoutNightVision {
    private BlackoutNightVision() {
    }

    public static boolean seesLikeKiller(PlayerEntity player) {
        return UndercoverRoles.isUndercover(player);
    }
}
