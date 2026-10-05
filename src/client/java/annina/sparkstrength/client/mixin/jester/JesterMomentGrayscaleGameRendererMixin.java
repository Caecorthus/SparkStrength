package annina.sparkstrength.client.mixin.jester;

import annina.sparkstrength.client.role.jester.JesterMomentGrayscale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Jester Moment grayscale. Injects before the single {@code Framebuffer.beginWrite(Z)V} in {@code render}, after the
 * world and vanilla's shared post effect and before the HUD, the same point as the Perfumer blur and SparkTraits
 * Depression, so the world greys while the HUD keeps its colours. {@code require = 0}: the effect is cosmetic, so a
 * renderer mod rewriting this method costs only the grayscale instead of crashing the game.
 * 小丑时刻灰度。在 {@code render} 唯一的 {@code Framebuffer.beginWrite(Z)V} 之前注入：位于世界与原版共享后处理之后、
 * HUD 之前，与调香师模糊和 SparkTraits 抑郁相同，因此世界变灰而 HUD 保持原色。{@code require = 0}：纯表现效果，
 * 若有渲染模组重写此方法，只会失去灰度而不会导致游戏崩溃。
 */
@Mixin(GameRenderer.class)
public abstract class JesterMomentGrayscaleGameRendererMixin {
    @Inject(
            method = "render(Lnet/minecraft/client/render/RenderTickCounter;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/Framebuffer;beginWrite(Z)V"),
            require = 0
    )
    private void sparkstrength$renderJesterMomentGrayscale(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        JesterMomentGrayscale.render(MinecraftClient.getInstance(), tickCounter.getTickDelta(true));
    }
}
