package annina.sparkstrength.client.mixin.partyanimal;

import annina.sparkstrength.role.partyanimal.PartygoerCarry;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A player held by the Partygoer stands upright in its arms, like the Kidnapper's drugged victim, instead of sitting.
 * 被派对客抱住的玩家直立站在它怀里（与绑架者迷晕的玩家一样），而不是坐着。
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
}
