package annina.sparkstrength.mixin.m67;

import annina.sparkstrength.item.m67.M67UseService;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class M67LivingEntityMixin {
    @Inject(method = "clearActiveItem", at = @At("HEAD"))
    private void sparkstrength$cancelM67OnClear(CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayerEntity player) {
            M67UseService.cancel(player);
        }
    }

    // Validate before vanilla adopts a different same-item stack. / 在原版接受同物品的替换堆叠之前校验。
    @Inject(method = "tickActiveItemStack", at = @At("HEAD"))
    private void sparkstrength$validateM67BeforeAdoption(CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayerEntity player) {
            M67UseService.tick(player);
        }
    }
    @Inject(method = "onDeath", at = @At("HEAD"))
    private void sparkstrength$forgetM67OnDeath(DamageSource source, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayerEntity player) {
            M67UseService.forgetPlayer(player);
        }
    }
}
