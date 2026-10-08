package annina.sparkstrength.role.spiritualist;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.compat.SparkFactionCooldownCompat;
import annina.sparkstrength.compat.SparkTraitsDroneCompat;
import annina.sparkstrength.compat.SparkWitchCompat;
import annina.sparkstrength.component.engineer.EngineerStunnedPlayerComponent;
import annina.sparkstrength.component.spiritualist.SpiritPossessionPlayerComponent;
import annina.sparkstrength.item.m67.M67RoundService;
import annina.sparkstrength.network.spiritualist.SpiritPossessionStateS2CPacket;
import annina.sparkstrength.replay.SparkStrengthReplayFormatters;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.agmas.noellesroles.spiritualist.SpiritPlayerComponent;
import org.agmas.noellesroles.taotie.SwallowedPlayerComponent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Server side of the Spiritualist's Wraith possession (灵界行者附身冤魂, role skill 2).
 * 灵界行者附身冤魂（职业技能 2）的服务端。
 *
 * <p>A possession is a NoellesRoles spirit projection whose view is locked onto a Wraith: from the body the projection
 * is started silently (no projection cooldown, no replay line), from the spirit the running projection is kept. So
 * NoellesRoles' spirit rules all apply unchanged: gray anonymous view, muted sound and voice, a defenseless body, and a
 * forced return when the body is hurt, moved, swallowed or killed. The chunk view and entity tracking are re-centred
 * on the Wraith every tick (mixin/spiritualist/SpiritPossession*), so distance does not matter. When the possession
 * ends, a projection it started is stopped silently and a spirit possession goes back to the spirit; either way the
 * 45 s cooldown starts.
 * 附身就是镜头锁定在冤魂身上的 NoellesRoles 灵魂出窍：从肉身发动时静默开启出窍（不产生出窍冷却与回放），从灵魂发动时沿用正在
 * 进行的出窍。因此 NoellesRoles 的灵魂规则全部照常生效：灰暗且认不出人的画面、听不到声音与语音、毫无防备的肉身，以及肉身受伤、
 * 被移动、被吞噬或死亡时的强制回归。区块视野与实体追踪每刻以冤魂为中心重新计算（mixin/spiritualist/SpiritPossession*），
 * 因此不受距离限制。附身结束时，由附身开启的出窍会被静默关闭，从灵魂发动的附身则回到灵魂；两种情况都会开始 45 秒冷却。</p>
 */
public final class SpiritPossessionService {
    /** Body-view re-evaluation after a possession ends. / 附身结束后以身体为准重新评估视野的时长。 */
    public static final int RESETTLE_TICKS = 40;
    /** A move this long in one tick is a teleport. / 单刻移动超过此距离视为传送。 */
    private static final double JUMP_DISTANCE_SQUARED = 8.0D * 8.0D;
    /** Ticket radius cap and margin, as the Bomber drone's view. / 票据半径上限与余量，与炸弹客无人机视野一致。 */
    private static final int MAX_TICKET_VIEW_DISTANCE = 8;
    private static final int TICKET_MARGIN = 2;
    /**
     * Refreshed every tick around the possessed Wraith, so its surroundings are loaded and tick entities whatever keeps
     * (or does not keep) them loaded otherwise; positions it left expire on their own. Argument: the Wraith's entity id.
     * 每刻在被附身冤魂周围刷新，使其周围区块无论有无其他票据都会加载并更新实体；冤魂离开的位置会自行过期。参数为冤魂实体 id。
     */
    private static final ChunkTicketType<Integer> POSSESSION_TICKET =
            ChunkTicketType.create(SparkStrength.MOD_ID + ":spirit_possession", Integer::compare, 40);

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    /** Spiritualist uuid -> remaining post-possession re-evaluation ticks. / 灵界行者 uuid -> 附身结束后剩余的重新评估刻数。 */
    private static final Map<UUID, Integer> RESETTLING = new HashMap<>();
    /** Player uuid -> server tick of its last processed start. / 玩家 uuid -> 上次被处理的开始请求所在的服务器刻。 */
    private static final Map<UUID, Integer> LAST_START = new HashMap<>();
    private static boolean registered;

