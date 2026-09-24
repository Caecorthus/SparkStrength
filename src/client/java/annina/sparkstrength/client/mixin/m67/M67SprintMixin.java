package annina.sparkstrength.client.mixin.m67;

import annina.sparkstrength.SparkStrengthItems;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientPlayerEntity.class)
public abstract class M67SprintMixin {
    // Wathe already disables use slowdown; exempt only this remaining sprint-start check.
    // Wathe 已禁用使用减速；这里只豁免 M67 的开始疾跑检查。
    @ModifyExpressionValue(method = "canStartSprinting()Z", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z"))
    private boolean sparkstrength$m67AllowsSprintStart(boolean original) {
        ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
        return original && !player.getActiveItem().isOf(SparkStrengthItems.m67());
    }
}
