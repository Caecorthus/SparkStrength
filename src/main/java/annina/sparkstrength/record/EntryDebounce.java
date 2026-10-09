package annina.sparkstrength.record;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Turns a per-tick "state is active" flag into debounced entries per player: an active tick is a new entry only after
 * at least {@code quietTicks} inactive ticks in a row (and always at least one), so a state that flickers off for
 * fewer than {@code quietTicks} ticks continues the same entry. Pure (no Minecraft types); server-thread use only.
 * 把每 tick 的“状态生效”标记转成按玩家去抖的“进入”事件：只有在连续熄灭至少 quietTicks 个 tick（且至少 1 个）之后，
 * 生效 tick 才算新的进入，因此熄灭少于 quietTicks 个 tick 的闪断仍算同一次进入。纯逻辑（不含 Minecraft 类型），仅在服务端线程使用。
 */
public final class EntryDebounce {
    /** Smallest tick gap between two active ticks that starts a new entry. 开始新进入所需的两次生效 tick 最小间隔。 */
    private final long entryGap;
    private final Map<UUID, Long> lastActiveTick = new HashMap<>();

    public EntryDebounce(int quietTicks) {
        if (quietTicks < 0) {
            throw new IllegalArgumentException("quietTicks must not be negative: " + quietTicks);
        }
        this.entryGap = Math.max(quietTicks, 1) + 1L;
    }

    /**
     * Feeds one tick of {@code player}'s state; returns true when this tick is a new entry. A clock that went
     * backwards also counts as a new entry rather than suppressing entries until it catches up.
     * 输入该玩家一个 tick 的状态；本 tick 为新的进入时返回 true。时钟倒退时也视为新的进入，而不是一直压制到追上为止。
     */
    public boolean observe(UUID player, boolean active, long worldTime) {
        if (player == null || !active) {
            return false;
        }
        Long last = lastActiveTick.put(player, worldTime);
        return last == null || worldTime < last || worldTime - last >= entryGap;
    }

    /** Death, reset or round end: the next active tick is a new entry. 死亡、重置或回合结束：下一个生效 tick 算新的进入。 */
    public void forget(UUID player) {
        if (player != null) {
            lastActiveTick.remove(player);
        }
    }

    public int trackedPlayers() {
        return lastActiveTick.size();
    }
}
