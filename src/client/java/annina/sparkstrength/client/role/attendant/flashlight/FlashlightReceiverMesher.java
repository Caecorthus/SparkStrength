package annina.sparkstrength.client.role.attendant.flashlight;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Meshes the solid and cutout block models of one 16x16x16 section into receiver geometry for the flashlight shader:
 * section-relative position, atlas UV, tint and the quad's geometric normal. No lighting or AO is baked in; the
 * vertex alpha carries the block layer's cutout threshold instead. Model selection mirrors vanilla's
 * BlockModelRenderer (same rendering seed per face, same face culling), so the light lands on the exact terrain texels.
 * 将单个 16x16x16 区段内实心与镂空方块模型网格化为手电筒受光几何：区段相对坐标、图集 UV、着色与面几何法线。
 * 不烘焙光照与 AO；顶点 alpha 改为携带方块层的镂空阈值。模型选择与原版 BlockModelRenderer 一致
 * （每个面相同的渲染种子、相同的面剔除），因此光照恰好落在地形纹素上。
 */
final class FlashlightReceiverMesher {
    private static final Direction[] DIRECTIONS = Direction.values();
    /** BakedQuad vertex data uses POSITION_COLOR_TEXTURE_LIGHT_NORMAL: 8 ints per vertex. / 每个顶点 8 个 int。 */
    private static final int QUAD_STRIDE = 8;
    private static final int NO_LAYER = -1;
    /** Cutout thresholds of vanilla's rendertype_cutout(_mipped) shaders, as vertex alpha bytes. / 原版镂空阈值的字节值。 */
    private static final int SOLID_CUTOFF = 0;
    private static final int CUTOUT_MIPPED_CUTOFF = 128;
    private static final int CUTOUT_CUTOFF = 26;

    /** Section plus a one-block border on each face. / 区段及其各面外扩一格。 */
    private static final int PADDED = 18;

    private final Random random = Random.createLocal();
    private final BlockPos.Mutable pos = new BlockPos.Mutable();
    private final BlockPos.Mutable neighbor = new BlockPos.Mutable();
    /**
     * Block states of the section and its six face-adjacent border slabs (edges and corners unused), so face culling
     * reads an array instead of the world for every face.
     * 区段及其六个面相邻边界层的方块状态（棱与角不使用），使每个面的剔除判断读取数组而非世界。
     */
    private final BlockState[] padded = new BlockState[PADDED * PADDED * PADDED];

