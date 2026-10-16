package annina.sparkstrength.sabotage;

import net.minecraft.util.StringIdentifiable;

/**
 * The look and round progress of a junction box, kept in its blockstate so clients can see it without packets.
 * 接线箱的外观与本局进度，存放在方块状态里，客户端无需数据包即可看到。
 */
public enum JunctionBoxState implements StringIdentifiable {
    /** Intact (possibly damaged; durability lives on the server). / 完好（可能已受损，耐久只在服务端）。 */
    CLOSED("closed"),
    /** Pried open at 0% durability, sparking, ready to be hacked. / 耐久归零被撬开，冒火花，可以入侵。 */
    BROKEN("broken"),
    /** Hacked: its sabotage is unlocked for the killer team. / 已入侵：对应破坏已对杀手阵营解锁。 */
    HACKED("hacked");

    private final String id;

    JunctionBoxState(String id) {
        this.id = id;
    }

    public boolean isOpen() {
        return this != CLOSED;
    }

    @Override
    public String asString() {
        return id;
    }
}
