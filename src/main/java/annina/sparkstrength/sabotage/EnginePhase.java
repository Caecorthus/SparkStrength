package annina.sparkstrength.sabotage;

import org.jetbrains.annotations.Nullable;

/**
 * Where an engine sabotage stands. Wire values are a stable packet contract (0 = no engine sabotage); never use
 * ordinal().
 * 动力破坏所处的阶段。wire 值是稳定的网络包契约（0 表示没有动力破坏），禁止使用 ordinal()。
 */
public enum EnginePhase {
    /** Engine units damaged; repair within 60 s or the engine is wrecked. / 动力装置受损；60 秒内不修就会损毁。 */
    CRITICAL(1, 1.0, false, false),
    /** Wrecked: the train halts and the round timer stops running. / 已损毁：列车停驶，回合倒计时暂停。 */
    WRECKED(2, 0.0, true, true),
    /** Still wrecked, but the train limps on at half speed and the timer runs again. / 仍是损毁，但列车半速行驶，倒计时恢复。 */
    EMERGENCY(3, 0.5, false, true);

    private final int wire;
    private final double motionFactor;
    private final boolean freezesRoundTimer;
    private final boolean boostsKillerEconomy;

    EnginePhase(int wire, double motionFactor, boolean freezesRoundTimer, boolean boostsKillerEconomy) {
        this.wire = wire;
        this.motionFactor = motionFactor;
        this.freezesRoundTimer = freezesRoundTimer;
        this.boostsKillerEconomy = boostsKillerEconomy;
    }

    public int wire() {
        return wire;
    }

    /** How fast the train looks to move, 1 = normal. / 列车看起来的速度，1 为正常。 */
    public double motionFactor() {
        return motionFactor;
    }

    public boolean freezesRoundTimer() {
        return freezesRoundTimer;
    }

    /** From the wreck until the repair, emergency power included. / 从损毁到修好，应急动力期间也算。 */
    public boolean boostsKillerEconomy() {
        return boostsKillerEconomy;
    }

    public static @Nullable EnginePhase byWire(int wire) {
        for (EnginePhase phase : values()) {
            if (phase.wire == wire) {
                return phase;
            }
        }
        return null;
    }

    /** Wire of a possibly absent phase. / 可能为空的阶段的 wire 值。 */
    public static int wireOf(@Nullable EnginePhase phase) {
        return phase == null ? 0 : phase.wire;
    }
}
