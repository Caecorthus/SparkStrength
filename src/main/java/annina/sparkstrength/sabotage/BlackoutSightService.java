package annina.sparkstrength.sabotage;

import annina.sparkstrength.component.tablet.TabletWorldComponent;
import annina.sparkstrength.network.sabotage.SyncBlackoutSightS2CPacket;
import dev.doctor4t.wathe.cca.WorldBlackoutComponent;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
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
 * Sends every client what {@link BlackoutSightRules} needs: the blackout state with each player's own tablet links,
 * checked at once on each change and every second while it lasts, but sent to a player only when their payload
 * differs from the last one they got. A link made in the dark is pushed to both sides right away; joining,
 * respawning or changing world re-checks that player at once.
 * 向每个客户端发送 BlackoutSightRules 所需的数据：熄灯状态与每名玩家自己的平板互认对象。熄灯状态一变化就检查，熄灯期间
 * 每秒检查一次，但只有当某名玩家的数据与他上次收到的不同时才发送。熄灯中完成的互认立即推送给双方；加入、重生或切换世界时
 * 立即重新检查该玩家。
 */
public final class BlackoutSightService {
    private static final int ACTIVE_RESYNC_INTERVAL_TICKS = 20;
    /** What a client holds before its first packet and after a disconnect. / 客户端收到首个包之前以及断线后持有的状态。 */
    private static final SyncBlackoutSightS2CPacket INACTIVE = new SyncBlackoutSightS2CPacket(false, List.of());
    private static final Map<RegistryKey<World>, Boolean> LAST_STATE = new HashMap<>();
    /** Last payload sent to each player; absent means {@link #INACTIVE}. / 每名玩家上次收到的数据；缺省即 INACTIVE。 */
    private static final Map<UUID, SyncBlackoutSightS2CPacket> SENT = new HashMap<>();
    private static boolean registered;

    private BlackoutSightService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            SENT.remove(handler.player.getUuid());
            refresh(handler.player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SENT.remove(handler.player.getUuid()));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> refresh(newPlayer));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) ->
                refresh(player));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> SENT.clear());
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

    /** The player's current world decides; used when they arrive somewhere. / 由玩家当前所在世界决定；用于玩家刚到达某处时。 */
    private static void refresh(ServerPlayerEntity player) {
        send(player, WorldBlackoutComponent.KEY.get(player.getServerWorld()).isBlackoutActive());
    }

    private static void send(ServerPlayerEntity player, boolean active) {
        List<UUID> partners = active
                ? List.copyOf(TabletWorldComponent.KEY.get(player.getServerWorld()).identityLinks(player.getUuid()))
                : List.of();
        SyncBlackoutSightS2CPacket packet = new SyncBlackoutSightS2CPacket(active, partners);
        if (packet.equals(SENT.getOrDefault(player.getUuid(), INACTIVE))) {
            return;
        }
        ServerPlayNetworking.send(player, packet);
        if (active) {
            SENT.put(player.getUuid(), packet);
        } else {
            SENT.remove(player.getUuid());
        }
    }
}
