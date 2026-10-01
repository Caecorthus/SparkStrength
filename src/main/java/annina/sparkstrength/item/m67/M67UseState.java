package annina.sparkstrength.item.m67;

import java.util.UUID;

/** Identity matters: another identical stack must not inherit a charge. / 相同物品的另一组堆叠不能继承蓄力。 */
final class M67UseState {
    private final UUID roundId;
    private final Object stack;
    private final Object hand;
    private final int slot;
    private final int count;
    private final long startTick;
    private boolean completed;

    M67UseState(UUID roundId, Object stack, Object hand, int slot, int count, long startTick) {
        this.roundId = roundId;
        this.stack = stack;
        this.hand = hand;
        this.slot = slot;
        this.count = count;
        this.startTick = startTick;
    }

    UUID roundId() {
        return roundId;
    }

    boolean matches(UUID currentRound, Object heldStack, Object activeHand, int selectedSlot, int heldCount) {
        return !completed && roundId.equals(currentRound) && stack == heldStack && hand == activeHand
                && slot == selectedSlot && count == heldCount;
    }

    /** The state holds no player, so the service resolves the required charge. / 状态不持有玩家，所需蓄力由服务解析后传入。 */
    boolean finish(long now, boolean authorizedRelease, int chargeTicks) {
        if (completed) {
            return false;
        }
        completed = true;
        return authorizedRelease && now - startTick >= chargeTicks;
    }
}
