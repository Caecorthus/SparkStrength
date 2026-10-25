package annina.sparkstrength.util;

/**
 * Pure checks for {@link ItemCooldownSync}. No Minecraft types, so tests run them directly.
 * {@link ItemCooldownSync} 的纯判定；不含 Minecraft 类型，便于测试直接执行。
 */
public final class ItemCooldownSyncRules {
    /**
     * Slack between the cooldown manager's own tick counter and world time. Both advance once per server tick, but in
     * different phases (world tick vs player tick), so a lock compared from another phase can look a tick short. Within
     * this slack the entry is left alone instead of being re-sent (each re-send restarts the client's sweep).
     * 冷却管理器自身刻计数与世界时间之间的容差。二者每个服务端刻各前进一次，但处于不同阶段（世界 tick 与玩家 tick），
     * 跨阶段比较时锁可能显得差一刻。差额在此范围内时不重发条目（每次重发都会让客户端冷却动画从头开始）。
     */
    public static final int PHASE_TOLERANCE_TICKS = 5;

    private ItemCooldownSyncRules() {
    }

    /**
     * Whether an entry with {@code remainingTicks} left must be raised to {@code lockTicks}. Raise only: a longer entry
     * (another mod's forced penalty) is never shortened. The phase tolerance only covers a running entry; a missing
     * one is always written, so the slot never reads ready while the lock still holds.
     * 剩余 {@code remainingTicks} 的条目是否需要提高到 {@code lockTicks}。只提高：更长的条目（其他模组的强制惩罚）绝不缩短。
     * 阶段容差只适用于仍在计时的条目；条目不存在时总是写入，锁仍生效时栏位绝不显示为可用。
     */
    public static boolean needsRaise(int remainingTicks, int lockTicks) {
        if (lockTicks <= 0) {
            return false;
        }
        int remaining = Math.max(0, remainingTicks);
        return remaining == 0 || (long) remaining + PHASE_TOLERANCE_TICKS < lockTicks;
    }

    /** {@code tick + ticks} without int overflow. / 不溢出的 {@code tick + ticks}。 */
    public static int endTick(int tick, int ticks) {
        return (int) Math.min(Integer.MAX_VALUE, (long) tick + Math.max(0, ticks));
    }
}
