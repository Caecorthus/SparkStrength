package annina.sparkstrength.role.jester;

import java.util.function.IntUnaryOperator;

/**
 * Pure rules for SparkStrength's tweaks to the NoellesRoles Jester Moment: the kill-driven screen grayscale, who is
 * cut off from voice chat, how long the shot freezes everyone, and how everyone is shuffled when the Jester revives.
 * No Minecraft types, so local tests run it as is.
 * SparkStrength 对 NoellesRoles 小丑时刻的调整规则：按击杀累积的屏幕灰度、谁被切断语音、中枪后全员定身多久、
 * 小丑复活时如何打乱所有人的位置。不依赖 Minecraft 类型，本地测试可直接运行。
 */
public final class JesterMomentRules {
    /** Each Jester Moment kill greys the Jester's screen by another 10%. / 小丑时刻每击杀一人，屏幕灰度再加 10%。 */
    public static final float GRAYSCALE_PER_KILL = 0.1F;
    /** The grayscale stops at 50%. / 灰度最高 50%。 */
    public static final float MAX_GRAYSCALE = 0.5F;
    /** One kill's step fades in over half a second. / 每一档灰度在半秒内渐入。 */
    public static final float GRAYSCALE_FADE_IN_PER_TICK = GRAYSCALE_PER_KILL / 10.0F;
    /** The whole effect clears in half a second once the moment ends. / 时刻结束后半秒内完全褪去。 */
    public static final float GRAYSCALE_FADE_OUT_PER_TICK = MAX_GRAYSCALE / 10.0F;
    /**
     * The shot freezes everyone for the Jester's fake death plus this margin. The revive lifts it on time; the margin
     * only bounds a transition NoellesRoles abandons.
     * 中枪后全员定身时长 = 小丑假死时长 + 此余量。复活时会准时解除；余量只用于 NoellesRoles 中途放弃转变时兜底。
     */
    public static final int FREEZE_MARGIN_TICKS = 40;

    private JesterMomentRules() {
    }

    /** Target grayscale for a kill count: 10% per kill, capped at 50%. / 击杀数对应的目标灰度：每人 10%，上限 50%。 */
    public static float grayscaleForKills(int kills) {
        if (kills <= 0) {
            return 0.0F;
        }
        return Math.min(MAX_GRAYSCALE, kills * GRAYSCALE_PER_KILL);
    }

    /** One client tick of easing toward the target. / 向目标灰度缓动一个客户端 tick。 */
    public static float stepGrayscale(float current, float target) {
        if (current < target) {
            return Math.min(target, current + GRAYSCALE_FADE_IN_PER_TICK);
        }
        return Math.max(target, current - GRAYSCALE_FADE_OUT_PER_TICK);
    }

    /**
     * The Jester is deaf and mute from the shot that starts the moment (fake death and stasis) until the moment ends.
     * 小丑从触发时刻的那一枪起（假死与禁锢期间）直到时刻结束，都听不到也说不出。
     */
    public static boolean isVoiceBlocked(boolean isJester, boolean inPsychoMode, boolean transitioning) {
        return isJester && (inPsychoMode || transitioning);
    }

    /** How long the shot freezes everyone. / 中枪后全员定身的 tick 数。 */
    public static int freezeTicks(int fakeDeathTicks) {
        return Math.max(0, fakeDeathTicks) + FREEZE_MARGIN_TICKS;
    }

    /**
     * Where everyone goes when the Jester revives: {@code result[i]} is the index of the spot player {@code i} moves
     * to. Sattolo's algorithm draws a uniformly random single cycle, so with two or more players nobody keeps their
     * own spot; one player (or none) stays put.
     * 小丑复活时每个人的去向：{@code result[i]} 为玩家 {@code i} 要去的位置下标。Sattolo 算法等概率抽取单一轮换，
     * 因此两人及以上时没有人留在原位；只有一人（或无人）时原地不动。
     *
     * @param nextInt {@code bound -> [0, bound)} random source / 随机源
     */
    public static int[] shuffleSpots(int players, IntUnaryOperator nextInt) {
        int[] spots = new int[Math.max(0, players)];
        for (int i = 0; i < spots.length; i++) {
            spots[i] = i;
        }
        for (int i = spots.length - 1; i > 0; i--) {
            int j = nextInt.applyAsInt(i);
            int swap = spots[i];
            spots[i] = spots[j];
            spots[j] = swap;
        }
        return spots;
    }
}
