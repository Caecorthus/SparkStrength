package annina.sparkstrength.client.role.attendant.flashlight;

import annina.sparkstrength.role.attendant.FlashlightBeamRules;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.doctor4t.wathe.client.WatheClient;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Render-thread cache of flashlight receiver meshes, one {@link VertexBuffer} per 16x16x16 section. Sections are meshed
 * lazily when a light's spill cone first reaches them (a few per frame, nearest first), kept in LRU order, marked dirty
 * on client block changes (stale geometry keeps drawing until the rebuild, so a door never flickers dark) and dropped
 * with their chunk, on world change and on resource reload (atlas UVs change).
 * 手电筒受光网格的渲染线程缓存，每个 16x16x16 区段一个 {@link VertexBuffer}。光源溢光锥首次覆盖区段时才按需构建
 * （每帧少量、由近及远），按 LRU 顺序保留；客户端方块变化时标记为脏（重建前继续绘制旧几何，开门不会闪黑），
 * 并随所在区块卸载、世界切换与资源重载（图集 UV 改变）一起丢弃。
 */
final class FlashlightReceiverCache {
    private static final int MAX_SECTIONS = 128;
    private static final int MAX_BUILDS_PER_FRAME = 2;
    private static final long BUILD_BUDGET_NANOS = 4_000_000L;
    private static final int INITIAL_ALLOCATOR_BYTES = 256 * 1024;
    /** Bounding-sphere radius of a section. / 区段包围球半径。 */
    private static final double SECTION_RADIUS = Math.sqrt(3.0) * 8.0;
    /** Sections outside the camera frustum build after every visible one. / 视锥外的区段排在所有可见区段之后构建。 */
    private static final double OFF_SCREEN_PRIORITY = 1.0e9;
    /**
     * wathe relocates sections below this Y as moving scenery while the train runs (its Sodium and vanilla section
     * renderers both split train from scenery here); their real positions are not where they are drawn.
     * 列车行驶时 wathe 将此 Y 以下的区段作为移动风景重新定位渲染（其 Sodium 与原版区段渲染都以此为界），
     * 这些区段的真实位置并非其绘制位置。
     */
    static final int WATHE_SCENERY_TOP_Y = 64;

    private final Long2ObjectLinkedOpenHashMap<Section> sections = new Long2ObjectLinkedOpenHashMap<>();
    private final List<LongArrayList> lightSections = new ArrayList<>();
    private final Long2DoubleOpenHashMap buildPriority = new Long2DoubleOpenHashMap();
    private final LongArrayList buildOrder = new LongArrayList();
    private final FlashlightReceiverMesher mesher = new FlashlightReceiverMesher();
    private @Nullable BufferAllocator allocator;
    private long frame;

    private static final class Section {
        private @Nullable VertexBuffer buffer;
        private boolean dirty;
        private long lastUsedFrame;
    }

    /**
     * Render thread, once per frame with lights: finds the sections inside each light's spill cone and builds missing
     * or dirty ones within the frame budget.
     * 渲染线程，有光源的每帧调用一次：找出每个光源溢光锥内的区段，并在每帧预算内构建缺失或过期的区段。
     */
    void prepare(ClientWorld world, List<FlashlightLight> lights, @Nullable Frustum frustum) {
        frame++;
        while (lightSections.size() < lights.size()) {
            lightSections.add(new LongArrayList());
        }
        buildPriority.clear();
        boolean sceneryRelocated = WatheClient.isTrainMoving();
        for (int index = 0; index < lights.size(); index++) {
            LongArrayList keys = lightSections.get(index);
            keys.clear();
            collectConeSections(world, lights.get(index), sceneryRelocated, keys);
            Vec3d origin = lights.get(index).origin();
            for (int i = 0; i < keys.size(); i++) {
                long key = keys.getLong(i);
                Section section = sections.getAndMoveToLast(key);
                if (section != null) {
                    section.lastUsedFrame = frame;
                    if (!section.dirty) {
                        continue;
                    }
                }
                double priority = centerDistanceSquared(key, origin)
                        + (isVisible(frustum, key) ? 0.0 : OFF_SCREEN_PRIORITY);
                if (!buildPriority.containsKey(key) || priority < buildPriority.get(key)) {
                    buildPriority.put(key, priority);
                }
            }
        }
        buildPending(world);
        evictUnused();
    }

