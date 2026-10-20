package annina.sparkstrength.client.mixin.undercover;

import annina.sparkstrength.client.role.undercover.UndercoverThermalRenderer;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Undercover's own first-person arm is warm under thermal too (owner 2026-10-19): once vanilla has posed and drawn it,
 * the same arm and sleeve are drawn white into the heat target. Does nothing while thermal is off.
 * 卧底自己的第一人称手臂在热成像下也是热的（负责人 2026-10-19）：原版摆好姿势并画完后，把同一手臂和袖子以白色画进热图。
 * 热成像关闭时什么都不做。
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class UndercoverThermalArmMixin {
    @Inject(method = "renderArm", at = @At("TAIL"))
    private void sparkstrength$warmArm(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                                       AbstractClientPlayerEntity player, ModelPart arm, ModelPart sleeve,
                                       CallbackInfo ci) {
        UndercoverThermalRenderer.renderArmHeat(matrices, arm, sleeve);
    }
}
