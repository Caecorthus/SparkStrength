package annina.sparkstrength.role.bomber.drone;

/**
 * Pure tuning and rule math for the Bomber drones (no Minecraft state). Charge is stored in basis points
 * (10000 = 100%) so every drain rate is a whole number per tick.
 * 炸弹客无人机的纯数值与规则计算（不依赖 Minecraft 状态）。电量以万分比存储（10000 = 100%），使每刻耗电均为整数。
 */
public final class DroneRules {
    public static final int CHARGE_MAX = 10_000;
    public static final int CHARGE_PER_PERCENT = 100;

    /** Grenade drone: -2%/s flying, -1%/s hovering, 0 grounded. / 投弹无人机：飞行 -2%/秒，悬停 -1%/秒，落地 0。 */
    public static final int GRENADE_FLYING_DRAIN_PER_TICK = 10;
    public static final int GRENADE_HOVER_DRAIN_PER_TICK = 5;
    /** Bomb drone: -5%/s flying, -2%/s hovering, 0 grounded. / 炸弹无人机：飞行 -5%/秒，悬停 -2%/秒，落地 0。 */
    public static final int BOMB_FLYING_DRAIN_PER_TICK = 25;
    public static final int BOMB_HOVER_DRAIN_PER_TICK = 10;
    /** Recovered grenade drone recharges +5% once per second. / 回收后的投弹无人机每秒回充 5%。 */
    public static final int RECHARGE_INTERVAL_TICKS = 20;
    public static final int RECHARGE_PER_INTERVAL = 5 * CHARGE_PER_PERCENT;

    /** Round-start lock for both drones (same clock as the M67 opening lock). / 两种无人机的开局锁（与 M67 开局锁同一时钟）。 */
    public static final int OPENING_TICKS = 1800;
    /** Grenade drone cooldown after it is destroyed or runs flat. / 投弹无人机被毁或耗尽电量后的冷却。 */
    public static final int GRENADE_LOST_COOLDOWN_TICKS = 900;

    /** Horizontal top speed in blocks per tick (5 and 7 blocks/s). / 水平最高速度（格/刻，即 5 与 7 格/秒）。 */
    public static final double GRENADE_HORIZONTAL_SPEED = 0.25;
    public static final double BOMB_HORIZONTAL_SPEED = 0.35;
    /** Vertical speed in blocks per tick (4 blocks/s). / 垂直速度（格/刻，即 4 格/秒）。 */
    public static final double VERTICAL_SPEED = 0.2;
    /** Unpowered fall. / 断电下坠。 */
    public static final double FALL_GRAVITY = 0.08;
    public static final double FALL_DRAG = 0.98;
    /** A grenade drone that ran flat breaks on landing, or after this long in the air. / 耗尽电量的投弹无人机落地即毁，或下坠超过此时长。 */
    public static final int MAX_FALL_TICKS = 100;
    /**
     * Server tolerance for one pilot-reported step, per axis group on top of that axis' top speed (lag, float error).
     * The move budget (see {@code DroneFlight}) holds the sustained speed to the same per-axis caps.
     * 服务器对驾驶者上报单步位移的容差，按水平/垂直分别叠加在各自最高速度之上（延迟、浮点误差）。
     * 移动预算（见 {@code DroneFlight}）把持续速度限制在同样的分轴上限内。
     */
    public static final double HORIZONTAL_STEP_TOLERANCE = 0.05;
    public static final double VERTICAL_STEP_TOLERANCE = 0.05;
    /** Corrections are sent when the server's collided position differs by more than this. / 服务器碰撞后位置偏差超过此值时发送纠正。 */
    public static final double CORRECTION_EPSILON = 0.0625;

    /** Bomb drone blast radius; victims use the M67 path judgement. / 炸弹无人机爆炸半径，受害者判定沿用 M67 路径判定。 */
    public static final double BOMB_BLAST_RADIUS = 4.0;
    /** At most one undetonated bomb drone in the world per owner. / 每名主人场上最多一架未引爆的炸弹无人机。 */
    public static final int MAX_ACTIVE_BOMB_DRONES = 1;

    public static final String BOMB_DRONE_SHOP_ENTRY_ID = "sparkstrength_bomb_drone";
    public static final int BOMB_DRONE_PRICE = 400;
    /** SparkTraits Conscience Bomber price. / SparkTraits 善良炸弹客价格。 */
    public static final int BOMB_DRONE_CONSCIENCE_PRICE = 200;

    private DroneRules() {
    }

    public static int drainPerTick(DroneKind kind, DroneState state) {
        return switch (state) {
            case FLYING -> kind == DroneKind.BOMB ? BOMB_FLYING_DRAIN_PER_TICK : GRENADE_FLYING_DRAIN_PER_TICK;
            case HOVERING -> kind == DroneKind.BOMB ? BOMB_HOVER_DRAIN_PER_TICK : GRENADE_HOVER_DRAIN_PER_TICK;
            case GROUNDED, FALLING -> 0;
        };
    }

    public static double horizontalSpeed(DroneKind kind) {
        return kind == DroneKind.BOMB ? BOMB_HORIZONTAL_SPEED : GRENADE_HORIZONTAL_SPEED;
    }

    /**
     * Longest legal horizontal (XZ length) part of one pilot-reported step; capped apart from the vertical part so a
     * client can never trade climb speed for extra horizontal speed.
     * 驾驶者单步上报中水平部分（XZ 长度）的上限；与垂直部分分开限制，客户端无法用爬升速度换取额外水平速度。
     */
    public static double maxHorizontalStep(DroneKind kind) {
        return horizontalSpeed(kind) + HORIZONTAL_STEP_TOLERANCE;
    }

    /** Longest legal vertical part (|y|) of one pilot-reported step. / 驾驶者单步上报中垂直部分（|y|）的上限。 */
    public static double maxVerticalStep() {
        return VERTICAL_SPEED + VERTICAL_STEP_TOLERANCE;
    }

    public static int clampCharge(int charge) {
        return Math.clamp(charge, 0, CHARGE_MAX);
    }

    /** Whole percent for HUD/tablet, rounded up so 0% means truly empty. / HUD/平板显示的整数百分比，向上取整使 0% 即真正耗尽。 */
    public static int percent(int charge) {
        int clamped = clampCharge(charge);
        return (clamped + CHARGE_PER_PERCENT - 1) / CHARGE_PER_PERCENT;
    }

    public static int recharge(int charge) {
        return clampCharge(charge + RECHARGE_PER_INTERVAL);
    }

    public static int bombDronePrice(boolean conscience) {
        return conscience ? BOMB_DRONE_CONSCIENCE_PRICE : BOMB_DRONE_PRICE;
    }
}
