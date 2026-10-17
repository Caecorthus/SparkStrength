package annina.sparkstrength.sabotage;

import org.jetbrains.annotations.Nullable;

/**
 * Pure numbers and decisions of the engine sabotage, free of Minecraft types so local tests can compile it alone.
 * 动力破坏的纯数值与判定，不依赖 Minecraft 类型，便于本地测试单独编译。
 *
 * <p>Three clocks, never mixed: world ticks run the 60 s repair window, the 2 min halt cap and every cooldown; Wathe's
 * round timer only skips its natural countdown while halted; the client's render time only drives the train's motion.
 * The phase is always derived from the world tick and the trigger tick, so every entry point (tick, repair submit,
 * purchase, income, round timer) agrees on it even before the end-of-tick update ran.
 * 三个时钟互不混用：世界 tick 管 60 秒抢修窗口、2 分钟停驶上限和所有冷却；Wathe 的回合倒计时只在停驶时跳过自然递减；
 * 客户端的渲染时间只管列车的运动表现。阶段永远由世界 tick 和触发 tick 推导，所以每个入口（tick、维修提交、购买、收入、
 * 回合倒计时）在 tick 末尾的更新执行之前就能得到一致的阶段。</p>
 */
public final class EngineRules {
    /** Damaged units must be repaired within 60 s. / 受损的装置必须在 60 秒内修好。 */
    public static final int CRITICAL_WINDOW_TICKS = 1_200;
    /** The train stays halted at most 2 min, then emergency power starts. / 列车最多停驶 2 分钟，之后启用应急动力。 */
    public static final int HALT_CAP_TICKS = 2_400;

    public static final int QUICK_GAUGES = 3;
    public static final int QUICK_HOLD_TICKS = 40;
    public static final int OVERHAUL_FUSES = 3;
    public static final int OVERHAUL_GAUGES = 4;
    public static final int OVERHAUL_HOLD_TICKS = 60;
    /** Each burnt fuse is held this long to replace it. / 每根烧坏的保险丝要按住这么久才能换好。 */
    public static final int FUSE_HOLD_TICKS = 10;
    /** Master Saboteur: one more gauge and one more second of restart on the quick repair. / 破坏大师：抢修多一块压力表、重启多按一秒。 */
    public static final int MASTER_EXTRA_GAUGES = 1;
    public static final int MASTER_EXTRA_HOLD_TICKS = 20;
    /** Master Saboteur: one more fuse on the overhaul. / 破坏大师：大修多一根保险丝。 */
    public static final int MASTER_EXTRA_FUSES = 1;
    public static final int MAX_GAUGES = 5;
    public static final int MAX_FUSES = 4;
    /** Server-side minimum per gauge and per fuse, so a forged completion cannot be instant. / 每块表、每根保险丝的服务端最短用时，防止伪造的瞬间完成。 */
    public static final int MIN_TICKS_PER_GAUGE = 14;
    public static final int MIN_TICKS_PER_FUSE = 20;

    private EngineRules() {
    }

    /** Last tick (exclusive) a quick repair still counts. / 抢修仍然有效的截止 tick（不含）。 */
    public static long deadlineTick(long triggeredAt) {
        return triggeredAt + CRITICAL_WINDOW_TICKS;
    }

    /** The engine is wrecked exactly at the deadline. / 动力恰好在截止时刻损毁。 */
    public static long wreckedTick(long triggeredAt) {
        return deadlineTick(triggeredAt);
    }

    public static long emergencyTick(long triggeredAt) {
        return wreckedTick(triggeredAt) + HALT_CAP_TICKS;
    }

    /**
     * The one phase decision: CRITICAL before the deadline, WRECKED for the halt cap after it, EMERGENCY afterwards.
     * The halt is {@code [wreckedTick, emergencyTick)}; the killer economy bonus is {@code [wreckedTick, repaired)}.
     * 唯一的阶段判定：截止之前是 CRITICAL，截止后停驶上限之内是 WRECKED，之后是 EMERGENCY。停驶区间是
     * [wreckedTick, emergencyTick)；杀手经济加成区间是 [wreckedTick, 修好)。
     */
    public static EnginePhase effectivePhase(long now, long triggeredAt) {
        if (now < wreckedTick(triggeredAt)) {
            return EnginePhase.CRITICAL;
        }
        if (now < emergencyTick(triggeredAt)) {
            return EnginePhase.WRECKED;
        }
        return EnginePhase.EMERGENCY;
    }

    /** A quick repair counts only strictly before the deadline; at the deadline tick the engine is wrecked. / 抢修只在截止之前有效；到截止那一 tick 算损毁。 */
    public static boolean quickRepairInTime(long now, long triggeredAt) {
        return now < deadlineTick(triggeredAt);
    }

    /** Ticks left of the repair window, 0 once wrecked. / 抢修窗口剩余 tick，损毁后为 0。 */
    public static long remainingTicks(long now, long triggeredAt) {
        return Math.max(0L, deadlineTick(triggeredAt) - now);
    }

    /** How long the train has been halted, counted from the wreck and kept through emergency power. / 已停驶多久，从损毁起算，进入应急动力后继续累计。 */
    public static long haltedTicks(long now, long triggeredAt) {
        return Math.max(0L, now - wreckedTick(triggeredAt));
    }

    /** The panel a unit opens in this phase ({@code null} = no engine sabotage). / 该阶段装置打开的面板（null 表示没有动力破坏）。 */
    public static EngineRepairMode repairModeFor(@Nullable EnginePhase phase) {
        if (phase == null) {
            return EngineRepairMode.STATUS;
        }
        return phase == EnginePhase.CRITICAL ? EngineRepairMode.QUICK : EngineRepairMode.OVERHAUL;
    }

    public static int gauges(EngineRepairMode mode, boolean masterSaboteur) {
        return switch (mode) {
            case STATUS -> QUICK_GAUGES;
            case QUICK -> Math.min(MAX_GAUGES, QUICK_GAUGES + (masterSaboteur ? MASTER_EXTRA_GAUGES : 0));
            case OVERHAUL -> OVERHAUL_GAUGES;
        };
    }

    public static int fuses(EngineRepairMode mode, boolean masterSaboteur) {
        if (mode != EngineRepairMode.OVERHAUL) {
            return 0;
        }
        return Math.min(MAX_FUSES, OVERHAUL_FUSES + (masterSaboteur ? MASTER_EXTRA_FUSES : 0));
    }

    public static int holdTicks(EngineRepairMode mode, boolean masterSaboteur) {
        return switch (mode) {
            case STATUS -> 0;
            case QUICK -> QUICK_HOLD_TICKS + (masterSaboteur ? MASTER_EXTRA_HOLD_TICKS : 0);
            case OVERHAUL -> OVERHAUL_HOLD_TICKS;
        };
    }

    /** Server minimum time of a repair panel: quick about 4 s, overhaul about 9 s. / 维修面板的服务端最短用时：抢修约 4 秒，大修约 9 秒。 */
    public static int minTicks(int gauges, int fuses, int holdTicks) {
        return Math.max(0, holdTicks) + Math.max(0, gauges) * MIN_TICKS_PER_GAUGE + Math.max(0, fuses) * MIN_TICKS_PER_FUSE;
    }
}
