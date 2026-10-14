package annina.sparkstrength.record;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-world blackout state behind the blackout record events: turns "is the blackout active" observations into edges
 * (lights out / lights back), and lets each player's flashlight event through once per blackout. Several callers feed
 * the same world (the world tick, the shop purchase, the Engineer's restoration); only the first to see a change gets
 * the edge, so each real edge is recorded exactly once. A blackout's flashlight entries are kept until its end edge:
 * Wathe applies the first blackout effects inside the purchase, so a flashlight can be seen before the start edge.
 * Pure (no Minecraft types); server-thread use only.
 * 停电记录事件背后的按世界停电状态：把“停电是否生效”的观察转换成边沿（熄灯 / 来电），并让每名玩家的手电筒事件每次停电只通过一次。
 * 同一世界有多个调用方输入（世界 tick、商店购买、工程师恢复电力），只有最先看到变化的那个得到边沿，因此每个真实边沿只记录一次。
 * 一次停电的手电筒记录保留到该次停电的结束边沿：Wathe 在购买过程中就施加首轮停电效果，所以手电筒可能先于开始边沿被看到。
 * 纯逻辑（不含 Minecraft 类型），仅在服务端线程使用。
 */
public final class BlackoutRecordState<K> {
    private final Map<K, Boolean> active = new HashMap<>();
    private final Map<K, Set<UUID>> flashlightPlayers = new HashMap<>();

    /**
     * Feeds the world's current blackout state; returns true when it differs from the last state fed for that world
     * (an unseen world counts as lights on). An end edge forgets that blackout's flashlight entries.
     * 输入该世界当前的停电状态；与上次输入的状态不同（未见过的世界视为亮灯）时返回 true。结束边沿会清掉该次停电的手电筒记录。
     */
    public boolean observe(K world, boolean blackoutActive) {
        if (world == null) {
            return false;
        }
        boolean previous = Boolean.TRUE.equals(active.put(world, blackoutActive));
        if (previous == blackoutActive) {
            return false;
        }
        if (!blackoutActive) {
            flashlightPlayers.remove(world);
        }
        return true;
    }

    public boolean isActive(K world) {
        return world != null && Boolean.TRUE.equals(active.get(world));
    }

    /**
     * True the first time {@code player}'s flashlight is seen in the world's current blackout, false afterwards until
     * that blackout ends.
     * 该玩家的手电筒在该世界本次停电中第一次被看到时返回 true，之后直到本次停电结束都返回 false。
     */
    public boolean firstFlashlight(K world, UUID player) {
        if (world == null || player == null) {
            return false;
        }
        return flashlightPlayers.computeIfAbsent(world, ignored -> new HashSet<>()).add(player);
    }

    /** Round end: lights considered on, no edge, no flashlight entries. 回合结束：视为亮灯，不产生边沿，清空手电筒记录。 */
    public void forget(K world) {
        if (world != null) {
            active.remove(world);
            flashlightPlayers.remove(world);
        }
    }
}
