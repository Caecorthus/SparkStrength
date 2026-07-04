package annina.sparkstrength.role.veteran;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * 老兵刀人后的额外金币奖励。
 *
 * <p>奖励挂在 {@code KillPlayer.AFTER} 上：只有死亡流程真正完成后才发钱，
 * 防止疯魔盾、死亡取消等情况也给老兵奖励。</p>
 */
public final class VeteranEconomyService {
    private VeteranEconomyService() {
    }

    public static void afterKill(
            ServerPlayerEntity victim,
            @Nullable ServerPlayerEntity killer,
            Identifier deathReason
    ) {
        if (killer == null || !GameConstants.DeathReasons.KNIFE.equals(deathReason)) {
            return;
        }

        GameWorldComponent game = GameWorldComponent.KEY.get(killer.getWorld());
        Role killerRole = game.getRole(killer);
        if (!VeteranRules.isVeteran(killerRole)) {
            return;
        }

        Role victimRole = game.getRole(victim);
        int reward = VeteranRules.killRewardForVictim(victimRole);
        PlayerShopComponent.KEY.get(killer).addToBalance(reward);
    }
}
