package annina.sparkstrength.role.jester;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * Pure rules for SparkStrength's tweaks to the NoellesRoles Jester Moment: the kill-driven screen grayscale, who is
 * cut off from voice chat, and where the triggering shooter is thrown. No Minecraft types, so local tests run it as is.
 * SparkStrength 对 NoellesRoles 小丑时刻的调整规则：按击杀累积的屏幕灰度、谁被切断语音、开枪者被传送到哪里。
 * 不依赖 Minecraft 类型，本地测试可直接运行。
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
     * Room spawns closer than this to the Jester are used only when every room is that close.
     * 距小丑不足该距离的房间出生点，只有在所有房间都这么近时才会被选用。
     */
    public static final double SHOOTER_MIN_DISTANCE_FROM_JESTER = 10.0D;

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

    /**
     * Picks where the shooter lands, or -1 to leave them in place (no room has a spawn point).
     * A room is drawn uniformly, then a spawn point inside it, both from the spawns at least
     * {@link #SHOOTER_MIN_DISTANCE_FROM_JESTER} from the Jester when any exist.
     * 选出开枪者的落点，返回 -1 表示原地不动（没有任何房间有出生点）。先等概率抽房间，再在房间内抽出生点；
     * 只要存在距小丑足够远的出生点，就只在这些出生点中抽取。
     *
     * @param spots   every spawn point of every room / 所有房间的所有出生点
     * @param nextInt {@code bound -> [0, bound)} random source / 随机源
     * @return index into {@code spots}, or -1 / {@code spots} 的下标，或 -1
     */
    public static int pickShooterSpot(List<RoomSpot> spots, double jesterX, double jesterY, double jesterZ,
                                      IntUnaryOperator nextInt) {
        double minDistanceSq = SHOOTER_MIN_DISTANCE_FROM_JESTER * SHOOTER_MIN_DISTANCE_FROM_JESTER;
        List<Integer> far = new ArrayList<>();
        for (int i = 0; i < spots.size(); i++) {
            if (spots.get(i).distanceSq(jesterX, jesterY, jesterZ) >= minDistanceSq) {
                far.add(i);
            }
        }
        List<Integer> pool = far;
        if (pool.isEmpty()) {
            pool = new ArrayList<>();
            for (int i = 0; i < spots.size(); i++) {
                pool.add(i);
            }
        }
        if (pool.isEmpty()) {
            return -1;
        }

        List<Integer> rooms = new ArrayList<>();
        for (int index : pool) {
            int room = spots.get(index).room();
            if (!rooms.contains(room)) {
                rooms.add(room);
            }
        }
        int room = rooms.get(nextInt.applyAsInt(rooms.size()));
        List<Integer> inRoom = new ArrayList<>();
        for (int index : pool) {
            if (spots.get(index).room() == room) {
                inRoom.add(index);
            }
        }
        return inRoom.get(nextInt.applyAsInt(inRoom.size()));
    }

    /** One spawn point of a Wathe room (1-based room number). / Wathe 房间的一个出生点（房间号从 1 开始）。 */
    public record RoomSpot(int room, double x, double y, double z) {
        double distanceSq(double otherX, double otherY, double otherZ) {
            double dx = x - otherX;
            double dy = y - otherY;
            double dz = z - otherZ;
            return dx * dx + dy * dy + dz * dz;
        }
    }
}
