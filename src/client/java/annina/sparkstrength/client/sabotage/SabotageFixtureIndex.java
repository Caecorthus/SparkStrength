package annina.sparkstrength.client.sabotage;

import annina.sparkstrength.SparkStrengthBlocks;
import annina.sparkstrength.sabotage.SabotageBlockScan;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Fixture positions of every loaded client chunk, so the 10-tick fixture refresh no longer re-reads 4,096 blocks per
 * section that holds a fixture. A chunk is indexed when it (re)loads, dropped when it unloads, and every client block
 * change in between ({@code World#onBlockChanged}, so admin placing and breaking too) updates it. As a safety net the
 * chunks of the scanned area are re-indexed every {@link #RESYNC_TICKS}. Render thread only.
 * 每个已加载客户端区块的固定装置位置，让每 10 tick 一次的刷新不再对含装置的每个区段重读 4096 个方块。区块（重新）加载时
 * 建立索引，卸载时丢弃，期间每次客户端方块变化（{@code World#onBlockChanged}，包括管理员放置与破坏）都会更新它。作为兜底，
 * 每 {@link #RESYNC_TICKS} tick 对扫描范围内的区块重新建立索引。仅在渲染线程使用。
 */
public final class SabotageFixtureIndex {
    static final int RESYNC_TICKS = 200;
    /** Same order as {@link SabotageBlockScan#scan}: chunk column, then y, z, x. / 与 scan 相同的顺序：区块列，再 y、z、x。 */
    private static final Comparator<BlockPos> SCAN_ORDER = Comparator
            .comparingInt((BlockPos pos) -> pos.getX() >> 4)
            .thenComparingInt(pos -> pos.getZ() >> 4)
            .thenComparingInt(BlockPos::getY)
            .thenComparingInt(BlockPos::getZ)
            .thenComparingInt(BlockPos::getX);

    private static final Long2ObjectOpenHashMap<Set<BlockPos>> CHUNKS = new Long2ObjectOpenHashMap<>();
    private static @Nullable ClientWorld world;

    private SabotageFixtureIndex() {
    }

    static void register() {
        ClientChunkEvents.CHUNK_LOAD.register(SabotageFixtureIndex::index);
        ClientChunkEvents.CHUNK_UNLOAD.register((unloadedFrom, chunk) -> {
            if (unloadedFrom == world && chunk != null) {
                CHUNKS.remove(chunk.getPos().toLong());
            }
        });
    }

    /** {@code World#onBlockChanged} on the client, render thread. / 客户端的 World#onBlockChanged，渲染线程。 */
    public static void onBlockChanged(ClientWorld changedIn, BlockPos pos, BlockState oldState, BlockState newState) {
        boolean was = SparkStrengthBlocks.isSabotageFixture(oldState);
        boolean is = SparkStrengthBlocks.isSabotageFixture(newState);
        if (was == is) {
            return;
        }
        bind(changedIn);
        long key = ChunkPos.toLong(ChunkSectionPos.getSectionCoord(pos.getX()), ChunkSectionPos.getSectionCoord(pos.getZ()));
        if (is) {
            CHUNKS.computeIfAbsent(key, ignored -> new HashSet<>()).add(pos.toImmutable());
            return;
        }
        Set<BlockPos> fixtures = CHUNKS.get(key);
        if (fixtures != null && fixtures.remove(pos) && fixtures.isEmpty()) {
            CHUNKS.remove(key);
        }
    }

    /**
     * The fixtures {@link SabotageBlockScan#scan} would find in {@code area} right now, in the same order.
     * 此刻 {@link SabotageBlockScan#scan} 在 {@code area} 中会找到的固定装置，顺序相同。
     */
    static List<SabotageClientState.Fixture> collect(ClientWorld in, Box area) {
        if (in != world || CHUNKS.isEmpty()) {
            return List.of();
        }
        List<BlockPos> found = new ArrayList<>();
        for (Long2ObjectMap.Entry<Set<BlockPos>> entry : CHUNKS.long2ObjectEntrySet()) {
            long key = entry.getLongKey();
            if (in.getChunk(ChunkPos.getPackedX(key), ChunkPos.getPackedZ(key), ChunkStatus.FULL, false) == null) {
                continue;
            }
            for (BlockPos pos : entry.getValue()) {
                if (SabotageBlockScan.covers(in, area, pos)) {
                    found.add(pos);
                }
            }
        }
        found.sort(SCAN_ORDER);
        List<SabotageClientState.Fixture> fixtures = new ArrayList<>(found.size());
        for (BlockPos pos : found) {
            BlockState state = in.getBlockState(pos);
            if (SparkStrengthBlocks.isSabotageFixture(state)) {
                fixtures.add(new SabotageClientState.Fixture(pos, state));
            }
        }
        return fixtures;
    }

    /** Re-indexes the loaded chunks under {@code area} from their blocks. / 按方块重新为 area 下的已加载区块建立索引。 */
    static void resync(ClientWorld in, Box area) {
        bind(in);
        int minX = ChunkSectionPos.getSectionCoord((int) Math.floor(area.minX));
        int maxX = ChunkSectionPos.getSectionCoord((int) Math.floor(area.maxX));
        int minZ = ChunkSectionPos.getSectionCoord((int) Math.floor(area.minZ));
        int maxZ = ChunkSectionPos.getSectionCoord((int) Math.floor(area.maxZ));
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                Chunk chunk = in.getChunk(cx, cz, ChunkStatus.FULL, false);
                if (chunk instanceof WorldChunk loaded) {
                    index(in, loaded);
                } else {
                    CHUNKS.remove(ChunkPos.toLong(cx, cz));
                }
            }
        }
    }

    /**
     * Disconnect: lets go of the old world. A world joined before this runs has rebound already and is kept.
     * 断开连接：放开旧世界。在此之前已加入的新世界已经重新绑定，予以保留。
     */
    static void release(@Nullable ClientWorld current) {
        if (current != world) {
            CHUNKS.clear();
            world = null;
        }
    }

    private static void index(ClientWorld loadedIn, @Nullable WorldChunk chunk) {
        if (chunk == null) {
            return;
        }
        bind(loadedIn);
        Set<BlockPos> fixtures = new HashSet<>();
        SabotageBlockScan.scanChunk(chunk, (pos, state) -> fixtures.add(pos));
        long key = chunk.getPos().toLong();
        if (fixtures.isEmpty()) {
            CHUNKS.remove(key);
        } else {
            CHUNKS.put(key, fixtures);
        }
    }

    /** A different world: its chunks are what loads next, so start empty. / 换了世界：接下来加载的是它的区块，从空开始。 */
    private static void bind(ClientWorld in) {
        if (in != world) {
            CHUNKS.clear();
            world = in;
        }
    }
}
