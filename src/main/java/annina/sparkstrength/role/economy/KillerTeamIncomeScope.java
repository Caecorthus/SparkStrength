package annina.sparkstrength.role.economy;

/**
 * Excludes only the scoped passive receipt, never a whole tick or reward callback.
 * 仅排除作用域内的被动入账，不排除整个 tick 或奖励回调。
 */
public final class KillerTeamIncomeScope {
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private KillerTeamIncomeScope() {
    }

    public static boolean isSuppressed() {
        return DEPTH.get() > 0;
    }

    public static void withoutContribution(Runnable receipt) {
        int previousDepth = DEPTH.get();
        DEPTH.set(previousDepth + 1);
        try {
            receipt.run();
        } finally {
            if (previousDepth == 0) {
                DEPTH.remove();
            } else {
                DEPTH.set(previousDepth);
            }
        }
    }
}
