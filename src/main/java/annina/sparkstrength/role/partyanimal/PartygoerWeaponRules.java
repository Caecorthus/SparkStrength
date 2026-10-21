package annina.sparkstrength.role.partyanimal;

/**
 * Pure weapon rules for killing a Partygoer (no Minecraft state): reaches, mirrored provider values, nearest-wins against
 * the player target and Bomber drones, blast shapes. Ids are plain strings so optional mods (SparkWitch) need no compile
 * dependency. Owner rule (2026-10-21): anything that can kill a player kills a Partygoer in one hit; non-lethal things
 * never do.
 * 击杀派对客的纯武器规则（不依赖 Minecraft 状态）：距离、镜像的提供方数值、相对玩家目标与炸弹客无人机的“最近者命中”、爆炸形状。
 * id 使用纯字符串，可选模组（SparkWitch）无需编译期依赖。主人规则（2026-10-21）：任何能杀死玩家的东西都能一击打死派对客；
 * 不致命的东西永远不能。
 */
public final class PartygoerWeaponRules {
    /** Server melee reach for the Wathe bat: vanilla 3 plus its own 1-block allowance. / Wathe 球棒服务端近战距离。 */
    public static final double MELEE_REACH = 4.0;
    /** Wathe knife stab ray plus latency tolerance. / Wathe 刀刺射线长度加延迟容差。 */
    public static final double KNIFE_RANGE = 3.0;
    public static final double KNIFE_REACH_TOLERANCE = 0.5;
    /** Undercover tomahawk: the knife's 3 blocks plus the same lag allowance. / 卧底战术斧：刀的 3 格加相同容差。 */
    public static final double TOMAHAWK_REACH = 3.5;
    /** Client ray fallbacks (the original pick's distance wins). / 客户端射线兜底长度（以原选择距离为准）。 */
    public static final double REVOLVER_RANGE = 30.0;
    public static final double DERRINGER_RANGE = 7.0;
    public static final double DEMON_HUNTER_PISTOL_RANGE = 5.0;
    /** Wathe's server cap for gun targets ({@code GunShootPayload$Receiver}). / Wathe 服务端枪械目标距离上限。 */
    public static final double GUN_MAX_DISTANCE = 65.0;
    /** Latency fallback cone between the look vector and a box sample point. / 视线与采样点的延迟兜底夹角。 */
    public static final double GUN_AIM_CONE_DEGREES = 25.0;
    /** Every ray grows the Partygoer's box by this margin (client pick and server check alike). / 射线判定外扩量。 */
    public static final double TARGET_MARGIN = 0.1;

    // ---- NoellesRoles values, mirrored / NoellesRoles 数值镜像 ----

    /** Poison needle cooldown ({@code PoisonNeedleItem.USE_COOLDOWN_TICKS}). / 毒针冷却。 */
    public static final int POISON_NEEDLE_COOLDOWN_TICKS = 45 * 20;

    // ---- SparkWitch ids and values, mirrored (optional mod) / SparkWitch id 与数值镜像（可选模组） ----

    public static final String NINJA_KNIFE_ID = "sparkwitch:ninja_knife";
    public static final double NINJA_KNIFE_RANGE = 4.0;
    public static final int NINJA_KNIFE_COOLDOWN_TICKS = 30 * 20;
    public static final String FEATHER_BLADE_ID = "sparkwitch:feather_blade";
    public static final String BLACK_RAVEN_ROLE_ID = "sparkwitch:black_raven";
    public static final int FEATHER_COOLDOWN_TICKS = 60 * 20;
    public static final String CEREMONIAL_SWORD_ID = "sparkwitch:ceremonial_sword";
    /** CCA key of the Grand Witch runtime (sword kill cooldown). / 大魔女运行时组件的 CCA key（剑杀冷却）。 */
    public static final String GRAND_WITCH_RUNTIME_ID = "sparkwitch:grand_witch_runtime";
    public static final int CEREMONIAL_SWORD_KILL_COOLDOWN_TICKS = 30 * 20;
    /** Only the TR shell kills (DK/AC/MR only debuff). / 只有 TR 炮弹会致死（DK/AC/MR 只是减益）。 */
    public static final String TR_SHELL_ID = "sparkwitch:tr_shell";
    public static final int TR_SHELL_SIZE = 5;
    public static final String SHURIKEN_ENTITY_ID = "sparkwitch:ninja_shuriken";

