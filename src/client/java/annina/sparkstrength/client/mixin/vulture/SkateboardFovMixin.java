package annina.sparkstrength.client.mixin.vulture;

import annina.sparkstrength.role.vulture.VultureSkateboardRules;
import annina.sparkstrength.role.vulture.VultureSkateboardService;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Keeps the skateboard's x3 out of the speed-based FOV widening. Vanilla reads the movement-speed attribute here, so
 * the ride would otherwise pin the FOV at its 1.5x cap for 10 s, even standing still, and widen the rider's view.
 * Other speed sources (sprint, Speed effects) still widen it as usual; Wathe's own RETURN inject is unaffected.
 * 让滑板的 x3 不参与基于速度的视野放大。原版在此读取移速属性，否则滑行 10 秒内（即使静止）视野都会被顶到 1.5 倍上限，
 * 并让骑手看得更宽。其他速度来源（疾跑、速度效果）照常放大视野；Wathe 自身的 RETURN 注入不受影响。
 */
@Mixin(AbstractClientPlayerEntity.class)
public abstract class SkateboardFovMixin {
    @ModifyExpressionValue(
            method = "getFovMultiplier",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;getAttributeValue(Lnet/minecraft/registry/entry/RegistryEntry;)D"
            )
    )
    private double sparkstrength$ignoreSkateboardSpeed(double speed) {
        EntityAttributeInstance attribute = ((AbstractClientPlayerEntity) (Object) this)
                .getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (attribute != null && attribute.hasModifier(VultureSkateboardService.SPEED_MODIFIER_ID)) {
            return speed / VultureSkateboardRules.SPEED_MULTIPLIER;
        }
        return speed;
    }
}
