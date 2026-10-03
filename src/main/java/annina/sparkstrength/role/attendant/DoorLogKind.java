package annina.sparkstrength.role.attendant;

import org.jetbrains.annotations.Nullable;

/**
 * What happened to a room door. Wire ids are a stable packet contract; never use ordinal().
 * 房门发生了什么。wire id 是稳定的网络包契约，禁止使用 ordinal()。
 *
 * <p>Only {@link #KEY_OPENED} names an actor (the opener's apparent identity); every other kind is anonymous by design.
 * 只有 KEY_OPENED 会显示操作者（开门者的表面身份）；其余类型刻意不显示是谁。</p>
 */
public enum DoorLogKind {
    /** Room key, master key, neutral master key or Key Fish. / 房间钥匙、万能钥匙、中立万能钥匙或钥匙鱼。 */
    KEY_OPENED("key_opened", 1, true),
    /** Lockpick open. / 撬锁器开门。 */
    PICKED("picked", 2, false),
    /** {@code DoorBlockEntity#blast()}: crowbar or Pig God charge. / 撬棍或猪神冲撞。 */
    BROKEN("broken", 3, false),
    /** Blasted door repaired. / 被破坏的门被修复。 */
    REPAIRED("repaired", 4, false),
    /** Door jammed (sneaking lockpick, repair-tool lock). / 门被堵住（潜行撬锁器、维修工具上锁）。 */
    JAMMED("jammed", 5, false),
    /** Jammed door freed by a repair tool. / 被堵的门被维修工具解开。 */
    UNJAMMED("unjammed", 6, false),
    /** Locked door opened without a key or lockpick (Jester psycho, Depression bat). / 无钥匙无撬锁器强行打开。 */
    FORCED("forced", 7, false);

    private final String id;
    private final int wire;
    private final boolean namesActor;

    DoorLogKind(String id, int wire, boolean namesActor) {
        this.id = id;
        this.wire = wire;
        this.namesActor = namesActor;
    }

    public String id() {
        return id;
    }

    public int wire() {
        return wire;
    }

    public boolean namesActor() {
        return namesActor;
    }

    /**
     * Single {@code %s} = door name. KEY_OPENED reads as a predicate after the actor's head and name, which the client
     * draws separately.
     * 唯一参数 %s 为门名。KEY_OPENED 是接在操作者头像与名字之后的谓语，头像与名字由客户端单独绘制。
     */
    public String translationKey() {
        return "screen.sparkstrength.tablet.door_log.event." + id;
    }

    public static @Nullable DoorLogKind fromWire(int wire) {
        for (DoorLogKind kind : values()) {
            if (kind.wire == wire) {
                return kind;
            }
        }
        return null;
    }
}
