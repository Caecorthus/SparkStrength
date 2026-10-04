package annina.sparkstrength.client.role.attendant.flashlight;

import annina.sparkstrength.role.attendant.FlashlightBeamGeometry;
import annina.sparkstrength.role.attendant.FlashlightBeamRules;
import dev.doctor4t.wathe.block.BarrierPanelBlock;
import dev.doctor4t.wathe.block.CullingBlock;
import dev.doctor4t.wathe.block.GlassPanelBlock;
import dev.doctor4t.wathe.block.PrivacyBlock;
import dev.doctor4t.wathe.client.WatheClient;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.TintedGlassBlock;
import net.minecraft.block.TransparentBlock;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Client-thread voxel caster behind {@link FlashlightRayMap} and the exact line-of-sight tests. A block occludes where
 * a ray meets its collision shape, so open doors, fluids, plants and other non-colliding blocks let light through, while
 * glass-like and invisible blocks transmit it (see {@link #transmitsLight}). Grid rays record where they leave the first
 * occluder ({@link FlashlightBeamGeometry.OccluderRun}); segment tests stop at the first entry. One shared instance:
 * it keeps per-cast caches and is not thread-safe.
 * {@link FlashlightRayMap} 与精确视线检测背后的客户端线程体素投射器。射线碰到方块碰撞箱即被遮挡，因此打开的门、
 * 流体、植物等无碰撞方块透光，玻璃类与不可见方块也透光（见 {@link #transmitsLight}）。网格射线记录离开首个遮挡体的
 * 距离；线段检测在首次进入时停止。全局单例，带每次投射的缓存，非线程安全。
 */
final class FlashlightRayCaster implements FlashlightBeamGeometry.VoxelVisitor, VoxelShapes.BoxConsumer {
    private static final BlockState AIR = Blocks.AIR.getDefaultState();
    private static final double RANGE = FlashlightBeamRules.RANGE_BLOCKS;
    private static final int MODE_NEAREST = 0;
    private static final int MODE_EXTEND = 1;
    private static final int MODE_OWN = 2;

    private final BlockPos.Mutable pos = new BlockPos.Mutable();
    private final double[] direction = new double[3];

    private ClientWorld world;
    private int bottomY;
    private int topY;
    private int bottomSection;
    private int sceneryTopY;

    private boolean chunkCached;
    private int chunkX;
    private int chunkZ;
    private ChunkSection[] sections;

    private boolean lightCached;
    private long lightPos;
    private int cachedBlockLight;
    private int cachedSkyLight;

    private double originX;
    private double originY;
    private double originZ;
    private double dirX;
    private double dirY;
    private double dirZ;
    private final FlashlightBeamGeometry.OccluderRun run = new FlashlightBeamGeometry.OccluderRun();
    private final double[] interval = new double[2];
    /** Grid casts measure the occluder depth; segment tests only need the first entry. / 网格投射测量遮挡深度。 */
    private boolean measureDepth;
    private int boxMode;
    private double boxX;
    private double boxY;
    private double boxZ;
    private double nearestEnter;
    private double nearestExit;
    private boolean extended;
    private double ownExit;
    private int lastOpenX;
    private int lastOpenY;
    private int lastOpenZ;

    /**
     * Casts the full grid for one pose into the given arrays (indexed {@code v * SIZE + u}).
     * 为一个姿态投射整张网格，结果写入以 v * SIZE + u 为索引的数组。
     */
    void castGrid(ClientWorld world, double ox, double oy, double oz, double[] basis,
                  float[] hitDistances, byte[] blockLight, byte[] skyLight) {
        begin(world);
        measureDepth = true;
        int size = FlashlightBeamGeometry.SIZE;
        for (int v = 0; v < size; v++) {
            for (int u = 0; u < size; u++) {
                FlashlightBeamGeometry.cellDirection(basis, u, v, direction);
                int cell = v * size + u;
                hitDistances[cell] = (float) castRay(ox, oy, oz, direction[0], direction[1], direction[2], RANGE);
                sampleLight(lastOpenX, lastOpenY, lastOpenZ);
                blockLight[cell] = (byte) cachedBlockLight;
                skyLight[cell] = (byte) cachedSkyLight;
            }
        }
        end();
    }

    /** True when the straight segment between two points crosses an occluder. / 两点间线段是否被遮挡。 */
    boolean isSegmentBlocked(ClientWorld world, double fromX, double fromY, double fromZ,
                             double toX, double toY, double toZ) {
        double dx = toX - fromX;
        double dy = toY - fromY;
        double dz = toZ - fromZ;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!(length > 1.0e-6)) {
            return false;
        }
        begin(world);
        measureDepth = false;
        double hit = castRay(fromX, fromY, fromZ, dx / length, dy / length, dz / length, length);
        end();
        return hit < length;
    }

    /**
     * How far a ray from the block centre along (dx, dy, dz) travels before leaving the block's own collision shape
     * (0 when the centre is outside it), so a block entity can be sampled just outside itself.
     * 从方块中心沿 (dx, dy, dz) 出发的射线离开该方块自身碰撞箱前经过的距离（中心不在碰撞箱内时为 0），
     * 使方块实体能在自身外侧采样。
     */
    double ownShapeExit(ClientWorld world, BlockPos blockPos, double dx, double dy, double dz) {
        BlockState state = world.getBlockState(blockPos);
        VoxelShape shape = state.getCollisionShape(world, blockPos);
        if (shape.isEmpty()) {
            return 0.0;
        }
        originX = blockPos.getX() + 0.5;
        originY = blockPos.getY() + 0.5;
        originZ = blockPos.getZ() + 0.5;
        dirX = dx;
        dirY = dy;
        dirZ = dz;
        boxX = blockPos.getX();
        boxY = blockPos.getY();
        boxZ = blockPos.getZ();
        ownExit = 0.0;
        boxMode = MODE_OWN;
        do {
            extended = false;
            shape.forEachBox(this);
        } while (extended);
        return ownExit;
    }

    private void begin(ClientWorld world) {
        this.world = world;
        bottomY = world.getBottomY();
        topY = world.getTopY();
        bottomSection = world.getBottomSectionCoord();
        // Relocated wathe scenery is not where it is drawn while the train runs, so rays pass its real blocks.
        // 列车行驶时 wathe 风景的绘制位置并非其真实位置，因此射线穿过其真实方块。
        sceneryTopY = WatheClient.isTrainMoving() ? FlashlightReceiverCache.WATHE_SCENERY_TOP_Y : Integer.MIN_VALUE;
        // Chunks and light may change between casts; caches only live for one cast.
        // 两次投射之间区块与光照可能变化，缓存只在单次投射内有效。
        chunkCached = false;
        sections = null;
        lightCached = false;
    }

    private void end() {
        world = null;
        sections = null;
    }

    private double castRay(double ox, double oy, double oz, double dx, double dy, double dz, double maxDistance) {
        originX = ox;
        originY = oy;
        originZ = oz;
        dirX = dx;
        dirY = dy;
        dirZ = dz;
        lastOpenX = (int) Math.floor(ox);
        lastOpenY = (int) Math.floor(oy);
        lastOpenZ = (int) Math.floor(oz);
        run.reset();
        double result = FlashlightBeamGeometry.walk(ox, oy, oz, dx, dy, dz, maxDistance, this);
        // The walk can end at range while an occluder run is still open. / 遮挡区间未结束时遍历也可能在射程处终止。
        return run.active() ? Math.min(result, run.exit()) : result;
    }

    @Override
    public double visit(int x, int y, int z, double enterDistance) {
        if (run.active()) {
            return continueRun(x, y, z, enterDistance);
        }
        BlockState state = blockState(x, y, z);
        if (state.isAir()) {
            markOpen(x, y, z);
            return -1.0;
        }
        pos.set(x, y, z);
        VoxelShape shape = state.getCollisionShape(world, pos);
        if (shape.isEmpty()) {
            markOpen(x, y, z);
            return -1.0;
        }
        boxX = x;
        boxY = y;
        boxZ = z;
        nearestEnter = Double.POSITIVE_INFINITY;
        if (shape == VoxelShapes.fullCube()) {
            consumeNearest(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
        } else {
            boxMode = MODE_NEAREST;
            shape.forEachBox(this);
        }
        if (nearestEnter == Double.POSITIVE_INFINITY || transmitsLight(state)) {
            markOpen(x, y, z);
            return -1.0;
        }
        if (!measureDepth) {
            return nearestEnter;
        }
        run.start(nearestEnter, nearestExit);
        extendWithin(shape);
        return run.capped() ? run.exit() : -1.0;
    }

    /** Next voxel of an open occluder run: extend it or close it. / 遮挡区间的下一个体素：延伸或结束。 */
    private double continueRun(int x, int y, int z, double enterDistance) {
        if (run.endsBefore(enterDistance)) {
            return run.exit();
        }
        BlockState state = blockState(x, y, z);
        if (state.isAir()) {
            return -1.0;
        }
        pos.set(x, y, z);
        VoxelShape shape = state.getCollisionShape(world, pos);
        if (shape.isEmpty() || transmitsLight(state)) {
            return -1.0;
        }
        boxX = x;
        boxY = y;
        boxZ = z;
        extendWithin(shape);
        return run.capped() ? run.exit() : -1.0;
    }

    /** Offers every box of the shape until none extends the run (boxes come in any order). / 反复提交各盒直到区间不再延伸。 */
    private void extendWithin(VoxelShape shape) {
        if (shape == VoxelShapes.fullCube()) {
            offer(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
            return;
        }
        boxMode = MODE_EXTEND;
        do {
            extended = false;
            shape.forEachBox(this);
        } while (extended && !run.capped());
    }

    @Override
    public void consume(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (boxMode == MODE_NEAREST) {
            consumeNearest(minX, minY, minZ, maxX, maxY, maxZ);
        } else if (boxMode == MODE_EXTEND) {
            offer(minX, minY, minZ, maxX, maxY, maxZ);
        } else if (rayBox(minX, minY, minZ, maxX, maxY, maxZ)
                && interval[0] <= ownExit + FlashlightBeamGeometry.OccluderRun.CONTACT_EPSILON && interval[1] > ownExit) {
            ownExit = interval[1];
            extended = true;
        }
    }

    /**
     * Slab test of the current ray against one box of the visited block; keeps the nearest. An origin inside the box
     * counts as a hit at 0. 当前射线与所访问方块的一个盒做平板测试并保留最近者；原点在盒内视为距离 0 命中。
     */
    private void consumeNearest(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (rayBox(minX, minY, minZ, maxX, maxY, maxZ) && interval[0] < nearestEnter) {
            nearestEnter = interval[0];
            nearestExit = interval[1];
        }
    }

    private void offer(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (rayBox(minX, minY, minZ, maxX, maxY, maxZ) && run.offer(interval[0], interval[1])) {
            extended = true;
        }
    }

    private boolean rayBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return FlashlightBeamGeometry.rayBox(originX, originY, originZ, dirX, dirY, dirZ,
                boxX + minX, boxY + minY, boxZ + minZ, boxX + maxX, boxY + maxY, boxZ + maxZ, interval);
    }

    /**
     * Light passes glass-like blocks: anything drawn in the translucent layer plus cutout glass and panes (vanilla
     * glass, panes, Wathe glass panels). Wathe's hull is drawn translucent but is a wall, tinted glass blocks light,
     * and Wathe privacy glass blocks it while frosted. Invisible collision blocks (vanilla barriers, Wathe barrier and
     * light-barrier panels) transmit too; block-entity blocks are excluded because their renderer draws them (Wathe
     * doors report MODEL and have a block entity, so they stay opaque either way).
     * 光可穿过玻璃类方块：半透明渲染层的方块，以及镂空渲染的玻璃与玻璃板（原版玻璃、玻璃板、Wathe 玻璃面板）。
     * Wathe 船体虽在半透明层渲染但属于墙体；染色玻璃遮光；Wathe 隐私玻璃雾化时遮光。不可见的碰撞方块（原版屏障、Wathe
     * 屏障面板与光屏障）同样透光；带方块实体的方块除外，因为它们由渲染器绘制（Wathe 门为 MODEL 且有方块实体，始终遮光）。
     */
    static boolean transmitsLight(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof CullingBlock || block instanceof TintedGlassBlock) {
            return false;
        }
        // Barrier panels switch to MODEL while builders view barriers; the beam should not change with that view.
        // 建造者查看屏障时屏障面板切换为 MODEL；光束不应随该视图改变。
        if (block instanceof BarrierPanelBlock
                || (state.getRenderType() == BlockRenderType.INVISIBLE && !state.hasBlockEntity())) {
            return true;
        }
        if (block instanceof PrivacyBlock && state.contains(PrivacyBlock.OPAQUE) && state.get(PrivacyBlock.OPAQUE)) {
            return false;
        }
        return block instanceof TransparentBlock
                || block instanceof PaneBlock
                || block instanceof GlassPanelBlock
                || RenderLayers.getBlockLayer(state) == RenderLayer.getTranslucent();
    }

    private void markOpen(int x, int y, int z) {
        lastOpenX = x;
        lastOpenY = y;
        lastOpenZ = z;
    }

    private BlockState blockState(int x, int y, int z) {
        if (y < bottomY || y >= topY || y < sceneryTopY) {
            return AIR;
        }
        int cx = x >> 4;
        int cz = z >> 4;
        if (!chunkCached || cx != chunkX || cz != chunkZ) {
            WorldChunk chunk = world.getChunkManager().getChunk(cx, cz, ChunkStatus.FULL, false);
            sections = chunk == null ? null : chunk.getSectionArray();
            chunkX = cx;
            chunkZ = cz;
            chunkCached = true;
        }
        if (sections == null) {
            return AIR;
        }
        int index = (y >> 4) - bottomSection;
        if (index < 0 || index >= sections.length) {
            return AIR;
        }
        ChunkSection section = sections[index];
        if (section == null || section.isEmpty()) {
            return AIR;
        }
        return section.getBlockState(x & 15, y & 15, z & 15);
    }

    private void sampleLight(int x, int y, int z) {
        long key = BlockPos.asLong(x, y, z);
        if (lightCached && key == lightPos) {
            return;
        }
        pos.set(x, y, z);
        cachedBlockLight = clampLight(world.getLightLevel(LightType.BLOCK, pos));
        cachedSkyLight = clampLight(world.getLightLevel(LightType.SKY, pos));
        lightPos = key;
        lightCached = true;
    }

    private static int clampLight(int level) {
        return Math.max(0, Math.min(15, level));
    }
}
