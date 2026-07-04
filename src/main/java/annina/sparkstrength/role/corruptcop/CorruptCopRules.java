package annina.sparkstrength.role.corruptcop;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.DoorInteraction;
import dev.doctor4t.wathe.api.event.GetInstinctHighlight;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Pure Corrupt Cop rules keyed by stable role/item ids.
 * 黑警的纯规则：项目本身依赖 NoellesRoles，这里使用稳定 id 是为了让规则和测试保持轻量。
 */
public final class CorruptCopRules {
    public static final Identifier CORRUPT_COP_ID = Identifier.of("noellesroles", "corrupt_cop");
    public static final Identifier NEUTRAL_MASTER_KEY_ID = Identifier.of("noellesroles", "neutral_master_key");
    public static final int INSTINCT_PRIORITY = 90;
    public static final int NEUTRAL_MASTER_KEY_COOLDOWN_TICKS = 200;

    private CorruptCopRules() {
    }

    public static @Nullable GetInstinctHighlight.HighlightResult instinctHighlight(
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
        return GetInstinctHighlight.HighlightResult.withKeybind(viewerRole.color(), INSTINCT_PRIORITY);
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
