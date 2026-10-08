package annina.sparkstrength.role.bomber.drone;

import java.util.Set;

/**
 * Pure weapon rules for breaking Bomber drones (no Minecraft state): melee id whitelist, reaches, aim tolerance and
 * the knife cooldown formula. Ids are plain strings so optional mods (SparkWitch) need no compile dependency.
 * 击毁炸弹客无人机的纯武器规则（不依赖 Minecraft 状态）：近战 id 白名单、距离、瞄准容差与刀冷却公式。
 * id 使用纯字符串，可选模组（SparkWitch）无需编译期依赖。
 */
public final class DroneWeaponRules {
    /**
     * Melee weapons matched by registry id: they carry no positive attack-damage modifier (the Wathe bat's is +0) or
     * belong to an optional mod. Everything else is judged by item tags and attack-damage modifiers.
     * 按注册 id 匹配的近战武器：它们没有正的攻击伤害修饰（Wathe 球棒为 +0）或来自可选模组。其余物品按物品标签与攻击伤害修饰判断。
     */
    public static final Set<String> MELEE_WEAPON_IDS = Set.of(
            "wathe:knife",
            "wathe:bat",
            "sparkwitch:ceremonial_sword",
            "sparkwitch:ninja_knife",
            "sparkwitch:fire_poker",
            "sparkwitch:vendetta_knife");

    /** SparkWitch kunai: server-side right-click hitscan. / SparkWitch 苦无：服务端右键即时射线。 */
    public static final String NINJA_KNIFE_ID = "sparkwitch:ninja_knife";
    /** SparkWitch Grand Witch sword: right-click dash. / SparkWitch 大魔女仪礼剑：右键冲刺。 */
    public static final String CEREMONIAL_SWORD_ID = "sparkwitch:ceremonial_sword";

    /** Server melee reach: vanilla interaction range 3 plus its own 1-block allowance. / 服务端近战距离：原版 3 格加 1 格余量。 */
    public static final double MELEE_REACH = 4.0;
    /** Wathe knife stab ray plus latency tolerance. / Wathe 刀刺射线长度加延迟容差。 */
    public static final double KNIFE_RANGE = 3.0;
    public static final double KNIFE_REACH_TOLERANCE = 0.5;
    /** Client ray lengths (fallbacks only; the original pick's distance wins). / 客户端射线长度（仅兜底，以原选择距离为准）。 */
    public static final double REVOLVER_RANGE = 30.0;
    public static final double DERRINGER_RANGE = 7.0;
    public static final double DEMON_HUNTER_PISTOL_RANGE = 5.0;
    /** Wathe's server cap for gun targets ({@code GunShootPayload$Receiver}). / Wathe 服务端枪械目标距离上限。 */
    public static final double GUN_MAX_DISTANCE = 65.0;
    /** Latency fallback cone between the look vector and a drone sample point. / 视线与无人机采样点的延迟兜底夹角。 */
    public static final double GUN_AIM_CONE_DEGREES = 25.0;
    /** SparkWitch kunai ray and its kill cooldown, mirrored by value. / SparkWitch 苦无射线与击杀冷却（按数值镜像）。 */
    public static final double NINJA_KNIFE_RANGE = 4.0;
    public static final int NINJA_KNIFE_COOLDOWN_TICKS = 30 * 20;
    /** Ceremonial sword dash watch: ticks, minimum step that counts as dashing, sweep padding. / 仪礼剑冲刺监视参数。 */
    public static final int SWORD_DASH_WATCH_TICKS = 7;
    public static final double SWORD_DASH_MIN_STEP = 0.5;
    public static final double SWORD_DASH_PADDING = 0.15;
    /** Drones are small; every ray grows their box by this margin (client pick and server check alike). / 射线判定外扩量。 */
    public static final double TARGET_MARGIN = 0.1;

    private DroneWeaponRules() {
    }

    public static boolean isWhitelistedMeleeId(String id) {
        return id != null && MELEE_WEAPON_IDS.contains(id);
    }

    /** Nearest-wins: a drone takes the hit only when strictly nearer. / 最近者命中：无人机严格更近才承受命中。 */
    public static boolean droneWins(double droneDistanceSquared, double targetDistanceSquared) {
        return droneDistanceSquared >= 0.0 && droneDistanceSquared < targetDistanceSquared;
    }

    public static boolean withinReach(double squaredDistance, double reach) {
        return Double.isFinite(squaredDistance) && squaredDistance >= 0.0 && squaredDistance <= reach * reach;
    }

    /**
     * Wathe's dynamic knife cooldown (5 s less per excess player, never below 10 s), as applied after a real stab.
     * Wathe 的动态刀冷却（每多一名多余玩家减 5 秒，最低 10 秒），与真实刺杀后一致。
     */
    public static int knifeCooldownTicks(int baseTicks, int totalPlayers, int killerCount, int killerDividend,
                                         int minimumTicks, int reductionPerExcess) {
        int excessPlayers = Math.max(0, totalPlayers - killerCount * killerDividend);
        return Math.max(minimumTicks, baseTicks - excessPlayers * reductionPerExcess);
    }

    /**
     * Per-segment probe for {@link #firstPathEntry}: distance from the start of segment {@code index} to where its first
     * {@code cutLength} blocks enter the nearest eligible drone, or a negative value when none does.
     * {@link #firstPathEntry} 的逐段探测：第 {@code index} 段前 {@code cutLength} 格内进入最近合格无人机处、距该段起点的距离；
     * 没有时返回负数。
     */
    @FunctionalInterface
    public interface SegmentProbe {
        double entryDistance(int index, double cutLength);
    }

    /**
     * Polyline nearest-wins (SparkWitch USEC rifle, whose AP rounds pierce blocks and sink). Walks the segments in flight
     * order, cutting the one that crosses {@code reach} (a path distance), and accepts the first probe entry strictly
     * inside its cut. Returns that path distance (travelled segment lengths plus the entry), always strictly less than
     * {@code reach}; -1 when nothing is entered before {@code reach} or the input is unusable. Zero-length, negative or
     * non-finite segments are skipped and add no distance, like SparkWitch's own walk.
     * 折线的最近者命中（SparkWitch USEC 步枪，其 AP 子弹会穿透方块并下坠）。按飞行顺序逐段检查，在跨过 {@code reach}（路径距离）
     * 的那一段处截断，接受第一个严格位于截断长度之内的探测结果。返回该路径距离（已走过的各段长度加段内距离），始终严格小于
     * {@code reach}；在 {@code reach} 之前没有命中或输入不可用时返回 -1。长度为零、为负或非有限的段被跳过且不计距离，
     * 与 SparkWitch 自身的遍历一致。
     */
    public static double firstPathEntry(double[] segmentLengths, double reach, SegmentProbe probe) {
        if (segmentLengths == null || probe == null || !(reach > 0.0) || !Double.isFinite(reach)) {
            return -1.0;
        }
        double travelled = 0.0;
        for (int index = 0; index < segmentLengths.length && travelled < reach; index++) {
            double length = segmentLengths[index];
            if (!(length > 0.0) || !Double.isFinite(length)) {
                continue;
            }
            double cut = Math.min(length, reach - travelled);
            double entry = probe.entryDistance(index, cut);
            if (entry >= 0.0 && entry < cut) {
                double distance = travelled + entry;
                // Rounding must never hand back reach itself (that would read as "not absorbed").
                // 舍入误差绝不能返回 reach 本身（那会被视为“未吸收”）。
                return distance < reach ? distance : -1.0;
            }
            travelled += length;
        }
        return -1.0;
    }
}
