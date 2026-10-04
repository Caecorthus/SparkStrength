package annina.sparkstrength.role.bomber.drone;

/**
 * Pure flight-state, pilot-input and battery-display rules used by {@code DroneEntity} and {@code DroneItem} (no
 * Minecraft state, so they can be unit-tested). Gameplay numbers stay in {@link DroneRules}.
 * {@code DroneEntity} 与 {@code DroneItem} 共用的纯飞行状态、驾驶输入与电量显示规则（不依赖 Minecraft 状态，便于单元测试）。
 * 玩法数值仍放在 {@link DroneRules}。
 */
public final class DroneFlight {
    /**
     * A powered drone still counts as moving for this many ticks after the server last applied a real (collided)
     * displacement, so packet jitter (0 or 2 moves in one server tick) never flickers FLYING/HOVERING.
     * 服务器最后一次应用真实（碰撞后）位移后的这些刻内，通电无人机仍算在移动，避免数据包抖动（一刻 0 或 2 个移动包）使 FLYING/HOVERING 闪烁。
     */
    public static final int MOVE_GRACE_TICKS = 5;
    /** Displacements up to this length (blocks) are not movement (float noise). / 不超过此长度（格）的位移不算移动（浮点噪声）。 */
    public static final double MIN_DISPLACEMENT = 1.0E-3;
    /**
     * Server move budget (one per axis group): each tick of world time refills one capped step of that axis group,
     * capped at this many steps (absorbs packet jitter and short server TPS dips, but flooding move packets cannot
     * raise the sustained speed above the per-axis cap).
     * 服务器移动预算（水平/垂直各一份）：每刻世界时间补充该轴组一个上限步长，上限为此步数（吸收数据包抖动与短暂的服务器 TPS 下降，
     * 但刷移动包无法使持续速度超过分轴上限）。
     */
    public static final int MOVE_BUDGET_STEPS = 3;
    /** Same step count as vanilla durability bars. / 与原版耐久条相同的格数。 */
    public static final int ITEM_BAR_STEPS = 13;
    public static final int ITEM_BAR_GREEN = 0x55FF55;
    public static final int ITEM_BAR_AMBER = 0xFFAA00;
    public static final int ITEM_BAR_RED = 0xFF5555;

    private DroneFlight() {
    }

    /**
     * State of a powered (not falling) drone, derived on the server from its real collided displacement only, never
     * from the pilot client's "moving" flag: in the air it is FLYING if it moved recently and HOVERING otherwise; resting
     * on something it is GROUNDED (no drain, rotors off) unless it moved horizontally recently, so sliding along the
     * floor counts as FLYING. Pushing into the floor or a wall moves nothing and so never costs the flying rate.
     * 通电（未下坠）无人机的状态，只由服务器依据真实碰撞后位移推导，绝不采用驾驶客户端的 moving 标志：
     * 空中近期有位移为 FLYING，否则 HOVERING；有支撑时为 GROUNDED（不耗电、旋翼停转），除非近期有水平位移——贴地滑行算 FLYING。
     * 顶着地面或墙不产生位移，因此不会按飞行速率耗电。
     */
    public static DroneState poweredState(boolean supported, boolean displacedRecently, boolean horizontalRecently) {
        if (supported) {
            return horizontalRecently ? DroneState.FLYING : DroneState.GROUNDED;
        }
        return displacedRecently ? DroneState.FLYING : DroneState.HOVERING;
    }

    /** Whether a displacement {@code ticksSince} ticks ago still counts as moving. / 若干刻前的位移是否仍算在移动。 */
    public static boolean recentlyDisplaced(long ticksSince) {
        return ticksSince >= 0 && ticksSince <= MOVE_GRACE_TICKS;
    }

    /** A displacement of this length counts as movement. / 该长度的位移算作移动。 */
    public static boolean isDisplacement(double length) {
        return length > MIN_DISPLACEMENT;
    }

    /** Charge after one tick in this state, never below 0. / 该状态下经过一刻后的电量，不低于 0。 */
    public static int drain(DroneKind kind, DroneState state, int charge) {
        return DroneRules.clampCharge(charge - DroneRules.drainPerTick(kind, state));
    }

    /** Carried grenade drones recharge on a shared world-time beat. / 携带中的投弹无人机按世界时间统一节拍充电。 */
    public static boolean rechargeDue(long worldTime) {
        return worldTime % DroneRules.RECHARGE_INTERVAL_TICKS == 0;
    }

    /**
     * Budget after {@code elapsedTicks} of world time (world time, not entity age, so a drone whose chunk is not
     * entity-ticking can still be flown).
     * 经过 elapsedTicks 世界时间后的预算（用世界时间而非实体年龄，区块未进行实体刻时仍可操控）。
     */
    public static double refillMoveBudget(double budget, double maxStep, long elapsedTicks) {
        double refilled = Math.max(0.0, budget) + Math.max(0L, elapsedTicks) * maxStep;
        return Math.min(refilled, maxStep * MOVE_BUDGET_STEPS);
    }

    /** Longest part accepted now: the axis cap, limited by the remaining budget. / 当前可接受的最长分量：轴上限与剩余预算取小。 */
    public static double allowedStep(double maxStep, double budget) {
        return Math.max(0.0, Math.min(maxStep, budget));
    }

    /** Factor that shortens an XZ step to at most {@code allowed} (1 when already short enough). / 把水平位移缩短到不超过 allowed 的系数。 */
    public static double horizontalScale(double x, double z, double allowed) {
        if (!(allowed > 0.0)) {
            return 0.0;
        }
        double length = Math.sqrt(x * x + z * z);
        return length > allowed ? allowed / length : 1.0;
    }

    /** Vertical part clamped to {@code [-allowed, allowed]}. / 垂直分量限制在 [-allowed, allowed]。 */
    public static double clampVertical(double y, double allowed) {
        double limit = Math.max(0.0, allowed);
        return Math.clamp(y, -limit, limit);
    }

    public static int itemBarStep(int charge) {
        return Math.round(ITEM_BAR_STEPS * (float) DroneRules.clampCharge(charge) / DroneRules.CHARGE_MAX);
    }

    /** Green above 50%, amber above 20%, red otherwise. / 高于 50% 绿色，高于 20% 琥珀色，否则红色。 */
    public static int itemBarColor(int charge) {
        int percent = DroneRules.percent(charge);
        if (percent > 50) {
            return ITEM_BAR_GREEN;
        }
        return percent > 20 ? ITEM_BAR_AMBER : ITEM_BAR_RED;
    }
}
