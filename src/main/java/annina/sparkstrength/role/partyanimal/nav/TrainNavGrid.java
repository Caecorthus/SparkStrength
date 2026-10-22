package annina.sparkstrength.role.partyanimal.nav;

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.Arrays;

/**
 * Walkable-cell grid of the train for the Partygoer, built lazily from collision boxes. Vanilla pathfinding cannot be
 * used: it only knows vanilla doors and treats every non-full block (windows, railings, wall panels) as walkable.
 * 派对客使用的列车可行走网格，按碰撞箱懒加载。不能用原版寻路：它只认识原版门，并把所有非整格方块（车窗、栏杆、墙板）当成可走。
 *
 * <p>A cell is the block holding the feet. It is standable when the centre column (0.6 wide, like a player) has a floor
 * no higher than a slab inside the block, or a full-height top just below it, and 1.8 blocks of clear headroom above
 * that floor. A move between neighbouring cells sweeps the same 0.6-wide column from centre to centre, so thin panels on
 * a shared face and diagonal corner cuts are caught. Room doors never collide (the Partygoer opens them); train doors are
 * walls. Rises above a step (0.6) are only allowed on stairs.
 * 单元格是脚所在的方块。中心柱（与玩家同宽 0.6）在本格内有不高于台阶板的地面，或下方方块顶面接近整格，且地面以上 1.8 格无遮挡时可站立。
 * 相邻格之间的移动从中心到中心扫掠同样宽 0.6 的柱体，因此能识别公共面上的薄墙板与斜向切角。房门不产生碰撞（派对客会推开）；列车外门视为墙。
 * 超过一级台阶（0.6）的上升只允许在楼梯上。</p>
 */
