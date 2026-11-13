package annina.sparkstrength.role.bomber;

import annina.sparkstrength.network.bomber.TimedBombMarkersPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Security guard: delivers the planted timed-bomb traps (bed and tray) to the players who may see them and to nobody
 * else. The trap owner used to ride in the block entities' client NBT, which every chunk-tracking client receives, and
 * each plant or trigger sent a public block-entity update; now that NBT says nothing about the trap
 * ({@code TimedBomb*StateMixin}) and this class sends {@link TimedBombMarkersPayload} to "trap viewers" only
 * ({@link BomberTrapService#seesTimedBombTraps}, the rule the client's warning smoke already used). A player outside
 * that set never gets a packet from here, not even an empty one.
 * <p>Markers reach a viewer on a plant (and are taken back on a trigger or clear), when a chunk holding one is sent to
 * them, and as a scan of the loaded traps when they become a viewer (round start, Bomber disguise, join). The viewer
 * set is re-checked every tick while the world has traps or a viewer still holds some; every send re-checks the live
 * rule as well. Same shape as SparkTraits' blue-poison markers. Server thread only.
 * 安全闸门：把已埋设的定时炸弹陷阱（床与托盘）只发送给有权查看的玩家。埋设者原本随方块实体的客户端 NBT 下发，所有追踪该区块
 * 的客户端都能收到，且每次埋设或触发都会发送公开的方块实体更新；现在该 NBT 不再包含陷阱信息（TimedBomb*StateMixin），由本类
 * 仅向“陷阱观察者”（BomberTrapService#seesTimedBombTraps，即客户端警示烟雾原本使用的规则）发送 TimedBombMarkersPayload。
 * 不在该集合中的玩家不会从这里收到任何数据包，空包也没有。
 * <p>观察者在陷阱被埋设时收到标记（触发或清除时收回），在含有陷阱的区块发送给他们时收到，并在成为观察者时（开局、伪装成炸弹客、
 * 加入）通过扫描已加载的陷阱补齐。只要本世界有陷阱或仍有观察者持有标记，就每 tick 重新检查观察者集合；每次发送也会再次检查
 * 实时规则。结构与 SparkTraits 的蓝毒标记相同。仅在服务端线程使用。
 */
public final class TimedBombMarkerSync {
    /** Stale markers (a broken block, an unloaded chunk) are withdrawn within this many ticks. / 失效标记在此 tick 数内收回。 */
    private static final int VALIDATE_INTERVAL_TICKS = 10;
    private static final Map<UUID, ViewerState> VIEWERS = new HashMap<>();
    private static boolean registered;

    private TimedBombMarkerSync() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        // A new connection starts with an empty client store; the next tick enters the viewer again.
        // 新连接的客户端存储为空；下一 tick 会重新让其进入观察者集合。
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> VIEWERS.remove(handler.player.getUuid()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> VIEWERS.remove(handler.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> VIEWERS.clear());
        ServerTickEvents.END_WORLD_TICK.register(TimedBombMarkerSync::tick);
    }

    /** Whether the block entity holds a planted trap right now (server). / 该方块实体当前是否持有陷阱（服务端）。 */
    static boolean holdsTrap(BlockEntity blockEntity) {
        return blockEntity instanceof TimedBombTrapHolder holder && holder.sparkstrength$hasTimedBombTrap();
    }

    /**
     * A trap was planted ({@code present}) or cleared at {@code pos}. Replaces the public block-entity update: only
     * current viewers learn of a new trap, and only viewers that were told about a trap learn of its removal.
     * {@code pos} 处的陷阱被埋设或清除。它取代了公开的方块实体更新：只有当前观察者会得知新陷阱，也只有被告知过的观察者会得知其移除。
     */
    public static void onMarkerChanged(ServerWorld world, BlockPos pos, boolean present) {
        BlockPos key = pos.toImmutable();
        if (present) {
            for (ServerPlayerEntity player : world.getPlayers()) {
                if (!BomberTrapService.seesTimedBombTraps(player)) {
                    continue;
                }
                ViewerState state = VIEWERS.get(player.getUuid());
                if (state == null || state.enter(world.getRegistryKey())) {
                    enter(player, world);
                } else if (state.sent.add(key)) {
                    send(player, false, List.of(key), List.of());
                }
            }
            return;
        }
        for (Map.Entry<UUID, ViewerState> entry : VIEWERS.entrySet()) {
            ViewerState state = entry.getValue();
            if (state.world != world.getRegistryKey() || !state.sent.remove(key)) {
                continue;
            }
            ServerPlayerEntity viewer = world.getServer().getPlayerManager().getPlayer(entry.getKey());
            if (viewer != null) {
                send(viewer, false, List.of(), List.of(key));
            }
        }
    }

    /**
     * Called right after vanilla sent {@code chunk} to {@code player} (first tracking, respawn, dimension change).
     * Always re-sends that chunk's traps: a new client world starts with an empty store.
     * 在原版把 {@code chunk} 发送给 {@code player} 之后立即调用（首次追踪、重生、切换维度）。总会重新发送该区块的陷阱：
     * 新的客户端世界从空存储开始。
     */
    public static void onChunkSent(ServerPlayerEntity player, ServerWorld world, WorldChunk chunk) {
        if (!BomberTrapService.hasTrackedTraps(world)) {
            return;
        }
        List<BlockPos> traps = new ArrayList<>();
        for (BlockPos pos : BomberTrapService.trackedTraps(world)) {
            if (pos.getX() >> 4 == chunk.getPos().x && pos.getZ() >> 4 == chunk.getPos().z
                    && holdsTrap(chunk.getBlockEntities().get(pos))) {
                traps.add(pos);
            }
        }
        if (traps.isEmpty() || !BomberTrapService.seesTimedBombTraps(player)) {
            return;
        }
        ViewerState state = VIEWERS.get(player.getUuid());
        if (state == null || state.enter(world.getRegistryKey())) {
            enter(player, world);
            return;
        }
        state.sent.addAll(traps);
        send(player, false, traps, List.of());
    }

    /**
     * The viewer set follows the rule every tick while there is something to show or to take back; a player who stops
     * qualifying (role swap, disguise ends, Bomber disguise lost at death) has their store cleared.
     * 只要有可显示或可收回的内容，观察者集合就每 tick 跟随规则更新；不再符合条件的玩家（身份互换、伪装结束、死亡时失去炸弹客伪装）
     * 的存储会被清空。
     */
    private static void tick(ServerWorld world) {
        boolean traps = BomberTrapService.hasTrackedTraps(world);
        if (!traps && !anyViewerHolds(world)) {
            return;
        }
        for (ServerPlayerEntity player : world.getPlayers()) {
            boolean viewer = BomberTrapService.seesTimedBombTraps(player);
            ViewerState state = VIEWERS.get(player.getUuid());
            if (viewer) {
                if (state == null || state.enter(world.getRegistryKey())) {
                    enter(player, world);
                }
            } else if (state != null) {
                VIEWERS.remove(player.getUuid());
                // They held these markers as a viewer a tick ago; the reset only takes them back.
                // 这些标记是其一 tick 前作为观察者持有的；重置只是把它们收回。
                if (!state.sent.isEmpty()) {
                    send(player, true, List.of(), List.of());
                }
            }
        }
        if (world.getTime() % VALIDATE_INTERVAL_TICKS == 0) {
            withdrawStaleMarkers(world);
        }
    }

    /** Makes {@code player} a viewer of this world and sends every loaded trap from empty. / 让该玩家成为本世界的观察者，并从空开始发送所有已加载的陷阱。 */
    private static void enter(ServerPlayerEntity player, ServerWorld world) {
        ViewerState state = new ViewerState(world.getRegistryKey());
        VIEWERS.put(player.getUuid(), state);
        List<BlockPos> loaded = new ArrayList<>();
        for (BlockPos pos : BomberTrapService.trackedTraps(world)) {
            WorldChunk chunk = world.getChunkManager().getWorldChunk(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk != null && holdsTrap(chunk.getBlockEntities().get(pos))) {
                loaded.add(pos);
            }
        }
        state.sent.addAll(loaded);
        // Reset first: whatever an earlier world or connection left in the client store is not this world's.
        // 先重置：客户端存储中之前的世界或连接留下的内容不属于本世界。
        send(player, true, loaded, List.of());
    }

    /**
     * Safety net for traps that vanish without the setter (a broken block, a replaced block entity) and for chunks that
     * unload; also forgets those positions in {@link BomberTrapService}'s index.
     * 兜底处理未经设置方法而消失的陷阱（方块被破坏、方块实体被替换）以及卸载的区块；同时从 BomberTrapService 的索引中忘掉这些位置。
     */
    private static void withdrawStaleMarkers(ServerWorld world) {
        List<BlockPos> gone = new ArrayList<>();
        for (BlockPos pos : BomberTrapService.trackedTraps(world)) {
            WorldChunk chunk = world.getChunkManager().getWorldChunk(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk != null && !holdsTrap(chunk.getBlockEntities().get(pos))) {
                gone.add(pos);
            }
        }
        gone.forEach(pos -> BomberTrapService.forgetTrap(world, pos));
        Iterator<Map.Entry<UUID, ViewerState>> viewers = VIEWERS.entrySet().iterator();
        while (viewers.hasNext()) {
            Map.Entry<UUID, ViewerState> entry = viewers.next();
            ServerPlayerEntity viewer = world.getServer().getPlayerManager().getPlayer(entry.getKey());
            if (viewer == null) {
                viewers.remove();
                continue;
            }
            ViewerState state = entry.getValue();
            if (state.world != world.getRegistryKey() || state.sent.isEmpty()) {
                continue;
            }
            List<BlockPos> stale = new ArrayList<>();
            for (BlockPos pos : state.sent) {
                WorldChunk chunk = world.getChunkManager().getWorldChunk(pos.getX() >> 4, pos.getZ() >> 4);
                if (chunk == null || !holdsTrap(chunk.getBlockEntities().get(pos))) {
                    stale.add(pos);
                }
            }
            if (!stale.isEmpty()) {
                stale.forEach(state.sent::remove);
                send(viewer, false, List.of(), stale);
            }
        }
    }

    private static boolean anyViewerHolds(ServerWorld world) {
        for (ViewerState state : VIEWERS.values()) {
            if (state.world == world.getRegistryKey() && !state.sent.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static void send(ServerPlayerEntity player, boolean reset, List<BlockPos> added, List<BlockPos> removed) {
        if (!ServerPlayNetworking.canSend(player, TimedBombMarkersPayload.ID)) {
            return;
        }
        for (TimedBombMarkersPayload packet : TimedBombMarkersPayload.split(reset, added, removed)) {
            ServerPlayNetworking.send(player, packet);
        }
    }

    /** What one viewer's client currently holds, for the world it was sent in. / 某个观察者的客户端当前持有的标记及其所属世界。 */
    private static final class ViewerState {
        private RegistryKey<World> world;
        private final Set<BlockPos> sent = new HashSet<>();

        private ViewerState(RegistryKey<World> world) {
            this.world = world;
        }

        /** True when the viewer is now in another world: the caller re-enters it from empty. / 观察者已在另一世界时为 true：调用方从空重新进入。 */
        private boolean enter(RegistryKey<World> current) {
            if (world == current) {
                return false;
            }
            world = current;
            sent.clear();
            return true;
        }
    }
}
