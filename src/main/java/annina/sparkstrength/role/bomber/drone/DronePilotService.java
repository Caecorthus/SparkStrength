package annina.sparkstrength.role.bomber.drone;

import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.compat.SparkTraitsCompat;
import annina.sparkstrength.compat.SparkTraitsDroneCompat;
import annina.sparkstrength.entity.DroneEntity;
import annina.sparkstrength.network.drone.DronePilotActionC2SPacket;
import annina.sparkstrength.network.drone.DronePilotCorrectionS2CPacket;
import annina.sparkstrength.network.drone.DronePilotMoveC2SPacket;
import annina.sparkstrength.network.drone.DronePilotStateS2CPacket;
import annina.sparkstrength.role.AbilityLocks;
import annina.sparkstrength.role.AbilityLocks.Lock;
import annina.sparkstrength.role.partyanimal.PartygoerCarry;
import annina.sparkstrength.tablet.TabletStateService;
import annina.sparkstrength.viewpoint.RemoteViewSessions;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
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
 * <p>Unlimited range (the whole play area): the drone is this feature's remote viewpoint. While a session lives,
 * {@link RemoteViewSessions#refreshView} keeps the drone's surroundings loaded and re-evaluates the pilot's tracking
 * every tick (the motionless body sends no movement packets), the {@code mixin/viewpoint/RemoteViewpoint*} mixins
 * centre the pilot's chunk view, chunk batches and entity tracking on {@link #droneOf}, and after the session the view
 * resettles on the body.
 * 无限距离（整个游玩区域）：无人机就是本功能的远程视点。会话期间 RemoteViewSessions#refreshView 每刻保持无人机周围区块加载并
 * 重新评估驾驶者的追踪（静止的身体不发送移动包）；mixin/viewpoint/RemoteViewpoint* 以 droneOf 为中心计算驾驶者的区块视野、
 * 区块批次与实体追踪；会话结束后视野回到身体。</p>
 */
public final class DronePilotService {
    /**
     * Engineer stun or SparkWitch Control Expert stun. The CE stun's payload deny-list leaves drone moves and exit open,
     * so the session itself must end here. / 工程师眩晕或 SparkWitch 控场专家眩晕。控场专家的数据包拦截放行无人机移动与退出，
     * 因此须在此结束会话本身。
     */
    private static final Set<Lock> STUN_LOCKS = AbilityLocks.of(Lock.ENGINEER_STUN, Lock.CONTROL_EXPERT_STUN);
    /**
     * Moves accepted per pilot per server tick: the client sends one per tick, the slack absorbs network jitter.
     * 每名驾驶者每服务器刻接受的移动包数：客户端每刻发送一次，余量用于吸收网络抖动。
     */
    public static final int MAX_MOVES_PER_TICK = 2;
    /** Minimum server ticks between two processed start requests of one player. / 同一玩家两次被处理的开始请求之间的最少服务器刻数。 */
    public static final int START_COOLDOWN_TICKS = 10;

    /** Pilot sessions; the chunk ticket is {@code sparkstrength:drone}. / 驾驶会话；区块票据为 sparkstrength:drone。 */
    private static final RemoteViewSessions<Session> VIEWS = new RemoteViewSessions<>("drone");
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
        VIEWS.registerCleanup();
    }

    public static void start(ServerPlayerEntity player, int entityId) {
        if (player == null) {
            return;
        }
        Session current = VIEWS.session(player.getUuid());
        if (current != null && current.drone.getId() == entityId) {
            // Already piloting it: nothing to restart or re-sync. / 已在驾驶该无人机：无需重启或重新同步。
            return;
        }
        // Spam guard: every processed start re-validates, may end a session and syncs the tablet.
        // 防刷：每次被处理的开始请求都会重新校验、可能结束会话并同步平板。
        if (!VIEWS.acceptStart(player.getUuid(), player.getServer().getTicks(), START_COOLDOWN_TICKS)) {
            return;
        }
        // Silent: the start or refusal sent below supersedes the old session on the client; its resettle stops again
        // if the new session opens.
        // 静默结束：下面发送的开始或拒绝状态会在客户端取代旧会话；新会话开始时其视野回归随之停止。
        Session previous = VIEWS.close(player.getUuid());
        ServerWorld world = player.getServerWorld();
        DroneEntity drone = world.getEntityById(entityId) instanceof DroneEntity found ? found : null;
        DronePilotEndReason refusal = refusal(player, world, drone);
        if (refusal != null) {
            sendState(player, new DronePilotStateS2CPacket(-1, (byte) 0, (byte) refusal.wire()));
            if (previous != null) {
                TabletStateService.syncTo(player);
            }
            return;
        }
        drone.setPilotUuid(player.getUuid());
        Session session = new Session(player.getUuid(), world, drone);
        VIEWS.open(session);
        refreshView(player, session);
        sendState(player, new DronePilotStateS2CPacket(drone.getId(), (byte) drone.kind().wire(), (byte) 0));
        TabletStateService.syncTo(player);
    }

    public static void move(ServerPlayerEntity player, DronePilotMoveC2SPacket packet) {
        if (player == null || packet == null) {
            return;
        }
        Session session = VIEWS.session(player.getUuid());
        // Moves still in flight after an end, or for another drone, are dropped silently.
        // 会话结束后仍在途中的移动包或指向其他无人机的移动包直接丢弃。
        if (session == null || session.drone.getId() != packet.entityId() || session.drone.isRemoved()
                || player.getServerWorld() != session.world() || !session.acceptMove(session.world().getTime())) {
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
        Session session = VIEWS.session(player.getUuid());
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
        Session session = VIEWS.session(player.getUuid());
        if (session != null && session.drone.getId() == entityId) {
            end(player, DronePilotEndReason.EXIT);
        }
    }

    /** End the player's session (no-op if none) and tell its client why. / 结束该玩家的会话（无会话则忽略）并告知原因。 */
    public static void end(ServerPlayerEntity player, DronePilotEndReason reason) {
        if (player == null) {
            return;
        }
        Session session = VIEWS.close(player.getUuid());
        if (session == null) {
            return;
        }
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
        ServerPlayerEntity pilot = session.world().getServer().getPlayerManager().getPlayer(session.viewerUuid());
        if (pilot != null) {
            end(pilot, reason);
        } else {
            VIEWS.drop(session.viewerUuid());
        }
    }

    public static boolean isPiloting(ServerPlayerEntity player) {
        return player != null && VIEWS.has(player.getUuid());
    }

    /** The drone this player's session controls, unchecked; null when not piloting. / 该玩家会话所控制的无人机（不做校验）；未驾驶时为 null。 */
    public static @Nullable DroneEntity pilotedDrone(ServerPlayerEntity player) {
        Session session = player == null ? null : VIEWS.session(player.getUuid());
        return session == null ? null : session.drone;
    }

    /**
     * Remote-viewpoint anchor for {@code RemoteViewpoint}: the piloted drone while {@link RemoteViewSessions#viewing}
     * accepts it (drone alive in the pilot's world, pilot no spectator and still its own camera) and the pilot is alive
     * and playing. Null means "use vanilla", so any failure reverts the view by itself on the next tick. Hot path: O(1)
     * with no sessions.
     * 供 RemoteViewpoint 查询的远程视点锚点：RemoteViewSessions#viewing 接受（无人机存活且与驾驶者同一世界、驾驶者不是旁观者且
     * 镜头仍是自身）且驾驶者存活并在局内时返回所驾驶的无人机。null 表示使用原版行为，因此任何失效都会在下一刻自动恢复视野。
     * 热路径：无会话时 O(1)。
     */
    public static @Nullable DroneEntity droneOf(ServerPlayerEntity pilot) {
        Session session = VIEWS.viewing(pilot);
        return session != null && GameFunctions.isPlayerPlayingAndAlive(pilot) ? session.drone : null;
    }

    public static void tick(ServerWorld world) {
        VIEWS.tick(world, (pilot, session) -> {
            DronePilotEndReason reason = endReason(pilot, session);
            if (reason != null) {
                end(pilot, reason);
            } else {
                refreshView(pilot, session);
            }
        });
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
        if (AbilityLocks.isLocked(player, STUN_LOCKS)) {
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
        UUID roundId = DroneService.currentRoundId(session.world());
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
        // A Partygoer grabbed the body (a balloon-marked Bomber is prey): the payload lock only stops new sessions and
        // shots, so the live one ends here. / 派对客抓走了身体（被塞气球的炸弹客也是猎物）：数据包锁只拦新会话与开火，
        // 进行中的会话在此结束。
        if (PartygoerCarry.isCarried(pilot)) {
            return DronePilotEndReason.PILOT_DOWN;
        }
        if (pilot.getServerWorld() != session.world() || pilot.getCameraEntity() != pilot || !DroneService.isRealBomber(pilot)
                || !pilot.getUuid().equals(drone.ownerUuid()) || !pilot.getUuid().equals(drone.pilotUuid())) {
            return DronePilotEndReason.UNAVAILABLE;
        }
        if (!holdsTablet(pilot)) {
            return DronePilotEndReason.NO_TABLET;
        }
        if (AbilityLocks.isLocked(pilot, STUN_LOCKS)) {
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
        Session session = pilotUuid == null ? null : VIEWS.session(pilotUuid);
        if (session != null && session.drone == drone) {
            return session;
        }
        return VIEWS.find(candidate -> candidate.drone == drone);
    }

    /**
     * One tick of the unlimited-range view: the drone's chunk ticket and the pilot's tracking around it.
     * 无限距离视野的单刻处理：无人机区块票据，以及以无人机为准的驾驶者实体追踪。
     */
    private static void refreshView(ServerPlayerEntity pilot, Session session) {
        if (!session.drone.isRemoved()) {
            VIEWS.refreshView(pilot, session);
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

    private static final class Session extends RemoteViewSessions.Session {
        private final DroneEntity drone;
        private long moveTick = Long.MIN_VALUE;
        private int movesThisTick;

        private Session(UUID pilotUuid, ServerWorld world, DroneEntity drone) {
            super(pilotUuid, world);
            this.drone = drone;
        }

        @Override
        public Entity anchor() {
            return drone;
        }

        /** The drone ticket simply expires; only the pilot mark is cleared. / 无人机票据自行过期，这里只清除驾驶者标记。 */
        @Override
        protected void release() {
            if (viewerUuid().equals(drone.pilotUuid())) {
                drone.setPilotUuid(null);
            }
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
