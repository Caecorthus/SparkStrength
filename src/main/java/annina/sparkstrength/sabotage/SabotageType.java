package annina.sparkstrength.sabotage;

import org.jetbrains.annotations.Nullable;

/**
 * A sabotage a junction box can unlock for the killer team. Wire values are a stable packet contract; never use
 * ordinal(). Junction boxes store the matching {@link JunctionBoxCircuit} in their blockstate.
 * 接线箱可为杀手阵营解锁的一种破坏。wire 值是稳定的网络包契约，禁止使用 ordinal()。接线箱在方块状态里保存对应的
 * {@link JunctionBoxCircuit}。
 */
public enum SabotageType {
    /** Lights out across the play area until someone repairs a circuit breaker. / 全图熄灯，直到有人修好电闸。 */
    LIGHTS("lights", 0),
    /**
     * Engine units damaged: 60 s to repair one, or the engine is wrecked and the train halts until someone does.
     * 动力装置受损：60 秒内修好任意一台，否则动力损毁、列车停驶，直到有人修好。
     */
    ENGINE("engine", 1);

    private final String id;
    private final int wire;

    SabotageType(String id, int wire) {
        this.id = id;
        this.wire = wire;
    }

    public String id() {
        return id;
    }

    public int wire() {
        return wire;
    }

    public String translationKey() {
        return "sabotage.sparkstrength." + id;
    }

    /**
     * Whether a map can host this sabotage: the engine needs at least one engine unit to repair; lights always work
     * (without a breaker they come back on Wathe's own timer).
     * 一张地图能否进行这种破坏：动力至少需要一台可维修的动力装置；灯光总是可以（没有电闸时按 Wathe 自己的计时来电）。
     */
    public boolean availableOn(boolean mapHasEngineUnits) {
        return this != ENGINE || mapHasEngineUnits;
    }

    public static @Nullable SabotageType byWire(int wire) {
        for (SabotageType type : values()) {
            if (type.wire == wire) {
                return type;
            }
        }
        return null;
    }
}
