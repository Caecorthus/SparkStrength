package annina.sparkstrength.role.pathogen;

import dev.doctor4t.wathe.api.event.CanSeeMoney;
import dev.doctor4t.wathe.api.event.TaskComplete;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Pathogen economy: +{@link PathogenRules#TASK_MONEY_REWARD} per completed task (Wathe gives FAKE-mood roles tasks, it
 * just never paid the Pathogen), and the coin counter while alive. Kept apart from {@code RoleEconomyService}: that
 * one also resets the balance on role assignment and serves the good-role tablet rules.
 * 病原体经济：每完成一个任务 +{@link PathogenRules#TASK_MONEY_REWARD}（Wathe 会给假理智职业派任务，只是从未给病原体发钱），
 * 存活时显示金币。与 {@code RoleEconomyService} 分开：后者还会在分配身份时重置余额并服务于好人平板规则。
 */
public final class PathogenEconomyService {
    private static boolean registered;

    private PathogenEconomyService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        TaskComplete.EVENT.register((player, task) -> onTaskComplete(player));
        CanSeeMoney.EVENT.register(PathogenEconomyService::canSeeMoney);
    }

    private static void onTaskComplete(ServerPlayerEntity player) {
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        if (PathogenRules.earnsTaskMoney(
                game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE,
                GameFunctions.isPlayerPlayingAndAlive(player),
                game.getRole(player),
                player.isSpectator(),
                player.isCreative())) {
            PlayerShopComponent.KEY.get(player).addToBalance(PathogenRules.TASK_MONEY_REWARD);
        }
    }

    /** Synced role and death facts only, so both sides agree. / 只读取已同步的身份与死亡信息，两端结果一致。 */
    private static @Nullable CanSeeMoney.Result canSeeMoney(@Nullable PlayerEntity player) {
        if (player == null) {
            return null;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        Boolean visible = PathogenRules.moneyVisible(game.getRole(player), game.isPlayerDead(player.getUuid()));
        if (visible == null) {
            return null;
        }
        return visible ? CanSeeMoney.Result.ALLOW : CanSeeMoney.Result.DENY;
    }
}
