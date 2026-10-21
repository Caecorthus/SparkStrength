package annina.sparkstrength.role.partyanimal;

/**
 * Phases of one Partygoer. Wire ids are synced to clients (entity data and the owner's HUD); never use ordinal().
 * 单只派对客的阶段。wire id 会同步到客户端（实体数据与召唤者 HUD）；禁止使用 ordinal()。
 */
public enum PartygoerState {
    /** Running to the locked prey. / 奔向锁定的猎物。 */
    HUNT(1),
    /** Carrying the prey to the destination room. / 扛着猎物前往终点房间。 */
    CARRY(2),
    /** Eating the prey at the destination. / 在终点吃猎物。 */
    EAT(3),
    /** Stunned for a moment after the prey was killed in its hands. / 手中猎物被杀后愣住片刻。 */
    RAGE_STUN(4),
    /** Charging the prey's killer. / 冲向杀死猎物的人。 */
    RAGE_CHASE(5),
    /** Eating the prey's killer on the spot. / 原地吃掉杀死猎物的人。 */
    RAGE_EAT(6),
    /** Inside a Taotie, waiting to be let out. / 在饕餮肚子里，等待被放出。 */
    SWALLOWED(7);

    private final int wire;

    PartygoerState(int wire) {
        this.wire = wire;
    }

    public int wire() {
        return wire;
    }

    public boolean eating() {
        return this == EAT || this == RAGE_EAT;
    }

    public boolean raging() {
        return this == RAGE_STUN || this == RAGE_CHASE || this == RAGE_EAT;
    }

    /** The prey is in its arms (carried or being eaten). / 猎物在它手中（被扛着或正在被吃）。 */
    public boolean preyInHand() {
        return this == CARRY || this == EAT;
    }

    public static PartygoerState fromWire(int wire) {
        for (PartygoerState state : values()) {
            if (state.wire == wire) {
                return state;
            }
        }
        return HUNT;
    }
}
