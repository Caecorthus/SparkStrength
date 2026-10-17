package annina.sparkstrength.sabotage;

import net.minecraft.util.StringIdentifiable;

/**
 * How an engine unit looks, as its {@code condition} blockstate value. The string ids are saved in maps and must never
 * change.
 * 动力装置的外观，即其 {@code condition} 方块状态值。字符串 id 保存在地图里，永远不能改。
 */
public enum EngineCondition implements StringIdentifiable {
    /** Running fine. / 运转正常。 */
    INTACT("intact"),
    /** An engine sabotage is on: 60 s to repair. / 动力破坏进行中：60 秒内抢修。 */
    DAMAGED("damaged"),
    /** The 60 s ran out: needs the big repair. / 60 秒已过：需要大修。 */
    WRECKED("wrecked");

    private final String id;

    EngineCondition(String id) {
        this.id = id;
    }

    @Override
    public String asString() {
        return id;
    }

    /** The look for a phase of the engine sabotage (null = none on). / 动力破坏某个阶段对应的外观（null 表示没有破坏）。 */
    public static EngineCondition of(EnginePhase phase) {
        if (phase == null) {
            return INTACT;
        }
        return switch (phase) {
            case CRITICAL -> DAMAGED;
            case WRECKED, EMERGENCY -> WRECKED;
        };
    }
}
