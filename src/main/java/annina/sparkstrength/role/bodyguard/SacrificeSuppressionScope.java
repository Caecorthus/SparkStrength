package annina.sparkstrength.role.bodyguard;

import java.util.function.Supplier;

/**
 * Marks the code where NoellesRoles decides a Bodyguard sacrifice: the KillPlayer.BEFORE dispatch and Taotie's
 * swallow. Inside it, the server answers "not my target" to BodyguardPlayerComponent.isCurrentTarget, so the sacrifice
 * never fires; everywhere else (the client highlight, SparkTraits' Impostor reward in AFTER) the method is untouched.
 * KillPlayer.AFTER dispatch is marked unsuppressed, so a kill nested inside a BEFORE listener still reports the truth.
 * Server code that needs the target inside BEFORE must read getCurrentTarget() instead.
 * 标记 NoellesRoles 判定保镖替死的代码：KillPlayer.BEFORE 分发与饕餮吞噬。范围内服务端对
 * BodyguardPlayerComponent.isCurrentTarget 回答“不是目标”，替死因此不会触发；其他地方（客户端高亮、SparkTraits
 * 在 AFTER 中的内鬼奖励）不受影响。KillPlayer.AFTER 分发标记为不抑制，所以嵌套在 BEFORE 监听器内的击杀仍得到真实结果。
 * 需要在 BEFORE 中读取目标的服务端代码必须改用 getCurrentTarget()。
 */
public final class SacrificeSuppressionScope {
    private static final ThreadLocal<Boolean> SUPPRESSED = ThreadLocal.withInitial(() -> false);

    private SacrificeSuppressionScope() {
    }

    public static <T> T call(boolean suppressed, Supplier<T> action) {
        boolean previous = SUPPRESSED.get();
        SUPPRESSED.set(suppressed);
        try {
            return action.get();
        } finally {
            if (previous) {
                SUPPRESSED.set(true);
            } else {
                SUPPRESSED.remove();
            }
        }
    }

    public static void run(boolean suppressed, Runnable action) {
        call(suppressed, () -> {
            action.run();
            return null;
        });
    }

    public static boolean isSuppressed() {
        return SUPPRESSED.get();
    }
}
