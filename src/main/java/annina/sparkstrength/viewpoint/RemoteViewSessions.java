package annina.sparkstrength.viewpoint;

import annina.sparkstrength.SparkStrength;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * Session bookkeeping of one remote-viewpoint feature (Bomber drone piloting, Spiritualist Wraith possession): the
 * viewer's camera sits on an anchor entity while the motionless body stays put, and the server streams chunks and
 * entities around that anchor ({@link RemoteViewpoint}). One instance per feature, so the two never share sessions,
 * throttles or resettles. The feature keeps its own rules (who may start, what ends a session, what its client is told)
 * and calls in here for the parts that are the same:
 * <ul>
 *   <li>sessions by viewer uuid, and the start throttle;</li>
 *   <li>the tick: offline viewers are dropped, every live session of the world goes back to the feature, which ends it
 *   or calls {@link #refreshView};</li>
 *   <li>the view refresh: an expiring chunk ticket around the anchor and a re-evaluation of the viewer's chunks and
 *   tracking (the motionless body sends no movement packets, so nothing else would re-evaluate them);</li>
 *   <li>after {@link #close}, {@link #RESETTLE_TICKS} of re-evaluation so the view settles back on the body;</li>
 *   <li>cleanup on disconnect, world unload and server stop.</li>
 * </ul>
 * Server thread only.
 * 单个远程视点功能（炸弹客无人机驾驶、灵界行者附身冤魂）的会话簿记：观看者的镜头位于锚点实体上，静止的身体留在原地，服务器围绕
 * 该锚点推送区块与实体（见 RemoteViewpoint）。每个功能各有一个实例，两者从不共用会话、防刷记录或视野回归。功能自行保留规则
 * （谁能开始、什么会结束会话、告知客户端什么），只把相同的部分交给这里：
 * 按观看者 uuid 记录会话与开始防刷；每刻丢弃离线观看者，并把本世界的每个存活会话交回功能，由功能结束会话或调用 refreshView；
 * 视野刷新：锚点周围一张会过期的区块票据，以及重新评估观看者的区块与追踪（静止的身体不发送移动包，否则无人重新评估）；
 * close 之后持续 RESETTLE_TICKS 刻重新评估，使视野回到身体；断线、世界卸载与服务器停止时清理。仅限服务器线程。
 */
public final class RemoteViewSessions<S extends RemoteViewSessions.Session> {
    /** Body-view re-evaluation after a session ends. / 会话结束后以身体为准重新评估视野的时长。 */
    public static final int RESETTLE_TICKS = 40;
    /**
     * Cap on the view radius used for the anchor's chunk ticket: the maps are small, so a wider ring would only load
     * (or generate) terrain far outside the play area. Ticket radius = min(view, cap) + margin, must stay <= 32.
     * 锚点区块票据使用的视距上限：地图很小，更大的范围只会加载（甚至生成）游玩区域外的地形。票据半径 = min(视距, 上限) + 余量，须 <= 32。
     */
    public static final int MAX_TICKET_VIEW_DISTANCE = 8;
    /** Extra ticket rings beyond the view so edge chunks are fully loaded before sending. / 视距外额外的票据圈，确保边缘区块发送前已完全加载。 */
    public static final int TICKET_MARGIN = 2;

    /**
     * Refreshed every tick around the anchor, so its surroundings are loaded and tick entities (tracker position
     * updates only run in entity-ticking chunks) whatever else keeps them loaded; positions the anchor left expire on
     * their own (fail-safe, no bookkeeping). Argument: the anchor's entity id.
     * 每刻在锚点周围刷新，使其周围区块无论有无其他票据都会加载并处理实体（追踪器只在处理实体的区块中发送位置更新）；锚点离开的位置
     * 会自行过期（无需记录，失效安全）。参数为锚点实体 id。
     */
    private final ChunkTicketType<Integer> ticket;
    private final Map<UUID, S> sessions = new HashMap<>();
    /** Viewer uuid -> remaining post-session re-evaluation ticks. / 观看者 uuid -> 会话结束后剩余的重新评估刻数。 */
    private final Map<UUID, Integer> resettling = new HashMap<>();
    /** Player uuid -> server tick of its last processed start. / 玩家 uuid -> 上次被处理的开始请求所在的服务器刻。 */
    private final Map<UUID, Integer> lastStart = new HashMap<>();

    /** {@code ticketName}: the chunk ticket's path under the mod id. / ticketName：区块票据在模组 id 下的路径。 */
    public RemoteViewSessions(String ticketName) {
        this.ticket = ChunkTicketType.create(SparkStrength.MOD_ID + ":" + ticketName, Integer::compare, 40);
    }

    /**
     * Disconnect, world unload and server stop. A disconnected viewer gets no packet; its session is dropped.
     * 断线、世界卸载与服务器停止。断线的观看者不发包，其会话被直接丢弃。
     */
    public void registerCleanup() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID uuid = handler.player.getUuid();
            drop(uuid);
            resettling.remove(uuid);
            lastStart.remove(uuid);
        });
        ServerWorldEvents.UNLOAD.register((server, world) -> {
            for (S session : List.copyOf(sessions.values())) {
                if (session.world() == world) {
                    drop(session.viewerUuid());
                }
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            sessions.clear();
            resettling.clear();
            lastStart.clear();
        });
    }

    // ---- Sessions / 会话 ----

    public @Nullable S session(UUID viewerUuid) {
        return sessions.get(viewerUuid);
    }

    public boolean has(UUID viewerUuid) {
        return !sessions.isEmpty() && sessions.containsKey(viewerUuid);
    }

    public @Nullable S find(Predicate<? super S> match) {
        for (S session : sessions.values()) {
            if (match.test(session)) {
                return session;
            }
        }
        return null;
    }

    /**
     * Spam guard: false for a start closer than {@code cooldownTicks} to the viewer's last processed one, otherwise
     * true and this start is remembered.
     * 防刷：距该观看者上次被处理的开始请求不足 cooldownTicks 时返回 false，否则返回 true 并记录本次请求。
     */
    public boolean acceptStart(UUID viewerUuid, int now, int cooldownTicks) {
        Integer last = lastStart.get(viewerUuid);
        if (last != null && now - last >= 0 && now - last < cooldownTicks) {
            return false;
        }
        lastStart.put(viewerUuid, now);
        return true;
    }

    /** A new session; a resettle still running for the viewer stops. / 开始新会话；该观看者仍在进行的视野回归随之停止。 */
    public void open(S session) {
        sessions.put(session.viewerUuid(), session);
        resettling.remove(session.viewerUuid());
    }

    /**
     * Ends the viewer's session, if any: releases it and lets the view settle back on the body. Returns the session so
     * the feature can finish its own end (packets, cooldowns), or null when there was none.
     * 结束该观看者的会话（若有）：释放会话并让视野回到身体。返回该会话供功能完成自己的结束流程（发包、冷却），没有会话时返回 null。
     */
    public @Nullable S close(UUID viewerUuid) {
        S session = sessions.remove(viewerUuid);
        if (session != null) {
            session.release();
            resettling.put(viewerUuid, RESETTLE_TICKS);
        }
        return session;
    }

    /** Ends without a resettle (viewer offline, world going). / 不经视野回归直接结束（观看者离线、世界卸载）。 */
    public void drop(UUID viewerUuid) {
        S session = sessions.remove(viewerUuid);
        if (session != null) {
            session.release();
        }
    }

    // ---- View centre / 视野中心 ----

    /**
     * The viewer's session while its anchor can centre the view: the anchor is alive in the viewer's world, and the
     * viewer is no spectator and still its own server camera (NoellesRoles Taotie swallow / Jester fake death take it).
     * Null means "use vanilla", so any failure reverts the view by itself on the next tick. Hot path: O(1) with no
     * session.
     * 锚点可作为视野中心时返回该观看者的会话：锚点存活且与观看者同一世界，观看者不是旁观者且服务端镜头仍是自身（NoellesRoles 饕餮
     * 吞噬/小丑假死会接管镜头）。null 表示使用原版行为，因此任何失效都会在下一刻自动恢复视野。热路径：无会话时 O(1)。
     */
    public @Nullable S viewing(@Nullable ServerPlayerEntity viewer) {
        if (sessions.isEmpty() || viewer == null) {
            return null;
        }
        S session = sessions.get(viewer.getUuid());
        if (session == null) {
            return null;
        }
        Entity anchor = session.anchor();
        if (anchor.isRemoved() || anchor.getWorld() != viewer.getWorld() || viewer.isSpectator()
                || viewer.getCameraEntity() != viewer) {
            return null;
        }
        return session;
    }

    // ---- Tick / 每刻 ----

    /**
     * One world tick: drops sessions whose viewer went offline, hands every live session of {@code world} to
     * {@code live} (which ends it or calls {@link #refreshView}), then advances the resettles.
     * 单个世界的一刻：丢弃观看者已离线的会话，把本世界每个存活会话交给 live（由其结束会话或调用 refreshView），再推进视野回归。
     */
    public void tick(ServerWorld world, BiConsumer<ServerPlayerEntity, S> live) {
        if (!sessions.isEmpty()) {
            for (S session : List.copyOf(sessions.values())) {
                if (session.world() != world) {
                    continue;
                }
                ServerPlayerEntity viewer = world.getServer().getPlayerManager().getPlayer(session.viewerUuid());
                if (viewer == null) {
                    drop(session.viewerUuid());
                    continue;
                }
                live.accept(viewer, session);
            }
        }
        if (!resettling.isEmpty()) {
            resettle(world);
        }
    }

    /**
     * One tick of the far view: refresh the anchor's chunk ticket (radius = the viewer's effective view distance,
     * capped, plus a margin) and re-evaluate the viewer's chunks and tracking around the anchor.
     * 远距离视野的单刻处理：刷新锚点区块票据（半径 = 观看者有效视距（有上限）+ 余量），并以锚点为准重新评估观看者的区块与实体追踪。
     */
    public void refreshView(ServerPlayerEntity viewer, S session) {
        ServerWorld world = session.world();
        Entity anchor = session.anchor();
        int serverViewDistance = MathHelper.clamp(world.getServer().getPlayerManager().getViewDistance(), 2, 32);
        int viewDistance = MathHelper.clamp(viewer.getViewDistance(), 2, serverViewDistance);
        int radius = Math.min(viewDistance, MAX_TICKET_VIEW_DISTANCE) + TICKET_MARGIN;
        world.getChunkManager().addTicket(ticket, anchor.getChunkPos(), radius, anchor.getId());
        world.getChunkManager().updatePosition(viewer);
    }

    /** Lets the view and tracking settle back on the body after a session. / 会话结束后让视野与追踪回到身体。 */
    private void resettle(ServerWorld world) {
        Iterator<Map.Entry<UUID, Integer>> iterator = resettling.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(entry.getKey());
            if (player == null) {
                iterator.remove();
                continue;
            }
            if (player.getServerWorld() != world || sessions.containsKey(entry.getKey())) {
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

    /**
     * One viewer's session: who views, in which world, from which anchor. Features extend it with their own state.
     * 单个观看者的会话：谁在观看、位于哪个世界、从哪个锚点观看。各功能在此基础上扩展自己的状态。
     */
    public abstract static class Session {
        private final UUID viewerUuid;
        private final ServerWorld world;

        protected Session(UUID viewerUuid, ServerWorld world) {
            this.viewerUuid = viewerUuid;
            this.world = world;
        }

        public final UUID viewerUuid() {
            return viewerUuid;
        }

        public final ServerWorld world() {
            return world;
        }

        /** The entity the view is centred on, fixed for the session. / 视野所围绕的实体，整个会话期间不变。 */
        public abstract Entity anchor();

        /**
         * Undoes what the session marked, once, whenever it goes (closed or dropped). Nothing by default.
         * 会话离开时（结束或丢弃）撤销其所做的标记，仅一次。默认不做任何事。
         */
        protected void release() {
        }
    }
}
