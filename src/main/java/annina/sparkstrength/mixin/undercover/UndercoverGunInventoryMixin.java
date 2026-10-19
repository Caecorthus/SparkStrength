package annina.sparkstrength.mixin.undercover;

import annina.sparkstrength.role.undercover.UndercoverService;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * No gun enters a stage-2 Undercover's inventory by insertion (pickup, give, server grants). Both public
 * {@code insertStack} overloads end in this one.
 * 枪不会以插入方式进入二阶段卧底的背包（拾取、给予、服务端发放）。两个公开的 insertStack 重载最终都走到这里。
 */
@Mixin(PlayerInventory.class)
public abstract class UndercoverGunInventoryMixin {
    @Shadow
    @Final
    public PlayerEntity player;

    @Inject(method = "insertStack(ILnet/minecraft/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void sparkstrength$stage2RefusesGuns(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (player instanceof ServerPlayerEntity serverPlayer && UndercoverService.refusesStack(serverPlayer, stack)) {
            cir.setReturnValue(false);
        }
    }
}