    /**
     * Render thread. Returns null when the section has no receiving face (all air, translucent or culled).
     * 渲染线程。区段没有受光面（全为空气、半透明或被剔除）时返回 null。
     */
    @Nullable
    BuiltBuffer build(ClientWorld world, WorldChunk chunk, int sectionX, int sectionY, int sectionZ,
                      BufferAllocator allocator) {
        int index = world.sectionCoordToIndex(sectionY);
        ChunkSection[] sections = chunk.getSectionArray();
        ChunkSection section = index >= 0 && index < sections.length ? sections[index] : null;
        if (section == null || section.isEmpty()) {
            return null;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        BlockRenderManager blockRenderManager = client.getBlockRenderManager();
        BlockColors blockColors = client.getBlockColors();
        BufferBuilder builder = new BufferBuilder(allocator, VertexFormat.DrawMode.QUADS,
                FlashlightShaders.RECEIVER_FORMAT);
        int originX = sectionX << 4;
        int originY = sectionY << 4;
        int originZ = sectionZ << 4;
        fillPadded(world, section, originX, originY, originZ);
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    BlockState state = padded[paddedIndex(x, y, z)];
                    if (state.isAir() || state.getRenderType() != BlockRenderType.MODEL) {
                        continue;
                    }
                    int cutoff = cutoff(RenderLayers.getBlockLayer(state));
                    if (cutoff == NO_LAYER) {
                        continue;
                    }
                    pos.set(originX + x, originY + y, originZ + z);
                    // Opaque full cube against opaque full cube is exactly the case where shouldDrawSide returns false
                    // (full culling faces on both sides); checking it first skips buried blocks cheaply.
                    // 不透明完整方块紧贴不透明完整方块时 shouldDrawSide 必然返回 false（两侧剔除面均完整）；先行判断可廉价跳过被埋住的方块。
                    boolean opaqueCube = state.isOpaqueFullCube(world, pos);
                    int culledFaces = 0;
                    if (opaqueCube) {
                        for (Direction direction : DIRECTIONS) {
                            if (neighborIsOpaqueCube(world, x, y, z, direction)) {
                                culledFaces |= 1 << direction.ordinal();
                            }
                        }
                        if (culledFaces == (1 << DIRECTIONS.length) - 1) {
                            continue;
                        }
                    }
                    BakedModel model = blockRenderManager.getModel(state);
                    long seed = state.getRenderingSeed(pos);
                    Vec3d offset = state.getModelOffset(world, pos);
                    float offsetX = x + (float) offset.x;
                    float offsetY = y + (float) offset.y;
                    float offsetZ = z + (float) offset.z;
                    for (Direction direction : DIRECTIONS) {
                        if ((culledFaces & 1 << direction.ordinal()) != 0) {
                            continue;
                        }
                        random.setSeed(seed);
                        List<BakedQuad> quads = model.getQuads(state, direction, random);
                        if (quads.isEmpty()) {
                            continue;
                        }
                        neighbor.set(pos, direction);
                        if (Block.shouldDrawSide(state, world, pos, direction, neighbor)) {
                            emit(builder, quads, state, world, blockColors, offsetX, offsetY, offsetZ, cutoff);
                        }
                    }
                    random.setSeed(seed);
                    emit(builder, model.getQuads(state, null, random), state, world, blockColors,
                            offsetX, offsetY, offsetZ, cutoff);
                }
            }
        }
        return builder.endNullable();
    }

    private void fillPadded(ClientWorld world, ChunkSection section, int originX, int originY, int originZ) {
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    padded[paddedIndex(x, y, z)] = section.getBlockState(x, y, z);
                }
            }
        }
        for (int a = 0; a < 16; a++) {
            for (int b = 0; b < 16; b++) {
                padded[paddedIndex(-1, a, b)] = worldState(world, originX - 1, originY + a, originZ + b);
                padded[paddedIndex(16, a, b)] = worldState(world, originX + 16, originY + a, originZ + b);
                padded[paddedIndex(a, -1, b)] = worldState(world, originX + a, originY - 1, originZ + b);
                padded[paddedIndex(a, 16, b)] = worldState(world, originX + a, originY + 16, originZ + b);
                padded[paddedIndex(a, b, -1)] = worldState(world, originX + a, originY + b, originZ - 1);
                padded[paddedIndex(a, b, 16)] = worldState(world, originX + a, originY + b, originZ + 16);
            }
        }
    }

    private BlockState worldState(ClientWorld world, int x, int y, int z) {
        return world.getBlockState(neighbor.set(x, y, z));
    }

    private boolean neighborIsOpaqueCube(ClientWorld world, int x, int y, int z, Direction direction) {
        BlockState state = padded[paddedIndex(x + direction.getOffsetX(), y + direction.getOffsetY(),
                z + direction.getOffsetZ())];
        return state.isOpaqueFullCube(world, neighbor.set(pos, direction));
    }

    /** x, y, z in [-1, 16]. / 坐标范围 [-1, 16]。 */
    private static int paddedIndex(int x, int y, int z) {
        return ((y + 1) * PADDED + z + 1) * PADDED + x + 1;
    }

    /** Translucent and tripwire layers are not receivers. / 半透明与绊线层不作为受光面。 */
    private static int cutoff(RenderLayer layer) {
        if (layer == RenderLayer.getSolid()) {
            return SOLID_CUTOFF;
        }
        if (layer == RenderLayer.getCutoutMipped()) {
            return CUTOUT_MIPPED_CUTOFF;
        }
        if (layer == RenderLayer.getCutout()) {
            return CUTOUT_CUTOFF;
        }
        return NO_LAYER;
    }

    private void emit(BufferBuilder builder, List<BakedQuad> quads, BlockState state, ClientWorld world,
                      BlockColors blockColors, float offsetX, float offsetY, float offsetZ, int cutoff) {
        for (BakedQuad quad : quads) {
            int tint = quad.hasColor() ? blockColors.getColor(state, world, pos, quad.getColorIndex()) : -1;
            int tintRed = tint >> 16 & 0xFF;
            int tintGreen = tint >> 8 & 0xFF;
            int tintBlue = tint & 0xFF;
            int[] data = quad.getVertexData();
            // Diagonal cross product: the true facing, also for rotated or cross-shaped models.
            // 对角线叉积：得到真实朝向，旋转模型与交叉模型也适用。
            float ax = x(data, 2) - x(data, 0);
            float ay = y(data, 2) - y(data, 0);
            float az = z(data, 2) - z(data, 0);
            float bx = x(data, 3) - x(data, 1);
            float by = y(data, 3) - y(data, 1);
            float bz = z(data, 3) - z(data, 1);
            float normalX = ay * bz - az * by;
            float normalY = az * bx - ax * bz;
            float normalZ = ax * by - ay * bx;
            float length = (float) Math.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
            if (length > 1.0e-6F) {
                normalX /= length;
                normalY /= length;
                normalZ /= length;
            } else {
                normalX = quad.getFace().getOffsetX();
                normalY = quad.getFace().getOffsetY();
                normalZ = quad.getFace().getOffsetZ();
            }
            for (int vertex = 0; vertex < 4; vertex++) {
                int base = vertex * QUAD_STRIDE;
                // Baked vertex colour is ABGR (RGBA bytes); usually white. / 烘焙顶点色为 ABGR（RGBA 字节），通常为白色。
                int baked = data[base + 3];
                builder.vertex(Float.intBitsToFloat(data[base]) + offsetX,
                                Float.intBitsToFloat(data[base + 1]) + offsetY,
                                Float.intBitsToFloat(data[base + 2]) + offsetZ)
                        .texture(Float.intBitsToFloat(data[base + 4]), Float.intBitsToFloat(data[base + 5]))
                        .color(tintRed * (baked & 0xFF) / 255,
                                tintGreen * (baked >> 8 & 0xFF) / 255,
                                tintBlue * (baked >> 16 & 0xFF) / 255,
                                cutoff)
                        .normal(normalX, normalY, normalZ);
            }
        }
    }

    private static float x(int[] data, int vertex) {
        return Float.intBitsToFloat(data[vertex * QUAD_STRIDE]);
    }

    private static float y(int[] data, int vertex) {
        return Float.intBitsToFloat(data[vertex * QUAD_STRIDE + 1]);
    }

    private static float z(int[] data, int vertex) {
        return Float.intBitsToFloat(data[vertex * QUAD_STRIDE + 2]);
    }
}
