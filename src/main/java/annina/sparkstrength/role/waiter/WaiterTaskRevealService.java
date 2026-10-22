package annina.sparkstrength.role.waiter;

import annina.sparkstrength.component.waiter.WaiterTaskRevealComponent;
import annina.sparkstrength.role.coroner.CoronerRules;
import annina.sparkstrength.role.coroner.CoronerService;
import dev.doctor4t.wathe.api.event.TaskComplete;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.agmas.noellesroles.Noellesroles;
import org.jetbrains.annotations.Nullable;

/** 服务员“任务完成后透视目标”能力的服务端事件注册。 */
public final class WaiterTaskRevealService {
    private static boolean registered;

    private WaiterTaskRevealService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        TaskComplete.EVENT.register(WaiterTaskRevealService::onTaskComplete);
    }

    private static void onTaskComplete(ServerPlayerEntity player, dev.doctor4t.wathe.cca.PlayerMoodComponent.Task taskType) {
        // 任何玩家完成任务都会记录一次；客户端只给服务员及其服务员伪装显示。
        WaiterTaskRevealComponent.KEY.get(player).startReveal();
    }

    public static void reset(ServerPlayerEntity player) {
        WaiterTaskRevealComponent.KEY.get(player).reset();
    }

    /**
     * Whether {@code viewer} is shown task reveals: a living Waiter or a Coroner in a Waiter disguise. The client
     * highlight and the server's sync recipients both use it, so nobody else learns who just did a task.
     * 观察者是否能看到任务透视：存活的服务员，或伪装成服务员的验尸官。客户端高亮与服务端同步对象共用此判定，
     * 其他人不会得知谁刚完成了任务。
     */
    public static boolean canSeeReveals(PlayerEntity viewer) {
        if (viewer == null || !GameFunctions.isPlayerPlayingAndAlive(viewer)) {
            return false;
        }
        return canSeeReveals(true, GameWorldComponent.KEY.get(viewer.getWorld()).isRole(viewer, Noellesroles.WAITER),
                CoronerService.activeDisguiseRoleForRules(viewer));
    }

    /**
     * {@link #canSeeReveals(PlayerEntity)} from facts already resolved (the client's per-frame instinct viewer).
     * 用已解析的事实判定（客户端每帧的本能观察者）。
     */
    public static boolean canSeeReveals(boolean playingAndAlive, boolean waiterRole, @Nullable Role disguiseRole) {
        return playingAndAlive && (waiterRole || CoronerRules.grantsWaiterDoublePickup(disguiseRole));
    }
}
