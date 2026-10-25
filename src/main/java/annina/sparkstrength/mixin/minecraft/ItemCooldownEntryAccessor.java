package annina.sparkstrength.mixin.minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads, and for {@code util.ItemCooldownSync} rewrites, the bounds of one vanilla item-cooldown entry. Only exact
 * server-clock locks write through the setters; every other cooldown (the Toxicologist's antidote discount included)
 * still goes through {@code ItemCooldownManager.set}.
 * 读取单个原版物品冷却条目的起止 tick，并供 {@code util.ItemCooldownSync} 改写。只有按服务端时钟维护的精确锁使用写入器；
 * 其他冷却（包括毒理学家的解毒剂冷却抵扣）仍走 {@code ItemCooldownManager.set}。
 */
@Mixin(targets = "net.minecraft.entity.player.ItemCooldownManager$Entry")
public interface ItemCooldownEntryAccessor {
    @Accessor("endTick")
    int sparkstrength$getEndTick();

    @Mutable
    @Accessor("startTick")
    void sparkstrength$setStartTick(int startTick);

    @Mutable
    @Accessor("endTick")
    void sparkstrength$setEndTick(int endTick);
}
