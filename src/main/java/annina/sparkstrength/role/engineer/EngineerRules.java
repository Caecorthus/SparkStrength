package annina.sparkstrength.role.engineer;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * 工程师增强的可调规则集中处。
 *
 * <p>所有价格、范围、持续时间和商店条目 ID 都放在这里，后续平衡数值时只需要改这一处，
 * 其它物品、实体、商店和回放逻辑都读取这些常量。</p>
 */
public final class EngineerRules {
    public enum CaptureTickDecision {
        WAIT,
        TRIGGER,
        EXPIRE
    }

    public static final Identifier ENGINEER_ID = Identifier.of("noellesroles", "engineer");

    public static final String CAPTURE_DEVICE_ENTRY_ID = "sparkstrength_capture_device";
    public static final String POWER_RESTORATION_ENTRY_ID = "sparkstrength_power_restoration";
    public static final String WATHE_BLACKOUT_ENTRY_ID = "blackout";

    public static final int CAPTURE_DEVICE_PRICE = 125;
    public static final int POWER_RESTORATION_PRICE = 300;

    public static final double CAPTURE_RADIUS = 5.0D;
    public static final double CAPTURE_RADIUS_SQUARED = CAPTURE_RADIUS * CAPTURE_RADIUS;
    public static final int CAPTURE_STUN_TICKS = GameConstants.getInTicks(0, 5);
    public static final int CAPTURE_MAX_LIFETIME_TICKS = GameConstants.getInTicks(2, 0);
    public static final int BLACKOUT_COOLDOWN_AFTER_RESTORATION_TICKS = GameConstants.getInTicks(1, 30);

    private EngineerRules() {
    }

    public static boolean isEngineer(@Nullable Role role) {
        return role != null && ENGINEER_ID.equals(role.identifier());
    }

    /**
     * Applies the complete capture-candidate policy to Minecraft facts supplied by the Adapter.
     * 对 Adapter 提供的 Minecraft 状态应用完整的捕捉候选规则。
     */
    public static boolean isCaptureCandidate(
            boolean isOwner,
            boolean playingAndAlive,
            boolean aliveAndSurvival,
            double squaredDistance
    ) {
        return !isOwner
                && playingAndAlive
                && aliveAndSurvival
                && squaredDistance <= CAPTURE_RADIUS_SQUARED;
    }

    /**
     * Keeps expiry authoritative when a player enters on the same tick as the lifetime boundary.
     * 当玩家恰好在寿命边界 tick 进入时，仍以装置过期为优先。
     */
    public static CaptureTickDecision decideCaptureTick(int lifetimeTicks, boolean hasCaptureCandidates) {
        if (lifetimeTicks >= CAPTURE_MAX_LIFETIME_TICKS) {
            return CaptureTickDecision.EXPIRE;
        }
        return hasCaptureCandidates ? CaptureTickDecision.TRIGGER : CaptureTickDecision.WAIT;
    }
}
