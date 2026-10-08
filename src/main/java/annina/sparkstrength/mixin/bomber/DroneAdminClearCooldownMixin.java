package annina.sparkstrength.mixin.bomber;

import annina.sparkstrength.mixin.minecraft.ServerItemCooldownManagerAccessor;
import annina.sparkstrength.role.bomber.drone.DroneService;
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
 * 0.1.5.3) only removes the held item's vanilla cooldown. During the opening that does not stick for Bomber drones:
 * their round-start lock is re-synced every tick from the M67 round clock, and placement and piloting read that clock;
 * queued grenade drone returns also re-apply the loss cooldown on delivery. When the command's {@code remove} really
 * cleared the item, {@link DroneService#onAdminCooldownCleared} releases the held drone kind for the rest of the round.
 * Role mechanics that remove cooldowns never come through here. {@code @Pseudo} plus {@code require = 0}: a silent
 * no-op without SparkFactionAPI or if the command changes.
 * 可选的 SparkFactionAPI 接缝，仅限管理员路径：/sparkfactionapi:clearCooldown（自 0.1.5.3 起方法体相同）只移除手持物品的原版冷却。
 * 开局锁期间这对炸弹客无人机无效：其开局锁每刻按 M67 回合时钟重新同步，放置与驾驶也读取该时钟；排队归还的投弹无人机在交付时
 * 还会重新写入损毁冷却。命令的 {@code remove} 确实清除了该物品时，由 DroneService#onAdminCooldownCleared 在本回合剩余时间内
 * 解除手持的无人机型号。移除冷却的职业机制不会经过这里。@Pseudo 与 require = 0：未安装 SparkFactionAPI 或命令变更时静默失效。
 */
@Pseudo
@Mixin(targets = "dev.caecorthus.sparkfactionapi.command.admin.CooldownCommand", remap = false)
public abstract class DroneAdminClearCooldownMixin {
    @WrapOperation(method = "clearCooldown", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/player/ItemCooldownManager;remove(Lnet/minecraft/item/Item;)V", remap = true),
            require = 0)
    private static void sparkstrength$releaseDroneLocks(ItemCooldownManager manager, Item item,
                                                        Operation<Void> original) {
        original.call(manager, item);
        // Only a clear that took effect releases the kind. / 仅在清除确实生效时解除该型号。
        if (manager instanceof ServerItemCooldownManager server && !manager.isCoolingDown(item)) {
            DroneService.onAdminCooldownCleared(((ServerItemCooldownManagerAccessor) server).sparkstrength$getPlayer(),
                    item);
        }
    }
}
