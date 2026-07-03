package annina.sparkstrength.role;

import dev.caecorthus.sparkfactionapi.api.FactionInstinctPolicy;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.DoorInteraction;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Weakly-coupled NoellesRoles Corrupt Cop compatibility rules.
 * 对 NoellesRoles 黑警的弱耦合兼容规则：只认稳定 id，不引入编译期依赖。
 */
public final class CorruptCopRules {
    public static final Identifier CORRUPT_COP_ID = Identifier.of("noellesroles", "corrupt_cop");
    public static final Identifier NEUTRAL_MASTER_KEY_ID = Identifier.of("noellesroles", "neutral_master_key");
    public static final int INSTINCT_PRIORITY = 90;
    public static final int NEUTRAL_MASTER_KEY_COOLDOWN_TICKS = 200;

    private CorruptCopRules() {
    }

    public static @Nullable FactionInstinctPolicy.InstinctResult instinctHighlight(
            Role viewerRole,
            boolean viewerAlive,
            boolean viewerSpectatingOrCreative,
            boolean samePlayer,
            boolean targetAlive,
            boolean targetSpectatingOrCreative,
            boolean targetInvisible
    ) {
        if (!isCorruptCop(viewerRole)
                || !viewerAlive
                || viewerSpectatingOrCreative
                || samePlayer
                || !targetAlive
                || targetSpectatingOrCreative
                || targetInvisible) {
            return null;
        }
        return FactionInstinctPolicy.InstinctResult.show(viewerRole.color(), true, INSTINCT_PRIORITY);
    }

    public static DoorInteraction.DoorInteractionResult neutralMasterKeyDoorResult(
            Identifier handItemId,
            Role playerRole,
            DoorInteraction.DoorType doorType,
            boolean blasted,
            boolean jammed,
            boolean open,
            boolean requiresKey,
            boolean coolingDown
    ) {
        if (!NEUTRAL_MASTER_KEY_ID.equals(handItemId) || !isCorruptCop(playerRole)) {
            return DoorInteraction.DoorInteractionResult.PASS;
        }
        if (blasted || jammed || open) {
            return DoorInteraction.DoorInteractionResult.PASS;
        }
        if (!canNeutralMasterKeyOpen(doorType, requiresKey)) {
            return DoorInteraction.DoorInteractionResult.PASS;
        }
        return coolingDown
                ? DoorInteraction.DoorInteractionResult.DENY
                : DoorInteraction.DoorInteractionResult.ALLOW;
    }

    public static boolean isCorruptCop(Role role) {
        return role != null && CORRUPT_COP_ID.equals(role.identifier());
    }

    private static boolean canNeutralMasterKeyOpen(DoorInteraction.DoorType doorType, boolean requiresKey) {
        return doorType == DoorInteraction.DoorType.TRAIN_DOOR
                || (doorType == DoorInteraction.DoorType.SMALL_DOOR && requiresKey);
    }
}
