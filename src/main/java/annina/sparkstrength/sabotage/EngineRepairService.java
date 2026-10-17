package annina.sparkstrength.sabotage;

import annina.sparkstrength.SparkStrengthBlocks;
import annina.sparkstrength.component.sabotage.SabotageWorldComponent;
import annina.sparkstrength.network.sabotage.CloseBreakerRepairS2CPacket;
import annina.sparkstrength.network.sabotage.OpenEngineRepairS2CPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Engine unit repair panels. A session is bound to the outage and the repair mode it was opened for: a quick repair
 * dies when the engine is wrecked, an overhaul survives the step to emergency power. Completion is re-validated
 * (alive, same world, unit still registered and there, distance, minimum time, quick repairs strictly before the
 * deadline); the first valid completion ends the sabotage and every later one finds it over. Status panels have no
 * session. Session ids come from {@link RepairSessionIds}, shared with the breaker panels.
 * 动力装置维修面板。每个会话绑定打开时的那次破坏与维修种类：抢修在动力损毁时作废，大修跨入应急动力后仍然有效。完成时重新校验
 * （存活、同一世界、装置仍已登记且还在、距离、最短用时、抢修必须在截止之前）；第一份有效的完成结束破坏，之后的提交都会发现它已经
 * 结束。状态面板没有会话。会话 id 来自 RepairSessionIds，与电闸面板共用。
 */
public final class EngineRepairService {
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private record Session(int id, RegistryKey<World> world, BlockPos pos, int outageId, EngineRepairMode mode,
                           long startTick, int gauges, int fuses, int holdTicks, int minTicks) {
    }

    private EngineRepairService() {
    }

    /** The read-only panel: no session, nothing to submit. / 只读面板：没有会话，不能提交。 */
    static void openStatus(ServerPlayerEntity player, BlockPos pos) {
        BreakerRepairService.closeFor(player, CloseBreakerRepairS2CPacket.REASON_OTHER);
        closeFor(player, CloseBreakerRepairS2CPacket.REASON_OTHER);
        EngineRepairMode mode = EngineRepairMode.STATUS;
        send(player, new OpenEngineRepairS2CPacket(0, pos.toImmutable(), mode.wire(),
                player.getServerWorld().random.nextLong(), EngineRules.gauges(mode, false),
                EngineRules.fuses(mode, false), EngineRules.holdTicks(mode, false), 0));
    }

    /** A repair panel for {@code phase}; the caller checked the player, the unit and that a sabotage is on. / 打开 phase 对应的维修面板；调用方已检查玩家、装置与破坏是否进行中。 */
    static void open(ServerPlayerEntity player, ServerWorld world, SabotageWorldComponent sabotage, BlockPos pos,
                     EnginePhase phase) {
        SabotageWorldComponent.Outage outage = sabotage.outage();
        if (outage == null) {
            return;
        }
        EngineRepairMode mode = EngineRules.repairModeFor(phase);
        boolean master = outage.masterSaboteur();
        int gauges = EngineRules.gauges(mode, master);
        int fuses = EngineRules.fuses(mode, master);
        int hold = EngineRules.holdTicks(mode, master);
        Session session = new Session(RepairSessionIds.next(), world.getRegistryKey(), pos.toImmutable(), outage.id(),
                mode, world.getTime(), gauges, fuses, hold, EngineRules.minTicks(gauges, fuses, hold));
        // One repair panel at a time: a breaker panel left open is dropped. / 同一时间只有一个维修面板：留着的电闸面板作废。
        BreakerRepairService.closeFor(player, CloseBreakerRepairS2CPacket.REASON_OTHER);
        Session previous = SESSIONS.put(player.getUuid(), session);
        if (previous != null) {
            close(player, previous, CloseBreakerRepairS2CPacket.REASON_OTHER);
        }
        world.playSound(null, pos, SoundEvents.BLOCK_IRON_TRAPDOOR_OPEN, SoundCategory.BLOCKS, 0.6F, 0.6F);
        send(player, new OpenEngineRepairS2CPacket(session.id(), session.pos(), mode.wire(), world.random.nextLong(),
                gauges, fuses, hold, session.minTicks()));
    }

