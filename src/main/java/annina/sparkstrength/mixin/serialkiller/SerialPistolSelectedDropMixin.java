package annina.sparkstrength.mixin.serialkiller;

import annina.sparkstrength.role.serialkiller.SerialPistolInventoryRules;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Q / Ctrl+Q on a selected serial pistol is refused before the stack leaves the slot (Wathe only blocks it while the
 * train HUD is on), and the client's predicted removal is resynced.
 * 对选中的连环手枪按 Q / Ctrl+Q 会在物品离开栏位前被拒绝（Wathe 只在列车 HUD 开启时拦截），并重新同步客户端的预测移除。
 */
@Mixin(ServerPlayerEntity.class)
public abstract class SerialPistolSelectedDropMixin {
    @Inject(method = "dropSelectedItem(Z)Z", at = @At("HEAD"), cancellable = true)
    private void sparkstrength$keepSelectedSerialPistol(boolean entireStack, CallbackInfoReturnable<Boolean> cir) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        if (SerialPistolInventoryRules.blocksDrop(player.getInventory().getMainHandStack())) {
            player.currentScreenHandler.syncState();
            cir.setReturnValue(false);
        }
    }
}
