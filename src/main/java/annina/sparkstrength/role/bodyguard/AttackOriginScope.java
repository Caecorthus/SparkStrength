package annina.sparkstrength.role.bodyguard;

import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Blast centre of the explosion whose kills are running on this thread. Kill events only carry the killer, so blast
 * code (Wathe grenade, M67, Bomber drone) wraps its kill loop here and the Democracy Shield checks the real centre.
 * 当前线程正在结算的爆炸中心。击杀事件只带攻击者，因此爆炸代码（Wathe 手雷、M67、炸弹客无人机）在这里包住击杀循环，
 * 民主盾牌据此按真实爆炸中心判定方向。
 */
public final class AttackOriginScope {
    private static final ThreadLocal<Deque<Vec3d>> BLASTS = ThreadLocal.withInitial(ArrayDeque::new);

    private AttackOriginScope() {
    }

    public static void runBlast(Vec3d center, Runnable action) {
        Deque<Vec3d> blasts = BLASTS.get();
        blasts.push(center);
        try {
            action.run();
        } finally {
            blasts.pop();
            if (blasts.isEmpty()) {
                BLASTS.remove();
            }
        }
    }

    /** Innermost blast centre, or null outside any blast. / 最内层的爆炸中心；不在爆炸中时为 null。 */
    public static @Nullable Vec3d currentBlast() {
        Deque<Vec3d> blasts = BLASTS.get();
        Vec3d center = blasts.peek();
        if (blasts.isEmpty()) {
            BLASTS.remove();
        }
        return center;
    }
}
