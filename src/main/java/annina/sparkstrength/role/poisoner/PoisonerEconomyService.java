package annina.sparkstrength.role.poisoner;

import annina.sparkstrength.role.PoisonOnset;
import annina.sparkstrength.util.SparkPlayers;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 毒师投毒成功后的金币奖励。
 *
 * <p>奖励挂在共享的 {@link PoisonOnset} 新中毒检测上（其底层是 Wathe 的 PlayerPoisoned 事件），而不是分别改毒针、毒气、
 * 毒药瓶、蝎子等道具代码。这样所有最终走 {@code setPoisonTicks(...)} 的投毒来源
 * 都能统一生效，同时也不会漏掉后续新增的标准投毒入口。</p>
 */
public final class PoisonerEconomyService {
    private static boolean registered;

    private PoisonerEconomyService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        PoisonOnset.subscribe(PoisonerEconomyService::onNewPoisoning);
    }

    private static void onNewPoisoning(ServerPlayerEntity target, @Nullable UUID poisoner) {
        // 只奖励有投毒者、且与 BEFORE 阶段记录一致的那次投毒；不一致时 PoisonOnset 传入 null，不发奖。
        if (poisoner == null) {
            return;
        }

        ServerPlayerEntity poisonerPlayer = target.getServer().getPlayerManager().getPlayer(poisoner);
        if (!isRewardablePoisoner(target, poisonerPlayer)) {
            return;
        }

        PlayerShopComponent.KEY.get(poisonerPlayer).addToBalance(PoisonerEconomyRules.POISON_REWARD);
    }

    private static boolean isRewardablePoisoner(
            ServerPlayerEntity target,
            @Nullable ServerPlayerEntity poisoner
    ) {
        if (poisoner == null || !SparkPlayers.isLivingParticipant(poisoner)) {
            return false;
        }

        GameWorldComponent game = GameWorldComponent.KEY.get(target.getWorld());
        if (!game.hasAnyRole(poisoner.getUuid()) || game.isPlayerDead(poisoner.getUuid())) {
            return false;
        }

        // 用目标所在世界的 GameWorldComponent 判定角色，确保奖励属于同一局游戏里的毒师身份。
        return PoisonerEconomyRules.isPoisoner(game.getRole(poisoner.getUuid()));
    }
}
