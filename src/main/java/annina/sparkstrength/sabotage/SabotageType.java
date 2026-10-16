package annina.sparkstrength.sabotage;

import net.minecraft.util.StringIdentifiable;
import org.jetbrains.annotations.Nullable;

/**
 * A sabotage a junction box can unlock for the killer team. Wire values are a stable packet contract; never use
 * ordinal(). The string id is also the {@code sabotage} blockstate value saved in maps, so it must never change.
 * 接线箱可为杀手阵营解锁的一种破坏。wire 值是稳定的网络包契约，禁止使用 ordinal()。字符串 id 同时是保存在地图里的
 * {@code sabotage} 方块状态值，永远不能改。
 */
public enum SabotageType implements StringIdentifiable {
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

    @Override
    public String asString() {
        return id;
    }

    public static @Nullable SabotageType byWire(int wire) {
        for (SabotageType type : values()) {
            if (type.wire == wire) {
                return type;
            }
        }
        return null;
    }

    /** The configurator's cycle order (wraps around). / 线路配置器的切换顺序（循环）。 */
    public SabotageType cycle(boolean backwards) {
        SabotageType[] values = values();
        int index = ordinal() + (backwards ? -1 : 1);
        return values[Math.floorMod(index, values.length)];
    }
}
