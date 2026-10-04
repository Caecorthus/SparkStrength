package annina.sparkstrength.role.bomber.drone;

import org.jetbrains.annotations.Nullable;

/**
 * The two Bomber drones. Wire ids are a stable packet contract (tablet rows, pilot state); never use ordinal().
 * 炸弹客的两种无人机。wire 编号是稳定的网络包契约（平板行、驾驶状态），禁止使用 ordinal()。
 */
public enum DroneKind {
    /** Starter, rechargeable, carries one bound M67. / 开局发放、可充电、可挂载一颗 M67。 */
    GRENADE("grenade_drone", 1),
    /** Shop-bought, single-use kamikaze. / 商店购买、一次性自爆。 */
    BOMB("bomb_drone", 2);

    private final String id;
    private final int wire;

    DroneKind(String id, int wire) {
        this.id = id;
        this.wire = wire;
    }

    public String id() {
        return id;
    }

    public int wire() {
        return wire;
    }

    public static @Nullable DroneKind fromWire(int wire) {
        for (DroneKind kind : values()) {
            if (kind.wire == wire) {
                return kind;
            }
        }
        return null;
    }
}
