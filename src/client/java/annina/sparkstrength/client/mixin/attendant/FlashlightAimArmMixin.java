package annina.sparkstrength.client.mixin.attendant;

import annina.sparkstrength.client.role.attendant.flashlight.FlashlightAim;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Points a player's lit-flashlight arm along their look, so the held item, the beam and its glare agree in third
 * person. TAIL of {@code BipedEntityModel#setAngles} runs after vanilla arm poses, Wathe's gun pose
 * ({@code positionRightArm/LeftArm} TAIL) and sneak/idle sway, and before {@code PlayerEntityModel#setAngles} copies
 * the arms onto the sleeves and the armor feature copies the biped state, so sleeves and armor follow. Only arms
 * holding a lit flashlight change. Client-only and visual.
 * 让玩家持已开启手电的手臂指向视线方向，使第三人称下手持物品、光束与光晕一致。{@code BipedEntityModel#setAngles}
 * 末尾晚于原版手臂姿态、Wathe 枪械姿态（positionRightArm/LeftArm 末尾）以及潜行与待机摆动，并早于
 * {@code PlayerEntityModel#setAngles} 把手臂复制到袖子、盔甲特征复制双足模型状态，因此袖子与盔甲同步跟随。
 * 只修改持已开启手电的手臂。仅客户端视觉效果。
 */
@Mixin(BipedEntityModel.class)
public abstract class FlashlightAimArmMixin {
    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void sparkstrength$aimLitFlashlightArm(
            LivingEntity entity,
            float limbAngle,
            float limbDistance,
            float animationProgress,
            float headYaw,
            float headPitch,
            CallbackInfo ci
    ) {
        FlashlightAim.poseArms((BipedEntityModel<?>) (Object) this, entity);
    }
}
