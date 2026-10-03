package annina.sparkstrength.role.attendant;

/**
 * Pure beam geometry for the client ray map: the beam basis, per-cell ray directions, point-to-cell mapping, the
 * third-person hand offset and an allocation-free voxel walk. No Minecraft types, so the CPU side stays testable and
 * mirrors the shader mapping of {@link FlashlightBeamRules}.
 * 手电筒光束纯几何：光束基向量、网格单元射线方向、点到单元映射、第三人称手部偏移与无分配体素遍历。
 * 不依赖 Minecraft 类型，便于测试，并与着色器使用的 {@link FlashlightBeamRules} 映射一致。
 *
 * <p>A basis array holds forward, right and up as consecutive xyz triples ({@link #FORWARD}, {@link #RIGHT},
 * {@link #UP}). right = normalize(forward x worldUp), up = right x forward, so (right, up, forward) matches the
 * shader's tangent-plane coordinates. 基向量数组依次存放 forward/right/up 的 xyz。</p>
 */
public final class FlashlightBeamGeometry {
    public static final int SIZE = FlashlightBeamRules.RAY_GRID_SIZE;
    public static final int CELL_COUNT = SIZE * SIZE;
    public static final int FORWARD = 0;
    public static final int RIGHT = 3;
    public static final int UP = 6;
    public static final int BASIS_LENGTH = 9;

    /** Third-person hand offset from the eye, in blocks (sideways, down, forward). / 第三人称手部相对眼睛的偏移（格）。 */
    public static final double HAND_SIDE_BLOCKS = 0.3;
    public static final double HAND_DROP_BLOCKS = 0.35;
    public static final double HAND_FORWARD_BLOCKS = 0.3;

    /**
     * Below this horizontal length forward is treated as vertical and right falls back to a fixed axis, so a player
     * looking straight up or down gets a stable basis. 水平分量低于此值视为竖直朝向，right 退回固定轴以保持稳定。
     */
    private static final double VERTICAL_EPSILON = 1.0e-4;
    private static final double BEHIND_EPSILON = 1.0e-9;

    private FlashlightBeamGeometry() {
    }

    /**
     * Visits the voxels a ray enters, in order. 按顺序访问射线进入的体素。
     */
    @FunctionalInterface
    public interface VoxelVisitor {
        /**
         * @param enterDistance distance along the unit ray where it enters this voxel (0 for the origin voxel)
         * @return the occluding hit distance (>= 0), or a negative value when light passes this voxel
         * 返回遮挡命中距离（>= 0），光可通过时返回负数。
         */
        double visit(int x, int y, int z, double enterDistance);
    }

    /**
     * Writes an orthonormal beam basis for {@code forward} (need not be unit length) into {@code out}.
     * 将 forward 方向（无需单位化）的正交光束基写入 out。
     */
    public static void basis(double fx, double fy, double fz, double[] out) {
        double length = Math.sqrt(fx * fx + fy * fy + fz * fz);
        if (!(length > 0.0)) {
            fx = 0.0;
            fy = 0.0;
            fz = 1.0;
            length = 1.0;
        }
        fx /= length;
        fy /= length;
        fz /= length;

        // forward x (0, 1, 0)
        double rx = -fz;
        double ry = 0.0;
        double rz = fx;
        double rightLength = Math.sqrt(rx * rx + rz * rz);
        if (rightLength < VERTICAL_EPSILON) {
            // forward x (0, 0, 1): world X-aligned right when looking straight up/down.
            // forward x (0, 0, 1)：竖直朝向时 right 对齐世界 X 轴。
            rx = fy;
            ry = -fx;
            rz = 0.0;
            rightLength = Math.sqrt(rx * rx + ry * ry);
        }
        rx /= rightLength;
        ry /= rightLength;
        rz /= rightLength;

        out[FORWARD] = fx;
        out[FORWARD + 1] = fy;
        out[FORWARD + 2] = fz;
        out[RIGHT] = rx;
        out[RIGHT + 1] = ry;
        out[RIGHT + 2] = rz;
        out[UP] = ry * fz - rz * fy;
        out[UP + 1] = rz * fx - rx * fz;
        out[UP + 2] = rx * fy - ry * fx;
    }

    /** Unit ray direction of grid cell (u, v), written to out[0..2]. / 网格单元 (u, v) 的单位射线方向。 */
    public static void cellDirection(double[] basis, int u, int v, double[] out) {
        double tu = FlashlightBeamRules.gridCellTangent(u);
        double tv = FlashlightBeamRules.gridCellTangent(v);
        double x = basis[FORWARD] + tu * basis[RIGHT] + tv * basis[UP];
        double y = basis[FORWARD + 1] + tu * basis[RIGHT + 1] + tv * basis[UP + 1];
        double z = basis[FORWARD + 2] + tu * basis[RIGHT + 2] + tv * basis[UP + 2];
        double length = Math.sqrt(x * x + y * y + z * z);
        out[0] = x / length;
        out[1] = y / length;
        out[2] = z / length;
    }

