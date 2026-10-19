package annina.sparkstrength.mixin.undercover;

import annina.sparkstrength.role.undercover.UndercoverService;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A stage-2 Undercover walks over guns without picking them up (owner 2026-10-19). Wathe's own pickup rule would let
 * it, since it is still innocent. Server side; the inventory guard catches every other way in.
 * 二阶段卧底走过枪也不会捡起（负责人 2026-10-19）。它仍是好人，Wathe 自己的规则本来允许拾取。服务端判断；其他进背包的途径由背包防护拦住。
 */
@Mixin(ItemEntity.class)
public abstract class UndercoverGunPickupMixin {
    @Shadow
    public abstract ItemStack getStack();

    @Inject(method = "onPlayerCollision", at = @At("HEAD"), cancellable = true)
    private void sparkstrength$stage2LeavesGuns(PlayerEntity player, CallbackInfo ci) {
        if (player instanceof ServerPlayerEntity serverPlayer && UndercoverService.refusesStack(serverPlayer, getStack())) {
            ci.cancel();
        }
    }
}
