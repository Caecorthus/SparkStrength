package annina.sparkstrength.sabotage;

/**
 * How a lights sabotage ends. / 灯光破坏如何结束。
 */
public enum OutageMode {
    /** Lights stay off until someone repairs a circuit breaker. / 熄灯直到有人修好电闸。 */
    REPAIR(0),
    /** Wathe's own 30-40 s timer, for maps without a circuit breaker. / Wathe 原有的 30-40 秒计时，用于没有电闸的地图。 */
    TIMED(1);

    private final int wire;

    OutageMode(int wire) {
        this.wire = wire;
    }

    public int wire() {
        return wire;
    }

    public static OutageMode byWire(int wire) {
        return wire == TIMED.wire ? TIMED : REPAIR;
    }
}
