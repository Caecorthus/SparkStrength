package annina.sparkstrength.item.grenade;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/** Pure blast sampling and finite path geometry. / 纯几何爆炸采样与有限路径计算。 */
public final class GrenadeBlastGeometry {
    public static final double GRID_STEP = .5;
    public static final int SAMPLE_COUNT = 27;
    public static final int HIT_THRESHOLD = 9;
    private static final double ROUNDING_EPSILON = 1e-10;

    private GrenadeBlastGeometry() {
    }

    public record Point(double x, double y, double z) {
    }

    public record Bounds(double minX, double minY, double minZ,
                         double maxX, double maxY, double maxZ) {
    }

    public record Cell(int x, int y, int z) {
    }

    /** Endpoints must also be tested; a zero-length query checks occupancy. / 必须检查端点；零长度查询检查占用。 */
    @FunctionalInterface
    public interface Space {
        boolean segmentClear(Point from, Point to);
    }

    public static List<Point> samples(Bounds bounds) {
        List<Point> points = new ArrayList<>(SAMPLE_COUNT);
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                for (int z = 0; z < 3; z++) {
                    points.add(new Point(sample(bounds.minX(), bounds.maxX(), x),
                            sample(bounds.minY(), bounds.maxY(), y),
                            sample(bounds.minZ(), bounds.maxZ(), z)));
                }
            }
        }
        return List.copyOf(points);
    }

    private static double sample(double minimum, double maximum, int index) {
        double fraction = (index + .5) / 3.0;
        return minimum + (maximum - minimum) * fraction;
    }

    public static double segmentLength(Point from, Point to) {
        double dx = to.x() - from.x();
        double dy = to.y() - from.y();
        double dz = to.z() - from.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static BitSet directReachable(Point origin, List<Point> samples, Space space, double maxPathLength) {
        BitSet reached = new BitSet(samples.size());
        if (!valid(origin) || !validBudget(maxPathLength) || !space.segmentClear(origin, origin)) return reached;
        for (int i = 0; i < samples.size(); i++) {
            Point target = samples.get(i);
            if (valid(target) && within(segmentLength(origin, target), maxPathLength)
                    && space.segmentClear(target, target) && space.segmentClear(origin, target)) reached.set(i);
        }
        return reached;
    }

    public static boolean meetsHitThreshold(int reachableSamples) {
        return reachableSamples >= HIT_THRESHOLD;
    }

    public static PropagationField propagate(Point origin, Space space, double maxPathLength) {
        if (!valid(origin) || !validBudget(maxPathLength) || !space.segmentClear(origin, origin)) {
            return new PropagationField(origin, space, maxPathLength, Map.of());
        }
        int limit = (int) Math.floor(maxPathLength / GRID_STEP);
        Cell start = new Cell(0, 0, 0);
        Map<Cell, Double> best = new HashMap<>();
        Map<Edge, Boolean> edgeClear = new HashMap<>();
        PriorityQueue<Node> queue = new PriorityQueue<>((a, b) -> {
            int order = Double.compare(a.cost(), b.cost());
            if (order != 0) return order;
            order = Integer.compare(a.cell().x(), b.cell().x());
            if (order != 0) return order;
            order = Integer.compare(a.cell().y(), b.cell().y());
            return order != 0 ? order : Integer.compare(a.cell().z(), b.cell().z());
        });
        best.put(start, 0.0);
        queue.add(new Node(start, 0));
        while (!queue.isEmpty()) {
            Node current = queue.remove();
            if (current.cost() > best.get(current.cell())) continue;
            Point from = point(origin, current.cell());
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        Cell next = new Cell(current.cell().x() + dx, current.cell().y() + dy,
                                current.cell().z() + dz);
                        if (Math.abs(next.x()) > limit || Math.abs(next.y()) > limit
                                || Math.abs(next.z()) > limit
                                || !GrenadeBlastRules.containsSphere(next.x() * GRID_STEP,
                                        next.y() * GRID_STEP, next.z() * GRID_STEP, maxPathLength)) continue;
                        double cost = current.cost() + GRID_STEP * Math.sqrt(dx * dx + dy * dy + dz * dz);
                        if (!within(cost, maxPathLength) || cost >= best.getOrDefault(next, Double.POSITIVE_INFINITY)) continue;
                        Edge edge = Edge.of(current.cell(), next);
                        boolean clear = edgeClear.computeIfAbsent(edge,
                                ignored -> space.segmentClear(from, point(origin, next)));
                        if (!clear) continue;
                        best.put(next, cost);
                        queue.add(new Node(next, cost));
                    }
                }
            }
        }
        return new PropagationField(origin, space, maxPathLength, Map.copyOf(best));
    }

    /** The final target joins through at most eight cell corners. / 目标点最多通过所在网格单元的八个角点接入。 */
    public static final class PropagationField {
        private final Point origin;
        private final Space space;
        private final double maxPathLength;
        private final Map<Cell, Double> best;

        private PropagationField(Point origin, Space space, double maxPathLength, Map<Cell, Double> best) {
            this.origin = origin;
            this.space = space;
            this.maxPathLength = maxPathLength;
            this.best = best;
        }

        public boolean reaches(Point target) {
            if (best.isEmpty() || !valid(target) || !within(segmentLength(origin, target), maxPathLength)
                    || !space.segmentClear(target, target)) return false;
            int x = (int) Math.floor((target.x() - origin.x()) / GRID_STEP);
            int y = (int) Math.floor((target.y() - origin.y()) / GRID_STEP);
            int z = (int) Math.floor((target.z() - origin.z()) / GRID_STEP);
            for (int dx = 0; dx <= 1; dx++) {
                for (int dy = 0; dy <= 1; dy++) {
                    for (int dz = 0; dz <= 1; dz++) {
                        Cell corner = new Cell(x + dx, y + dy, z + dz);
                        Double cost = best.get(corner);
                        if (cost == null) continue;
                        Point from = point(origin, corner);
                        if (within(cost + segmentLength(from, target), maxPathLength)
                                && space.segmentClear(from, target)) return true;
                    }
                }
            }
            return false;
        }
    }

    private record Node(Cell cell, double cost) {
    }

    private record Edge(Cell first, Cell second) {
        static Edge of(Cell a, Cell b) {
            int order = Integer.compare(a.x(), b.x());
            if (order == 0) order = Integer.compare(a.y(), b.y());
            if (order == 0) order = Integer.compare(a.z(), b.z());
            return order <= 0 ? new Edge(a, b) : new Edge(b, a);
        }
    }

    private static Point point(Point origin, Cell cell) {
        return new Point(origin.x() + cell.x() * GRID_STEP,
                origin.y() + cell.y() * GRID_STEP, origin.z() + cell.z() * GRID_STEP);
    }

    private static boolean valid(Point point) {
        return point != null && Double.isFinite(point.x())
                && Double.isFinite(point.y()) && Double.isFinite(point.z());
    }

    private static boolean validBudget(double budget) {
        return Double.isFinite(budget) && budget >= 0;
    }

    private static boolean within(double length, double budget) {
        return Double.isFinite(length) && length <= budget + ROUNDING_EPSILON;
    }
}
