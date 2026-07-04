package annina.sparkstrength.client.role;

import annina.sparkstrength.SparkStrengthEntities;
import annina.sparkstrength.component.role.RoleEnhancementPlayerComponent;
import annina.sparkstrength.role.NoellesRoleEnhancementRules;
import annina.sparkstrength.role.NoellesRoleIds;
import dev.caecorthus.sparktraits.impl.EffectiveTraitService;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.CanSeeMoney;
import dev.doctor4t.wathe.api.event.GetInstinctHighlight;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.WatheClient;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Client-only hooks for the NoellesRoles enhancements.
 * NoellesRoles 增强机制的客户端挂钩。
 */
public final class NoellesRoleEnhancementClientHooks {
    private NoellesRoleEnhancementClientHooks() {
    }

    public static void register() {
        GetInstinctHighlight.EVENT.register(NoellesRoleEnhancementClientHooks::criminologistHighlight);
        CanSeeMoney.EVENT.register(NoellesRoleEnhancementClientHooks::canSeeMoney);
        EntityRendererRegistry.register(SparkStrengthEntities.capsule(), FlyingItemEntityRenderer::new);
    }

    private static GetInstinctHighlight.HighlightResult criminologistHighlight(Entity target) {
        ClientPlayerEntity viewer = MinecraftClient.getInstance().player;
        if (viewer == null || !(target instanceof PlayerEntity targetPlayer)) {
            return null;
        }
        if (!GameFunctions.isPlayerPlayingAndAlive(viewer)) {
            return null;
        }

        GameWorldComponent gameComponent = GameWorldComponent.KEY.get(viewer.getWorld());
        Role role = gameComponent.getRole(viewer);
        if (!NoellesRoleIds.isDetective(role)) {
            return null;
        }

        RoleEnhancementPlayerComponent component = RoleEnhancementPlayerComponent.KEY.get(viewer);
        if (component.isCriminologistRevealing(targetPlayer.getUuid())) {
            return GetInstinctHighlight.HighlightResult.always(
                    NoellesRoleEnhancementRules.CRIMINOLOGIST_HIGHLIGHT_COLOR
            );
        }
        return null;
    }

    private static CanSeeMoney.Result canSeeMoney(PlayerEntity player) {
        if (player == null || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            return null;
        }
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        return NoellesRoleEnhancementRules.isGoodMoneyRole(role) || EffectiveTraitService.hasImpostor(player)
                ? CanSeeMoney.Result.ALLOW
                : null;
    }

    public static String playerName(java.util.UUID uuid) {
        var entry = WatheClient.PLAYER_ENTRIES_CACHE.get(uuid);
        if (entry != null && entry.getDisplayName() != null) {
            return entry.getDisplayName().getString();
        }
        if (entry != null) {
            return entry.getProfile().getName();
        }
        var client = MinecraftClient.getInstance();
        if (client.player != null) {
            var profile = GameWorldComponent.KEY.get(client.player.getWorld()).getGameProfiles().get(uuid);
            if (profile != null) {
                return profile.getName();
            }
        }
        return uuid.toString();
    }
}
