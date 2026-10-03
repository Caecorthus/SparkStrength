package annina.sparkstrength.role.toxicologist;

import java.util.function.IntPredicate;

/**
 * Pure decisions for Blue Vitriol targets and blue-item delivery. No Minecraft types, so the same answer is computed
 * from synced data on the client (prediction) and on the server (authority).
 * 蓝矾目标与蓝毒道具发放的纯判定。不依赖 Minecraft 类型；客户端（预测）与服务端（权威）用同步数据得出相同结论。
 */
public final class ToxicologistBlueItemRules {
    public static final int HOTBAR_SIZE = 9;
    public static final int NO_SLOT = -1;

    public enum BlockTarget {
        NONE,
        PLATE,
        BED
    }

    public enum StackTarget {
        NONE,
        FOOD,
        CAPSULE
    }

    private ToxicologistBlueItemRules() {
    }

    public static BlockTarget blockTarget(boolean foodPlatter, boolean trimmedBed) {
        if (foodPlatter) {
            return BlockTarget.PLATE;
        }
        return trimmedBed ? BlockTarget.BED : BlockTarget.NONE;
    }

    /**
     * True when Blue Vitriol takes over a block use. A blacklisted block is left to Wathe's own FAIL, and anything
     * else passes so the block's normal interaction (eating from a plate, sleeping) still runs.
     * 为 true 时由蓝矾接管方块交互。黑名单方块交给 Wathe 自己返回 FAIL；其余情况放行，保留餐盘取食、睡觉等原交互。
     */
    public static boolean shouldHandleBlockUse(
            boolean holdingVitriol,
            BlockTarget target,
            boolean interactionBlacklisted,
            boolean toxicologistLike,
            boolean playingAndAlive,
            boolean spectator
    ) {
        return holdingVitriol
                && target != BlockTarget.NONE
                && !interactionBlacklisted
                && toxicologistLike
                && playingAndAlive
                && !spectator;
    }

    /**
     * Only native Wathe poison can be converted; blue-only or clean stacks are not targets, so the click falls back to
     * vanilla slot handling.
     * 只有 Wathe 原生毒可以转化；只带蓝毒或无毒的物品不是目标，点击会回落到原版格子逻辑。
     */
    public static StackTarget stackTarget(
            boolean foodOrDrink,
            boolean nativePoisoned,
            boolean filledCapsule,
            boolean capsuleContentsNativePoisoned
    ) {
        if (filledCapsule) {
            return capsuleContentsNativePoisoned ? StackTarget.CAPSULE : StackTarget.NONE;
        }
        return foodOrDrink && nativePoisoned ? StackTarget.FOOD : StackTarget.NONE;
    }

    /**
     * Hotbar slot for a bought stack: first merge into an equal, non-full stack anywhere in the hotbar, else the first
     * empty slot, else {@link #NO_SLOT}. Wathe's default delivery only uses empty slots, which would waste a slot per
     * purchase of a stackable item.
     * 购买物品放入的快捷栏格：优先合并到快捷栏中相同且未满的堆叠，否则放入第一个空格，都没有则返回 NO_SLOT。
     * Wathe 默认只放空格，可堆叠物品每买一次就会多占一格。
     */
    public static int hotbarSlot(int hotbarSize, IntPredicate canMergeInto, IntPredicate isEmpty) {
        for (int slot = 0; slot < hotbarSize; slot++) {
            if (canMergeInto.test(slot)) {
                return slot;
            }
        }
        for (int slot = 0; slot < hotbarSize; slot++) {
            if (isEmpty.test(slot)) {
                return slot;
            }
        }
        return NO_SLOT;
    }
}