    /**
     * Render thread: draws this light's cached sections with {@code program} bound, setting ChunkOffset per section.
     * 渲染线程：在已绑定 {@code program} 时绘制该光源的缓存区段，并逐区段设置 ChunkOffset。
     */
    void draw(ShaderProgram program, int lightIndex, Vec3d camera, @Nullable Frustum frustum) {
        GlUniform chunkOffset = program.chunkOffset;
        if (chunkOffset == null || lightIndex >= lightSections.size()) {
            return;
        }
        LongArrayList keys = lightSections.get(lightIndex);
        for (int i = 0; i < keys.size(); i++) {
            long key = keys.getLong(i);
            Section section = sections.get(key);
            if (section == null || section.buffer == null || !isVisible(frustum, key)) {
                continue;
            }
            chunkOffset.set(
                    (float) ((ChunkSectionPos.unpackX(key) << 4) - camera.x),
                    (float) ((ChunkSectionPos.unpackY(key) << 4) - camera.y),
                    (float) ((ChunkSectionPos.unpackZ(key) << 4) - camera.z));
            chunkOffset.upload();
            section.buffer.bind();
            section.buffer.draw();
        }
    }

    /** A block changed: its section, plus the face neighbour across a section border. / 方块变化：所在区段及跨边界的相邻区段。 */
    void onBlockChanged(BlockPos pos) {
        int sectionX = ChunkSectionPos.getSectionCoord(pos.getX());
        int sectionY = ChunkSectionPos.getSectionCoord(pos.getY());
        int sectionZ = ChunkSectionPos.getSectionCoord(pos.getZ());
        markDirty(sectionX, sectionY, sectionZ);
        int localX = pos.getX() & 15;
        int localY = pos.getY() & 15;
        int localZ = pos.getZ() & 15;
        if (localX == 0 || localX == 15) {
            markDirty(sectionX + (localX == 0 ? -1 : 1), sectionY, sectionZ);
        }
        if (localY == 0 || localY == 15) {
            markDirty(sectionX, sectionY + (localY == 0 ? -1 : 1), sectionZ);
        }
        if (localZ == 0 || localZ == 15) {
            markDirty(sectionX, sectionY, sectionZ + (localZ == 0 ? -1 : 1));
        }
    }

    /**
     * A chunk (re)arrived: its sections and the neighbouring columns' border faces may change.
     * 区块（重新）到达：其区段以及相邻列的边界面都可能变化。
     */
    void onChunkLoaded(ChunkPos pos) {
        for (Section section : sectionsNear(pos, 1)) {
            section.dirty = true;
        }
    }

