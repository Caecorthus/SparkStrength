package annina.sparkstrength.client.role.attendant.flashlight;

import annina.sparkstrength.role.attendant.FlashlightBeamGeometry;
import annina.sparkstrength.role.attendant.FlashlightBeamRules;
import dev.doctor4t.wathe.block.CullingBlock;
import dev.doctor4t.wathe.block.GlassPanelBlock;
import dev.doctor4t.wathe.block.PrivacyBlock;
import net.minecraft.block.Block;
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
 * Client-thread voxel caster behind {@link FlashlightRayMap}. A block occludes where a ray meets its collision shape,
 * so open doors, fluids, plants and other non-colliding blocks let light through, while glass-like blocks transmit it
 * (see {@link #transmitsLight}). One shared instance: it keeps per-cast caches and is not thread-safe.
 * {@link FlashlightRayMap} 背后的客户端线程体素投射器。射线碰到方块碰撞箱即被遮挡，因此打开的门、流体、植物等
 * 无碰撞方块透光，玻璃类方块也透光（见 {@link #transmitsLight}）。全局单例，带每次投射的缓存，非线程安全。
 */
final class FlashlightRayCaster implements FlashlightBeamGeometry.VoxelVisitor, VoxelShapes.BoxConsumer {
    private static final BlockState AIR = Blocks.AIR.getDefaultState();
    private static final double RANGE = FlashlightBeamRules.RANGE_BLOCKS;

    private final BlockPos.Mutable pos = new BlockPos.Mutable();
    private final double[] direction = new double[3];

    private ClientWorld world;
    private int bottomY;
    private int topY;
    private int bottomSection;

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
    private int boxX;
    private int boxY;
    private int boxZ;
    private double boxHit;
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
        double hit = castRay(fromX, fromY, fromZ, dx / length, dy / length, dz / length, length);
        end();
        return hit < length;
    }

    private void begin(ClientWorld world) {
        this.world = world;
        bottomY = world.getBottomY();
        topY = world.getTopY();
        bottomSection = world.getBottomSectionCoord();
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
        return FlashlightBeamGeometry.walk(ox, oy, oz, dx, dy, dz, maxDistance, this);
    }

    @Override
    public double visit(int x, int y, int z, double enterDistance) {
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
        double hit;
        if (shape == VoxelShapes.fullCube()) {
            hit = enterDistance;
        } else {
            boxX = x;
            boxY = y;
            boxZ = z;
            boxHit = Double.POSITIVE_INFINITY;
            shape.forEachBox(this);
            hit = boxHit == Double.POSITIVE_INFINITY ? -1.0 : boxHit;
        }
        if (hit < 0.0 || transmitsLight(state)) {
            markOpen(x, y, z);
            return -1.0;
        }
        return hit;
    }

    /** Slab test of the current ray against one collision box of the visited block. / 当前射线与一个碰撞盒的平板测试。 */
    @Override
    public void consume(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        double near = Double.NEGATIVE_INFINITY;
        double far = Double.POSITIVE_INFINITY;

        double lo = boxX + minX - originX;
        double hi = boxX + maxX - originX;
        if (dirX == 0.0) {
            if (lo > 0.0 || hi < 0.0) {
                return;
            }
        } else {
            double a = lo / dirX;
            double b = hi / dirX;
            near = Math.max(near, Math.min(a, b));
            far = Math.min(far, Math.max(a, b));
        }

        lo = boxY + minY - originY;
        hi = boxY + maxY - originY;
        if (dirY == 0.0) {
            if (lo > 0.0 || hi < 0.0) {
                return;
            }
        } else {
            double a = lo / dirY;
            double b = hi / dirY;
            near = Math.max(near, Math.min(a, b));
            far = Math.min(far, Math.max(a, b));
        }

        lo = boxZ + minZ - originZ;
        hi = boxZ + maxZ - originZ;
        if (dirZ == 0.0) {
            if (lo > 0.0 || hi < 0.0) {
                return;
            }
        } else {
            double a = lo / dirZ;
            double b = hi / dirZ;
            near = Math.max(near, Math.min(a, b));
            far = Math.min(far, Math.max(a, b));
        }

        // An origin inside the box counts as a hit at 0, like a full cube. / 原点在碰撞盒内视为距离 0 命中，与整方块一致。
        double entry = Math.max(near, 0.0);
        if (far >= entry && entry < boxHit) {
            boxHit = entry;
        }
    }

    /**
     * Light passes glass-like blocks: anything drawn in the translucent layer plus cutout glass and panes (vanilla
     * glass, panes, Wathe glass panels). Wathe's hull is drawn translucent but is a wall, tinted glass blocks light,
     * and Wathe privacy glass blocks it while frosted.
     * 光可穿过玻璃类方块：半透明渲染层的方块，以及镂空渲染的玻璃与玻璃板（原版玻璃、玻璃板、Wathe 玻璃面板）。
     * Wathe 船体虽在半透明层渲染但属于墙体；染色玻璃遮光；Wathe 隐私玻璃雾化时遮光。
     */
    static boolean transmitsLight(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof CullingBlock || block instanceof TintedGlassBlock) {
            return false;
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
        if (y < bottomY || y >= topY) {
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
