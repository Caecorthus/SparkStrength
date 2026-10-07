package annina.sparkstrength.role.corruptcop;

import annina.sparkstrength.component.corruptcop.CorruptCopAbilityComponent;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.TaskComplete;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.agmas.noellesroles.taotie.SwallowedPlayerComponent;

/**
 * Server authority for the Corrupt Cop ability toggle and its task unlock.
 * 黑警主动技能开关及其任务解锁的服务端权威入口。
 */
public final class CorruptCopAbilityService {
    private static boolean registered;

    private CorruptCopAbilityService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        TaskComplete.EVENT.register((player, taskType) -> onTaskComplete(player));
    }

    /**
     * Wathe gives the FAKE-mood Corrupt Cop tasks too; only tasks done while holding the role count toward the unlock.
     * Wathe 同样会给假理智的黑警派任务；只有担任黑警期间完成的任务计入解锁。
     */
    public static void onTaskComplete(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        Role role = GameWorldComponent.KEY.get(player.getServerWorld()).getRole(player);
        if (!CorruptCopRules.isCorruptCop(role)) {
            return;
        }

        CorruptCopAbilityComponent component = CorruptCopAbilityComponent.KEY.get(player);
        int required = component.requiredTasks();
        int previous = component.completedTasks();
        if (CorruptCopTaskGateRules.isUnlocked(previous, required)) {
            return;
        }
        int done = component.recordCompletedTask();
        if (CorruptCopTaskGateRules.crossesUnlock(previous, done, required)) {
            player.sendMessage(Text.translatable("message.sparkstrength.corrupt_cop.unlocked", required), false);
        }
    }

    /**
     * Consumes the shared ability packet for every living cop; while locked it only reports the remaining tasks.
     * 为所有存活黑警消费通用技能包；未解锁时只提示剩余任务数。
     */
    public static boolean toggle(ServerPlayerEntity player) {
        if (player == null
                || !GameFunctions.isPlayerAliveAndSurvival(player)
                || SwallowedPlayerComponent.isPlayerSwallowed(player)) {
            return false;
        }

        Role role = GameWorldComponent.KEY.get(player.getServerWorld()).getRole(player);
        if (!CorruptCopRules.isCorruptCop(role)) {
            return false;
        }

        CorruptCopAbilityComponent component = CorruptCopAbilityComponent.KEY.get(player);
        if (!component.isUnlocked()) {
            player.sendMessage(Text.translatable(
                    "message.sparkstrength.corrupt_cop.locked",
                    CorruptCopTaskGateRules.remainingTasks(component.completedTasks(), component.requiredTasks())
            ), true);
            return true;
        }
        component.setActive(CorruptCopRules.nextAbilityActive(true, component.isActive()));
        return true;
    }

    public static void reset(ServerPlayerEntity player) {
        if (player != null) {
            CorruptCopAbilityComponent.KEY.get(player).reset();
        }
    }
}
