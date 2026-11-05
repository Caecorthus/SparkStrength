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
    WITCH("witch", 3),
    /**
     * Proximity channel every tablet holder joins: no shared history, each message reaches only holders in range of
     * the sender ({@link TabletPublicRules}). Declared last so a faction network stays the default selection.
     * 每名平板持有者都会加入的近距离频道：没有共享记录，每条消息只送达发送者附近的持有者（见 TabletPublicRules）。
     * 声明在最后，使阵营网络仍为默认选择。
     */
    PUBLIC("public", 4);

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
     * Other members' identities (chat senders and member rows) reveal to each viewer only after that viewer has
     * completed {@link TabletIdentityRules#POLICE_REVEAL_TASKS} tasks this round; redaction is server-side per viewer
     * ({@link TabletIdentityRules}). No tablet channel outlines its members in the world. A code capability only, never
     * a wire field, and never link-capable: identity links stay {@link #anonymousSenders()} (killer) only.
     * 其他成员的身份（聊天发送者与成员行）只在查看者本局完成指定数量任务后才对其显示；脱敏在服务端按查看者进行。
     * 任何平板频道都不会在世界中描边成员。仅为代码能力，不上线传输，也不可互认：身份互认仍只属于 anonymousSenders（杀手）。
     */
    public boolean revealsAfterTasks() {
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
     * viewer. Also the only source of link eligibility ({@link TabletLinkRules#canLink}). A code capability only, never
     * a wire field.
     * 发送者与成员对每位查看者显示为“???”，直到双方互认；脱敏在服务端按查看者进行。也是互认资格的唯一来源。
     * 仅为代码能力，不上线传输。
     */
    public boolean anonymousSenders() {
        return this == KILLER;
    }

    /**
     * Faction networks (police, killer, witch) as opposed to the public proximity channel. Only switches between two
     * different faction networks cost the switch cooldown ({@link TabletChannelRules#canSwitch}).
     * 阵营网络（义警、杀手、魔女），区别于公共近距离频道。只有在两个不同阵营网络之间切换才计入切换冷却。
     */
    public boolean isFaction() {
        return this != PUBLIC;
    }

    /**
     * Range-limited delivery with a per-receiver inbox instead of a shared channel history; no member list is shown,
     * since listing nearby holders would act as a radar through walls.
     * 按距离送达、每名接收者单独收件，而不是共享频道记录；不显示成员列表，因为列出附近持有者等同于穿墙雷达。
     */
    public boolean isProximity() {
        return this == PUBLIC;
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
