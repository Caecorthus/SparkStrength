package annina.sparkstrength.role.veteran;

import annina.sparkstrength.network.veteran.SyncVeteranBlackoutS2CPacket;
import dev.doctor4t.wathe.cca.WorldBlackoutComponent;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

/**
 * 把 Wathe 服务端停电状态同步给客户端老兵高亮。
 *
 * <p>同步策略很轻：状态变化时立即发包；停电期间每秒补发一次 true，
 * 让中途重连或刚切入世界的客户端也能尽快拿到正确状态。</p>
 */
public final class VeteranBlackoutService {
    private static final int ACTIVE_RESYNC_INTERVAL_TICKS = 20;
    private static final Map<RegistryKey<World>, Boolean> LAST_BLACKOUT_STATE = new HashMap<>();

    private VeteranBlackoutService() {
    }

    public static void tick(ServerWorld world) {
        boolean active = WorldBlackoutComponent.KEY.get(world).isBlackoutActive();
        RegistryKey<World> worldKey = world.getRegistryKey();
        boolean previous = LAST_BLACKOUT_STATE.getOrDefault(worldKey, false);
        boolean changed = previous != active;
        LAST_BLACKOUT_STATE.put(worldKey, active);

        if (changed || (active && world.getTime() % ACTIVE_RESYNC_INTERVAL_TICKS == 0)) {
            syncToWorld(world, active);
        }
    }

    public static void clear(ServerWorld world) {
        LAST_BLACKOUT_STATE.put(world.getRegistryKey(), false);
        syncToWorld(world, false);
    }

    private static void syncToWorld(ServerWorld world, boolean active) {
        SyncVeteranBlackoutS2CPacket packet = new SyncVeteranBlackoutS2CPacket(active);
        for (ServerPlayerEntity player : world.getPlayers()) {
            ServerPlayNetworking.send(player, packet);
        }
    }
}
