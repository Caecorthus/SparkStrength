package annina.sparkstrength.tablet;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Locale;

/**
 * Faction-scoped tablet networks. Wire ids and string ids are stable packet/NBT contracts; never use ordinal().
 * 按阵营隔离的平板网络。wire id 与字符串 id 是稳定的网络包/NBT 契约，禁止使用 ordinal()。
 *
 * <p>Wire id 0 is reserved for "no channel".
 * wire id 0 保留给“无频道”。</p>
 */
public enum TabletChannel {
    POLICE("police", 1),
    KILLER("killer", 2),
    WITCH("witch", 3);

    public static final int NO_CHANNEL_WIRE = 0;

    private final String id;
    private final int wire;

    TabletChannel(String id, int wire) {
        this.id = id;
        this.wire = wire;
    }

    public String id() {
        return id;
    }

    public int wire() {
        return wire;
    }

    /**
     * Only the police network outlines its members in the world. Witches already recognise each other through the
     * witch cohort tag and witch instinct (a tablet outline would override its role colours), and a killer outline
     * would single out real killers from the Undercover, whom NoellesRoles masks at PRIORITY_HIGH.
     * 只有义警网络会在世界中描边成员。魔女已能通过魔女同伙标记与魔女本能互认（平板描边会覆盖其身份颜色）；
     * 杀手描边则会把真正的杀手与卧底区分开（NoellesRoles 以 PRIORITY_HIGH 为卧底伪装）。
     */
    public boolean outlinesMembers() {
        return this == POLICE;
    }

    /**
     * Emergency meetings and suspects exist only on the police network.
     * 紧急会议与嫌疑人功能只存在于义警网络。
     */
    public boolean hasMeetingFeatures() {
        return this == POLICE;
    }

    /**
     * Senders and members are shown as "???" to each viewer until the two have linked; redaction is server-side per
     * viewer. A code capability only, never a wire field.
     * 发送者与成员对每位查看者显示为“???”，直到双方互认；脱敏在服务端按查看者进行。仅为代码能力，不上线传输。
     */
    public boolean anonymousSenders() {
        return this == KILLER;
    }

    public String translationKey() {
        return "screen.sparkstrength.tablet.channel." + id;
    }

    public String shortTranslationKey() {
        return translationKey() + ".short";
    }

    public static int wireOf(@Nullable TabletChannel channel) {
        return channel == null ? NO_CHANNEL_WIRE : channel.wire;
    }

    public static @Nullable TabletChannel fromWire(int wire) {
        for (TabletChannel channel : values()) {
            if (channel.wire == wire) {
                return channel;
            }
        }
        return null;
    }

    public static @Nullable TabletChannel fromId(@Nullable String id) {
        if (id == null) {
            return null;
        }
        String normalized = id.toLowerCase(Locale.ROOT);
        for (TabletChannel channel : values()) {
            if (channel.id.equals(normalized)) {
                return channel;
            }
        }
        return null;
    }

    public static int mask(Collection<TabletChannel> channels) {
        int mask = 0;
        for (TabletChannel channel : channels) {
            mask |= 1 << channel.wire;
        }
        return mask;
    }

    public static EnumSet<TabletChannel> fromMask(int mask) {
        EnumSet<TabletChannel> channels = EnumSet.noneOf(TabletChannel.class);
        for (TabletChannel channel : values()) {
            if ((mask & (1 << channel.wire)) != 0) {
                channels.add(channel);
            }
        }
        return channels;
    }
}
