package annina.sparkstrength.mixin.undercover;

import annina.sparkstrength.role.undercover.UndercoverService;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The CQCM mask, the tactical tomahawk and the blackout outfit never become item entities. Whatever the drop path (Q,
 * a thrown cursor stack, server code), the stack goes straight back into the owner's inventory.
 * CQCM 面具、战术斧与熄灯黑衣永远不会变成掉落物。无论哪种丢弃方式（Q 键、扔出光标物品、服务端代码），都直接放回主人背包。
 */
@Mixin(PlayerEntity.class)
public abstract class UndercoverBoundItemMixin {
    @Inject(method = "dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;",
            at = @At("HEAD"), cancellable = true)
    private void sparkstrength$keepUndercoverGear(ItemStack stack, boolean throwRandomly, boolean retainOwnership,
                                                  CallbackInfoReturnable<ItemEntity> cir) {
        if (UndercoverService.isBound(stack)) {
            PlayerEntity self = (PlayerEntity) (Object) this;
            if (!self.getWorld().isClient) {
                self.getInventory().insertStack(stack);
            }
            cir.setReturnValue(null);
        }
    }
}
