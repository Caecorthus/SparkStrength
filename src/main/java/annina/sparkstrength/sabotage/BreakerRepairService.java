package annina.sparkstrength.sabotage;

import annina.sparkstrength.SparkStrengthBlocks;
import annina.sparkstrength.SparkStrengthSounds;
import annina.sparkstrength.component.sabotage.SabotageWorldComponent;
import annina.sparkstrength.network.sabotage.CloseBreakerRepairS2CPacket;
import annina.sparkstrength.network.sabotage.OpenBreakerRepairS2CPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Circuit breaker repair panels. A session is bound to the outage it was opened for, so an old panel can never repair a
 * later outage; completion is re-validated (alive, same world, breaker still there, distance, minimum time).
 * 电闸维修面板。每个会话绑定打开它时的那次熄灯，旧面板永远修不了后来的熄灯；完成时重新校验
 * （存活、同一世界、电闸还在、距离、最短用时）。
 */
public final class BreakerRepairService {
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static int nextSessionId = 1;

    private record Session(int id, RegistryKey<World> world, BlockPos pos, int outageId, long startTick, int switches,
                           int holdTicks) {
    }

    private BreakerRepairService() {
    }

    public static void onBreakerUse(ServerPlayerEntity player, BlockPos pos, BlockState state) {
        if (!SabotageAccess.canRepairBreaker(player)) {
            return;
        }
        ServerWorld world = player.getServerWorld();
        SabotageWorldComponent sabotage = SabotageWorldComponent.KEY.get(world);
        SabotageWorldComponent.Outage outage = sabotage.outage();
        if (outage == null || outage.type() != SabotageType.LIGHTS) {
            player.sendMessage(Text.translatable("message.sparkstrength.sabotage.breaker_normal"), true);
            return;
        }
        if (outage.mode() != OutageMode.REPAIR) {
            player.sendMessage(Text.translatable("message.sparkstrength.sabotage.breaker_timed"), true);
            return;
        }
        boolean master = outage.masterSaboteur();
        int switches = SabotageRules.repairSwitches(world.random.nextInt(3), master);
        int hold = SabotageRules.repairHoldTicks(master);
        Session session = new Session(nextSessionId++, world.getRegistryKey(), pos.toImmutable(), outage.id(),
                world.getTime(), switches, hold);
        Session previous = SESSIONS.put(player.getUuid(), session);
        if (previous != null) {
            close(player, previous, CloseBreakerRepairS2CPacket.REASON_OTHER);
        }
        world.playSound(null, pos, SparkStrengthSounds.CIRCUIT_BREAKER_SWITCH, SoundCategory.BLOCKS, 0.8F, 0.8F);
        if (ServerPlayNetworking.canSend(player, OpenBreakerRepairS2CPacket.ID)) {
            ServerPlayNetworking.send(player, new OpenBreakerRepairS2CPacket(session.id(), session.pos(),
                    world.random.nextLong(), switches, hold));
        }
    }

    public static void handleComplete(ServerPlayerEntity player, int sessionId) {
        Session session = SESSIONS.get(player.getUuid());
        if (session == null || session.id() != sessionId) {
            return;
        }
        SESSIONS.remove(player.getUuid());
        ServerWorld world = player.getServerWorld();
        SabotageWorldComponent sabotage = SabotageWorldComponent.KEY.get(world);
        SabotageWorldComponent.Outage outage = sabotage.outage();
        if (outage == null || outage.id() != session.outageId() || world.getRegistryKey() != session.world()) {
            close(player, session, CloseBreakerRepairS2CPacket.REASON_POWER_BACK);
            return;
        }
        if (!SabotageAccess.canRepairBreaker(player)
                || !world.getBlockState(session.pos()).isOf(SparkStrengthBlocks.circuitBreaker())
                || !withinRange(player, session.pos())
                || world.getTime() - session.startTick()
                        < SabotageRules.repairMinTicks(session.switches(), session.holdTicks())) {
            close(player, session, CloseBreakerRepairS2CPacket.REASON_OTHER);
            return;
        }
        if (LightsSabotageService.repair(world, sabotage, player, session.pos())) {
            close(player, session, CloseBreakerRepairS2CPacket.REASON_REPAIRED);
        }
    }

    public static void handleCancel(ServerPlayerEntity player, int sessionId) {
        Session session = SESSIONS.get(player.getUuid());
        if (session != null && session.id() == sessionId) {
            SESSIONS.remove(player.getUuid());
        }
    }

    public static void tick(ServerWorld world) {
        long now = world.getTime();
        Iterator<Map.Entry<UUID, Session>> iterator = SESSIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Session> entry = iterator.next();
            Session session = entry.getValue();
            if (session.world() != world.getRegistryKey()) {
                continue;
            }
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(entry.getKey());
            boolean expired = now - session.startTick() > SabotageRules.REPAIR_SESSION_TIMEOUT_TICKS;
            if (player == null || player.getServerWorld() != world || expired
                    || !SabotageAccess.canRepairBreaker(player) || !withinRange(player, session.pos())) {
                iterator.remove();
                if (player != null) {
                    close(player, session, CloseBreakerRepairS2CPacket.REASON_OTHER);
                }
            }
        }
    }

    public static void closeAll(ServerWorld world, int reason) {
        Iterator<Map.Entry<UUID, Session>> iterator = SESSIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Session> entry = iterator.next();
            if (entry.getValue().world() != world.getRegistryKey()) {
                continue;
            }
            iterator.remove();
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(entry.getKey());
            if (player != null) {
                close(player, entry.getValue(), reason);
            }
        }
    }

    public static void onDisconnect(ServerPlayerEntity player) {
        SESSIONS.remove(player.getUuid());
    }

    private static boolean withinRange(ServerPlayerEntity player, BlockPos pos) {
        double range = SabotageRules.REPAIR_RANGE;
        return player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos)) <= range * range;
    }

    private static void close(ServerPlayerEntity player, Session session, int reason) {
        if (ServerPlayNetworking.canSend(player, CloseBreakerRepairS2CPacket.ID)) {
            ServerPlayNetworking.send(player, new CloseBreakerRepairS2CPacket(session.id(), reason));
        }
    }
}
