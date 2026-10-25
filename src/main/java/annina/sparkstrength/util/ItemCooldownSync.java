package annina.sparkstrength.util;

import annina.sparkstrength.mixin.minecraft.ItemCooldownEntryAccessor;
import annina.sparkstrength.mixin.minecraft.ItemCooldownManagerAccessor;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.Item;
import net.minecraft.network.packet.s2c.play.CooldownUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Exact vanilla item cooldowns for locks SparkStrength keeps on its own server clock: the Vulture skateboard's opening
 * lock and ready tick, the M67 and drone round openings, and a re-asserted Bodyguard shield cooldown. That clock is the
 * real gate; the vanilla entry only shows it in the slot (and in Wathe's timer above the hotbar).
 * 为 SparkStrength 按自身服务端时钟维护的锁写入精确的原版物品冷却：秃鹫滑板的开局锁与可用时刻、M67 与无人机的开局锁，
 * 以及补回的保镖盾冷却。真正的判定是该时钟；原版条目只负责在物品栏（以及 Wathe 快捷栏上方的计时）中显示。
 *
 * <p>Why not {@code ItemCooldownManager.set}: SparkTraits Fast Hands (x0.9) and NoellesRoles Stimulation (x0.8)
 * shorten every server-side {@code set} at HEAD. A per-tick re-sync through {@code set} then never reaches the lock
 * and re-sends every tick; each {@code CooldownUpdateS2CPacket} restarts the client's sweep, so the slot stayed full
 * until the last ~2 s and the timers read short of the real lock. These writes rewrite the live entry in place (or let
 * {@code set} create it, then correct it) and send the exact time. Cooldowns the vanilla entry itself gates (M67 throws,
 * drone returns, ...) keep using {@code set}, so those modifiers still apply there.
 * 为何不用 {@code ItemCooldownManager.set}：SparkTraits 快手（×0.9）与 NoellesRoles 亢奋（×0.8）会在 HEAD 缩短服务端
 * 的每次 {@code set}。经 {@code set} 的逐刻重同步永远达不到锁的时长，于是每刻重发；每个 {@code CooldownUpdateS2CPacket}
 * 都让客户端冷却动画从头开始，物品栏冷却条一直是满的、直到最后约 2 秒才下降，计时也比真实的锁短。这里原地改写现有条目
 * （无条目时借 {@code set} 创建后再校正）并发送精确时长。由原版条目本身判定的冷却（M67 投掷、无人机归还等）仍使用
 * {@code set}，这些倍率在那里照常生效。</p>
 */
public final class ItemCooldownSync {
    private ItemCooldownSync() {
    }

    /** Remaining ticks of the vanilla entry, 0 when none. / 原版条目的剩余刻数，无条目时为 0。 */
    public static int remainingTicks(ItemCooldownManager manager, Item item) {
        ItemCooldownManagerAccessor access = (ItemCooldownManagerAccessor) manager;
        Object entry = access.sparkstrength$getEntries().get(item);
        return entry instanceof ItemCooldownEntryAccessor bounds
                ? Math.max(0, bounds.sparkstrength$getEndTick() - access.sparkstrength$getTick())
                : 0;
    }

    /**
     * Per-tick upkeep of a server-clock lock: raises the entry to exactly {@code lockTicks} when it is short by more
     * than {@link ItemCooldownSyncRules#PHASE_TOLERANCE_TICKS}; never shortens. Returns true when it wrote.
     * 服务端时钟锁的逐刻维护：条目比 {@code lockTicks} 短超过容差时，精确提高到 {@code lockTicks}；绝不缩短。写入时返回 true。
     */
    public static boolean raiseExact(ServerPlayerEntity player, Item item, int lockTicks) {
        return ItemCooldownSyncRules.needsRaise(remainingTicks(player.getItemCooldownManager(), item), lockTicks)
                && writeExact(player, item, lockTicks);
    }

    /**
     * Makes the entry hold exactly {@code ticks} remaining when it held less; never shortens (a longer entry, e.g. a
     * SparkTraits forced floor a {@code set} hook kept, is left alone). Returns false for a non-positive length, or when
     * another mod cancelled {@code set} and no entry exists.
     * 条目剩余不足 {@code ticks} 时精确写为 {@code ticks}；绝不缩短（更长的条目，例如 {@code set} 钩子保留的 SparkTraits
     * 强制下限，保持不变）。时长非正，或其他模组取消了 {@code set} 导致无条目时返回 false。
     */
    public static boolean writeExact(ServerPlayerEntity player, Item item, int ticks) {
        if (ticks <= 0) {
            return false;
        }
        ItemCooldownManager manager = player.getItemCooldownManager();
        ItemCooldownManagerAccessor access = (ItemCooldownManagerAccessor) manager;
        Object entry = access.sparkstrength$getEntries().get(item);
        if (entry == null) {
            // Vanilla path only creates the entry (and runs set hooks); a modified length is corrected below.
            // 仅借原版路径创建条目（并触发 set 钩子）；被倍率修改的时长在下方校正。
            manager.set(item, ticks);
            entry = access.sparkstrength$getEntries().get(item);
        }
        if (!(entry instanceof ItemCooldownEntryAccessor bounds)) {
            return false;
        }
        int tick = access.sparkstrength$getTick();
        int end = ItemCooldownSyncRules.endTick(tick, ticks);
        if (bounds.sparkstrength$getEndTick() >= end) {
            // Already long enough: set wrote it unmodified (and sent it), or a hook lengthened it.
            // 已足够长：set 以原值写入（并已发送），或被钩子延长。
            return true;
        }
        bounds.sparkstrength$setStartTick(tick);
        bounds.sparkstrength$setEndTick(end);
        if (player.networkHandler != null) {
            player.networkHandler.sendPacket(new CooldownUpdateS2CPacket(item, end - tick));
        }
        return true;
    }
}
