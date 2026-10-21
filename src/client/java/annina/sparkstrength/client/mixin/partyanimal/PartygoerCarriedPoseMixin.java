package annina.sparkstrength.client.mixin.partyanimal;

import annina.sparkstrength.role.partyanimal.PartygoerCarry;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A player carried by the Partygoer lies across its outstretched arms, legs straight, instead of sitting.
 * 被派对客扛着的玩家横躺在它伸出的双臂上，双腿伸直，而不是坐着。
 */
@Mixin(LivingEntityRenderer.class)
public abstract class PartygoerCarriedPoseMixin {
    @WrapOperation(
            method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;hasVehicle()Z", ordinal = 0)
    )
    private boolean sparkstrength$carriedIsNotSitting(LivingEntity entity, Operation<Boolean> original) {
        return original.call(entity) && !PartygoerCarry.isCarried(entity);
    }

    @Inject(method = "setupTransforms", at = @At("TAIL"))
    private void sparkstrength$lieAcrossArms(LivingEntity entity, MatrixStack matrices, float animationProgress,
                                             float bodyYaw, float tickDelta, float scale, CallbackInfo ci) {
        if (!PartygoerCarry.isCarried(entity)) {
            return;
        }
        // Body yaw already follows the carrier: tip over sideways, face up, centred on the arms.
        // 身体朝向已跟随搬运者：向侧面倒下、脸朝上，以手臂为中心。
        matrices.translate(0.0F, 0.55F, 0.0F);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(90.0F));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90.0F));
        matrices.translate(0.0F, -0.9F, 0.0F);
    }
}
