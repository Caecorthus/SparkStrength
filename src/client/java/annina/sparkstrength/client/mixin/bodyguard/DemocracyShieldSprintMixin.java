package annina.sparkstrength.client.mixin.bodyguard;

import annina.sparkstrength.SparkStrengthItems;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * No sprinting behind a raised Democracy Shield. Vanilla's canStartSprinting already refuses to start a sprint while an
 * item is in use, but a sprint already running keeps going; the client owns its sprint state, so stop it here
 * (the server also clears it every tick while raised).
 * 举着民主盾牌时不能疾跑。原版 canStartSprinting 已禁止在使用物品时开始疾跑，但已在进行的疾跑不会停止；
 * 疾跑状态由客户端掌控，因此在这里停止（服务端在举盾期间也会每 tick 清除）。
 */
@Mixin(ClientPlayerEntity.class)
public abstract class DemocracyShieldSprintMixin {
    @Inject(method = "tickMovement()V", at = @At("HEAD"))
    private void sparkstrength$stopSprintBehindShield(CallbackInfo ci) {
        ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
        if (player.isSprinting() && player.isUsingItem()
                && player.getActiveItem().isOf(SparkStrengthItems.democracyShield())) {
            player.setSprinting(false);
        }
    }
}
