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
    private static final ThreadLocal<Boolean> ACTIVE = new ThreadLocal<>();

    private RaycastShapeScope() {
    }

    public static boolean isRaycast() {
        return Boolean.TRUE.equals(ACTIVE.get());
    }

    /** Nested queries and exceptions restore the caller's scope on both logical sides.
     * 双端的嵌套查询与异常都会恢复调用者的作用域。 */
    public static <T> T query(Supplier<T> query) {
        Boolean previous = ACTIVE.get();
        ACTIVE.set(true);
        try {
            return query.get();
        } finally {
            if (previous == null) {
                ACTIVE.remove();
            } else {
                ACTIVE.set(previous);
            }
        }
    }
}
