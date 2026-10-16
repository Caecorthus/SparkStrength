package annina.sparkstrength.sabotage;

import net.minecraft.util.StringIdentifiable;
import org.jetbrains.annotations.Nullable;

/**
 * What a junction box is wired to, as its {@code sabotage} blockstate value: one sabotage type, or nothing (a
 * decorative box that cannot be pried or hacked). The string ids are saved in maps and must never change.
 * 接线箱接入的系统，即其 {@code sabotage} 方块状态值：某一种破坏，或不接入任何系统（装饰用，不能撬也不能入侵）。
 * 字符串 id 保存在地图里，永远不能改。
 */
public enum JunctionBoxCircuit implements StringIdentifiable {
    LIGHTS("lights", SabotageType.LIGHTS),
    /** Decoration only. Also keeps the property at two values, which Minecraft requires. / 仅作装饰；也让该属性保持 Minecraft 要求的至少两个取值。 */
    NONE("none", null);

    private final String id;
    private final @Nullable SabotageType type;

    JunctionBoxCircuit(String id, @Nullable SabotageType type) {
        this.id = id;
        this.type = type;
    }

    public @Nullable SabotageType type() {
        return type;
    }

    public String translationKey() {
        return type == null ? "sabotage.sparkstrength.none" : type.translationKey();
    }

    @Override
    public String asString() {
        return id;
    }

    /** The configurator's cycle order (wraps around). / 线路配置器的切换顺序（循环）。 */
    public JunctionBoxCircuit cycle(boolean backwards) {
        JunctionBoxCircuit[] values = values();
        return values[Math.floorMod(ordinal() + (backwards ? -1 : 1), values.length)];
    }
}
