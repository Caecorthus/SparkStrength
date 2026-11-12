package annina.sparkstrength.client.mixin.bandit;

import annina.sparkstrength.item.BanditRevolverItem;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Keeps the revolver's short-barrel / speed-grip bonus out of the speed-based FOV widening: vanilla reads the
 * movement-speed attribute here, so drawing the gun would otherwise widen the view by up to 20% and holstering it would
 * narrow it again. Other speed sources still widen it as usual.
 * 让改装左轮短枪管/移速握把的加成不参与基于速度的视野放大：原版在此读取移速属性，否则掏枪会让视野最多放大 20%，收枪又缩回。
 * 其他速度来源照常放大视野。
 */
@Mixin(AbstractClientPlayerEntity.class)
public abstract class BanditRevolverFovMixin {
    @ModifyExpressionValue(
            method = "getFovMultiplier",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;getAttributeValue(Lnet/minecraft/registry/entry/RegistryEntry;)D"
            )
    )
    private double sparkstrength$ignoreBanditRevolverSpeed(double speed) {
        EntityAttributeInstance attribute = ((AbstractClientPlayerEntity) (Object) this)
                .getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        EntityAttributeModifier modifier = attribute == null
                ? null
                : attribute.getModifier(BanditRevolverItem.SPEED_MODIFIER_ID);
        return modifier == null ? speed : speed / (1.0D + modifier.value());
    }
}
