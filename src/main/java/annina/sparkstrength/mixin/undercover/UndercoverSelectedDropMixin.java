package annina.sparkstrength.mixin.undercover;

import annina.sparkstrength.role.undercover.UndercoverService;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Q / Ctrl+Q on the selected CQCM mask or tactical tomahawk is refused before the stack leaves the slot, and the
 * client's predicted removal is resynced. Other drop paths are caught by {@link UndercoverBoundItemMixin}.
 * 对选中的 CQCM 面具或战术斧按 Q / Ctrl+Q 会在物品离开栏位前被拒绝，并重新同步客户端的预测移除。其他丢弃途径由 UndercoverBoundItemMixin 拦截。
 */
@Mixin(ServerPlayerEntity.class)
public abstract class UndercoverSelectedDropMixin {
    @Inject(method = "dropSelectedItem(Z)Z", at = @At("HEAD"), cancellable = true)
    private void sparkstrength$keepSelectedUndercoverGear(boolean entireStack, CallbackInfoReturnable<Boolean> cir) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        if (UndercoverService.isBound(player.getInventory().getMainHandStack())) {
            player.currentScreenHandler.syncState();
            cir.setReturnValue(false);
        }
    }
}
