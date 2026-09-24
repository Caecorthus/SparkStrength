package annina.sparkstrength.item.grenade;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import static annina.sparkstrength.item.grenade.GrenadeBlastGeometry.*;

public final class GrenadeBlastService {
    private static final double COLLISION_EPSILON = 1.0e-5;

    private GrenadeBlastService() {
    }

    // Geometry only: preserve candidate order and leave protection/attribution to the caller.
    // 只筛选几何命中并保留候选顺序，保护与击杀归属仍由调用方处理。
    public static List<ServerPlayerEntity> filterVictims(ServerWorld world, Entity grenade,
                                                        List<ServerPlayerEntity> candidates,
                                                        double maxPathLength) {
        Vec3d center = grenade.getBoundingBox().getCenter();
        Point origin = new Point(center.x, center.y, center.z);
        List<ServerPlayerEntity> nearby = candidates.stream().filter(player ->
                GrenadeBlastRules.containsSphere(player.getX() - origin.x(), player.getY() - origin.y(),
                        player.getZ() - origin.z(), maxPathLength)).toList();
        if (nearby.isEmpty()) {
            return List.of();
        }
        Space space = new CollisionSpace(world, grenade, origin, maxPathLength);
        if (!space.segmentClear(origin, origin)) {
            return List.of();
        }
        PropagationField propagation = null;
        List<ServerPlayerEntity> victims = new ArrayList<>();
        for (ServerPlayerEntity player : nearby) {
            Box box = player.getBoundingBox();
            List<Point> points = samples(new Bounds(box.minX, box.minY, box.minZ,
                    box.maxX, box.maxY, box.maxZ));
            BitSet reachable = directReachable(origin, points, space, maxPathLength);
            if (!meetsHitThreshold(reachable.cardinality())) {
                int possible = 0;
                for (Point point : points) {
                    if (segmentLength(origin, point) <= maxPathLength && space.segmentClear(point, point)) {
                        possible++;
                    }
                }
                if (!meetsHitThreshold(possible)) {
                    continue;
                }
                // One shared search per explosion, created only when direct rays are insufficient.
                // 仅在直线不足时建立路径图，一次爆炸的所有玩家共用。
                if (propagation == null) {
                    propagation = propagate(origin, space, maxPathLength);
                }
                for (int i = 0; i < points.size() && !meetsHitThreshold(reachable.cardinality()); i++) {
                    if (!reachable.get(i) && propagation.reaches(points.get(i))) {
                        reachable.set(i);
                    }
                }
            }
            if (meetsHitThreshold(reachable.cardinality())) {
                victims.add(player);
            }
        }
        return victims;
    }

    private static final class CollisionSpace implements Space {
        private final List<Collider>[] cells;
        private final int minX, minY, minZ, maxX, maxY, maxZ, height, depth;
        private int queryId;

        @SuppressWarnings("unchecked")
        private CollisionSpace(ServerWorld world, Entity grenade, Point origin, double radius) {
            Box bounds = new Box(origin.x() - radius, origin.y() - radius, origin.z() - radius,
                    origin.x() + radius, origin.y() + radius, origin.z() + radius).expand(COLLISION_EPSILON);
            minX = floor(bounds.minX);
            minY = floor(bounds.minY);
            minZ = floor(bounds.minZ);
            maxX = floor(bounds.maxX);
            maxY = floor(bounds.maxY);
            maxZ = floor(bounds.maxZ);
            height = maxY - minY + 1;
            depth = maxZ - minZ + 1;
            // The blast occupies a small fixed neighborhood; use direct cell indexing.
            // 爆炸仅覆盖局部小范围，用连续数组直接定位碰撞单元。
            cells = (List<Collider>[]) new List<?>[(maxX - minX + 1) * height * depth];
            // Grenade context keeps player-only door passing from changing blast geometry.
            // 使用手雷上下文，避免玩家穿门能力改变爆炸的遮挡形状。
            for (VoxelShape shape : world.getBlockCollisions(grenade, bounds)) {
                for (Box box : shape.getBoundingBoxes()) {
                    index(box.expand(COLLISION_EPSILON));
                }
            }
            // Missing chunks block propagation; the collision query does not request chunk loads.
            // 未加载区块阻断传播，碰撞查询不请求加载区块。
            for (int x = floor(bounds.minX) >> 4; x <= floor(bounds.maxX) >> 4; x++) {
                for (int z = floor(bounds.minZ) >> 4; z <= floor(bounds.maxZ) >> 4; z++) {
                    if (!world.isChunkLoaded(x, z)) {
                        index(new Box(Math.max(bounds.minX, x * 16.0), bounds.minY,
                                Math.max(bounds.minZ, z * 16.0), Math.min(bounds.maxX, (x + 1) * 16.0),
                                bounds.maxY, Math.min(bounds.maxZ, (z + 1) * 16.0)).expand(COLLISION_EPSILON));
                    }
                }
            }
        }

