package annina.sparkstrength.role.toxicologist;

import annina.sparkstrength.role.coroner.CoronerService;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Blue-poison rules for the Toxicologist buff: Blue Vitriol, Blue Belladonna and the blue-state passive.
 * 毒理学家蓝毒增强规则：蓝矾、蓝颠茄与蓝毒状态被动。
 */
public final class ToxicologistBlueRules {
    public static final String BLUE_VITRIOL_ENTRY_ID = "sparkstrength_toxicologist_blue_vitriol";
    public static final int BLUE_VITRIOL_PRICE = 50;
    public static final String BLUE_BELLADONNA_ENTRY_ID = "sparkstrength_toxicologist_blue_belladonna";
    public static final int BLUE_BELLADONNA_PRICE = 25;
    public static final int MAX_STACK = 16;

    /** Blue state the Toxicologist gets from eating Blue Belladonna: 5 seconds, refreshed (not added) on repeat.
     *  毒理学家吃蓝颠茄获得的蓝毒状态：5 秒，重复食用只刷新不叠加。 */
    public static final int BELLADONNA_BLUE_TICKS = 5 * 20;
    public static final int BELLADONNA_EAT_TICKS = 16;

    /** "10 sanity per second" in SparkTraits' convention (1 point = 0.01 Wathe mood). 按 SparkTraits 约定 1 点理智 = 0.01 心情值。 */
    public static final float SANITY_REGEN_PER_SECOND = 0.10f;
    public static final float SANITY_REGEN_PER_TICK = SANITY_REGEN_PER_SECOND / 20.0f;
    /** Status-effect amplifier 1 is Speed II. 状态效果等级 1 即速度 II。 */
    public static final int SPEED_AMPLIFIER = 1;
    /** Owned passive Speed lasts 1 s and is re-applied once it drops below 0.5 s, so it ends within a tick of the
     *  blue state without resending the effect every tick.
     *  被动速度每次给 1 秒，剩余不足 0.5 秒时才续上；蓝毒状态结束后一 tick 内移除，也不必每 tick 重发效果。 */
    public static final int PASSIVE_SPEED_TICKS = 20;
    public static final int PASSIVE_SPEED_REAPPLY_BELOW_TICKS = 10;
    /** Our instance is recognised by amplifier plus expected remaining duration, as VeteranBlackoutService does.
     *  按等级加预期剩余时长识别自己给的效果，做法同 VeteranBlackoutService。 */
    public static final int PASSIVE_SPEED_DURATION_TOLERANCE_TICKS = 2;

    private ToxicologistBlueRules() {
    }

    /**
     * Real Toxicologists and Coroners currently disguised as one share the blue buff; works on both sides because
     * role and disguise components are synced to their owner.
     * 真正的毒理学家与当前伪装成毒理学家的验尸官共享蓝毒增强；职业与伪装组件都会同步给本人，所以双端可用。
     */
    public static boolean isToxicologistLike(@Nullable PlayerEntity player) {
        if (player == null) {
            return false;
        }
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        return isToxicologistLike(ToxicologistCapsuleRules.isToxicologist(role), CoronerService.hasToxicologistDisguise(player));
    }

    public static boolean isToxicologistLike(boolean toxicologistRole, boolean coronerToxicologistDisguise) {
        return toxicologistRole || coronerToxicologistDisguise;
    }

    public static float regeneratedMood(float mood) {
        return Math.min(1.0f, mood + SANITY_REGEN_PER_TICK);
    }

    public enum PassiveSpeedAction {
        /** Add or refresh our Speed II. 添加或续上我们的速度 II。 */
        APPLY,
        /** Our Speed II is still fresh enough. 我们的速度 II 仍然够长。 */
        KEEP,
        /** Another source's equal-or-stronger SPEED wins untouched; stop claiming it. 其它来源的同级或更强速度优先，不碰也不认领。 */
        YIELD
    }

    public static boolean isPassiveEligible(boolean toxicologistLike, boolean playingAndAlive, boolean spectatingOrCreative) {
        return toxicologistLike && playingAndAlive && !spectatingOrCreative;
    }

    /**
     * The blue state is either remaining window ticks after SparkTraits' own decrement, or a drain SparkTraits skipped
     * for this player during the current world tick (the exemption heartbeat). See ToxicologistBluePassiveService.
     * 蓝毒状态 = SparkTraits 自减后仍有剩余窗口，或本世界 tick 内 SparkTraits 刚为该玩家跳过一次扣理智（豁免心跳）。
     */
    public static boolean isBlueStateActive(int remainingWindowTicks, boolean drainSkippedThisTick) {
        return remainingWindowTicks > 0 || drainSkippedThisTick;
    }

    /** Infinite (negative) durations and instances outliving ours can never be ours.
     *  无限时长（负数）或活得比我们给的更久的效果一定不是我们的。 */
    public static boolean isOwnedPassiveSpeed(int amplifier, int remainingTicks, int appliedTicks, long elapsedTicks) {
        long elapsed = Math.max(0L, elapsedTicks);
        if (amplifier != SPEED_AMPLIFIER || remainingTicks < 0 || elapsed >= appliedTicks) {
            return false;
        }
        return Math.abs(remainingTicks - (appliedTicks - elapsed)) <= PASSIVE_SPEED_DURATION_TOLERANCE_TICKS;
    }

    /**
     * A weaker foreign SPEED is overridden: vanilla keeps it as a hidden effect under ours and restores it when ours
     * expires. An equal or stronger foreign SPEED (any duration, including infinite) is never overwritten or claimed.
     * 更弱的外来速度会被覆盖：原版把它藏在我们的效果下，我们的到期后自动恢复。同级或更强的外来速度（含无限时长）绝不覆盖也不认领。
     */
    public static PassiveSpeedAction passiveSpeedAction(boolean hasSpeed, int amplifier, int remainingTicks, boolean owned) {
        if (!hasSpeed) {
            return PassiveSpeedAction.APPLY;
        }
        if (owned) {
            return remainingTicks < PASSIVE_SPEED_REAPPLY_BELOW_TICKS ? PassiveSpeedAction.APPLY : PassiveSpeedAction.KEEP;
        }
        return amplifier >= SPEED_AMPLIFIER ? PassiveSpeedAction.YIELD : PassiveSpeedAction.APPLY;
    }

    /**
     * When ours hides a weaker foreign SPEED (applied before or after ours), removing it would also delete the hidden
     * one, so we let ours run out (at most PASSIVE_SPEED_TICKS) and vanilla restores the weaker effect.
     * 若我们的效果下藏着更弱的外来速度（无论先后施加），直接移除会连它一起删掉；因此让我们的效果自然到期
     * （最多 PASSIVE_SPEED_TICKS），由原版恢复。
     */
    public static boolean shouldRemoveOwnedPassiveSpeed(boolean owned, boolean hidesAnotherSpeed) {
        return owned && !hidesAnotherSpeed;
    }
}