    /** Ignores session ids it does not own: the breaker panels share the packet. / 忽略不属于自己的会话 id：电闸面板共用这个包。 */
    public static void handleComplete(ServerPlayerEntity player, int sessionId) {
        if (sessionId == 0) {
            return;
        }
        ServerWorld world = player.getServerWorld();
        // The packet may arrive before this tick's update: a quick session the deadline killed is closed right here.
        // 包可能早于本 tick 的更新到达：被截止时间作废的抢修会话会在这里直接关闭。
        EngineSabotageService.advance(world);
        Session session = SESSIONS.get(player.getUuid());
        if (session == null || session.id() != sessionId) {
            return;
        }
        long now = world.getTime();
        if (now - session.startTick() < session.minTicks()) {
            // Too early (only a modified client gets here): ignore and keep the session; the panel retries.
            // 过早（只有改过的客户端才会走到这里）：忽略并保留会话，面板会重试。
            return;
        }
        if (SabotageActionLocks.isLocked(player)) {
            // Stunned as the restart finished: ignore and keep the session; the panel asks for another try.
            // 重启完成时处于眩晕：忽略并保留会话，面板会提示再试一次。
            return;
        }
        SESSIONS.remove(player.getUuid());
        if (world.getRegistryKey() != session.world()) {
            close(player, session, CloseBreakerRepairS2CPacket.REASON_OTHER);
            return;
        }
        SabotageWorldComponent sabotage = SabotageWorldComponent.KEY.get(world);
        SabotageWorldComponent.Outage outage = sabotage.outage();
        if (outage == null || outage.type() != SabotageType.ENGINE || outage.id() != session.outageId()) {
            // Someone else was first. / 别人先修好了。
            close(player, session, CloseBreakerRepairS2CPacket.REASON_POWER_BACK);
            return;
        }
        EnginePhase phase = sabotage.enginePhase(now);
        if (session.mode() != EngineRules.repairModeFor(phase)
                || (session.mode() == EngineRepairMode.QUICK
                && !EngineRules.quickRepairInTime(now, sabotage.engineTriggeredAt()))) {
            phaseChanged(player, session);
            return;
        }
        if (!SabotageAccess.canRepairBreaker(player) || !sabotage.engineUnits().contains(session.pos())
                || !world.getBlockState(session.pos()).isOf(SparkStrengthBlocks.engineUnit())
                || !withinRange(player, session.pos())) {
            close(player, session, CloseBreakerRepairS2CPacket.REASON_OTHER);
            return;
        }
        boolean repaired = EngineSabotageService.repair(world, session.outageId(), player, session.pos());
        close(player, session, repaired ? CloseBreakerRepairS2CPacket.REASON_REPAIRED
                : CloseBreakerRepairS2CPacket.REASON_POWER_BACK);
    }

    public static void handleCancel(ServerPlayerEntity player, int sessionId) {
        Session session = SESSIONS.get(player.getUuid());
        if (session != null && session.id() == sessionId) {
            SESSIONS.remove(player.getUuid());
        }
    }

    public static void tick(ServerWorld world) {
        if (SESSIONS.isEmpty()) {
            return;
        }
        long now = world.getTime();
        SabotageWorldComponent sabotage = SabotageWorldComponent.KEY.get(world);
        SabotageWorldComponent.Outage outage = sabotage.outage();
        EnginePhase phase = sabotage.enginePhase(now);
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
            } else if (outage == null || outage.type() != SabotageType.ENGINE || outage.id() != session.outageId()) {
                iterator.remove();
                close(player, session, CloseBreakerRepairS2CPacket.REASON_POWER_BACK);
            } else if (session.mode() != EngineRules.repairModeFor(phase)) {
                iterator.remove();
                phaseChanged(player, session);
            }
        }
    }

    /**
     * The phase just moved on: sessions for another repair mode are dropped (quick repairs once wrecked); overhauls
     * carry on into emergency power.
     * 阶段刚刚推进：维修种类不再对应的会话作废（损毁后的抢修）；大修继续进入应急动力。
     */
    static void onPhaseChanged(ServerWorld world) {
        if (SESSIONS.isEmpty()) {
            return;
        }
        EngineRepairMode wanted = EngineRules.repairModeFor(EngineSabotageService.phase(world));
        Iterator<Map.Entry<UUID, Session>> iterator = SESSIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Session> entry = iterator.next();
            Session session = entry.getValue();
            if (session.world() != world.getRegistryKey() || session.mode() == wanted) {
                continue;
            }
            iterator.remove();
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(entry.getKey());
            if (player != null) {
                phaseChanged(player, session);
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

    /** Drops this player's engine session, if any, and closes its panel. / 作废本人的动力会话（如有）并关闭面板。 */
    static void closeFor(ServerPlayerEntity player, int reason) {
        Session session = SESSIONS.remove(player.getUuid());
        if (session != null) {
            close(player, session, reason);
        }
    }

    /** A quick repair outlived the deadline: tell the player the engine now needs the overhaul. / 抢修超过了截止时间：告诉玩家动力现在需要大修。 */
    private static void phaseChanged(ServerPlayerEntity player, Session session) {
        close(player, session, CloseBreakerRepairS2CPacket.REASON_PHASE_CHANGED);
        if (session.mode() == EngineRepairMode.QUICK) {
            // Chat, not the action bar: the wrecked notice takes the action bar over at the same tick.
            // 用聊天栏而不是动作栏：同一 tick 损毁提示会占用动作栏。
            player.sendMessage(Text.translatable("message.sparkstrength.engine.panel_wrecked")
                    .formatted(Formatting.RED), false);
        }
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

    private static void send(ServerPlayerEntity player, OpenEngineRepairS2CPacket packet) {
        if (ServerPlayNetworking.canSend(player, OpenEngineRepairS2CPacket.ID)) {
            ServerPlayNetworking.send(player, packet);
        }
    }
}
