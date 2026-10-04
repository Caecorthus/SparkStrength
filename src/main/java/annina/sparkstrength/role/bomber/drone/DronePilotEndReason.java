package annina.sparkstrength.role.bomber.drone;

/**
 * Why a pilot session ended or was refused; shown to the pilot. Wire values are a stable packet contract.
 * 驾驶会话结束或被拒绝的原因，显示给驾驶者。wire 值是稳定的网络包契约。
 */
public enum DronePilotEndReason {
    EXIT(0, "exit"),
    DESTROYED(1, "destroyed"),
    DEPLETED(2, "depleted"),
    DETONATED(3, "detonated"),
    PILOT_DOWN(4, "pilot_down"),
    NO_TABLET(5, "no_tablet"),
    STUNNED(6, "stunned"),
    ROUND_OVER(7, "round_over"),
    UNAVAILABLE(8, "unavailable"),
    OPENING(9, "opening"),
    /** SparkTraits blocks the pilot's role skills (incl. NoellesRoles' silenced killer). / SparkTraits 封锁驾驶者的职业技能（含 NoellesRoles 被沉默的杀手）。 */
    SKILL_BLOCKED(10, "skill_blocked");

    private final int wire;
    private final String id;

    DronePilotEndReason(int wire, String id) {
        this.wire = wire;
        this.id = id;
    }

    public int wire() {
        return wire;
    }

    /** Lang key suffix: {@code message.sparkstrength.drone.pilot_end.<id>}. / 语言键后缀。 */
    public String id() {
        return id;
    }

    public String translationKey() {
        return "message.sparkstrength.drone.pilot_end." + id;
    }

    public static DronePilotEndReason fromWire(int wire) {
        for (DronePilotEndReason reason : values()) {
            if (reason.wire == wire) {
                return reason;
            }
        }
        return EXIT;
    }
}
