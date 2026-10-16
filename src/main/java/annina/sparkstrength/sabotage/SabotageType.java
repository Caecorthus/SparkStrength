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
    LIGHTS("lights", 0);

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

    public static @Nullable SabotageType byWire(int wire) {
        for (SabotageType type : values()) {
            if (type.wire == wire) {
                return type;
            }
        }
        return null;
    }
}
