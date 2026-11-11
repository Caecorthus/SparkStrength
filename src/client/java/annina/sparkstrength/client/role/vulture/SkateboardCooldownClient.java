package annina.sparkstrength.client.role.vulture;

import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.component.vulture.SkateboardRideComponent;
import annina.sparkstrength.mixin.minecraft.ItemCooldownEntryAccessor;
import annina.sparkstrength.mixin.minecraft.ItemCooldownManagerAccessor;
import annina.sparkstrength.role.vulture.VultureSkateboardRules;
import annina.sparkstrength.util.ItemCooldownSyncRules;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.Item;

/**
 * Draws the skateboard's slot sweep (and Wathe's timer above the hotbar) from the synced lock window instead of from
 * cooldown packets. A vanilla client entry starts its sweep over at every packet and counts down on the client's own
 * clock, so the bar could sit full until the last seconds and the timer could run ahead of the real lock. Each frame
 * the local entry is rewritten in place to span exactly the lock window; the server still decides every use.
 * 按同步的锁区间绘制滑板栏位的冷却条（以及 Wathe 快捷栏上方的计时），而不是依赖冷却数据包。原版客户端条目每收到一个包
 * 就让冷却条从头开始，且按客户端自己的时钟倒数，冷却条可能一直是满的、直到最后几秒才下降，计时也可能跑在真实的锁前面。
 * 这里每帧原地改写本地条目，使其恰好覆盖锁区间；能否使用仍完全由服务端判定。
 */
public final class SkateboardCooldownClient {
    /** Whether the board's current local entry was last drawn by {@link #apply}. / 滑板当前的本地条目是否由 {@link #apply} 绘制。 */
    private static boolean wroteEntry;

    private SkateboardCooldownClient() {
    }

    /** Ticks left of the local player's board lock on the shared clock, 0 with no lock. / 本地玩家滑板锁按共用时钟的剩余刻数，无锁时为 0。 */
    public static int lockRemaining(ClientPlayerEntity player) {
        SkateboardRideComponent ride = SkateboardRideComponent.KEY.get(player);
        return ride.hasLock()
                ? VultureSkateboardRules.lockRemaining(ride.lockEndTick(), VultureClock.now(player.getWorld()))
                : 0;
    }

    public static void apply(ClientPlayerEntity player) {
        SkateboardRideComponent ride = SkateboardRideComponent.KEY.get(player);
        Item board = SparkStrengthItems.skateboard();
        ItemCooldownManager manager = player.getItemCooldownManager();
        if (!ride.hasLock()) {
            if (wroteEntry) {
                // The server just dropped the lock (ran out, admin clear, round over, no longer the Vulture). The
                // entry we drew goes with it, whichever of the two packets (cooldown removal, lock sync) came first.
                // 服务端刚解除了锁（到期、管理员清除、回合结束、不再是秃鹫）。我们绘制的条目随之移除，
                // 无论两个数据包（移除冷却、锁同步）谁先到达。
                wroteEntry = false;
                manager.remove(board);
            }
            return;
        }
        long now = VultureClock.now(player.getWorld());
        int remaining = VultureSkateboardRules.lockRemaining(ride.lockEndTick(), now);
        if (remaining <= 0) {
            // Nothing to hold up: the last rewrite left an entry that runs out by itself.
            // 无需维持：上一次改写留下的条目会自行到期。
            return;
        }
        ItemCooldownManagerAccessor access = (ItemCooldownManagerAccessor) manager;
        Object entry = access.sparkstrength$getEntries().get(board);
        if (entry == null) {
            // Lost or never received (a respawn makes a fresh manager); the client-side set sends nothing.
            // 条目丢失或从未收到（重生会换新的管理器）；客户端的 set 不发送任何数据。
            manager.set(board, remaining);
            entry = access.sparkstrength$getEntries().get(board);
        }
        if (!(entry instanceof ItemCooldownEntryAccessor bounds)) {
            return;
        }
        int tick = access.sparkstrength$getTick();
        long elapsed = Math.max(0L, now - ride.lockStartTick());
        bounds.sparkstrength$setStartTick((int) Math.max(Integer.MIN_VALUE, tick - elapsed));
        bounds.sparkstrength$setEndTick(ItemCooldownSyncRules.endTick(tick, remaining));
        wroteEntry = true;
    }
}
