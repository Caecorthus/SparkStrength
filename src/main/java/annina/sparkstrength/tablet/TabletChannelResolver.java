package annina.sparkstrength.tablet;

import annina.sparkstrength.compat.SparkFactionCompat;
import annina.sparkstrength.compat.SparkTraitsCompat;
import annina.sparkstrength.component.tablet.TabletWorldComponent;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Gathers identity facts for tablet channels from role flags, owner-synced traits and the role-only base faction.
 * 从身份标记、同步给本人的天赋与仅由身份决定的基础阵营收集平板频道事实。
 *
 * <p>{@link #identityChannels(PlayerEntity)} is side-agnostic so client shop listings and server purchase
 * validation (which pick entries by index) always agree. Server-only live/frozen access builds on top of it.
 * identityChannels 与端无关，保证按下标购买的客户端商店列表与服务端校验一致；服务端的存活/冻结访问建立在其之上。</p>
 */
public final class TabletChannelResolver {
    private TabletChannelResolver() {
    }

    public static TabletChannelRules.Facts facts(PlayerEntity player) {
        if (player == null) {
            return new TabletChannelRules.Facts(false, false, false, false, false, false, false);
        }
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        boolean hasRole = role != null && role != WatheRoles.NO_ROLE;
        return new TabletChannelRules.Facts(
                hasRole,
                hasRole && TabletShopRules.canBuyTabletRole(role),
                hasRole && role.canUseKiller(),
                hasRole && SparkTraitsCompat.hasImpostor(player),
                hasRole && SparkTraitsCompat.hasConscience(player),
                hasRole && SparkFactionCompat.isWitchFactionRole(role),
                // Real round role only: a disguise (e.g. a Coroner shown as Undercover) never joins the network.
                // 仅看真实局内身份：伪装（如显示为卧底的验尸官）不会加入该网络。
                hasRole && TabletShopRules.isUndercover(role)
        );
    }

    public static EnumSet<TabletChannel> identityChannels(PlayerEntity player) {
        return TabletChannelRules.allowed(facts(player));
    }

    /**
     * Server-authoritative access for one holder. Resolving it records the alive-time allowed set and persists the
     * chosen selection, so every packet handler and sync pass sees the same channel.
     * 服务端权威的单个持有者访问状态。解析时会记录存活时的允许集合并回写所选频道，保证所有处理器与同步轮次一致。
     *
     * <p>Alive holders use live identity; dead holders are read-only and keep the set frozen at their last alive
     * resolution because SparkTraits clears traits at death (a dead Impostor must keep both networks).
     * 存活者使用实时身份；死亡者只读，并沿用最后一次存活时冻结的集合，因为 SparkTraits 会在死亡时清除天赋。</p>
     */
    public static Access access(ServerPlayerEntity player) {
        TabletWorldComponent state = TabletWorldComponent.KEY.get(player.getServerWorld());
        UUID uuid = player.getUuid();
        boolean alive = GameFunctions.isPlayerPlayingAndAlive(player);
        EnumSet<TabletChannel> allowed;
        if (alive) {
            allowed = identityChannels(player);
            state.freezeAllowedChannels(uuid, allowed);
        } else {
            EnumSet<TabletChannel> frozen = state.frozenAllowedChannels(uuid);
            allowed = frozen != null ? frozen : identityChannels(player);
        }
        TabletChannel selected = TabletChannelRules.choose(allowed, state.selectedChannel(uuid));
        state.setSelectedChannel(uuid, selected);
        return new Access(allowed, selected, alive, TabletChannelRules.canSend(selected, alive));
    }

    /**
     * Resolves each holder once per sync pass so reflective identity lookups scale with holders, not viewers x holders.
     * 每轮同步只解析每个持有者一次，使反射身份查询随持有者数量线性增长，而非观看者 x 持有者。
     */
    public static Map<UUID, Access> roster(Collection<ServerPlayerEntity> holders) {
        LinkedHashMap<UUID, Access> roster = new LinkedHashMap<>();
        for (ServerPlayerEntity holder : holders) {
            roster.put(holder.getUuid(), access(holder));
        }
        return roster;
    }

    public static Map<UUID, Access> roster(ServerWorld world) {
        return roster(TabletAccess.tabletHolders(world));
    }

    /**
     * @param allowed  networks the holder belongs to (push fan-out, member lists, outlines, police electorate; anonymous
     *                 channels filter member lists per viewer by links and draw no outlines) / 持有者所属的网络（匿名频道的成员列表还会按互认逐人过滤，且不绘制描边）
     * @param selected channel currently viewed and posted to; null means no signal / 当前查看与发言的频道，null 表示无信号
     * @param alive    {@code GameFunctions.isPlayerPlayingAndAlive} / 是否局内存活
     * @param canSend  {@link TabletChannelRules#canSend} / 是否可发送聊天
     */
    public record Access(EnumSet<TabletChannel> allowed, @Nullable TabletChannel selected, boolean alive, boolean canSend) {
        public Access {
            allowed = allowed == null ? EnumSet.noneOf(TabletChannel.class) : EnumSet.copyOf(allowed);
        }

        @Override
        public EnumSet<TabletChannel> allowed() {
            return EnumSet.copyOf(allowed);
        }

        public boolean isMember(@Nullable TabletChannel channel) {
            return channel != null && allowed.contains(channel);
        }

        public int allowedMask() {
            return TabletChannel.mask(allowed);
        }

        public boolean canSwitchChannels() {
            return allowed.size() > 1;
        }

        /** Meeting and suspect features follow the viewed channel only. / 会议与嫌疑人功能只跟随当前查看的频道。 */
        public boolean viewsMeetingFeatures() {
            return selected != null && selected.hasMeetingFeatures();
        }

        /**
         * Police electorate membership is identity-based, independent of the viewed channel.
         * 义警选民资格由身份决定，与当前查看的频道无关。
         */
        public boolean isPoliceElector() {
            return alive && allowed.contains(TabletChannel.POLICE);
        }
    }
}
