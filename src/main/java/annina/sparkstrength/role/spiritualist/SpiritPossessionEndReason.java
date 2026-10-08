package annina.sparkstrength.role.spiritualist;

import org.jetbrains.annotations.Nullable;

/**
 * Why a possession ended; shown to the Spiritualist. Wire values are a stable packet contract.
 * 附身结束的原因，显示给灵界行者。wire 值是稳定的网络包契约。
 */
public enum SpiritPossessionEndReason {
    /** Sneak or role skill 2 again; no message. / 潜行或再按职业技能 2；不显示消息。 */
    EXIT(0, null),
    /** The 20 s ran out. / 20 秒用完。 */
    EXPIRED(1, "expired"),
    /** The Wraith left the world, disconnected or stopped being a Wraith. / 冤魂离开世界、断线或不再是冤魂。 */
    TARGET_LOST(2, "target_lost"),
    /** The spirit projection ended (body hurt, moved or swallowed, or the ability key). / 灵魂出窍结束（肉身受伤、被移动、被吞噬，或按下技能键）。 */
    RETURNED(3, "returned"),
    /** Round over, death, role change, a stun or a SparkTraits skill block. / 对局结束、死亡、身份变化、眩晕或 SparkTraits 技能封锁。 */
    INTERRUPTED(4, "interrupted");

    private final int wire;
    private final @Nullable String id;

    SpiritPossessionEndReason(int wire, @Nullable String id) {
        this.wire = wire;
        this.id = id;
    }

    public int wire() {
        return wire;
    }

    /** {@code message.sparkstrength.spirit_possession.end.<id>}, or null when the end is silent. / 无提示时为 null。 */
    public @Nullable String translationKey() {
        return id == null ? null : "message.sparkstrength.spirit_possession.end." + id;
    }

    public static SpiritPossessionEndReason fromWire(int wire) {
        for (SpiritPossessionEndReason reason : values()) {
            if (reason.wire == wire) {
                return reason;
            }
        }
        return EXIT;
    }
}
