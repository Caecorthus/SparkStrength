package annina.sparkstrength.client.mixin.vulture;

import annina.sparkstrength.client.role.vulture.SkateboardRenderer;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the board under a rider whose body another mod draws instead of the player model. SparkTraits' Pig cancels
 * {@code PlayerEntityRenderer#render} at HEAD and draws a pig body, so {@code LivingEntityRenderer#render} and its
 * feature list, the board's {@code SkateboardRenderer.Feature} included, never run. The outermost whole-method wrap
 * sees every HEAD cancel; when the vanilla body call was skipped, the board is drawn after the replacement body. Only
 * replacements cancel there; whole players are hidden earlier (SparkWitch's Blind gate wraps
 * {@code WorldRenderer#renderEntity}). Feature-loop gates never reach a replaced body, so the Blind's feature skip is
 * asked explicitly: {@code SkateboardRenderer.renderUnderReplacedBody} draws nothing while SparkWitch's public
 * {@code hidesFeaturesFromBlind} holds (owner 2026-10-07: the Blind keeps the pig outline but never sees the board).
 * Wathe's psycho feature filter is still not applied here. Normal bodies keep the board in the feature loop under both
 * gates.
 * 让身体被其他模组替换绘制的骑手脚下仍有滑板。SparkTraits 的猪形态在 HEAD 取消 {@code PlayerEntityRenderer#render}
 * 并绘制猪身体，{@code LivingEntityRenderer#render} 及其特征列表（含滑板的 {@code SkateboardRenderer.Feature}）都不会
 * 运行。最外层的整方法包裹能看到所有 HEAD 取消；原版身体调用被跳过时，在替换身体之后绘制滑板。只有替换绘制会在此取消；
 * 整个玩家的隐藏发生得更早（SparkWitch 盲人闸门包裹 {@code WorldRenderer#renderEntity}）。特征循环内的闸门不会作用于
 * 替换身体，因此显式询问盲人的附加层跳过：SparkWitch 公开的 {@code hidesFeaturesFromBlind} 为真时，
 * {@code SkateboardRenderer.renderUnderReplacedBody} 不绘制任何东西（所有者 2026-10-07：盲人保留猪形轮廓，但永远看不到
 * 滑板）。Wathe 疯魔的附加层过滤在此仍不生效。普通身体的滑板仍在附加层循环内受这两个闸门约束。
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class SkateboardReplacedBodyMixin {
    /** Render thread only; saved and restored around each call. / 仅渲染线程；每次调用前后保存并恢复。 */
    @Unique
    private static boolean sparkstrength$vanillaBodyRendered;

    @WrapMethod(method = "render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V")
    private void sparkstrength$boardUnderReplacedBody(
            AbstractClientPlayerEntity player,
            float yaw,
            float tickDelta,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            Operation<Void> original
    ) {
        boolean outer = sparkstrength$vanillaBodyRendered;
        sparkstrength$vanillaBodyRendered = false;
        try {
            original.call(player, yaw, tickDelta, matrices, vertexConsumers, light);
            if (!sparkstrength$vanillaBodyRendered) {
                SkateboardRenderer.renderUnderReplacedBody((PlayerEntityRenderer) (Object) this, player, tickDelta,
                        matrices, vertexConsumers, light);
            }
        } finally {
            sparkstrength$vanillaBodyRendered = outer;
        }
    }

    @Inject(
            method = "render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"
            )
    )
    private void sparkstrength$markVanillaBody(
            AbstractClientPlayerEntity player,
            float yaw,
            float tickDelta,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            CallbackInfo ci
    ) {
        sparkstrength$vanillaBodyRendered = true;
    }
}
