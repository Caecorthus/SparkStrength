package annina.sparkstrength.role.bodyguard;

import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Pure rules for the SparkStrength Bodyguard rework: prices, shield timings, stamina points and the front-arc check.
 * Matches the NoellesRoles Bodyguard by its stable id, so the rules never touch NoellesRoles classes.
 * SparkStrength 保镖改造的纯规则：价格、盾的时长、体力点数与前方半圆判定。按稳定 id 识别 NoellesRoles 保镖，
 * 不引用 NoellesRoles 的类。
 */
public final class BodyguardRules {
    public static final Identifier BODYGUARD_ID = Identifier.of("noellesroles", "bodyguard");
    public static final Identifier SECOND_STRIKE_TRAIT_ID = Identifier.of("sparktraits", "second_strike");

    public static final String VEST_ENTRY_ID = "sparkstrength_bodyguard_vest";
    public static final String SHIELD_ENTRY_ID = "sparkstrength_democracy_shield";
    public static final int VEST_PRICE = 200;
    public static final int SHIELD_PRICE = 100;
    public static final int ENTRY_STOCK = 1;

    /** Coins lost when the protected target dies. / 保护目标死亡时扣除的金币。 */
    public static final int TARGET_DEATH_PENALTY = 50;

    /**
     * One stamina point is half a HUD stamina icon of the base 200-tick bar (owner-confirmed), fixed in ticks so
     * Excellent Physique's doubled bar holds twice the blocks.
     * 一点体力 = 基础 200 tick 体力条的半个 HUD 图标（所有者确认）；按固定 tick 计，体质优异翻倍后能多挡一倍。
     */
    public static final int TICKS_PER_STAMINA_POINT = 10;
    public static final int SHIELD_BLOCK_POINTS = 5;
    public static final int SHIELD_SWALLOW_POINTS = 8;
    public static final int SHIELD_CEREMONIAL_SWORD_POINTS = 10;
    public static final int SHIELD_TR_SHELL_POINTS = 15;

    /** Longest raise; vanilla ends the use and calls finishUsing. / 最长举盾时间；到时原版结束使用并调用 finishUsing。 */
    public static final int MAX_RAISE_TICKS = 200;
    /** Like vanilla's shield, a raise only blocks after 5 ticks. / 与原版盾相同，举起 5 tick 后才生效。 */
    public static final int BLOCK_WARMUP_TICKS = 5;
    /** Cooldown per raised tick: 1 s up costs 3 s, the full 10 s costs 30 s. / 每举 1 tick 冷却 3 tick：举 1 秒冷却 3 秒，举满 10 秒冷却 30 秒。 */
    public static final int COOLDOWN_PER_RAISED_TICK = 3;
    public static final int MIN_LOWER_COOLDOWN_TICKS = 20;
    public static final int FULL_COOLDOWN_TICKS = MAX_RAISE_TICKS * COOLDOWN_PER_RAISED_TICK;
    /** A shield broken by running out of stamina always rests the full 30 s. / 体力耗尽破盾固定冷却 30 秒。 */
    public static final int BREAK_COOLDOWN_TICKS = FULL_COOLDOWN_TICKS;
    /** Delay when switching to the shield, and the cooldown given to a cooldown item switched to from it. / 切到盾的延迟，以及从盾切到有冷却物品时给该物品的冷却。 */
    public static final int SWITCH_DELAY_TICKS = 40;
    /** Raised walk speed: 30% of normal, vanilla's raised shield is 20%. / 举盾移速：正常的 30%，原版举盾为 20%。 */
    public static final double RAISED_SPEED_MULTIPLIER = 0.3D;
    /** A knife "kill" from farther than this is a mark or a puppet, not an attacker in front. / 超过该距离的刀杀是标记或傀儡，攻击者并不在面前。 */
    public static final double MELEE_MAX_DISTANCE = 5.0D;

    private BodyguardRules() {
    }

    public static boolean isBodyguard(@Nullable Role role) {
        return role != null && BODYGUARD_ID.equals(role.identifier());
    }

    public static int staminaCostTicks(int points) {
        return points * TICKS_PER_STAMINA_POINT;
    }

    /** Cooldown after lowering the shield, given how long it was up. / 按举盾时长计算放下后的冷却。 */
    public static int cooldownAfterLowering(int raisedTicks) {
        int scaled = Math.max(0, raisedTicks) * COOLDOWN_PER_RAISED_TICK;
        return Math.max(MIN_LOWER_COOLDOWN_TICKS, Math.min(FULL_COOLDOWN_TICKS, scaled));
    }

    public static boolean isBlockingRaise(int useTicks) {
        return useTicks >= BLOCK_WARMUP_TICKS;
    }

    /**
     * Vanilla's shield arc: the source is in front when the horizontal direction from the source to the holder points
     * against the holder's head-yaw facing. A source at the holder's own spot is not in front.
     * 原版盾的判定：从来源指向持盾者的水平方向与持盾者头部朝向相反时，来源在前方。与持盾者重合的来源不算前方。
     */
    public static boolean isInFront(float headYawDegrees, double holderX, double holderZ, double sourceX, double sourceZ) {
        double towardHolderX = holderX - sourceX;
        double towardHolderZ = holderZ - sourceZ;
        double length = Math.sqrt(towardHolderX * towardHolderX + towardHolderZ * towardHolderZ);
        if (length < 1.0E-6D) {
            return false;
        }
        double yaw = Math.toRadians(headYawDegrees);
        double facingX = -Math.sin(yaw);
        double facingZ = Math.cos(yaw);
        return (towardHolderX / length) * facingX + (towardHolderZ / length) * facingZ < 0.0D;
    }

    /**
     * Whether a target death costs this Bodyguard coins: a living, unswallowed, non-Impostor Bodyguard whose current
     * target died, once per target. An Impostor Bodyguard is paid by SparkTraits for the same death instead.
     * 目标死亡是否扣该保镖的钱：存活、未被吞、非内鬼、且死者正是当前目标，每个目标只扣一次。内鬼保镖由 SparkTraits 为同一次死亡发奖励。
     */
    public static boolean shouldPenalize(
            boolean bodyguardAlive,
            boolean bodyguardSwallowed,
            boolean impostor,
            boolean victimIsCurrentTarget,
            boolean alreadyPenalizedForVictim
    ) {
        return bodyguardAlive && !bodyguardSwallowed && !impostor && victimIsCurrentTarget && !alreadyPenalizedForVictim;
    }

    /** Coins actually taken: the penalty, but never below zero. / 实际扣除的金币：罚款额，但余额不低于 0。 */
    public static int penaltyAmount(int balance) {
        return Math.max(0, Math.min(balance, TARGET_DEATH_PENALTY));
    }
}