    /** Grid cell on one axis for a tangent-plane coordinate, or -1 outside the grid. / 切平面坐标所在单元，网格外为 -1。 */
    public static int cellOf(double tangent) {
        double unit = FlashlightBeamRules.tangentToGridUnit(tangent);
        if (!(unit >= 0.0) || unit >= 1.0) {
            return -1;
        }
        return Math.min(SIZE - 1, (int) (unit * SIZE));
    }

    /**
     * Flat cell index {@code v * SIZE + u} of the ray through a point at local offset (lx, ly, lz) from the origin,
     * or -1 when the point is behind the origin or outside the grid.
     * 相对原点偏移为 (lx, ly, lz) 的点所在射线单元的扁平索引 v * SIZE + u；在原点后方或网格外返回 -1。
     */
    public static int cellIndex(double[] basis, double lx, double ly, double lz) {
        double depth = lx * basis[FORWARD] + ly * basis[FORWARD + 1] + lz * basis[FORWARD + 2];
        if (!(depth > BEHIND_EPSILON)) {
            return -1;
        }
        int u = cellOf((lx * basis[RIGHT] + ly * basis[RIGHT + 1] + lz * basis[RIGHT + 2]) / depth);
        if (u < 0) {
            return -1;
        }
        int v = cellOf((lx * basis[UP] + ly * basis[UP + 1] + lz * basis[UP + 2]) / depth);
        if (v < 0) {
            return -1;
        }
        return v * SIZE + u;
    }

    /**
     * Eye-to-hand offset for a held flashlight, rotated by body yaw (Minecraft degrees: 0 faces +Z, 90 faces -X).
     * Written to out[0..2]. 手持手电相对眼睛的偏移，按身体偏航角旋转（Minecraft 角度：0 朝 +Z，90 朝 -X）。
     */
    public static void handOffset(double bodyYawDegrees, boolean rightHand, double[] out) {
        double yaw = Math.toRadians(bodyYawDegrees);
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);
        // Facing (-sin, 0, cos); the player's right is (-cos, 0, -sin). / 朝向 (-sin, 0, cos)，右侧 (-cos, 0, -sin)。
        double side = rightHand ? HAND_SIDE_BLOCKS : -HAND_SIDE_BLOCKS;
        out[0] = -cos * side - sin * HAND_FORWARD_BLOCKS;
        out[1] = -HAND_DROP_BLOCKS;
        out[2] = -sin * side + cos * HAND_FORWARD_BLOCKS;
    }

    /**
     * Amanatides-Woo voxel walk from (ox, oy, oz) along the unit direction (dx, dy, dz). Returns the first hit
     * distance reported by {@code visitor}, or {@code maxDistance} when nothing occludes within range.
     * Amanatides-Woo 体素遍历：返回 visitor 报告的首个命中距离，射程内无遮挡时返回 maxDistance。
     */
    public static double walk(double ox, double oy, double oz, double dx, double dy, double dz,
                              double maxDistance, VoxelVisitor visitor) {
        int x = (int) Math.floor(ox);
        int y = (int) Math.floor(oy);
        int z = (int) Math.floor(oz);
        int stepX = dx > 0.0 ? 1 : dx < 0.0 ? -1 : 0;
        int stepY = dy > 0.0 ? 1 : dy < 0.0 ? -1 : 0;
        int stepZ = dz > 0.0 ? 1 : dz < 0.0 ? -1 : 0;
        double deltaX = stepX == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dx);
        double deltaY = stepY == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dy);
        double deltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dz);
        double nextX = stepX > 0 ? (x + 1.0 - ox) * deltaX : stepX < 0 ? (ox - x) * deltaX : Double.POSITIVE_INFINITY;
        double nextY = stepY > 0 ? (y + 1.0 - oy) * deltaY : stepY < 0 ? (oy - y) * deltaY : Double.POSITIVE_INFINITY;
        double nextZ = stepZ > 0 ? (z + 1.0 - oz) * deltaZ : stepZ < 0 ? (oz - z) * deltaZ : Double.POSITIVE_INFINITY;

        double distance = 0.0;
        while (distance < maxDistance) {
            double hit = visitor.visit(x, y, z, distance);
            if (hit >= 0.0) {
                return Math.min(hit, maxDistance);
            }
            if (nextX <= nextY && nextX <= nextZ) {
                x += stepX;
                distance = nextX;
                nextX += deltaX;
            } else if (nextY <= nextZ) {
                y += stepY;
                distance = nextY;
                nextY += deltaY;
            } else {
                z += stepZ;
                distance = nextZ;
                nextZ += deltaZ;
            }
        }
        return maxDistance;
    }
}
