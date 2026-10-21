package annina.sparkstrength.role.partyanimal.nav;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.LongPredicate;

/**
 * Searches over {@link TrainNavGrid}: A* to one cell, nearest of several goals by walking distance, and a bounded flood
 * fill for room interiors. Every search has a node budget so a bad map can never stall the server tick.
 * 基于 TrainNavGrid 的搜索：到单个格子的 A*、按步行距离找多个目标中最近的一个，以及用于房间内部的有界泛洪。
 * 每种搜索都有节点上限，坏地图也不会卡住服务器 tick。
 */
public final class TrainPathfinder {
    /** Enough for a whole long train (about 370 x 30 cells across two floors). / 足够覆盖整列长火车。 */
    public static final int DEFAULT_BUDGET = 40_000;

    private TrainPathfinder() {
    }

    /**
     * A* from {@code start} to {@code goal}; the cells in walking order (start first), or an empty list.
     * 从 start 到 goal 的 A*；按行走顺序返回格子（首个为 start），找不到时为空列表。
     */
    public static List<Long> path(TrainNavGrid grid, long start, long goal, int budget) {
        if (start == Long.MIN_VALUE || goal == Long.MIN_VALUE
                || !grid.isStandable(start) || !grid.isStandable(goal)) {
            return List.of();
        }
        if (start == goal) {
            return List.of(start);
        }
        Map<Long, Double> best = new HashMap<>();
        Map<Long, Long> parent = new HashMap<>();
        PriorityQueue<Node> open = new PriorityQueue<>();
        best.put(start, 0.0);
        open.add(new Node(start, 0.0, heuristic(start, goal, grid)));
        int expanded = 0;
        while (!open.isEmpty() && expanded < budget) {
            Node node = open.poll();
            if (node.cost > best.getOrDefault(node.cell, Double.MAX_VALUE)) {
                continue;
            }
            if (node.cell == goal) {
                return rebuild(parent, goal);
            }
            expanded++;
            grid.neighbors(node.cell, (next, step) -> {
                double cost = node.cost + step;
                if (cost < best.getOrDefault(next, Double.MAX_VALUE)) {
                    best.put(next, cost);
                    parent.put(next, node.cell);
                    open.add(new Node(next, cost, cost + heuristic(next, goal, grid)));
                }
            });
        }
        return List.of();
    }

    /**
     * Dijkstra from {@code start} until the first of {@code goals} is reached; that goal, or {@link Long#MIN_VALUE}.
     * 从 start 出发的 Dijkstra，到达 goals 中的第一个即停；返回该目标，否则为 Long.MIN_VALUE。
     */
    public static long nearest(TrainNavGrid grid, long start, Set<Long> goals, int budget) {
        if (start == Long.MIN_VALUE || goals.isEmpty() || !grid.isStandable(start)) {
            return Long.MIN_VALUE;
        }
        if (goals.contains(start)) {
            return start;
        }
        Map<Long, Double> best = new HashMap<>();
        PriorityQueue<Node> open = new PriorityQueue<>();
        best.put(start, 0.0);
        open.add(new Node(start, 0.0, 0.0));
        int expanded = 0;
        while (!open.isEmpty() && expanded < budget) {
            Node node = open.poll();
            if (node.cost > best.getOrDefault(node.cell, Double.MAX_VALUE)) {
                continue;
            }
            if (goals.contains(node.cell)) {
                return node.cell;
            }
            expanded++;
            grid.neighbors(node.cell, (next, step) -> {
                double cost = node.cost + step;
                if (cost < best.getOrDefault(next, Double.MAX_VALUE)) {
                    best.put(next, cost);
                    open.add(new Node(next, cost, cost));
                }
            });
        }
        return Long.MIN_VALUE;
    }

    /**
     * Breadth-first flood from {@code seed} that never enters a {@code wall} cell; cell -> step depth in visit order.
     * Stops after {@code cap} cells (a result of exactly {@code cap} cells means "open space", not a room).
     * 从 seed 出发、不进入 wall 格的广度优先泛洪；返回 格子 -> 步数，按访问顺序。达到 cap 个格子即停止（恰好 cap 个表示开放空间而非房间）。
     */
    public static Map<Long, Integer> flood(TrainNavGrid grid, long seed, LongPredicate wall, int cap) {
        Map<Long, Integer> depth = new LinkedHashMap<>();
        if (seed == Long.MIN_VALUE || !grid.isStandable(seed) || wall.test(seed)) {
            return depth;
        }
        ArrayDeque<Long> queue = new ArrayDeque<>();
        depth.put(seed, 0);
        queue.add(seed);
        while (!queue.isEmpty() && depth.size() < cap) {
            long cell = queue.poll();
            int next = depth.get(cell) + 1;
            grid.neighbors(cell, (neighbor, step) -> {
                if (depth.size() < cap && !depth.containsKey(neighbor) && !wall.test(neighbor)) {
                    depth.put(neighbor, next);
                    queue.add(neighbor);
                }
            });
        }
        return depth;
    }

    private static double heuristic(long from, long to, TrainNavGrid grid) {
        double dx = TrainNavGrid.x(from) - TrainNavGrid.x(to);
        double dz = TrainNavGrid.z(from) - TrainNavGrid.z(to);
        double dy = TrainNavGrid.y(from) - TrainNavGrid.y(to);
        return Math.sqrt(dx * dx + dz * dz) + 0.5 * Math.abs(dy);
    }

    private static List<Long> rebuild(Map<Long, Long> parent, long goal) {
        List<Long> cells = new ArrayList<>();
        Long cell = goal;
        while (cell != null) {
            cells.add(cell);
            cell = parent.get(cell);
        }
        Collections.reverse(cells);
        return cells;
    }

    private record Node(long cell, double cost, double estimate) implements Comparable<Node> {
        @Override
        public int compareTo(Node other) {
            return Double.compare(estimate, other.estimate);
        }
    }
}
