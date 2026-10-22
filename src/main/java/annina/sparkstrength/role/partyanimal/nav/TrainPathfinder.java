package annina.sparkstrength.role.partyanimal.nav;

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
     * A* from {@code start} to {@code goal}; the cells in walking order (start first). When the goal can't be reached
     * within the budget, the path to the explored cell nearest the goal (just {@code start} if nothing is nearer), so a
     * target standing on a counter is still approached; check the last cell to tell. Empty only when start or goal is
     * not standable.
     * 从 start 到 goal 的 A*；按行走顺序返回格子（首个为 start）。预算内到不了目标时，返回通往已探索格中离目标最近者的路径（没有更近的
     * 就只有 start），因此站在柜台上的目标仍会被靠近；用最后一格判断是否到达。仅当起点或终点不可站立时为空。
     */
    public static List<Long> path(TrainNavGrid grid, long start, long goal, int budget) {
        if (start == Long.MIN_VALUE || goal == Long.MIN_VALUE
                || !grid.isStandable(start) || !grid.isStandable(goal)) {
            return List.of();
        }
        if (start == goal) {
            return List.of(start);
        }
        Search search = new Search(grid, goal, true);
        search.best.put(start, 0.0);
        search.open.add(start, 0.0, heuristic(start, goal, grid));
        long closest = start;
        double closestDistance = heuristic(start, goal, grid);
        int expanded = 0;
        while (!search.open.isEmpty() && expanded < budget) {
            search.open.poll();
            long cell = search.open.polledCell;
            double cost = search.open.polledCost;
            if (cost > search.best.get(cell)) {
                continue;
            }
            if (cell == goal) {
                return rebuild(search.parent, goal);
            }
            double distance = heuristic(cell, goal, grid);
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = cell;
            }
            expanded++;
            search.expand(cell, cost);
        }
        return rebuild(search.parent, closest);
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
        LongSet goalCells = goals instanceof LongSet cells ? cells : new LongOpenHashSet(goals);
        Search search = new Search(grid, Long.MIN_VALUE, false);
        search.best.put(start, 0.0);
        search.open.add(start, 0.0, 0.0);
        int expanded = 0;
        while (!search.open.isEmpty() && expanded < budget) {
            search.open.poll();
            long cell = search.open.polledCell;
            double cost = search.open.polledCost;
            if (cost > search.best.get(cell)) {
                continue;
            }
            if (goalCells.contains(cell)) {
                return cell;
            }
            expanded++;
            search.expand(cell, cost);
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

    private static List<Long> rebuild(Long2LongOpenHashMap parent, long goal) {
        List<Long> cells = new ArrayList<>();
        long cell = goal;
        while (cell != Long.MIN_VALUE) {
            cells.add(cell);
            cell = parent.get(cell);
        }
        Collections.reverse(cells);
        return cells;
    }

    /**
     * One search's tables, and the neighbour sink reused for every expansion (no lambda per cell). Without a goal it
     * is Dijkstra: the estimate is the cost and no parents are kept.
     * 一次搜索的表，以及每次扩展复用的邻格回调（不为每格新建 lambda）。没有目标时即 Dijkstra：估值等于代价，不记父格。
     */
    private static final class Search implements TrainNavGrid.NeighborSink {
        final TrainNavGrid grid;
        final long goal;
        final Long2DoubleOpenHashMap best = new Long2DoubleOpenHashMap();
        final Long2LongOpenHashMap parent;
        final OpenSet open = new OpenSet();
        long fromCell;
        double fromCost;

        Search(TrainNavGrid grid, long goal, boolean keepParents) {
            this.grid = grid;
            this.goal = goal;
            best.defaultReturnValue(Double.MAX_VALUE);
            parent = keepParents ? new Long2LongOpenHashMap() : null;
            if (parent != null) {
                parent.defaultReturnValue(Long.MIN_VALUE);
            }
        }

        void expand(long cell, double cost) {
            fromCell = cell;
            fromCost = cost;
            grid.neighbors(cell, this);
        }

        @Override
        public void accept(long next, double step) {
            double cost = fromCost + step;
            if (cost < best.get(next)) {
                best.put(next, cost);
                if (parent != null) {
                    parent.put(next, fromCell);
                    open.add(next, cost, cost + heuristic(next, goal, grid));
                } else {
                    open.add(next, cost, cost);
                }
            }
        }
    }

    /**
     * Binary min-heap on the estimate over parallel primitive arrays. Sifts exactly like {@link java.util.PriorityQueue}
     * so equal estimates leave in the same order, and paths stay the same.
     * 以估值为键、基于并行基本类型数组的二叉小顶堆。上浮下沉与 PriorityQueue 完全一致，估值相同时出堆顺序不变，路径也就不变。
     */
    static final class OpenSet {
        private long[] cells = new long[64];
        private double[] costs = new double[64];
        private double[] estimates = new double[64];
        private int size;
        long polledCell;
        double polledCost;

        boolean isEmpty() {
            return size == 0;
        }

        void add(long cell, double cost, double estimate) {
            if (size == cells.length) {
                int capacity = size * 2;
                cells = Arrays.copyOf(cells, capacity);
                costs = Arrays.copyOf(costs, capacity);
                estimates = Arrays.copyOf(estimates, capacity);
            }
            int k = size++;
            while (k > 0) {
                int up = (k - 1) >>> 1;
                if (Double.compare(estimate, estimates[up]) >= 0) {
                    break;
                }
                move(up, k);
                k = up;
            }
            set(k, cell, cost, estimate);
        }

        /** Removes the head into {@link #polledCell} and {@link #polledCost}. / 取出堆顶到 polledCell 与 polledCost。 */
        void poll() {
            polledCell = cells[0];
            polledCost = costs[0];
            int n = --size;
            if (n == 0) {
                return;
            }
            long cell = cells[n];
            double cost = costs[n];
            double estimate = estimates[n];
            int k = 0;
            int half = n >>> 1;
            while (k < half) {
                int child = (k << 1) + 1;
                int right = child + 1;
                if (right < n && Double.compare(estimates[child], estimates[right]) > 0) {
                    child = right;
                }
                if (Double.compare(estimate, estimates[child]) <= 0) {
                    break;
                }
                move(child, k);
                k = child;
            }
            set(k, cell, cost, estimate);
        }

        private void move(int from, int to) {
            cells[to] = cells[from];
            costs[to] = costs[from];
            estimates[to] = estimates[from];
        }

        private void set(int at, long cell, double cost, double estimate) {
            cells[at] = cell;
            costs[at] = cost;
            estimates[at] = estimate;
        }
    }
}
