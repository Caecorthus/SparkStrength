package annina.sparkstrength.role;

import dev.caecorthus.sparkfactionapi.api.FactionInstinctPolicy;
import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.DoorInteraction;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * Bridges NoellesRoles Corrupt Cop enhancements through public Wathe/SparkFactionAPI hooks.
 * 通过 wathe 与 SparkFactionAPI 的公开钩子桥接 NoellesRoles 黑警增强。
 */
public final class CorruptCopFeatureService {
    private static boolean registered;

    private CorruptCopFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        SparkFactionApi.registerInstinctPolicy(CorruptCopFeatureService::instinctHighlight);
        DoorInteraction.EVENT.register(CorruptCopFeatureService::doorInteraction);
    }

    private static FactionInstinctPolicy.InstinctResult instinctHighlight(
            PlayerEntity viewer,
            Entity target,
            GameWorldComponent gameComponent
    ) {
        if (!(target instanceof PlayerEntity targetPlayer)) {
            return null;
        }
        Role viewerRole = gameComponent.getRole(viewer);
        return CorruptCopRules.instinctHighlight(
                viewerRole,
                GameFunctions.isPlayerPlayingAndAlive(viewer),
                GameFunctions.isPlayerSpectatingOrCreative(viewer),
                viewer.getUuid().equals(targetPlayer.getUuid()),
                GameFunctions.isPlayerPlayingAndAlive(targetPlayer),
                GameFunctions.isPlayerSpectatingOrCreative(targetPlayer),
                targetPlayer.isInvisible()
        );
    }

    private static DoorInteraction.DoorInteractionResult doorInteraction(
            DoorInteraction.DoorInteractionContext context
    ) {
        PlayerEntity player = context.getPlayer();
        ItemStack handItem = context.getHandItem();
        Item item = handItem.getItem();
        Identifier handItemId = Registries.ITEM.getId(item);
        Role playerRole = GameWorldComponent.KEY.get(context.getWorld()).getRole(player);
        DoorInteraction.DoorInteractionResult result = CorruptCopRules.neutralMasterKeyDoorResult(
                handItemId,
                playerRole,
                context.getDoorType(),
                context.isBlasted(),
                context.isJammed(),
                context.isOpen(),
                context.requiresKey(),
                player.getItemCooldownManager().isCoolingDown(item)
        );
        if (result == DoorInteraction.DoorInteractionResult.ALLOW) {
            player.getItemCooldownManager().set(item, CorruptCopRules.NEUTRAL_MASTER_KEY_COOLDOWN_TICKS);
        }
        return result;
    }
}
