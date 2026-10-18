package annina.sparkstrength.sabotage;

import annina.sparkstrength.component.tablet.TabletWorldComponent;
import annina.sparkstrength.network.sabotage.SyncBlackoutSightS2CPacket;
import dev.doctor4t.wathe.cca.WorldBlackoutComponent;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sends every client what {@link BlackoutSightRules} needs: the blackout state at once on each change and every second
 * while it lasts (so a player who joins mid-blackout catches up), with each player's own tablet links; a link made in
 * the dark is pushed to both sides right away.
 * 向每个客户端发送 BlackoutSightRules 所需的数据：熄灯状态一变化就发送，熄灯期间每秒补发一次（中途加入的玩家也能
 * 跟上），同时附上每名玩家自己的平板互认对象；熄灯中完成的互认立即推送给双方。
 */
public final class BlackoutSightService {
    private static final int ACTIVE_RESYNC_INTERVAL_TICKS = 20;
    private static final Map<RegistryKey<World>, Boolean> LAST_STATE = new HashMap<>();

    private BlackoutSightService() {
    }

    public static void tick(ServerWorld world) {
        boolean active = WorldBlackoutComponent.KEY.get(world).isBlackoutActive();
        boolean previous = Boolean.TRUE.equals(LAST_STATE.put(world.getRegistryKey(), active));
        if (previous != active || (active && world.getTime() % ACTIVE_RESYNC_INTERVAL_TICKS == 0)) {
            for (ServerPlayerEntity player : world.getPlayers()) {
                send(player, active);
            }
        }
    }

    public static void clear(ServerWorld world) {
        LAST_STATE.put(world.getRegistryKey(), false);
        for (ServerPlayerEntity player : world.getPlayers()) {
            send(player, false);
        }
    }

    /** A tablet identity link just completed between {@code a} and {@code b}. / a 与 b 刚完成平板身份互认。 */
    public static void linked(@Nullable ServerPlayerEntity a, @Nullable ServerPlayerEntity b) {
        for (ServerPlayerEntity player : new ServerPlayerEntity[]{a, b}) {
            if (player != null && WorldBlackoutComponent.KEY.get(player.getServerWorld()).isBlackoutActive()) {
                send(player, true);
            }
        }
    }

    private static void send(ServerPlayerEntity player, boolean active) {
        List<UUID> partners = active
                ? List.copyOf(TabletWorldComponent.KEY.get(player.getServerWorld()).identityLinks(player.getUuid()))
                : List.of();
        ServerPlayNetworking.send(player, new SyncBlackoutSightS2CPacket(active, partners));
    }
}