    private PartygoerWeaponRules() {
    }

    /**
     * Nearest-wins: the Partygoer takes the hit only when strictly nearer than the player target and than the nearest
     * Bomber drone on the same ray (a tie goes to the player, then to the drone, so drones keep their behaviour).
     * 最近者命中：派对客只有严格近于玩家目标、且严格近于同一射线上最近的炸弹客无人机时才承受命中（距离相同时归玩家，其次归无人机，
     * 无人机行为保持不变）。
     */
    public static boolean partygoerWins(double partygoerSquared, double targetSquared, double droneSquared) {
        return partygoerSquared >= 0.0 && partygoerSquared < targetSquared && partygoerSquared < droneSquared;
    }

    /**
     * One polyline segment probe (USEC rifle walk): the distance to whichever of the Partygoer or the drone the segment
     * enters first (a tie goes to the drone, as in {@link #partygoerWins}), or -1 when it enters neither. Negative or
     * non-finite inputs mean "not entered".
     * 折线单段探测（USEC 步枪遍历）：线段先进入的派对客或无人机的距离（距离相同时归无人机，与 {@link #partygoerWins} 一致）；
     * 两者都未进入时返回 -1。负数或非有限输入表示“未进入”。
     */
    public static double firstEntryDistance(double partygoerSquared, double droneSquared) {
        boolean partygoer = partygoerSquared >= 0.0 && Double.isFinite(partygoerSquared);
        boolean drone = droneSquared >= 0.0 && Double.isFinite(droneSquared);
        if (partygoer && (!drone || partygoerSquared < droneSquared)) {
            return Math.sqrt(partygoerSquared);
        }
        return drone ? Math.sqrt(droneSquared) : -1.0;
    }

    public static boolean withinReach(double squaredDistance, double reach) {
        return Double.isFinite(squaredDistance) && squaredDistance >= 0.0 && squaredDistance <= reach * reach;
    }

    /**
     * Blast sphere (closed): the feet (where a player's blast check measures) or the body centre lies inside; the caller
     * adds line of sight. / 爆炸闭球：脚下（玩家爆炸判定的测量点）或身体中心位于球内；视线由调用方补查。
     */
    public static boolean inBlastSphere(double feetDx, double feetDy, double feetDz,
                                        double centreDx, double centreDy, double centreDz, double radius) {
        return insideSphere(feetDx, feetDy, feetDz, radius) || insideSphere(centreDx, centreDy, centreDz, radius);
    }

    /**
     * SparkWitch TR shell cube ({@code PotionBlastRings.inCube}): feet inside the horizontal N×N square and the body
     * overlapping the vertical slab [−N/2, N/2]. Non-finite input is outside.
     * SparkWitch TR 炮弹立方体：脚下位于水平 N×N 正方形内，且身体与竖直区间 [−N/2, N/2] 重叠。非有限输入视为在外。
     */
    public static boolean inShellCube(int size, double dx, double dz, double dyMin, double dyMax) {
        if (size <= 0 || !Double.isFinite(dx) || !Double.isFinite(dz) || !Double.isFinite(dyMin)
                || !Double.isFinite(dyMax)) {
            return false;
        }
        double half = size / 2.0;
        return Math.abs(dx) <= half && Math.abs(dz) <= half && dyMin <= half && dyMax >= -half;
    }

    /**
     * Piercing weapons (throwing axe, death ray) kill every Partygoer they enter strictly before {@code cutSquared}
     * (the first drone or block that stops them). / 穿透武器（飞斧、死光）击杀其在 cutSquared（挡住它的第一架无人机或方块）之前
     * 严格先进入的每只派对客。
     */
    public static boolean beforeCut(double entrySquared, double cutSquared) {
        return entrySquared >= 0.0 && entrySquared < cutSquared;
    }

    /** Wathe bat and tomahawk swings count only at full charge. / Wathe 球棒与战术斧只在蓄满时生效。 */
    public static boolean fullyCharged(float progress) {
        return progress >= 1.0F;
    }

    private static boolean insideSphere(double dx, double dy, double dz, double radius) {
        return Double.isFinite(dx) && Double.isFinite(dy) && Double.isFinite(dz)
                && Double.isFinite(radius) && radius >= 0.0
                && dx * dx + dy * dy + dz * dz <= radius * radius;
    }
}