        private void index(Box box) {
            Collider collider = new Collider(box);
            for (int x = Math.max(minX, floor(box.minX)); x <= Math.min(maxX, floor(box.maxX)); x++) {
                for (int y = Math.max(minY, floor(box.minY)); y <= Math.min(maxY, floor(box.maxY)); y++) {
                    for (int z = Math.max(minZ, floor(box.minZ)); z <= Math.min(maxZ, floor(box.maxZ)); z++) {
                        int cell = cellIndex(x, y, z);
                        if (cells[cell] == null) cells[cell] = new ArrayList<>();
                        cells[cell].add(collider);
                    }
                }
            }
        }

        @Override
        public boolean segmentClear(Point from, Point to) {
            int currentQuery = ++queryId;
            for (int x = Math.max(minX, floor(Math.min(from.x(), to.x())));
                 x <= Math.min(maxX, floor(Math.max(from.x(), to.x()))); x++) {
                for (int y = Math.max(minY, floor(Math.min(from.y(), to.y())));
                     y <= Math.min(maxY, floor(Math.max(from.y(), to.y()))); y++) {
                    for (int z = Math.max(minZ, floor(Math.min(from.z(), to.z())));
                         z <= Math.min(maxZ, floor(Math.max(from.z(), to.z()))); z++) {
                        List<Collider> colliders = cells[cellIndex(x, y, z)];
                        if (colliders == null) {
                            continue;
                        }
                        for (Collider collider : colliders) {
                            if (collider.lastQuery == currentQuery) {
                                continue;
                            }
                            collider.lastQuery = currentQuery;
                            if (intersects(collider.box, from, to)) {
                                return false;
                            }
                        }
                    }
                }
            }
            return true;
        }

        private int cellIndex(int x, int y, int z) {
            return ((x - minX) * height + y - minY) * depth + z - minZ;
        }

        // Closed segment/slab intersection includes endpoints, tangent faces and zero-length queries.
        // 闭线段与盒体相交检测包含端点、贴面和零长度查询，不允许从零宽缝穿过。
        private static boolean intersects(Box box, Point from, Point to) {
            double enter = 0.0;
            double exit = 1.0;
            for (int axis = 0; axis < 3; axis++) {
                double start = axis == 0 ? from.x() : axis == 1 ? from.y() : from.z();
                double end = axis == 0 ? to.x() : axis == 1 ? to.y() : to.z();
                double min = axis == 0 ? box.minX : axis == 1 ? box.minY : box.minZ;
                double max = axis == 0 ? box.maxX : axis == 1 ? box.maxY : box.maxZ;
                double delta = end - start;
                if (delta == 0.0) {
                    if (start < min || start > max) {
                        return false;
                    }
                } else {
                    double a = (min - start) / delta;
                    double b = (max - start) / delta;
                    enter = Math.max(enter, Math.min(a, b));
                    exit = Math.min(exit, Math.max(a, b));
                    if (enter > exit) {
                        return false;
                    }
                }
            }
            return true;
        }

        private static int floor(double value) {
            return (int) Math.floor(value);
        }

        private static final class Collider {
            private final Box box;
            private int lastQuery;

            private Collider(Box box) {
                this.box = box;
            }
        }
    }
}
