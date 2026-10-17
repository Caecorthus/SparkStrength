package annina.sparkstrength.sabotage;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

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
    /** The damaged units sound an alarm every 2 s while the repair window runs. / 抢修窗口期间受损装置每 2 秒响一次警报。 */
    public static final int ALARM_INTERVAL_TICKS = 40;
    /** The countdown on the action bar turns red for its last 20 s. / 动作栏倒计时在最后 20 秒变红。 */
    public static final int URGENT_TICKS = 400;

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

    /** Whole seconds for a countdown, rounded up so 0:00 only shows once the time is really up. / 倒计时的整秒数，向上取整，时间真正用完时才显示 0:00。 */
    public static long ceilSeconds(long ticks) {
        return ticks <= 0 ? 0 : (ticks + 19) / 20;
    }

    /** The countdown is in its last 20 s. / 倒计时进入最后 20 秒。 */
    public static boolean urgent(long now, long triggeredAt) {
        return remainingTicks(now, triggeredAt) <= URGENT_TICKS;
    }

    /**
     * Seconds on the tablet's engine card: the repair window counts down (rounded up) while CRITICAL; once wrecked it
     * counts up how long the train has been halted, kept through emergency power.
     * 平板动力卡片上的秒数：CRITICAL 时是向下数的抢修剩余时间（向上取整）；损毁后是向上数的已停驶时间，进入应急动力后继续累计。
     */
    public static long cardSeconds(long now, long triggeredAt) {
        if (effectivePhase(now, triggeredAt) == EnginePhase.CRITICAL) {
            return ceilSeconds(remainingTicks(now, triggeredAt));
        }
        return haltedTicks(now, triggeredAt) / 20;
    }

    /**
     * The phases to enter, in order, to go from the last applied phase to the derived one: each transition's side
     * effects run exactly once, and both run when ticks were skipped. Empty when nothing changed or when the derived
     * phase lies before the applied one (only tooling moves the clock back).
     * 从最近已应用的阶段走到推导出的阶段，需要依次进入的阶段：每次转换的副作用只执行一次，跳过了 tick 时两次都会执行。没有变化，
     * 或推导出的阶段早于已应用的阶段（只有工具会把时钟往回拨）时为空。
     */
    public static List<EnginePhase> pendingTransitions(@Nullable EnginePhase applied, EnginePhase derived) {
        List<EnginePhase> transitions = new ArrayList<>(3);
        for (int step = applied == null ? 0 : order(applied) + 1; step <= order(derived); step++) {
            transitions.add(byOrder(step));
        }
        return transitions;
    }

    /** Whether {@code phase} lies before {@code other} in the CRITICAL, WRECKED, EMERGENCY order. / phase 是否在 other 之前。 */
    public static boolean isBefore(EnginePhase phase, EnginePhase other) {
        return order(phase) < order(other);
    }

    /**
     * Tooling: the trigger tick that puts {@code now} at the very start of {@code phase}.
     * 工具用：让 now 恰好处在 phase 开始时刻的触发 tick。
     */
    public static long triggerTickFor(EnginePhase phase, long now) {
        return switch (phase) {
            case CRITICAL -> now;
            case WRECKED -> now - CRITICAL_WINDOW_TICKS;
            case EMERGENCY -> now - CRITICAL_WINDOW_TICKS - HALT_CAP_TICKS;
        };
    }

    /** The phases always run CRITICAL, WRECKED, EMERGENCY; never wire values or ordinal(). / 阶段总是按 CRITICAL、WRECKED、EMERGENCY 的顺序；不用 wire 值或 ordinal()。 */
    private static int order(EnginePhase phase) {
        return switch (phase) {
            case CRITICAL -> 0;
            case WRECKED -> 1;
            case EMERGENCY -> 2;
        };
    }

    private static EnginePhase byOrder(int order) {
        return switch (order) {
            case 0 -> EnginePhase.CRITICAL;
            case 1 -> EnginePhase.WRECKED;
            default -> EnginePhase.EMERGENCY;
        };
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