public final class TrainNavGrid {
    public static final double HALF_WIDTH = 0.3;
    public static final double HEIGHT = 1.8;
    public static final double STEP = 0.6;
    public static final double MAX_RISE = 1.05;
    public static final double MAX_DROP = 1.05;
    private static final double SLAB_MAX = 0.5625;
    private static final double FULL_MIN = 0.9375;
    private static final double CLEARANCE = 0.05;
    private static final double EPS = 1.0E-4;
    private static final int[][] DIRECTIONS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };
    private static final int[] DY_ORDER = {0, 1, -1};
    private static final double UNKNOWN_FLOOR = Double.NEGATIVE_INFINITY;
    private static final long[] NO_EDGES = new long[0];

    private final NavBlocks blocks;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;
    private final Long2ObjectOpenHashMap<double[][]> boxCache = new Long2ObjectOpenHashMap<>();
    private final Long2DoubleOpenHashMap floorCache = new Long2DoubleOpenHashMap();
    /**
     * Per cell, the {@link #neighbors} answer as (cell, cost bits) pairs, so repeated searches skip the collision sweeps.
     * 每格的 neighbors 结果，按（格子, 代价位）成对存放，重复搜索时不再做碰撞扫掠。
     */
    private final Long2ObjectOpenHashMap<long[]> edgeCache = new Long2ObjectOpenHashMap<>();

    /** Bounds are inclusive block coordinates (Wathe's play area). / 边界为闭区间方块坐标（Wathe 游玩区）。 */
    public TrainNavGrid(NavBlocks blocks, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.blocks = blocks;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        floorCache.defaultReturnValue(UNKNOWN_FLOOR);
    }

    // ---- Cell keys (same packing as BlockPos#asLong) / 单元格键（与 BlockPos#asLong 相同的打包方式） ----

    public static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | (long) y & 0xFFFL;
    }

    public static int x(long key) {
        return (int) (key >> 38);
    }

    public static int y(long key) {
        return (int) (key << 52 >> 52);
    }

    public static int z(long key) {
        return (int) (key << 26 >> 38);
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    // ---- Standing / 站立 ----

    /** Absolute feet height in the cell, or NaN when nothing can stand there. / 该格内的绝对脚高，不可站立时为 NaN。 */
    public double floor(int x, int y, int z) {
        long key = key(x, y, z);
        double cached = floorCache.get(key);
        if (cached != UNKNOWN_FLOOR) {
            return cached;
        }
        double floor = computeFloor(x, y, z);
        if (blocks.isLoaded(x, z)) {
            floorCache.put(key, floor);
        }
        return floor;
    }

    public double floor(long key) {
        return floor(x(key), y(key), z(key));
    }

    public boolean isStandable(int x, int y, int z) {
        return !Double.isNaN(floor(x, y, z));
    }

    public boolean isStandable(long key) {
        return isStandable(x(key), y(key), z(key));
    }

    private double computeFloor(int x, int y, int z) {
        if (!contains(x, y, z)) {
            return Double.NaN;
        }
        double floor;
        double here = centreTop(x, y, z);
        if (here >= 0.0) {
            if (here > SLAB_MAX) {
                return Double.NaN;
            }
            floor = y + here;
        } else {
            double below = centreTop(x, y - 1, z);
            if (below < FULL_MIN) {
                return Double.NaN;
            }
            floor = y - 1 + below;
        }
        if (floor < minY) {
            return Double.NaN;
        }
        double cx = x + 0.5;
        double cz = z + 0.5;
        if (anyCollision(cx - HALF_WIDTH, floor + CLEARANCE, cz - HALF_WIDTH,
                cx + HALF_WIDTH, floor + HEIGHT, cz + HALF_WIDTH)) {
            return Double.NaN;
        }
        return floor;
    }

    /**
     * Highest top among this block's boxes that reach into the centre column, in [0, 1]; -1 when none do.
     * 本方块伸入中心柱的碰撞箱的最高顶面，范围 [0, 1]；没有则为 -1。
     */
    private double centreTop(int x, int y, int z) {
        double top = -1.0;
        double lo = 0.5 - HALF_WIDTH;
        double hi = 0.5 + HALF_WIDTH;
        for (double[] box : boxes(x, y, z)) {
            if (box[0] < hi - EPS && box[3] > lo + EPS && box[2] < hi - EPS && box[5] > lo + EPS) {
                top = Math.max(top, box[4]);
            }
        }
        return top;
    }

    // ---- Moving / 移动 ----

    /**
     * Whether the Partygoer can walk from cell a to the neighbouring cell b (at most one block apart on each axis).
     * 派对客能否从 a 走到相邻的 b（每个轴最多相差一格）。
     */
    public boolean canMove(int ax, int ay, int az, int bx, int by, int bz) {
        double fa = floor(ax, ay, az);
        double fb = floor(bx, by, bz);
        if (Double.isNaN(fa) || Double.isNaN(fb)) {
            return false;
        }
        double rise = fb - fa;
        if (rise > MAX_RISE || -rise > MAX_DROP) {
            return false;
        }
        if (Math.abs(rise) > STEP && !onStairs(ax, az, fa) && !onStairs(bx, bz, fb)) {
            return false;
        }
        double bottom = Math.max(fa, fb) + CLEARANCE;
        return !anyCollision(Math.min(ax, bx) + 0.5 - HALF_WIDTH, bottom, Math.min(az, bz) + 0.5 - HALF_WIDTH,
                Math.max(ax, bx) + 0.5 + HALF_WIDTH, bottom + HEIGHT - CLEARANCE, Math.max(az, bz) + 0.5 + HALF_WIDTH);
    }

    private boolean onStairs(int x, int z, double floor) {
        return (blocks.flags(x, (int) Math.floor(floor - 0.01), z) & NavBlocks.FLAG_STAIRS) != 0;
    }

    /**
     * Calls {@code sink} for every reachable neighbour of {@code cell} with its step cost (distance, plus a little for
     * height changes so flat routes win).
     * 对 cell 的每个可达邻格调用 sink，并给出步进代价（水平距离，高度变化略加代价，让平路优先）。
     */
    public void neighbors(long cell, NeighborSink sink) {
        long[] edges = edgeCache.get(cell);
        if (edges == null) {
            edges = computeEdges(cell);
            if (aroundLoaded(x(cell), z(cell))) {
                edgeCache.put(cell, edges);
            }
        }
        for (int i = 0; i < edges.length; i += 2) {
            sink.accept(edges[i], Double.longBitsToDouble(edges[i + 1]));
        }
    }

    private long[] computeEdges(long cell) {
        int x = x(cell);
        int y = y(cell);
        int z = z(cell);
        double from = floor(x, y, z);
        if (Double.isNaN(from)) {
            return NO_EDGES;
        }
        long[] edges = new long[DIRECTIONS.length * 2];
        int size = 0;
        for (int[] direction : DIRECTIONS) {
            int nx = x + direction[0];
            int nz = z + direction[1];
            for (int dy : DY_ORDER) {
                int ny = y + dy;
                if (!isStandable(nx, ny, nz)) {
                    continue;
                }
                if (canMove(x, y, z, nx, ny, nz)) {
                    double horizontal = direction[0] != 0 && direction[1] != 0 ? Math.sqrt(2.0) : 1.0;
                    edges[size++] = key(nx, ny, nz);
                    edges[size++] = Double.doubleToRawLongBits(
                            horizontal + 0.5 * Math.abs(floor(nx, ny, nz) - from));
                }
                break;
            }
        }
        return size == edges.length ? edges : Arrays.copyOf(edges, size);
    }

    /**
     * A cell's moves read only the columns next to it, so they can be cached once all nine are loaded.
     * 一格的移动只读取其相邻各列，九列都已加载时即可缓存。
     */
    private boolean aroundLoaded(int x, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!blocks.isLoaded(x + dx, z + dz)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Nearest standable cell to a world position (feet), searching the block, the one below and above, then the ring
     * around them; {@link Long#MIN_VALUE} when there is none.
     * 离世界坐标（脚部）最近的可站立格：先查本格、下方与上方，再查周围一圈；都没有时返回 Long.MIN_VALUE。
     */
    public long snap(double x, double y, double z) {
        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(y + 0.01);
        int bz = (int) Math.floor(z);
        long best = Long.MIN_VALUE;
        double bestDistance = Double.MAX_VALUE;
        for (int dy : DY_ORDER) {
            if (isStandable(bx, by + dy, bz)) {
                return key(bx, by + dy, bz);
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                for (int dy : DY_ORDER) {
                    if (!isStandable(bx + dx, by + dy, bz + dz)) {
                        continue;
                    }
                    double ex = bx + dx + 0.5 - x;
                    double ey = floor(bx + dx, by + dy, bz + dz) - y;
                    double ez = bz + dz + 0.5 - z;
                    double distance = ex * ex + ey * ey + ez * ez;
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = key(bx + dx, by + dy, bz + dz);
                    }
                }
            }
        }
        return best;
    }

    public int flags(int x, int y, int z) {
        return blocks.flags(x, y, z);
    }

    /** Forget everything (blocks changed, e.g. a door was chopped away). / 清空缓存（方块已变化，如门被拆）。 */
    public void invalidate() {
        boxCache.clear();
        floorCache.clear();
        edgeCache.clear();
    }

    /**
     * Forget what depends on the block at (x, y, z), e.g. one half of a chopped-down door. A cell's floor reads its own
     * column from one block below to two above; its moves read the columns next to it from two below to three above.
     * 忘掉依赖 (x, y, z) 处方块的缓存，例如被拆门的一半。一格的地面读取本列下方一格到上方两格；其移动读取相邻各列下方两格到上方三格。
     */
    public void invalidate(int x, int y, int z) {
        boxCache.remove(key(x, y, z));
        for (int cy = y - 2; cy <= y + 1; cy++) {
            floorCache.remove(key(x, cy, z));
        }
        for (int cx = x - 1; cx <= x + 1; cx++) {
            for (int cz = z - 1; cz <= z + 1; cz++) {
                for (int cy = y - 3; cy <= y + 2; cy++) {
                    edgeCache.remove(key(cx, cy, cz));
                }
            }
        }
    }

    private boolean anyCollision(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        int x0 = (int) Math.floor(minX);
        int x1 = (int) Math.floor(maxX - EPS);
        int y0 = (int) Math.floor(minY);
        int y1 = (int) Math.floor(maxY - EPS);
        int z0 = (int) Math.floor(minZ);
        int z1 = (int) Math.floor(maxZ - EPS);
        for (int bx = x0; bx <= x1; bx++) {
            for (int by = y0; by <= y1; by++) {
                for (int bz = z0; bz <= z1; bz++) {
                    for (double[] box : boxes(bx, by, bz)) {
                        if (box[0] + bx < maxX - EPS && box[3] + bx > minX + EPS
                                && box[1] + by < maxY - EPS && box[4] + by > minY + EPS
                                && box[2] + bz < maxZ - EPS && box[5] + bz > minZ + EPS) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private double[][] boxes(int x, int y, int z) {
        long key = key(x, y, z);
        double[][] cached = boxCache.get(key);
        if (cached == null) {
            cached = blocks.boxes(x, y, z);
            if (blocks.isLoaded(x, z)) {
                boxCache.put(key, cached);
            }
        }
        return cached;
    }

    @FunctionalInterface
    public interface NeighborSink {
        void accept(long cell, double cost);
    }
}