    private SpiritPossessionService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerTickEvents.END_WORLD_TICK.register(SpiritPossessionService::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID uuid = handler.player.getUuid();
            SESSIONS.remove(uuid);
            RESETTLING.remove(uuid);
            LAST_START.remove(uuid);
        });
        ServerWorldEvents.UNLOAD.register((server, world) -> SESSIONS.values().removeIf(session -> session.world == world));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            SESSIONS.clear();
            RESETTLING.clear();
            LAST_START.clear();
        });
        // Optional SFA seam: forced-cooldown features may floor/extend the possession cooldown (nominal 45 s).
        // 可选 SFA 接缝：强制冷却功能可为附身冷却设下限或延长（标称 45 秒）。
        SparkFactionCooldownCompat.registerRoleSkillStore(
                SpiritPossessionRules.COOLDOWN_STORE_ID,
                SpiritPossessionService::isSpiritualist,
                player -> SpiritPossessionPlayerComponent.KEY.get(player).getCooldownTicks(),
                player -> OptionalInt.of(SpiritPossessionRules.COOLDOWN_TICKS),
                (player, ticks) -> SpiritPossessionPlayerComponent.KEY.get(player).setCooldownTicks(ticks));
        // Wathe fires ResetPlayer before RoleAssigned at round start, so the opening cooldown survives the reset.
        // 开局时 Wathe 先触发 ResetPlayer 再触发 RoleAssigned，因此开局冷却不会被重置清掉。
        RoleAssigned.EVENT.register((player, role) -> {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                assignForRole(serverPlayer, role);
            }
        });
        ResetPlayer.EVENT.register(player -> {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                clearPlayer(serverPlayer);
            }
        });
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                for (ServerPlayerEntity player : serverWorld.getPlayers()) {
                    clearPlayer(player);
                }
            }
        });
    }

    public static boolean isSpiritualist(ServerPlayerEntity player) {
        return SpiritPossessionRules.isSpiritualist(GameWorldComponent.KEY.get(player.getWorld()).getRole(player));
    }

    /** Every Spiritualist assignment starts on the opening cooldown. / 每次被分配为灵界行者都从开局冷却开始。 */
    public static void assignForRole(ServerPlayerEntity player, @Nullable Role role) {
        end(player, SpiritPossessionEndReason.INTERRUPTED);
        SpiritPossessionPlayerComponent.KEY.get(player).setCooldownTicks(
                SpiritPossessionRules.isSpiritualist(role) ? SpiritPossessionRules.OPENING_COOLDOWN_TICKS : 0);
    }

    public static void clearPlayer(ServerPlayerEntity player) {
        end(player, SpiritPossessionEndReason.INTERRUPTED);
        SpiritPossessionPlayerComponent.KEY.get(player).reset();
    }

    // ---- Start / exit / 开始与退出 ----

    public static void start(ServerPlayerEntity player, int targetEntityId) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUuid();
        ServerWorld world = player.getServerWorld();
        int now = world.getServer().getTicks();
        Integer last = LAST_START.get(uuid);
        if (last != null && now - last < SpiritPossessionRules.START_SPAM_TICKS) {
            return;
        }
        LAST_START.put(uuid, now);
        if (SESSIONS.containsKey(uuid)) {
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        if (game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE
                || !SpiritPossessionRules.isSpiritualist(game.getRole(player))
                || !GameFunctions.isPlayerAliveAndSurvival(player)
                || !canAct(player)) {
            return;
        }
        SpiritPossessionPlayerComponent cooldown = SpiritPossessionPlayerComponent.KEY.get(player);
        if (cooldown.getCooldownTicks() > 0) {
            return;
        }
        UUID roundId = M67RoundService.currentRoundId(world);
        if (roundId == null) {
            return;
        }
        if (!(world.getEntityById(targetEntityId) instanceof ServerPlayerEntity target)
                || target == player
                || !isPossessable(target, world)) {
            return;
        }
        SpiritPlayerComponent spirit = SpiritPlayerComponent.KEY.get(player);
        boolean projecting = spirit.isProjecting();
        double distanceSquared = projecting
                ? target.getBoundingBox().getCenter().squaredDistanceTo(spirit.getBodyX(), spirit.getBodyY(), spirit.getBodyZ())
                : target.getBoundingBox().getCenter().squaredDistanceTo(player.getEyePos());
        if (!SpiritPossessionRules.withinReach(projecting, distanceSquared)) {
            return;
        }

        if (!projecting) {
            // Silent projection: no NoellesRoles cooldown or replay line. / 静默出窍：不产生 NoellesRoles 冷却与回放。
            spirit.startProjecting();
        }
        SESSIONS.put(uuid, new Session(uuid, target, world, roundId, !projecting));
        RESETTLING.remove(uuid);
        sendState(player, new SpiritPossessionStateS2CPacket(
                target.getId(), SpiritPossessionRules.MAX_POSSESSION_TICKS, (byte) 0));

        NbtCompound extra = new NbtCompound();
        extra.putUuid("target", target.getUuid());
        GameRecordManager.recordGlobalEvent(world, SparkStrengthReplayFormatters.SPIRIT_POSSESSION_STARTED, player, extra);
    }

    public static void exit(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        if (SESSIONS.containsKey(player.getUuid())) {
            end(player, SpiritPossessionEndReason.EXIT);
        } else {
            // A late exit (the possession already ended): confirm so the client never waits. / 迟到的退出：照样确认，客户端无需等待。
            sendState(player, new SpiritPossessionStateS2CPacket(-1, 0, (byte) SpiritPossessionEndReason.EXIT.wire()));
        }
    }

    // ---- Queries / 查询 ----

    public static boolean isPossessing(ServerPlayerEntity player) {
        return player != null && !SESSIONS.isEmpty() && SESSIONS.containsKey(player.getUuid());
    }

    /**
     * For the mixin/spiritualist/SpiritPossession* streaming mixins: the Wraith whose surroundings {@code player}'s
     * view is centred on, or null for vanilla behaviour, so any failure restores the body view on the next tick. Hot
     * path: O(1) with no session.
     * 供 mixin/spiritualist/SpiritPossession* 流式加载 mixin 查询视野中心：返回玩家视野所围绕的冤魂，null 表示使用原版行为，
     * 因此任何失效都会在下一刻自动恢复为身体视野。热路径：无会话时 O(1)。
     */
    public static @Nullable ServerPlayerEntity targetOf(ServerPlayerEntity player) {
        if (SESSIONS.isEmpty() || player == null) {
            return null;
        }
        Session session = SESSIONS.get(player.getUuid());
        if (session == null) {
            return null;
        }
        ServerPlayerEntity target = session.target;
        if (target.isRemoved() || target.getWorld() != player.getWorld() || player.isSpectator()
                || player.getCameraEntity() != player) {
            return null;
        }
        return target;
    }

    // ---- Tick / 每刻 ----

    public static void tick(ServerWorld world) {
        if (!SESSIONS.isEmpty()) {
            for (Session session : List.copyOf(SESSIONS.values())) {
                if (session.world != world) {
                    continue;
                }
                ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(session.spiritualistUuid);
                if (player == null) {
                    SESSIONS.remove(session.spiritualistUuid);
                    continue;
                }
                SpiritPossessionEndReason reason = endReason(player, session);
                if (reason != null) {
                    end(player, reason);
                } else {
                    refreshView(player, session);
                }
            }
        }
        if (!RESETTLING.isEmpty()) {
            resettle(world);
        }
    }

    private static @Nullable SpiritPossessionEndReason endReason(ServerPlayerEntity player, Session session) {
        ServerWorld world = session.world;
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        if (player.getServerWorld() != world
                || game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE
                || !Objects.equals(M67RoundService.currentRoundId(world), session.roundId)
                || !SpiritPossessionRules.isSpiritualist(game.getRole(player))
                || !GameFunctions.isPlayerPlayingAndAlive(player)
                || player.isSpectator()
                || player.getCameraEntity() != player) {
            return SpiritPossessionEndReason.INTERRUPTED;
        }
        // NoellesRoles ended the projection: body hurt, moved or swallowed, or the ability key.
        // NoellesRoles 结束了出窍：肉身受伤、被移动、被吞噬，或按下了技能键。
        if (!SpiritPlayerComponent.KEY.get(player).isProjecting()) {
            return SpiritPossessionEndReason.RETURNED;
        }
        if (!canAct(player)) {
            return SpiritPossessionEndReason.INTERRUPTED;
        }
        // Same instance in the same world: a respawned or re-joined Wraith is a new instance with a new entity id.
        // 同一世界中的同一实例：重生或重新加入的冤魂是带有新实体 id 的新实例。
        if (world.getPlayerByUuid(session.target.getUuid()) != session.target || !isPossessable(session.target, world)) {
            return SpiritPossessionEndReason.TARGET_LOST;
        }
        if (--session.remainingTicks <= 0) {
            return SpiritPossessionEndReason.EXPIRED;
        }
        return null;
    }

    /**
     * Ends {@code player}'s possession, if any: stops a projection the possession started, starts the cooldown, lets
     * the view settle back and tells the client.
     * 结束该玩家的附身（若有）：关闭由附身开启的出窍、开始冷却、让视野回到身体并通知客户端。
     */
    private static void end(ServerPlayerEntity player, SpiritPossessionEndReason reason) {
        Session session = SESSIONS.remove(player.getUuid());
        if (session == null) {
            return;
        }
        SpiritPlayerComponent spirit = SpiritPlayerComponent.KEY.get(player);
        if (session.startedFromBody && spirit.isProjecting()) {
            spirit.stopProjecting();
        }
        SpiritPossessionPlayerComponent cooldown = SpiritPossessionPlayerComponent.KEY.get(player);
        cooldown.setCooldownTicks(SpiritPossessionRules.cooldownAfterSession(cooldown.getCooldownTicks()));
        RESETTLING.put(player.getUuid(), RESETTLE_TICKS);
        sendState(player, new SpiritPossessionStateS2CPacket(-1, 0, (byte) reason.wire()));
    }

    /**
     * One tick of the far view: keep the Wraith's surroundings loaded (radius = the Spiritualist's effective view
     * distance, capped, plus a margin), re-evaluate the Spiritualist's chunks and tracking around it, relay any jump.
     * 远距离视野的单刻处理：保持冤魂周围区块加载（半径 = 灵界行者有效视距（有上限）+ 余量），以冤魂为准重新评估灵界行者的区块与
     * 实体追踪，并转发跳跃。
     */
    private static void refreshView(ServerPlayerEntity player, Session session) {
        ServerWorld world = session.world;
        int serverViewDistance = MathHelper.clamp(world.getServer().getPlayerManager().getViewDistance(), 2, 32);
        int viewDistance = MathHelper.clamp(player.getViewDistance(), 2, serverViewDistance);
        int radius = Math.min(viewDistance, MAX_TICKET_VIEW_DISTANCE) + TICKET_MARGIN;
        world.getChunkManager().addTicket(POSSESSION_TICKET, session.target.getChunkPos(), radius, session.target.getId());
        world.getChunkManager().updatePosition(player);
        relayJump(player, session);
    }

    /**
     * A teleported Wraith lands in a chunk that may not tick entities yet, and vanilla only syncs positions from
     * ticking chunks, so the Spiritualist's view would wait on the old spot. The jump is sent straight away instead.
     * 被传送的冤魂可能落在尚未更新实体的区块里，而原版只在会更新实体的区块同步位置，灵界行者的画面会停在原处。这里直接发送这次跳跃。
     */
    private static void relayJump(ServerPlayerEntity player, Session session) {
        Vec3d pos = session.target.getPos();
        if (session.lastTargetPos != null && pos.squaredDistanceTo(session.lastTargetPos) > JUMP_DISTANCE_SQUARED) {
            player.networkHandler.sendPacket(new EntityPositionS2CPacket(session.target));
        }
        session.lastTargetPos = pos;
    }

    /** Lets the view and tracking settle back on the body after a possession. / 附身结束后让视野与追踪回到身体。 */
    private static void resettle(ServerWorld world) {
        Iterator<Map.Entry<UUID, Integer>> iterator = RESETTLING.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(entry.getKey());
            if (player == null) {
                iterator.remove();
                continue;
            }
            if (player.getServerWorld() != world || SESSIONS.containsKey(entry.getKey())) {
                continue;
            }
            world.getChunkManager().updatePosition(player);
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                iterator.remove();
            } else {
                entry.setValue(remaining);
            }
        }
    }

    // ---- Helpers / 工具 ----

    /** Locks that refuse a start and end a running possession. / 拒绝开始并会结束进行中附身的各类锁定。 */
    private static boolean canAct(ServerPlayerEntity player) {
        return GameFunctions.isPlayerPlayingAndAlive(player)
                && !SwallowedPlayerComponent.isPlayerSwallowed(player)
                && !EngineerStunnedPlayerComponent.KEY.get(player).isStunned()
                && !SparkWitchCompat.isControlExpertStunned(player)
                && !SparkTraitsDroneCompat.isRoleSkillBlocked(player)
                && !SparkTraitsDroneCompat.isLastStandPending(player);
    }

    /** Any active Wraith, promoted forms included (owner 2026-10-07). / 任意活跃冤魂，含晋升形态（所有者 2026-10-07）。 */
    private static boolean isPossessable(@Nullable ServerPlayerEntity target, ServerWorld world) {
        return target != null
                && !target.isRemoved()
                && !target.isSpectator()
                && target.getServerWorld() == world
                && SparkWitchCompat.isWraithActive(target);
    }

    private static void sendState(ServerPlayerEntity player, SpiritPossessionStateS2CPacket packet) {
        if (ServerPlayNetworking.canSend(player, SpiritPossessionStateS2CPacket.ID)) {
            ServerPlayNetworking.send(player, packet);
        }
    }

    private static final class Session {
        private final UUID spiritualistUuid;
        private final ServerPlayerEntity target;
        private final ServerWorld world;
        private final UUID roundId;
        /** True when the possession started its own projection. / 附身自行开启了出窍时为 true。 */
        private final boolean startedFromBody;
        private int remainingTicks = SpiritPossessionRules.MAX_POSSESSION_TICKS;
        private @Nullable Vec3d lastTargetPos;

        private Session(UUID spiritualistUuid, ServerPlayerEntity target, ServerWorld world, UUID roundId,
                        boolean startedFromBody) {
            this.spiritualistUuid = spiritualistUuid;
            this.target = target;
            this.world = world;
            this.roundId = roundId;
            this.startedFromBody = startedFromBody;
        }
    }
}
