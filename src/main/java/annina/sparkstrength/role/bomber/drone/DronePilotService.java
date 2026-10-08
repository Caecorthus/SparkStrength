package annina.sparkstrength.role.bomber.drone;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.compat.SparkTraitsCompat;
import annina.sparkstrength.compat.SparkTraitsDroneCompat;
import annina.sparkstrength.compat.SparkWitchCompat;
import annina.sparkstrength.component.engineer.EngineerStunnedPlayerComponent;
import annina.sparkstrength.entity.DroneEntity;
import annina.sparkstrength.network.drone.DronePilotActionC2SPacket;
import annina.sparkstrength.network.drone.DronePilotCorrectionS2CPacket;
import annina.sparkstrength.network.drone.DronePilotMoveC2SPacket;
import annina.sparkstrength.network.drone.DronePilotStateS2CPacket;
import annina.sparkstrength.tablet.TabletStateService;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-authoritative pilot sessions (one drone per pilot, one pilot per drone).
 * 服务器权威的驾驶会话（每名驾驶者一架无人机，每架无人机一名驾驶者）。
 *
 * <ul>
 *   <li>{@link #start}: the sender is alive, playing and in survival, standing on the ground with its own camera, its
 *   REAL role is Bomber, the tablet is in its main hand, and the drone is one of its own drones in its world, of the
 *   current round, not falling or empty; the opening lock is over and the sender is neither Engineer- nor SparkWitch
 *   Control-Expert-stunned nor SparkTraits killer-interaction- or role-skill-blocked. A start for the drone already
 *   piloted is a no-op, and starts closer than {@link #START_COOLDOWN_TICKS} to the previous one are ignored. Any
 *   previous session ends first.
 *   Success sets {@link DroneEntity#setPilotUuid} and sends a start state; a refusal sends an end state with the reason.
 *   发送者存活、在局内且为生存模式，站在地面上且镜头为自身，真实身份为炸弹客，主手持平板；无人机属于发送者、位于其所在世界、
 *   属于本回合、未下坠且仍有电；开局锁已结束，且发送者未被工程师或 SparkWitch 控场专家眩晕、未被 SparkTraits 禁止杀手交互或
 *   封锁职业技能。
 *   请求驾驶正在驾驶的无人机时不做任何事，距上次请求不足 START_COOLDOWN_TICKS 的请求被忽略。先结束旧会话；
 *   成功时设置驾驶者并发送开始状态，拒绝时发送带原因的结束状态。</li>
 *   <li>{@link #move}: only from the current pilot for its drone, at most {@link #MAX_MOVES_PER_TICK} per tick;
 *   {@code DroneEntity#applyPilotStep} caps and collides the step, and a rejected step is answered with a correction.
 *   只接受当前驾驶者对其无人机的移动，每刻最多 MAX_MOVES_PER_TICK 次；applyPilotStep 负责限速与碰撞，被修正时回发纠正包。</li>
 *   <li>{@link #action}: only FIRE (other bytes are rejected); it re-validates the session, then
 *   {@code DroneCombatService#fire}. 只接受 FIRE（其他字节一律拒绝）；先重新校验会话再调用 fire。</li>
 *   <li>{@link #exit}: ends the session and leaves the drone hovering where it is (its own packet id, so role-skill
 *   payload blockers never trap the pilot). 结束会话，无人机原地悬停（独立包 id，职业技能包拦截不会困住驾驶者）。</li>
 *   <li>{@link #tick}: ends sessions whose conditions fail (death, tablet left the main hand, drone removed/falling,
 *   stun, skill block, camera taken, round end). A pilot is always the drone's owner, so a disconnect ends silently
 *   and the drone then loses its owner (see {@code DroneEntity}: grenade drone falls and breaks, bomb drone fizzles).
 *   条件失效时结束会话（死亡、平板离开主手、无人机移除/下坠、眩晕、技能封锁、镜头被接管、回合结束）。驾驶者必然是无人机主人，
 *   因此断线时静默结束，随后无人机失去主人（见 DroneEntity：投弹无人机坠落损毁，炸弹无人机哑火消失）。</li>
 * </ul>
 *
 * <p>Unlimited range (the whole play area): while a session lives, an expiring chunk ticket refreshed every tick keeps
 * the drone's surroundings loaded and entity-ticking (tracker position updates only run in entity-ticking chunks), and
 * the {@code mixin/bomber/DronePilot*} mixins centre the pilot's chunk view, chunk batches and entity tracking on
 * {@link #droneOf}. The motionless body sends no movement packets, so the pilot's tracking is re-evaluated every tick
 * here, and for {@link #RESETTLE_TICKS} after the session so the view settles back on the body.
 * 无限距离（整个游玩区域）：会话期间每刻刷新一张会过期的区块票据，保持无人机周围区块加载并处理实体（追踪器只在处理实体的区块中
 * 发送位置更新）；mixin/bomber/DronePilot* 以 droneOf 为中心计算驾驶者的区块视野、区块批次与实体追踪。静止的身体不会发送移动包，
 * 因此这里每刻重新评估驾驶者的追踪，并在会话结束后继续 RESETTLE_TICKS 刻，使视野回到身体。</p>
 */
public final class DronePilotService {
    /**
     * Moves accepted per pilot per server tick: the client sends one per tick, the slack absorbs network jitter.
     * 每名驾驶者每服务器刻接受的移动包数：客户端每刻发送一次，余量用于吸收网络抖动。
     */
    public static final int MAX_MOVES_PER_TICK = 2;
    /**
     * Cap on the view radius used for the drone's chunk ticket: the maps are small, so a wider ring would only load
     * (or generate) terrain far outside the play area. Ticket radius = min(view, cap) + margin, must stay <= 32.
     * 无人机区块票据使用的视距上限：地图很小，更大的范围只会加载（甚至生成）游玩区域外的地形。票据半径 = min(视距, 上限) + 余量，须 <= 32。
     */
    public static final int MAX_TICKET_VIEW_DISTANCE = 8;
    /** Extra ticket rings beyond the view so edge chunks are fully loaded before sending. / 视距外额外的票据圈，确保边缘区块发送前已完全加载。 */
    public static final int TICKET_MARGIN = 2;
    /** Body-view re-evaluation after a session ends. / 会话结束后以身体为准重新评估视野的时长。 */
    public static final int RESETTLE_TICKS = 40;
    /** Minimum server ticks between two processed start requests of one player. / 同一玩家两次被处理的开始请求之间的最少服务器刻数。 */
    public static final int START_COOLDOWN_TICKS = 10;
    /**
     * Refreshed every tick while piloting; positions the drone left behind expire on their own (fail-safe, no
     * bookkeeping). Argument: the drone's entity id.
     * 驾驶期间每刻刷新；无人机离开的位置会自行过期（无需记录，失效安全）。参数为无人机实体 id。
     */
    private static final ChunkTicketType<Integer> DRONE_TICKET =
            ChunkTicketType.create(SparkStrength.MOD_ID + ":drone", Integer::compare, 40);

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    /** Pilot uuid -> remaining post-session re-evaluation ticks. / 驾驶者 uuid -> 会话结束后剩余的重新评估刻数。 */
    private static final Map<UUID, Integer> RESETTLING = new HashMap<>();
    /** Player uuid -> server tick of its last processed start. / 玩家 uuid -> 上次被处理的开始请求所在的服务器刻。 */
    private static final Map<UUID, Integer> LAST_START = new HashMap<>();
    private static boolean registered;

    private DronePilotService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerTickEvents.END_WORLD_TICK.register(DronePilotService::tick);
        // A disconnected pilot gets no packet. The pilot is the owner, so the drone then counts as owner-lost
        // (DroneEntity: grenade drone falls and breaks, bomb drone fizzles); only an EXIT leaves it hovering.
        // 断线的驾驶者不发包。驾驶者即主人，因此无人机随后视为失去主人（DroneEntity：投弹无人机坠落损毁，炸弹无人机哑火）；
        // 只有主动退出才会让无人机原地悬停。
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            drop(handler.player.getUuid());
            RESETTLING.remove(handler.player.getUuid());
            LAST_START.remove(handler.player.getUuid());
        });
        ServerWorldEvents.UNLOAD.register((server, world) -> dropWorld(world));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            SESSIONS.clear();
            RESETTLING.clear();
            LAST_START.clear();
        });
    }

    public static void start(ServerPlayerEntity player, int entityId) {
        if (player == null) {
            return;
        }
        Session current = SESSIONS.get(player.getUuid());
        if (current != null && current.drone.getId() == entityId) {
            // Already piloting it: nothing to restart or re-sync. / 已在驾驶该无人机：无需重启或重新同步。
            return;
        }
        // Spam guard: every processed start re-validates, may end a session and syncs the tablet.
        // 防刷：每次被处理的开始请求都会重新校验、可能结束会话并同步平板。
        int now = player.getServer().getTicks();
        Integer last = LAST_START.get(player.getUuid());
        if (last != null && now - last >= 0 && now - last < START_COOLDOWN_TICKS) {
            return;
        }
        LAST_START.put(player.getUuid(), now);
        // Silent: the start or refusal sent below supersedes the old session on the client.
        // 静默结束：下面发送的开始或拒绝状态会在客户端取代旧会话。
        Session previous = SESSIONS.remove(player.getUuid());
        if (previous != null) {
            release(previous);
        }
        ServerWorld world = player.getServerWorld();
        DroneEntity drone = world.getEntityById(entityId) instanceof DroneEntity found ? found : null;
        DronePilotEndReason refusal = refusal(player, world, drone);
        if (refusal != null) {
            sendState(player, new DronePilotStateS2CPacket(-1, (byte) 0, (byte) refusal.wire()));
            if (previous != null) {
                RESETTLING.put(player.getUuid(), RESETTLE_TICKS);
                TabletStateService.syncTo(player);
            }
            return;
        }
        drone.setPilotUuid(player.getUuid());
        Session session = new Session(player.getUuid(), world, drone);
        SESSIONS.put(player.getUuid(), session);
        RESETTLING.remove(player.getUuid());
        refreshView(player, session);
        sendState(player, new DronePilotStateS2CPacket(drone.getId(), (byte) drone.kind().wire(), (byte) 0));
        TabletStateService.syncTo(player);
    }

    public static void move(ServerPlayerEntity player, DronePilotMoveC2SPacket packet) {
        if (player == null || packet == null) {
            return;
        }
        Session session = SESSIONS.get(player.getUuid());
        // Moves still in flight after an end, or for another drone, are dropped silently.
        // 会话结束后仍在途中的移动包或指向其他无人机的移动包直接丢弃。
        if (session == null || session.drone.getId() != packet.entityId() || session.drone.isRemoved()
                || player.getServerWorld() != session.world || !session.acceptMove(session.world.getTime())) {
            return;
        }
        DroneEntity drone = session.drone;
        if (!Double.isFinite(packet.x()) || !Double.isFinite(packet.y()) || !Double.isFinite(packet.z())
                || !Float.isFinite(packet.yaw()) || !Float.isFinite(packet.pitch())) {
            sendCorrection(player, drone);
            return;
        }
        boolean accepted = drone.applyPilotStep(
                new Vec3d(packet.x(), packet.y(), packet.z()),
                MathHelper.wrapDegrees(packet.yaw()),
                MathHelper.clamp(packet.pitch(), -90.0F, 90.0F),
                packet.moving()
        );
        if (!accepted) {
            sendCorrection(player, drone);
        }
    }

    /** FIRE only; any other action byte is rejected. / 只接受 FIRE；其他动作字节一律拒绝。 */
    public static void action(ServerPlayerEntity player, int entityId, byte action) {
        if (player == null || action != DronePilotActionC2SPacket.FIRE) {
            return;
        }
        Session session = SESSIONS.get(player.getUuid());
        if (session == null || session.drone.getId() != entityId) {
            return;
        }
        // Same checks as the tick, so a click racing a stun, death or skill block never fires.
        // 与 tick 相同的校验，避免与眩晕、死亡或技能封锁竞态时开火。
        DronePilotEndReason reason = endReason(player, session);
        if (reason != null) {
            end(player, reason);
        } else {
            DroneCombatService.fire(session.drone, player);
        }
    }

    /** Leave the drone (it keeps hovering); stale exits for another drone are ignored. / 离开无人机（无人机继续悬停）；指向其他无人机的过期退出包被忽略。 */
    public static void exit(ServerPlayerEntity player, int entityId) {
        if (player == null) {
            return;
        }
        Session session = SESSIONS.get(player.getUuid());
        if (session != null && session.drone.getId() == entityId) {
            end(player, DronePilotEndReason.EXIT);
        }
    }

    /** End the player's session (no-op if none) and tell its client why. / 结束该玩家的会话（无会话则忽略）并告知原因。 */
    public static void end(ServerPlayerEntity player, DronePilotEndReason reason) {
        if (player == null) {
            return;
        }
        Session session = SESSIONS.remove(player.getUuid());
        if (session == null) {
            return;
        }
        release(session);
        RESETTLING.put(player.getUuid(), RESETTLE_TICKS);
        sendState(player, new DronePilotStateS2CPacket(-1, (byte) session.drone.kind().wire(), (byte) reason.wire()));
        TabletStateService.syncTo(player);
    }

    /**
     * End whatever session pilots this drone (drone destroyed/detonated/depleted). Callers removing a drone call this
     * first, so the pilot's camera returns to the body before the entity disappears.
     * 结束驾驶此无人机的会话。移除无人机的调用方须先调用此方法，使驾驶者的视角在实体消失前回到身体。
     */
    public static void endFor(DroneEntity drone, DronePilotEndReason reason) {
        if (drone == null || drone.getWorld().isClient()) {
            return;
        }
        Session session = sessionOf(drone);
        if (session == null) {
            drone.setPilotUuid(null);
            return;
        }
        ServerPlayerEntity pilot = session.world.getServer().getPlayerManager().getPlayer(session.pilotUuid);
        if (pilot != null) {
            end(pilot, reason);
        } else {
            drop(session.pilotUuid);
        }
    }

    public static boolean isPiloting(ServerPlayerEntity player) {
        return player != null && SESSIONS.containsKey(player.getUuid());
    }

    /** The drone this player's session controls, unchecked; null when not piloting. / 该玩家会话所控制的无人机（不做校验）；未驾驶时为 null。 */
    public static @Nullable DroneEntity pilotedDrone(ServerPlayerEntity player) {
        Session session = player == null ? null : SESSIONS.get(player.getUuid());
        return session == null ? null : session.drone;
    }

    /**
     * View-centre query for the {@code mixin/bomber/DronePilot*} streaming mixins: the piloted drone only while it is
     * alive in the pilot's world, the pilot is alive and playing, not a spectator, and still its own camera (NoellesRoles
     * Taotie swallow / Jester fake death take the server camera). Null means "use vanilla", so any failure reverts the
     * view by itself on the next tick. Hot path: O(1) with no sessions.
     * 供 mixin/bomber/DronePilot* 流式加载 mixin 查询视野中心：仅当无人机存活且与驾驶者同一世界、驾驶者存活且在局内、不是旁观者、
     * 镜头仍是自身时（NoellesRoles 饕餮吞噬/小丑假死会接管服务端镜头）返回所驾驶的无人机。null 表示使用原版行为，因此任何失效都会在
     * 下一刻自动恢复视野。热路径：无会话时 O(1)。
     */
    public static @Nullable DroneEntity droneOf(ServerPlayerEntity pilot) {
        if (SESSIONS.isEmpty() || pilot == null) {
            return null;
        }
        Session session = SESSIONS.get(pilot.getUuid());
        if (session == null) {
            return null;
        }
        DroneEntity drone = session.drone;
        if (drone.isRemoved() || drone.getWorld() != pilot.getWorld() || pilot.isSpectator()
                || pilot.getCameraEntity() != pilot || !GameFunctions.isPlayerPlayingAndAlive(pilot)) {
            return null;
        }
        return drone;
    }

    public static void tick(ServerWorld world) {
        if (!SESSIONS.isEmpty()) {
            for (Session session : List.copyOf(SESSIONS.values())) {
                if (session.world != world) {
                    continue;
                }
                ServerPlayerEntity pilot = world.getServer().getPlayerManager().getPlayer(session.pilotUuid);
                if (pilot == null) {
                    drop(session.pilotUuid);
                    continue;
                }
                DronePilotEndReason reason = endReason(pilot, session);
                if (reason != null) {
                    end(pilot, reason);
                } else {
                    refreshView(pilot, session);
                }
            }
        }
        if (!RESETTLING.isEmpty()) {
            resettle(world);
        }
    }

    private static @Nullable DronePilotEndReason refusal(
            ServerPlayerEntity player, ServerWorld world, @Nullable DroneEntity drone
    ) {
        if (!pilotAlive(player)) {
            return DronePilotEndReason.PILOT_DOWN;
        }
        // The body sends no movement packets while piloting, so it must not be left floating (anti-fly kick), and
        // another mod must not hold its camera. / 驾驶期间身体不发送移动包，因此不能悬空（防飞行踢出），镜头也不能被其他模组占用。
        if (!DroneService.isRealBomber(player) || !player.isOnGround() || player.getCameraEntity() != player) {
            return DronePilotEndReason.UNAVAILABLE;
        }
        if (!holdsTablet(player)) {
            return DronePilotEndReason.NO_TABLET;
        }
        if (isStunned(player)) {
            return DronePilotEndReason.STUNNED;
        }
        if (SparkTraitsCompat.isKillerInteractionBlocked(player)) {
            return DronePilotEndReason.UNAVAILABLE;
        }
        if (SparkTraitsDroneCompat.isRoleSkillBlocked(player)) {
            return DronePilotEndReason.SKILL_BLOCKED;
        }
        // An admin cooldown clear of this drone's kind lifts the lock for that player (the owner check follows).
        // 管理员清除该型号冷却后，该玩家不再受开局锁限制（随后仍校验主人）。
        if (drone == null ? DroneService.openingRemaining(world) > 0
                : DroneService.openingRemaining(player, drone.kind()) > 0) {
            return DronePilotEndReason.OPENING;
        }
        UUID roundId = DroneService.currentRoundId(world);
        if (drone == null || drone.isRemoved() || drone.getWorld() != world || roundId == null
                || !roundId.equals(drone.roundId()) || !player.getUuid().equals(drone.ownerUuid())) {
            return DronePilotEndReason.UNAVAILABLE;
        }
        if (drone.pilotUuid() != null && !drone.pilotUuid().equals(player.getUuid())) {
            return DronePilotEndReason.UNAVAILABLE;
        }
        if (drone.state() == DroneState.FALLING || drone.charge() <= 0) {
            return DronePilotEndReason.DEPLETED;
        }
        return null;
    }

    /** Why a live session must end now, or null to keep it. / 当前会话须结束的原因；为 null 表示继续。 */
    private static @Nullable DronePilotEndReason endReason(ServerPlayerEntity pilot, Session session) {
        DroneEntity drone = session.drone;
        UUID roundId = DroneService.currentRoundId(session.world);
        if (roundId == null || !roundId.equals(drone.roundId())) {
            return DronePilotEndReason.ROUND_OVER;
        }
        if (drone.isRemoved()) {
            // Removers should have called endFor with the precise reason; this is the fallback.
            // 移除方本应先以准确原因调用 endFor；此处为兜底。
            return DronePilotEndReason.DESTROYED;
        }
        if (!pilotAlive(pilot)) {
            return DronePilotEndReason.PILOT_DOWN;
        }
        if (pilot.getServerWorld() != session.world || pilot.getCameraEntity() != pilot || !DroneService.isRealBomber(pilot)
                || !pilot.getUuid().equals(drone.ownerUuid()) || !pilot.getUuid().equals(drone.pilotUuid())) {
            return DronePilotEndReason.UNAVAILABLE;
        }
        if (!holdsTablet(pilot)) {
            return DronePilotEndReason.NO_TABLET;
        }
        if (isStunned(pilot)) {
            return DronePilotEndReason.STUNNED;
        }
        if (SparkTraitsCompat.isKillerInteractionBlocked(pilot)) {
            return DronePilotEndReason.UNAVAILABLE;
        }
        // SparkTraits role-skill block (also covers NoellesRoles' silenced killer). / SparkTraits 职业技能封锁（也涵盖 NoellesRoles 被沉默的杀手）。
        if (SparkTraitsDroneCompat.isRoleSkillBlocked(pilot)) {
            return DronePilotEndReason.SKILL_BLOCKED;
        }
        if (drone.state() == DroneState.FALLING) {
            return DronePilotEndReason.DEPLETED;
        }
        return null;
    }

    /**
     * Engineer stun or SparkWitch Control Expert stun. The CE stun's payload deny-list leaves drone moves and exit open,
     * so the session itself must end here. / 工程师眩晕或 SparkWitch 控场专家眩晕。控场专家的数据包拦截放行无人机移动与退出，
     * 因此须在此结束会话本身。
     */
    private static boolean isStunned(ServerPlayerEntity player) {
        return EngineerStunnedPlayerComponent.KEY.get(player).isStunned()
                || SparkWitchCompat.isControlExpertStunned(player);
    }

    private static boolean pilotAlive(ServerPlayerEntity player) {
        return player.isAlive()
                && GameFunctions.isPlayerPlayingAndAlive(player)
                && GameFunctions.isPlayerAliveAndSurvival(player);
    }

    /** The main-hand stack is the selected hotbar slot. / 主手物品即当前选中的快捷栏槽位。 */
    private static boolean holdsTablet(ServerPlayerEntity player) {
        return player.getMainHandStack().isOf(SparkStrengthItems.tablet());
    }

    private static @Nullable Session sessionOf(DroneEntity drone) {
        UUID pilotUuid = drone.pilotUuid();
        Session session = pilotUuid == null ? null : SESSIONS.get(pilotUuid);
        if (session != null && session.drone == drone) {
            return session;
        }
        for (Session candidate : SESSIONS.values()) {
            if (candidate.drone == drone) {
                return candidate;
            }
        }
        return null;
    }

    /** End without a packet (pilot offline). / 不发包地结束（驾驶者已离线）。 */
    private static void drop(UUID pilotUuid) {
        Session session = SESSIONS.remove(pilotUuid);
        if (session != null) {
            release(session);
        }
    }

    private static void dropWorld(ServerWorld world) {
        for (Session session : List.copyOf(SESSIONS.values())) {
            if (session.world == world) {
                drop(session.pilotUuid);
            }
        }
    }

    /** The drone ticket simply expires; only the pilot mark is cleared. / 无人机票据自行过期，这里只清除驾驶者标记。 */
    private static void release(Session session) {
        if (session.pilotUuid.equals(session.drone.pilotUuid())) {
            session.drone.setPilotUuid(null);
        }
    }

    /**
     * One tick of the unlimited-range view: refresh the drone's chunk ticket (radius = the pilot's effective view
     * distance, capped, plus a margin) and re-evaluate the pilot's entity tracking around the drone.
     * 无限距离视野的单刻处理：刷新无人机区块票据（半径 = 驾驶者有效视距（有上限）+ 余量），并以无人机为准重新评估驾驶者的实体追踪。
     */
    private static void refreshView(ServerPlayerEntity pilot, Session session) {
        DroneEntity drone = session.drone;
        if (drone.isRemoved()) {
            return;
        }
        int serverViewDistance = MathHelper.clamp(session.world.getServer().getPlayerManager().getViewDistance(), 2, 32);
        int viewDistance = MathHelper.clamp(pilot.getViewDistance(), 2, serverViewDistance);
        int radius = Math.min(viewDistance, MAX_TICKET_VIEW_DISTANCE) + TICKET_MARGIN;
        session.world.getChunkManager().addTicket(DRONE_TICKET, drone.getChunkPos(), radius, drone.getId());
        session.world.getChunkManager().updatePosition(pilot);
    }

    /** Lets the view and tracking settle back on the body after a session. / 会话结束后让视野与追踪回到身体。 */
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

    private static void sendState(ServerPlayerEntity player, DronePilotStateS2CPacket packet) {
        if (ServerPlayNetworking.canSend(player, DronePilotStateS2CPacket.ID)) {
            ServerPlayNetworking.send(player, packet);
        }
    }

    private static void sendCorrection(ServerPlayerEntity player, DroneEntity drone) {
        if (ServerPlayNetworking.canSend(player, DronePilotCorrectionS2CPacket.ID)) {
            ServerPlayNetworking.send(player,
                    new DronePilotCorrectionS2CPacket(drone.getId(), drone.getX(), drone.getY(), drone.getZ()));
        }
    }

    private static final class Session {
        private final UUID pilotUuid;
        private final ServerWorld world;
        private final DroneEntity drone;
        private long moveTick = Long.MIN_VALUE;
        private int movesThisTick;

        private Session(UUID pilotUuid, ServerWorld world, DroneEntity drone) {
            this.pilotUuid = pilotUuid;
            this.world = world;
            this.drone = drone;
        }

        private boolean acceptMove(long now) {
            if (now != moveTick) {
                moveTick = now;
                movesThisTick = 0;
            }
            return ++movesThisTick <= MAX_MOVES_PER_TICK;
        }
    }
}
