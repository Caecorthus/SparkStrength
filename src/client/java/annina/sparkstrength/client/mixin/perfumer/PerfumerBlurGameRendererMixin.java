package annina.sparkstrength.client.mixin.perfumer;

import annina.sparkstrength.client.role.perfumer.PerfumerBlurRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cooling Oil world blur. {@code render} has exactly one {@code Framebuffer.beginWrite(Z)V} call, inside the
 * "world rendered this frame" branch, right after the world, entity outlines and vanilla's shared post effect and
 * before the HUD; injecting before it (the same point SparkTraits Depression and SparkWitch Black Raven use) blurs the
 * world while the HUD stays sharp. {@code require = 0}: the blur is cosmetic over the HUD squint, so a renderer mod
 * rewriting this method costs only the blur instead of crashing the game.
 * 风油精世界模糊。{@code render} 中只有一处 {@code Framebuffer.beginWrite(Z)V} 调用，位于“本帧渲染了世界”分支内，
 * 紧接在世界、实体描边与原版共享后处理之后、HUD 之前；在其前注入（与 SparkTraits 抑郁、SparkWitch 黑鸦相同）
 * 可以模糊世界而保持 HUD 清晰。{@code require = 0}：模糊叠加在 HUD 眯眼效果之上属于表现层，若有渲染模组重写此方法，
 * 只会失去模糊而不会导致游戏崩溃。
 */
@Mixin(GameRenderer.class)
public abstract class PerfumerBlurGameRendererMixin {
    @Inject(
            method = "render(Lnet/minecraft/client/render/RenderTickCounter;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/Framebuffer;beginWrite(Z)V"),
            require = 0
    )
    private void sparkstrength$renderCoolingOilBlur(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        PerfumerBlurRenderer.render(MinecraftClient.getInstance(), tickCounter.getTickDelta(true));
    }
}
