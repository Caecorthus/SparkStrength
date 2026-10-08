package annina.sparkstrength.mixin.sparkfactionapi;

import annina.sparkstrength.compat.SparkFactionAdminClear;
import annina.sparkstrength.mixin.minecraft.ServerItemCooldownManagerAccessor;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerItemCooldownManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Optional SparkFactionAPI seam, admin path only: {@code /sparkfactionapi:clearCooldown} (the same method body since
 * 0.1.5.3) only removes the held item's vanilla cooldown. That does not stick for SparkStrength locks re-synced every
 * tick from their own state (Bomber drones, the M67 opening, the Vulture skateboard, the democracy shield). When the
 * command's {@code remove} really cleared the item, {@link SparkFactionAdminClear#onCooldownCleared} lets the item's
 * owner release its lock. Role mechanics that remove cooldowns never come through here. {@code @Pseudo} plus
 * {@code require = 0}: a silent no-op without SparkFactionAPI or if the command changes.
 * 可选的 SparkFactionAPI 接缝，仅限管理员路径：/sparkfactionapi:clearCooldown（自 0.1.5.3 起方法体相同）只移除手持物品的原版冷却。
 * 对每刻按自身状态重新同步的 SparkStrength 锁（炸弹客无人机、M67 开局锁、秃鹫滑板、民主盾牌）这并不生效。命令的
 * {@code remove} 确实清除了该物品时，由 SparkFactionAdminClear#onCooldownCleared 交给该物品的所属逻辑解除锁定。移除冷却的
 * 职业机制不会经过这里。@Pseudo 与 require = 0：未安装 SparkFactionAPI 或命令变更时静默失效。
 */
@Pseudo
@Mixin(targets = "dev.caecorthus.sparkfactionapi.command.admin.CooldownCommand", remap = false)
public abstract class AdminClearCooldownMixin {
    @WrapOperation(method = "clearCooldown", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/player/ItemCooldownManager;remove(Lnet/minecraft/item/Item;)V", remap = true),
            require = 0)
    private static void sparkstrength$releaseAdminClearedLocks(ItemCooldownManager manager, Item item,
                                                               Operation<Void> original) {
        original.call(manager, item);
        // Only a clear that took effect releases the lock. / 仅在清除确实生效时解除锁定。
        if (manager instanceof ServerItemCooldownManager server && !manager.isCoolingDown(item)) {
            SparkFactionAdminClear.onCooldownCleared(
                    ((ServerItemCooldownManagerAccessor) server).sparkstrength$getPlayer(), item);
        }
    }
}
