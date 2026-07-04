package annina.sparkstrength.event;

import annina.sparkstrength.component.role.RoleEnhancementPlayerComponent;
import annina.sparkstrength.component.role.RoleEnhancementWorldComponent;
import annina.sparkstrength.component.noisemaker.NoisemakerGlowTargetComponent;
import annina.sparkstrength.component.noisemaker.NoisemakerGlowUserComponent;
import annina.sparkstrength.noisemaker.NoisemakerGlowService;
import annina.sparkstrength.role.CorruptCopFeatureService;
import annina.sparkstrength.role.FlashlightBlackoutService;
import annina.sparkstrength.role.NoellesRoleEnhancementService;
import annina.sparkstrength.tablet.TabletStateService;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import dev.doctor4t.wathe.api.event.TaskComplete;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * 统一注册 SparkStrength 的服务端事件。
 */
public final class SparkStrengthEvents {
    private SparkStrengthEvents() {
    }

    public static void register() {
        CorruptCopFeatureService.register();
        FlashlightBlackoutService.register();
        NoellesRoleEnhancementService.register();
        ServerTickEvents.END_WORLD_TICK.register(TabletStateService::tick);

        RoleAssigned.EVENT.register((player, role) -> {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                NoellesRoleEnhancementService.assignForRole(serverPlayer, role);
            }
        });
        TaskComplete.EVENT.register((player, taskType) -> NoellesRoleEnhancementService.onTaskComplete(player));

        ResetPlayer.EVENT.register(player -> {
            // Wathe 在死亡、重置玩家、新一局开始等场景会触发 ResetPlayer。
            // 这里把点亮冷却和目标倒计时都清掉，避免跨局残留。
            NoisemakerGlowUserComponent.KEY.get(player).reset();
            NoisemakerGlowTargetComponent.KEY.get(player).reset();
            RoleEnhancementPlayerComponent.KEY.get(player).clearAll();
        });

        KillPlayer.AFTER.register((victim, killer, deathReason) -> {
            // 大嗓门死亡后的“杀手发光 15 秒”是被动效果，不写入回放。
            NoisemakerGlowService.glowKillerWhenNoisemakerDies(victim, killer);
            NoellesRoleEnhancementService.afterKill(victim, killer, deathReason);
        });

        GameEvents.ON_FINISH_FINALIZE.register((world, gameComponent) -> {
            if (world instanceof ServerWorld serverWorld) {
                RoleEnhancementWorldComponent.KEY.get(serverWorld).clearRoundState();
                TabletStateService.clearRoundState(serverWorld);
                for (ServerPlayerEntity player : serverWorld.getPlayers()) {
                    RoleEnhancementPlayerComponent.KEY.get(player).clearAll();
                }
            }
        });
    }
}
