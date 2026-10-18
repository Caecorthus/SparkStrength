package annina.sparkstrength.sabotage;

/**
 * What a sabotage card on the tablet says. Wire values are a stable packet contract; never use ordinal().
 * 平板上一张破坏卡片的状态。wire 值是稳定的网络包契约，禁止使用 ordinal()。
 */
public enum SabotageRowState {
    /** No junction box of this type hacked yet. / 还没有入侵过这一类接线箱。 */
    LOCKED(0),
    /** Unlocked; usable once the post-unlock wait is over (seconds = remaining). / 已解锁，等待结束后可用（seconds 为剩余秒数）。 */
    ARMING(1),
    READY(2),
    /** Shared team cooldown (seconds = remaining). / 团队共享冷却（seconds 为剩余秒数）。 */
    COOLDOWN(3),
    /** This sabotage is on (seconds = elapsed). / 该破坏正在进行（seconds 为已持续秒数）。 */
    ACTIVE(4),
    /** Another sabotage is on. / 另一个破坏正在进行。 */
    BLOCKED(5);

    private final int wire;

    SabotageRowState(int wire) {
        this.wire = wire;
    }

    public int wire() {
        return wire;
    }

    public static SabotageRowState byWire(int wire) {
        for (SabotageRowState state : values()) {
            if (state.wire == wire) {
                return state;
            }
        }
        return LOCKED;
    }

    /** Pure: the card state from the team's timers. / 纯函数：由团队计时得出卡片状态。 */
    public static SabotageRowState of(boolean thisActive, boolean otherActive, boolean unlocked, long now,
                                      long readyTick, long cooldownEndTick) {
        if (thisActive) {
            return ACTIVE;
        }
        if (!unlocked) {
            return LOCKED;
        }
        if (otherActive) {
            return BLOCKED;
        }
        if (now < readyTick) {
            return ARMING;
        }
        if (now < cooldownEndTick) {
            return COOLDOWN;
        }
        return READY;
    }
}
