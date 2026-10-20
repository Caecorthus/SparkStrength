package annina.sparkstrength.client.mixin.undercover;

import annina.sparkstrength.client.role.undercover.UndercoverThermalRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Only during the thermal heat pass ({@link UndercoverThermalRenderer#isRenderingHeat()}): no name labels (they would
 * become white boxes), and invisible bodies are drawn, since thermal sees body heat. Outside the pass both call through.
 * 仅在热成像热图 pass 期间：不画名字标签（会变成白色方块），并且隐身的身体也会画出来，因为热成像看的是体温。pass 之外原样放行。
 */
@Mixin(LivingEntityRenderer.class)
public abstract class UndercoverThermalSilhouetteMixin {
    @Inject(method = "hasLabel(Lnet/minecraft/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
    private void sparkstrength$noLabelInHeatPass(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (UndercoverThermalRenderer.isRenderingHeat()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isVisible", at = @At("HEAD"), cancellable = true)
    private void sparkstrength$invisibleBodiesStillHot(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (UndercoverThermalRenderer.isRenderingHeat()) {
            cir.setReturnValue(true);
        }
    }
}