    void onChunkUnloaded(ChunkPos pos) {
        var iterator = sections.long2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            long key = entry.getLongKey();
            if (ChunkSectionPos.unpackX(key) == pos.x && ChunkSectionPos.unpackZ(key) == pos.z) {
                close(entry.getValue());
                iterator.remove();
            }
        }
    }

    /** Render thread: frees every buffer and the mesh allocator. / 渲染线程：释放全部缓冲与网格分配器。 */
    void clear() {
        for (Section section : sections.values()) {
            close(section);
        }
        sections.clear();
        for (LongArrayList keys : lightSections) {
            keys.clear();
        }
        buildPriority.clear();
        if (allocator != null) {
            allocator.close();
            allocator = null;
        }
    }

    private void collectConeSections(ClientWorld world, FlashlightLight light, boolean sceneryRelocated,
                                     LongArrayList out) {
        Vec3d origin = light.origin();
        Vec3d direction = light.direction();
        double range = FlashlightBeamRules.RANGE_BLOCKS;
        int minX = ChunkSectionPos.getSectionCoord(origin.x - range);
        int maxX = ChunkSectionPos.getSectionCoord(origin.x + range);
        int minY = Math.max(world.getBottomSectionCoord(), ChunkSectionPos.getSectionCoord(origin.y - range));
        int maxY = Math.min(world.getTopSectionCoord() - 1, ChunkSectionPos.getSectionCoord(origin.y + range));
        int minZ = ChunkSectionPos.getSectionCoord(origin.z - range);
        int maxZ = ChunkSectionPos.getSectionCoord(origin.z + range);
        if (sceneryRelocated) {
            minY = Math.max(minY, ChunkSectionPos.getSectionCoord(WATHE_SCENERY_TOP_Y));
        }
        for (int sectionX = minX; sectionX <= maxX; sectionX++) {
            for (int sectionZ = minZ; sectionZ <= maxZ; sectionZ++) {
                if (!world.getChunkManager().isChunkLoaded(sectionX, sectionZ)) {
                    continue;
                }
                for (int sectionY = minY; sectionY <= maxY; sectionY++) {
                    double dx = (sectionX << 4) + 8.0 - origin.x;
                    double dy = (sectionY << 4) + 8.0 - origin.y;
                    double dz = (sectionZ << 4) + 8.0 - origin.z;
                    if (intersectsSpillCone(dx, dy, dz, direction)) {
                        out.add(ChunkSectionPos.asLong(sectionX, sectionY, sectionZ));
                    }
                }
            }
        }
    }

    /**
     * Conservative bounding-sphere test against the spill cone clipped to the beam range.
     * 以包围球对裁剪到射程的溢光锥做保守相交测试。
     */
    private static boolean intersectsSpillCone(double dx, double dy, double dz, Vec3d direction) {
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance > FlashlightBeamRules.RANGE_BLOCKS + SECTION_RADIUS) {
            return false;
        }
        if (distance <= SECTION_RADIUS) {
            return true;
        }
        double cos = (dx * direction.x + dy * direction.y + dz * direction.z) / distance;
        double angle = Math.acos(MathHelper.clamp(cos, -1.0, 1.0));
        return angle <= FlashlightBeamRules.SPILL_HALF_ANGLE_RADIANS + Math.asin(SECTION_RADIUS / distance);
    }

    private void buildPending(ClientWorld world) {
        if (buildPriority.isEmpty()) {
            return;
        }
        buildOrder.clear();
        buildOrder.addAll(buildPriority.keySet());
        buildOrder.sort((long left, long right) -> Double.compare(buildPriority.get(left), buildPriority.get(right)));
        long start = System.nanoTime();
        int built = 0;
        for (int i = 0; i < buildOrder.size() && built < MAX_BUILDS_PER_FRAME; i++) {
            if (built > 0 && System.nanoTime() - start > BUILD_BUDGET_NANOS) {
                break;
            }
            build(world, buildOrder.getLong(i));
            built++;
        }
        if (built > 0) {
            VertexBuffer.unbind();
        }
    }

    private void build(ClientWorld world, long key) {
        int sectionX = ChunkSectionPos.unpackX(key);
        int sectionZ = ChunkSectionPos.unpackZ(key);
        WorldChunk chunk = world.getChunkManager().getWorldChunk(sectionX, sectionZ);
        BuiltBuffer mesh = chunk == null ? null
                : mesher.build(world, chunk, sectionX, ChunkSectionPos.unpackY(key), sectionZ, allocator());
        Section section = sections.get(key);
        if (section == null) {
            section = new Section();
        }
        if (mesh == null) {
            close(section);
        } else {
            if (section.buffer == null) {
                section.buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            }
            section.buffer.bind();
            // upload() closes the BuiltBuffer, releasing the allocator for the next section.
            // upload() 会关闭 BuiltBuffer，从而释放分配器供下一个区段使用。
            section.buffer.upload(mesh);
        }
        section.dirty = false;
        section.lastUsedFrame = frame;
        sections.putAndMoveToLast(key, section);
    }

    /** Least recently used first; never evicts a section this frame still draws. / 先淘汰最久未用者；不淘汰本帧仍需绘制的区段。 */
    private void evictUnused() {
        while (sections.size() > MAX_SECTIONS) {
            Section oldest = sections.get(sections.firstLongKey());
            if (oldest.lastUsedFrame == frame) {
                return;
            }
            close(sections.removeFirst());
        }
    }

    private void markDirty(int sectionX, int sectionY, int sectionZ) {
        Section section = sections.get(ChunkSectionPos.asLong(sectionX, sectionY, sectionZ));
        if (section != null) {
            section.dirty = true;
        }
    }

    private List<Section> sectionsNear(ChunkPos pos, int radius) {
        List<Section> near = new ArrayList<>();
        var iterator = sections.long2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            long key = entry.getLongKey();
            if (Math.abs(ChunkSectionPos.unpackX(key) - pos.x) <= radius
                    && Math.abs(ChunkSectionPos.unpackZ(key) - pos.z) <= radius) {
                near.add(entry.getValue());
            }
        }
        return near;
    }

    private BufferAllocator allocator() {
        if (allocator == null) {
            allocator = new BufferAllocator(INITIAL_ALLOCATOR_BYTES);
        }
        return allocator;
    }

    private static boolean isVisible(@Nullable Frustum frustum, long key) {
        if (frustum == null) {
            return true;
        }
        double minX = ChunkSectionPos.unpackX(key) << 4;
        double minY = ChunkSectionPos.unpackY(key) << 4;
        double minZ = ChunkSectionPos.unpackZ(key) << 4;
        return frustum.isVisible(new Box(minX, minY, minZ, minX + 16.0, minY + 16.0, minZ + 16.0));
    }

    private static double centerDistanceSquared(long key, Vec3d origin) {
        double dx = (ChunkSectionPos.unpackX(key) << 4) + 8.0 - origin.x;
        double dy = (ChunkSectionPos.unpackY(key) << 4) + 8.0 - origin.y;
        double dz = (ChunkSectionPos.unpackZ(key) << 4) + 8.0 - origin.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static void close(Section section) {
        if (section.buffer != null) {
            section.buffer.close();
            section.buffer = null;
        }
    }
}
