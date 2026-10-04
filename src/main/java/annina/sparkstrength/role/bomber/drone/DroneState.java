package annina.sparkstrength.role.bomber.drone;

/**
 * Flight state of a placed drone, synced to every tracking client (rotor animation, flight noise). Wire ids are
 * stable; never use ordinal().
 * 已放置无人机的飞行状态，同步给所有追踪客户端（旋翼动画、飞行噪音）。wire 编号稳定，禁止使用 ordinal()。
 */
public enum DroneState {
    /** Resting on a surface, rotors off: silent, no battery drain. / 停在地面，旋翼停转：无声、不耗电。 */
    GROUNDED(0, false),
    /** Airborne without movement: noisy, hover drain. / 空中悬停：有噪音，按悬停耗电。 */
    HOVERING(1, true),
    /** Airborne and moving under a pilot: noisy, flight drain. / 被操控移动中：有噪音，按飞行耗电。 */
    FLYING(2, true),
    /** Battery empty, dropping under gravity: rotors off. / 电量耗尽、受重力下坠：旋翼停转。 */
    FALLING(3, false);

    private final int wire;
    private final boolean rotorsOn;

    DroneState(int wire, boolean rotorsOn) {
        this.wire = wire;
        this.rotorsOn = rotorsOn;
    }

    public int wire() {
        return wire;
    }

    /** Rotors spinning, i.e. the flight noise plays. / 旋翼转动，即播放飞行噪音。 */
    public boolean rotorsOn() {
        return rotorsOn;
    }

    public static DroneState fromWire(int wire) {
        for (DroneState state : values()) {
            if (state.wire == wire) {
                return state;
            }
        }
        return GROUNDED;
    }
}
