package annina.sparkstrength.sabotage;

/**
 * Which panel an engine unit opens. Wire values are a stable packet contract; never use ordinal().
 * 动力装置打开哪一种面板。wire 值是稳定的网络包契约，禁止使用 ordinal()。
 */
public enum EngineRepairMode {
    /** Intact unit, or a viewer who may not repair: gauges only, no session on the server. / 完好的装置，或不能维修的查看者：只看表盘，服务端不建会话。 */
    STATUS(0),
    /** Damaged (CRITICAL): set the gauges into the green, then hold restart. / 受损（CRITICAL）：把压力表调进绿区，再按住重启。 */
    QUICK(1),
    /** Wrecked or emergency power: replace the fuses first, then gauges and a longer restart. / 损毁或应急动力：先换保险丝，再调表、更长的重启。 */
    OVERHAUL(2);

    private final int wire;

    EngineRepairMode(int wire) {
        this.wire = wire;
    }

    public int wire() {
        return wire;
    }

    public static EngineRepairMode byWire(int wire) {
        for (EngineRepairMode mode : values()) {
            if (mode.wire == wire) {
                return mode;
            }
        }
        return STATUS;
    }
}
