package annina.sparkstrength.tablet;

import annina.sparkstrength.compat.SparkFactionCompat;
import annina.sparkstrength.role.corruptcop.CorruptCopRules;
import annina.sparkstrength.role.veteran.VeteranRules;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Pure grant, police-network and highlight rules for the SparkStrength tablet item. The tablet is never sold: it is
 * granted free to every tablet-eligible player (the class name is historical).
 * SparkStrength 平板物品的纯发放、义警网络与高亮规则。平板从不出售，而是免费发放给每名符合条件的玩家（类名沿用旧称）。
 */
public final class TabletShopRules {
    public static final Identifier VIGILANTE_ID = Identifier.of("wathe", "vigilante");
    public static final Identifier UNDERCOVER_ID = Identifier.of("noellesroles", "undercover");
    /**
     * Mid-round grant reconciliation cadence while the round is ACTIVE. / 对局 ACTIVE 期间局中补发对账的间隔。
     */
    public static final int GRANT_RECONCILE_INTERVAL_TICKS = 20;
    public static final String POLICE_GRANTED_KEY = "message.sparkstrength.tablet.police_granted";
    public static final String KILLER_GRANTED_KEY = "message.sparkstrength.tablet.killer_granted";
    public static final String WITCH_GRANTED_KEY = "message.sparkstrength.tablet.witch_granted";
    public static final String IMPOSTOR_GRANTED_KEY = "message.sparkstrength.tablet.impostor_granted";
    public static final String UNDERCOVER_GRANTED_KEY = "message.sparkstrength.tablet.undercover_granted";
    public static final String ATTENDANT_GRANTED_KEY = "message.sparkstrength.tablet.attendant_granted";
    public static final String BOMBER_GRANTED_KEY = "message.sparkstrength.tablet.bomber_granted";
    /** Police-network suspect highlight; tablets never outline channel members. / 义警网络嫌疑人高亮色；平板从不描边频道成员。 */
    public static final int SUSPECT_HIGHLIGHT_COLOR = 0xFF8C00;

    private TabletShopRules() {
    }

    /**
     * Police-network role ({@code TabletChannelRules.Facts#policeRole}): SparkFactionAPI {@code PoliceRoles} minus
     * Veteran, with Corrupt Cop registered by {@code TabletShopService#register}.
     * 义警网络身份（Facts#policeRole）：SparkFactionAPI PoliceRoles 去掉老兵；黑警由 TabletShopService#register 注册。
     */
    public static boolean isPoliceNetworkRole(@Nullable Role role) {
        if (role == null || VeteranRules.isVeteran(role)) {
            return false;
        }
        Boolean police = SparkFactionCompat.isPoliceRole(role);
        // Old/absent APIs keep exactly the original whitelist, never infer police from gear/faction.
        // 缺失或旧版 API 严格保留原白名单，绝不通过装备或阵营推断警职。
        return police != null ? police : isVigilante(role) || CorruptCopRules.isCorruptCop(role);
    }

    public static boolean isVigilante(@Nullable Role role) {
        return role != null && VIGILANTE_ID.equals(role.identifier());
    }

    public static boolean isUndercover(@Nullable Role role) {
        return role != null && UNDERCOVER_ID.equals(role.identifier());
    }

    /**
     * Tablet eligibility: a real round role that belongs to at least one tablet network
     * ({@code TabletChannelResolver.identityChannels} non-empty), or a real role whose tablet feature needs no channel:
     * the Attendant (door monitor) or the Bomber (drone directory; a SparkTraits Conscience Bomber has no channel).
     * Disguises never count: a Coroner's Attendant disguise is lent a temporary tablet instead, and a Coroner's Bomber
     * disguise gets no drones at all.
     * 平板资格：拥有有效局内身份，且属于至少一个平板网络（identityChannels 非空），或真实身份的平板功能无需频道：
     * 乘务员（房门监控）或炸弹客（无人机目录；SparkTraits 善良炸弹客没有频道）。伪装不计入：验尸官的乘务员伪装改为借出临时平板，
     * 验尸官的炸弹客伪装没有无人机。
     *
     * @param hasRole            {@code TabletChannelRules.Facts#hasRole} / 拥有有效局内身份
     * @param hasIdentityChannel identity channel set is non-empty / 身份频道集非空
     * @param realAttendant      real round role is Attendant / 真实局内身份为乘务员
     * @param realBomber         real round role is Bomber / 真实局内身份为炸弹客
     */
    public static boolean isTabletEligible(
            boolean hasRole, boolean hasIdentityChannel, boolean realAttendant, boolean realBomber
    ) {
        return hasRole && (hasIdentityChannel || realAttendant || realBomber);
    }

    /**
     * Private chat lines sent with a granted tablet: one for the channel set (Undercover keeps its own killer line),
     * then the Attendant's door-monitor line, then the Bomber's drone line. Empty for an ineligible player.
     * 发放平板时发送的私聊提示：先发频道提示（卧底保留其专属的杀手频道提示），再发乘务员房门监控提示，最后发炸弹客无人机提示；
     * 无资格者为空。
     */
    public static List<String> grantMessageKeys(
            @Nullable Set<TabletChannel> channels,
            boolean undercover,
            boolean realAttendant,
            boolean realBomber
    ) {
        List<String> keys = new ArrayList<>(3);
        String channelKey = channelGrantMessageKey(channels, undercover);
        if (channelKey != null) {
            keys.add(channelKey);
        }
        if (realAttendant) {
            keys.add(ATTENDANT_GRANTED_KEY);
        }
        if (realBomber) {
            keys.add(BOMBER_GRANTED_KEY);
        }
        return List.copyOf(keys);
    }

    private static @Nullable String channelGrantMessageKey(@Nullable Set<TabletChannel> channels, boolean undercover) {
        if (channels == null || channels.isEmpty()) {
            return null;
        }
        if (channels.contains(TabletChannel.POLICE)) {
            return channels.contains(TabletChannel.KILLER) ? IMPOSTOR_GRANTED_KEY : POLICE_GRANTED_KEY;
        }
        if (channels.contains(TabletChannel.KILLER)) {
            return undercover ? UNDERCOVER_GRANTED_KEY : KILLER_GRANTED_KEY;
        }
        return channels.contains(TabletChannel.WITCH) ? WITCH_GRANTED_KEY : null;
    }
}
