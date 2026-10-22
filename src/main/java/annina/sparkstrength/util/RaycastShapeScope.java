package annina.sparkstrength.util;

import java.util.function.Supplier;

/**
 * Marks block-shape queries made for a {@code RaycastContext} so door-passing collision exemptions stay movement-only.
 * A COLLIDER ray takes its shape context from the casting entity, so an exemption keyed on that entity would otherwise
 * also let its sight and aim rays through closed doors.
 * 标记为 RaycastContext 发起的方块形状查询，使穿门碰撞豁免只作用于移动。COLLIDER 射线沿用施放实体的形状上下文，
 * 否则按实体生效的豁免也会让其视线与瞄准射线穿过关闭的门。
 */
public final class RaycastShapeScope {
    /**
     * Per-thread nesting depth; one mutable cell per thread, so the per-block-step path allocates nothing. A depth above
     * zero means a ray query is running on this thread (client and server threads never share it).
     * 每个线程的嵌套深度；每个线程只有一个可变单元，逐方块步进的路径不产生分配。深度大于零表示本线程正在执行射线查询
     * （客户端与服务端线程互不共享）。
     */
    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    private RaycastShapeScope() {
    }

    public static boolean isRaycast() {
        return DEPTH.get()[0] > 0;
    }

    /** Nested queries and exceptions restore the caller's scope on both logical sides.
     * 双端的嵌套查询与异常都会恢复调用者的作用域。 */
    public static <T> T query(Supplier<T> query) {
        int[] depth = enter();
        try {
            return query.get();
        } finally {
            exit(depth);
        }
    }

    /**
     * Allocation-free form of {@link #query} for the ray wrapper: pair every {@code enter} with {@code exit(token)} in a
     * {@code finally}, on the same thread.
     * {@link #query} 的无分配形式，供射线包装使用：每次 {@code enter} 都必须在同一线程的 {@code finally} 中以
     * {@code exit(token)} 配对。
     */
    public static int[] enter() {
        int[] depth = DEPTH.get();
        depth[0]++;
        return depth;
    }

    /** Leaves the scope {@link #enter} opened. / 退出 {@link #enter} 打开的作用域。 */
    public static void exit(int[] token) {
        token[0]--;
    }
}
