package annina.sparkstrength.role.corruptcop;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.DoorInteraction;
import dev.doctor4t.wathe.api.event.GetInstinctHighlight;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Pure Corrupt Cop rules keyed by stable role/item ids.
 * 黑警的纯规则：项目本身依赖 NoellesRoles，这里使用稳定 id 是为了让规则和测试保持轻量。
 */
public final class CorruptCopRules {
    public static final Identifier CORRUPT_COP_ID = Identifier.of("noellesroles", "corrupt_cop");
    public static final Identifier NEUTRAL_MASTER_KEY_ID = Identifier.of("noellesroles", "neutral_master_key");
    // SparkWitch Insider (内应), matched by stable id only: SparkWitch is not a SparkStrength dependency.
    // SparkWitch 的内应，仅按稳定 id 匹配：SparkWitch 不是 SparkStrength 的依赖。
    public static final Identifier INSIDER_ID = Identifier.of("sparkwitch", "insider");
    public static final int ABILITY_COLOR = 0x193264;
    public static final float LATERAL_SPEED_MULTIPLIER = 2.25F;
    public static final int INSTINCT_PRIORITY = 90;
    public static final int NEUTRAL_MASTER_KEY_COOLDOWN_TICKS = 200;
    private static final double NORMALIZED_MOVEMENT_INPUT_EPSILON = 1.0E-7D;

    private CorruptCopRules() {
    }

    public static @Nullable GetInstinctHighlight.HighlightResult instinctHighlight(
            Role viewerRole,
            boolean viewerAlive,
            boolean viewerSpectatingOrCreative,
            boolean samePlayer,
            boolean targetAlive,
            boolean targetSpectatingOrCreative,
            boolean targetInvisible,
            boolean throughWallsVision
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
        /*
         * NoellesRoles' Moment vision window answers keyless at priority 0, which this priority-90 answer outranks;
         * stay keyless during that window so the cop's Moment wall vision is not reduced to key-held instinct.
         * NoellesRoles 的黑警时刻透视窗口以优先级 0 免按键作答，会被这里的优先级 90 覆盖；
         * 因此窗口期间同样免按键，避免黑警时刻的透视退化为按住本能键才可见。
         */
        return throughWallsVision
                ? GetInstinctHighlight.HighlightResult.always(viewerRole.color(), INSTINCT_PRIORITY)
                : GetInstinctHighlight.HighlightResult.withKeybind(viewerRole.color(), INSTINCT_PRIORITY);
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
        return neutralMasterKeyDoorResult(
                handItemId,
                playerRole != null && playerRole.isNeutral(),
                isCorruptCop(playerRole),
                doorType,
                blasted,
                jammed,
                open,
                requiresKey,
                coolingDown
        );
    }

    public static DoorInteraction.DoorInteractionResult neutralMasterKeyDoorResult(
            Identifier handItemId,
            boolean neutralKeyUser,
            boolean corruptCopKeyUser,
            DoorInteraction.DoorType doorType,
            boolean blasted,
            boolean jammed,
            boolean open,
            boolean requiresKey,
            boolean coolingDown
    ) {
        /*
         * 中立万能钥匙现在对所有中立身份生效；验尸官伪装中立尸体时，
         * 调用方会把 neutralKeyUser 置为 true，让临时获得的钥匙同样能开列车门。
         */
        if (!NEUTRAL_MASTER_KEY_ID.equals(handItemId) || !neutralKeyUser) {
            return DoorInteraction.DoorInteractionResult.PASS;
        }
        if (blasted || jammed || open) {
            return DoorInteraction.DoorInteractionResult.PASS;
        }
        if (!canNeutralMasterKeyOpen(doorType, requiresKey, corruptCopKeyUser)) {
            return DoorInteraction.DoorInteractionResult.PASS;
        }
        return coolingDown
                ? DoorInteraction.DoorInteractionResult.DENY
                : DoorInteraction.DoorInteractionResult.ALLOW;
    }

    public static boolean isCorruptCop(Role role) {
        return role != null && CORRUPT_COP_ID.equals(role.identifier());
    }

    public static boolean isInsider(Role role) {
        return role != null && INSIDER_ID.equals(role.identifier());
    }

    public static boolean usesKillerStyleInstinctLight(
            Role viewerRole,
            boolean instinctKeyPressed,
            boolean viewerPlayingAndAlive
    ) {
        return isCorruptCop(viewerRole) && instinctKeyPressed && viewerPlayingAndAlive;
    }

    public static boolean nextAbilityActive(boolean isCorruptCop, boolean currentlyActive) {
        return isCorruptCop && !currentlyActive;
    }

    public static Vec3d lateralVelocityBonus(
            Vec3d movementInput,
            float speed,
            float yaw,
            boolean isCorruptCop,
            boolean abilityActive,
            boolean aliveSurvival
    ) {
        if (!isCorruptCop || !abilityActive || !aliveSurvival || movementInput == null) {
            return Vec3d.ZERO;
        }

        double lengthSquared = movementInput.lengthSquared();
        if (lengthSquared < NORMALIZED_MOVEMENT_INPUT_EPSILON) {
            return Vec3d.ZERO;
        }

        Vec3d normalizedInput = lengthSquared > 1.0D ? movementInput.normalize() : movementInput;
        double lateralInput = normalizedInput.x;
        if (Math.abs(lateralInput) < NORMALIZED_MOVEMENT_INPUT_EPSILON) {
            return Vec3d.ZERO;
        }

        // Vanilla already applied one lateral share; add only the remaining multiplier after normalization.
        // 原版已经应用了一份横移速度；归一化之后只补上剩余倍率。
        double bonusSpeed = lateralInput * speed * (LATERAL_SPEED_MULTIPLIER - 1.0D);
        float radians = yaw * MathHelper.RADIANS_PER_DEGREE;
        return new Vec3d(
                bonusSpeed * MathHelper.cos(radians),
                0.0D,
                bonusSpeed * MathHelper.sin(radians)
        );
    }

    private static boolean canNeutralMasterKeyOpen(
            DoorInteraction.DoorType doorType,
            boolean requiresKey,
            boolean corruptCop
    ) {
        return doorType == DoorInteraction.DoorType.TRAIN_DOOR
                // 仍保留 SparkStrength 黑警增强里原本的小门行为；其它中立角色只吃“中立万能钥匙”的列车门能力。
                || (corruptCop && doorType == DoorInteraction.DoorType.SMALL_DOOR && requiresKey);
    }
}
