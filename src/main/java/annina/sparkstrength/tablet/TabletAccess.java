package annina.sparkstrength.tablet;

import annina.sparkstrength.SparkStrengthItems;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Centralizes tablet possession, channel membership and meeting eligibility checks.
 * 集中处理平板持有、频道成员与会议资格判断，避免各处规则漂移。
 */
public final class TabletAccess {
    private TabletAccess() {
    }

    public static boolean hasTabletInHotbar(PlayerEntity player) {
        if (player == null) {
            return false;
        }
        for (int slot = TabletRules.HOTBAR_START_SLOT; slot <= TabletRules.HOTBAR_END_SLOT; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (stack.isOf(SparkStrengthItems.tablet())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isInGame(PlayerEntity player) {
        return player != null && GameWorldComponent.KEY.get(player.getWorld()).hasAnyRole(player.getUuid());
    }

    public static boolean isInGame(ServerWorld world, java.util.UUID uuid) {
        return GameWorldComponent.KEY.get(world).hasAnyRole(uuid);
    }

    public static List<ServerPlayerEntity> tabletHolders(ServerWorld world) {
        return world.getPlayers().stream()
                .filter(TabletAccess::hasTabletInHotbar)
                .toList();
    }

    /**
     * Holders that belong to {@code channel} (live or frozen membership), whatever channel they are viewing. This is
     * identity-based push fan-out; the member list sent for an anonymous channel is filtered per viewer by links.
     * 属于该频道的持有者（实时或冻结成员资格），与其当前查看的频道无关。此处是按身份的推送范围；匿名频道下发的成员列表另按观看者的互认状态过滤。
     */
    public static List<ServerPlayerEntity> channelMembers(
            Collection<ServerPlayerEntity> holders,
            Map<UUID, TabletChannelResolver.Access> roster,
            TabletChannel channel
    ) {
        return holders.stream()
                .filter(holder -> {
                    TabletChannelResolver.Access access = roster.get(holder.getUuid());
                    return access != null && access.isMember(channel);
                })
                .toList();
    }

    /**
     * Police electorate: alive holders on the police network, including an Impostor viewing the killer channel.
     * Killer/witch-only holders never count, so they cannot raise the 2/3 threshold or stall meeting auto-finish.
     * 义警选民：义警网络中的存活持有者（包括正在查看杀手频道的内鬼）；仅杀手/魔女持有者不计入，避免抬高 2/3 门槛或拖住会议。
     */
    public static Set<UUID> policeElectorate(Map<UUID, TabletChannelResolver.Access> roster) {
        LinkedHashSet<UUID> electorate = new LinkedHashSet<>();
        roster.forEach((uuid, access) -> {
            if (access.isPoliceElector()) {
                electorate.add(uuid);
            }
        });
        return electorate;
    }
}
