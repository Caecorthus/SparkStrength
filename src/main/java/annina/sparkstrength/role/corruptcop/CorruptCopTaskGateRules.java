package annina.sparkstrength.role.corruptcop;

/**
 * Pure task gate for the Corrupt Cop's SparkStrength buffs: the "Arrogant ASF" toggle and the keyed instinct x-ray
 * unlock after {@code totalPlayers / 8} completed tasks, at least 1 and at most 3. Total players counts every round
 * role holder, dead ones included, the same count NoellesRoles uses for the cop's Moment threshold; integer division
 * matches it.
 * 黑警 SparkStrength 增强的纯任务门槛：“展示豪度”开关与按键本能透视需完成 {@code 总人数 / 8} 个任务后解锁，至少 1 个、
 * 至多 3 个。总人数为本局全部身份持有者（含已死亡者），与 NoellesRoles 计算黑警时刻门槛所用人数一致；同样取整除。
 */
public final class CorruptCopTaskGateRules {
    public static final int PLAYERS_PER_TASK = 8;
    public static final int MIN_REQUIRED_TASKS = 1;
    public static final int MAX_REQUIRED_TASKS = 3;

    private CorruptCopTaskGateRules() {
    }

    public static int requiredTasks(int totalPlayers) {
        int byPlayers = Math.max(0, totalPlayers) / PLAYERS_PER_TASK;
        return Math.max(MIN_REQUIRED_TASKS, Math.min(MAX_REQUIRED_TASKS, byPlayers));
    }

    public static boolean isUnlocked(int completedTasks, int requiredTasks) {
        return completedTasks >= requiredTasks;
    }

    public static int remainingTasks(int completedTasks, int requiredTasks) {
        return Math.max(0, requiredTasks - completedTasks);
    }

    /**
     * Whether this completion is the one that unlocks.
     * 本次完成是否恰好解锁。
     */
    public static boolean crossesUnlock(int previousTasks, int completedTasks, int requiredTasks) {
        return !isUnlocked(previousTasks, requiredTasks) && isUnlocked(completedTasks, requiredTasks);
    }
}
