package annina.sparkstrength.client.role.attendant.flashlight;

import annina.sparkstrength.role.attendant.FlashlightBeamGeometry;
import annina.sparkstrength.role.attendant.FlashlightBeamRules;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * CPU-raycast occlusion grid for one flashlight: {@code SIZE x SIZE} rays over the tangent plane described by
 * {@link annina.sparkstrength.role.attendant.FlashlightBeamRules#gridCellTangent(int)}, cast from {@link #origin()}
 * along normalize(forward + tu*right + tv*up). It doubles as a shadow map for the shaders and as the line-of-sight
 * test for entity lighting, so light never passes through walls.
 * 单支手电筒的 CPU 射线遮挡网格：在切平面上投射 SIZE x SIZE 条射线。它既是着色器的阴影图，也是实体照明的视线检测，
 * 保证光不会穿墙。
 *
 * <p>GPU layout of {@link #texture()}: R = high byte and G = low byte of
 * {@code FlashlightBeamRules.encodeDistance(hitDistance)}, B = block light * 17, A = sky light * 17 (light sampled in
 * the open cell just before the hit). Texel (u, v) = grid cell (u, v); v grows along {@link #up()}.
 * GPU 布局：R/G 为命中距离 16 位编码的高/低字节，B/A 为命中前空气格的方块光/天空光 ×17；纹素 (u, v) 即网格单元。</p>
 *
 * <p>Lifetime: one map per tracked flashlight, recast in place on client ticks, so {@link #texture()} keeps the same
 * object (and GL id) until the light stops being tracked and the map is closed. Use a map only within the frame that
 * returned it. 生命周期：每支被追踪的手电一张图，在客户端 tick 中原地重投射，因此 texture() 在停止追踪并关闭前始终是
 * 同一对象（同一 GL id）。只在获得它的那一帧内使用。</p>
 */
public final class FlashlightRayMap implements AutoCloseable {
    public static final int SIZE = FlashlightBeamRules.RAY_GRID_SIZE;

    private final double[] basis = new double[FlashlightBeamGeometry.BASIS_LENGTH];
    private final float[] hitDistances = new float[FlashlightBeamGeometry.CELL_COUNT];
    private final byte[] blockLight = new byte[FlashlightBeamGeometry.CELL_COUNT];
    private final byte[] skyLight = new byte[FlashlightBeamGeometry.CELL_COUNT];
    private double originX;
    private double originY;
    private double originZ;
    private Vec3d origin = Vec3d.ZERO;
    private Vec3d forward = new Vec3d(0.0, 0.0, 1.0);
    private Vec3d right = new Vec3d(-1.0, 0.0, 0.0);
    private Vec3d up = new Vec3d(0.0, 1.0, 0.0);
    private boolean hasCast;
    private boolean textureDirty;
    private boolean closed;
    private @Nullable NativeImageBackedTexture texture;

    public FlashlightRayMap() {
    }

    /** Client thread: recasts every cell from a new pose. / 客户端线程：从新姿态重投射全部单元。 */
    void recast(FlashlightRayCaster caster, ClientWorld world,
                double ox, double oy, double oz, double dx, double dy, double dz) {
        FlashlightBeamGeometry.basis(dx, dy, dz, basis);
        originX = ox;
        originY = oy;
        originZ = oz;
        caster.castGrid(world, ox, oy, oz, basis, hitDistances, blockLight, skyLight);
        origin = new Vec3d(ox, oy, oz);
        forward = basisVector(FlashlightBeamGeometry.FORWARD);
        right = basisVector(FlashlightBeamGeometry.RIGHT);
        up = basisVector(FlashlightBeamGeometry.UP);
        hasCast = true;
        textureDirty = true;
    }

    boolean hasCast() {
        return hasCast;
    }

    public Vec3d origin() {
        return origin;
    }

    public Vec3d forward() {
        return forward;
    }

    public Vec3d right() {
        return right;
    }

    public Vec3d up() {
        return up;
    }

    /** Hit distance of cell (u, v) in blocks; RANGE_BLOCKS when the ray hit nothing. / 单元命中距离，未命中为射程。 */
    public float hitDistance(int u, int v) {
        return hitDistances[v * SIZE + u];
    }

    /** CPU occlusion test with the same mapping and bias as the shaders. / 与着色器相同映射与偏移的 CPU 遮挡测试。 */
    public boolean isLit(Vec3d point) {
        return isLit(point.x, point.y, point.z);
    }

    /**
     * Allocation-free {@link #isLit(Vec3d)}: false behind the origin, outside the grid or beyond the cell's hit.
     * 无分配版本：位于原点后方、网格外或超出该单元命中距离时为 false。
     */
    public boolean isLit(double x, double y, double z) {
        if (!hasCast) {
            return false;
        }
        double lx = x - originX;
        double ly = y - originY;
        double lz = z - originZ;
        int cell = FlashlightBeamGeometry.cellIndex(basis, lx, ly, lz);
        if (cell < 0) {
            return false;
        }
        double distance = Math.sqrt(lx * lx + ly * ly + lz * lz);
        return !FlashlightBeamRules.isShadowed(distance, hitDistances[cell]);
    }

    /**
     * Render thread only; uploads lazily after each recast. Nearest filtering and clamp-to-edge wrapping.
     * 仅渲染线程；每次重投射后延迟上传。最近邻过滤，边缘钳制。
     */
    public AbstractTexture texture() {
        RenderSystem.assertOnRenderThread();
        if (closed) {
            throw new IllegalStateException("Flashlight ray map used after close");
        }
        if (texture == null) {
            texture = new NativeImageBackedTexture(new NativeImage(NativeImage.Format.RGBA, SIZE, SIZE, false));
            textureDirty = true;
        }
        if (textureDirty) {
            upload(texture);
            textureDirty = false;
        }
        return texture;
    }

    private void upload(NativeImageBackedTexture target) {
        NativeImage image = target.getImage();
        if (image == null) {
            return;
        }
        for (int v = 0; v < SIZE; v++) {
            for (int u = 0; u < SIZE; u++) {
                int cell = v * SIZE + u;
                int encoded = FlashlightBeamRules.encodeDistance(hitDistances[cell]);
                // NativeImage packs RGBA texels as ABGR ints. / NativeImage 以 ABGR 整数存放 RGBA 纹素。
                int red = (encoded >> 8) & 0xFF;
                int green = encoded & 0xFF;
                int blue = blockLight[cell] * 17;
                int alpha = skyLight[cell] * 17;
                image.setColor(u, v, (alpha << 24) | (blue << 16) | (green << 8) | red);
            }
        }
        target.bindTexture();
        // level, x, y, skipPixels, skipRows, width, height, blur, clamp, mipmap, close
        image.upload(0, 0, 0, 0, 0, SIZE, SIZE, false, true, false, false);
    }

    @Override
    public void close() {
        closed = true;
        hasCast = false;
        if (texture != null) {
            texture.close();
            texture = null;
        }
    }

    private Vec3d basisVector(int offset) {
        return new Vec3d(basis[offset], basis[offset + 1], basis[offset + 2]);
    }
}
