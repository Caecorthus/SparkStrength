package annina.sparkstrength.client.role.corruptcop;

import annina.sparkstrength.compat.SparkTraitsCompat;
import annina.sparkstrength.component.corruptcop.CorruptCopAbilityComponent;
import annina.sparkstrength.role.corruptcop.CorruptCopConcealmentRules;
import annina.sparkstrength.role.corruptcop.CorruptCopRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.GetInstinctHighlight;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.WatheClient;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.agmas.noellesroles.corruptcop.CorruptCopPlayerComponent;

/**
 * Client-side instinct presentation hooks for Corrupt Cop.
 * 黑警本能视觉表现的客户端挂钩。
 */
public final class CorruptCopClientHooks {
    private CorruptCopClientHooks() {
    }

    public static void register() {
        GetInstinctHighlight.EVENT.register(CorruptCopClientHooks::highlight);
    }

    public static boolean usesKillerStyleInstinctLight() {
        ClientPlayerEntity viewer = MinecraftClient.getInstance().player;
        if (viewer == null) {
            return false;
        }

        Role role = GameWorldComponent.KEY.get(viewer.getWorld()).getRole(viewer);
        return CorruptCopRules.usesKillerStyleInstinctLight(
                role,
                WatheClient.isInstinctEnabled(),
                GameFunctions.isPlayerPlayingAndAlive(viewer)
        );
    }

    /**
     * Instinct is client-authoritative presentation, so each client vetoes its own view; this reads the cop's
     * toggle from the all-tracker CorruptCopAbilityComponent sync and the Moment from NoellesRoles' player component.
     * 本能透视由客户端自行呈现，因此由每个客户端否决自己的视野；黑警开关读取同步给所有追踪者的
     * CorruptCopAbilityComponent，黑警时刻读取 NoellesRoles 的玩家组件。
     */
    public static boolean shouldConcealInstinct(Entity target) {
        ClientPlayerEntity viewer = MinecraftClient.getInstance().player;
        if (viewer == null || !(target instanceof PlayerEntity targetPlayer)) {
            return false;
        }

        GameWorldComponent gameComponent = GameWorldComponent.KEY.get(viewer.getWorld());
        Role viewerRole = gameComponent.getRole(viewer);
        Role targetRole = gameComponent.getRole(targetPlayer);
        boolean viewerConcealingCop = isConcealingCop(viewer, viewerRole);
        boolean targetConcealingCop = isConcealingCop(targetPlayer, targetRole);
        // Fast path: this runs for every rendered entity each frame.
        // 快速路径：每帧会对每个渲染实体调用。
        if (!viewerConcealingCop && !targetConcealingCop) {
            return false;
        }

        return CorruptCopConcealmentRules.shouldConceal(
                SparkTraitsCompat.isFinalMomentActive(viewer.getWorld()),
                GameFunctions.isPlayerPlayingAndAlive(viewer),
                GameFunctions.isPlayerSpectatingOrCreative(viewer),
                viewer.getUuid().equals(targetPlayer.getUuid()),
                GameFunctions.isPlayerPlayingAndAlive(targetPlayer),
                viewer.squaredDistanceTo(targetPlayer),
                viewerConcealingCop,
                viewerConcealingCop && CorruptCopPlayerComponent.KEY.get(viewer).isCorruptCopMomentActive(),
                CorruptCopRules.isInsider(viewerRole),
                targetConcealingCop,
                CorruptCopRules.isInsider(targetRole)
        );
    }

    private static boolean isConcealingCop(PlayerEntity player, Role role) {
        return CorruptCopRules.isCorruptCop(role) && CorruptCopAbilityComponent.KEY.get(player).isActive();
    }

    private static GetInstinctHighlight.HighlightResult highlight(Entity target) {
        ClientPlayerEntity viewer = MinecraftClient.getInstance().player;
        if (viewer == null || !(target instanceof PlayerEntity targetPlayer)) {
            return null;
        }

        GameWorldComponent gameComponent = GameWorldComponent.KEY.get(viewer.getWorld());
        Role role = gameComponent.getRole(viewer);
        boolean corruptCop = CorruptCopRules.isCorruptCop(role);
        return CorruptCopRules.instinctHighlight(
                role,
                GameFunctions.isPlayerPlayingAndAlive(viewer),
                GameFunctions.isPlayerSpectatingOrCreative(viewer),
                viewer.getUuid().equals(targetPlayer.getUuid()),
                GameFunctions.isPlayerPlayingAndAlive(targetPlayer),
                GameFunctions.isPlayerSpectatingOrCreative(targetPlayer),
                targetPlayer.isInvisible(),
                corruptCop && CorruptCopPlayerComponent.KEY.get(viewer).canSeePlayersThroughWalls(),
                corruptCop && CorruptCopAbilityComponent.KEY.get(viewer).isUnlocked()
        );
    }
}
