package annina.sparkstrength.tablet;

import java.util.Collection;
import java.util.EnumSet;

/**
 * Role-granted tablet sections that are not chat networks. Wire bits are a stable packet contract; never use ordinal().
 * 由身份授予、不属于聊天网络的平板分区。wire 位是稳定的网络包契约，禁止使用 ordinal()。
 *
 * <p>Features are independent of {@link TabletChannel}: a holder with no channel ("no signal") still sees its
 * features, and features never affect shop listings, pricing, links or the police electorate.
 * 功能与频道相互独立：没有频道（无信号）的持有者仍能看到其功能；功能不影响商店、定价、互认或义警选民。</p>
 */
public enum TabletFeature {
    /** Attendant room-door monitor. / 乘务员房门监控。 */
    DOOR_LOG("door_log", 1);

    private final String id;
    private final int wire;

    TabletFeature(String id, int wire) {
        this.id = id;
        this.wire = wire;
    }

    public String id() {
        return id;
    }

    public int wire() {
        return wire;
    }

    public static int mask(Collection<TabletFeature> features) {
        int mask = 0;
        for (TabletFeature feature : features) {
            mask |= 1 << feature.wire;
        }
        return mask;
    }

    public static EnumSet<TabletFeature> fromMask(int mask) {
        EnumSet<TabletFeature> features = EnumSet.noneOf(TabletFeature.class);
        for (TabletFeature feature : values()) {
            if ((mask & (1 << feature.wire)) != 0) {
                features.add(feature);
            }
        }
        return features;
    }
}
