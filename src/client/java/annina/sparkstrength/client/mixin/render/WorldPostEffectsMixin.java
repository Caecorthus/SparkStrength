package annina.sparkstrength.client.mixin.render;

import annina.sparkstrength.client.role.jester.JesterMomentGrayscale;
import annina.sparkstrength.client.role.perfumer.PerfumerBlurRenderer;
import annina.sparkstrength.client.role.undercover.UndercoverThermalRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * SparkStrength's world post effects, in one hook. {@code render} has exactly one {@code Framebuffer.beginWrite(Z)V}
 * call, inside the "world rendered this frame" branch, right after the world, entity outlines and vanilla's shared
 * post effect and before the HUD; injecting before it (the point SparkTraits Depression and SparkWitch Black Raven use
 * too) changes the world while the HUD keeps its colours and stays sharp. Each effect owns a private processor that
 * reads and writes the main framebuffer, so they stack in this fixed order (the order their three separate hooks ran
 * in, by mixin config order): Jester Moment grayscale, then Cooling Oil blur, then Undercover thermal.
 * SparkStrength 的世界后处理效果，集中在一个钩子里。{@code render} 中只有一处 {@code Framebuffer.beginWrite(Z)V} 调用，
 * 位于“本帧渲染了世界”分支内，紧接在世界、实体描边与原版共享后处理之后、HUD 之前；在其前注入（SparkTraits 抑郁与
 * SparkWitch 黑鸦也用此处）只改变世界，HUD 保持原色与清晰。每个效果持有私有处理器并读写主帧缓冲，因此按固定顺序叠加
 * （即原先三个独立钩子按 mixin 配置顺序执行的顺序）：小丑时刻灰度，然后风油精模糊，最后卧底热成像。
 */
@Mixin(GameRenderer.class)
public abstract class WorldPostEffectsMixin {
    @Inject(
            method = "render(Lnet/minecraft/client/render/RenderTickCounter;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/Framebuffer;beginWrite(Z)V"),
            require = 1
    )
    private void sparkstrength$renderWorldPostEffects(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        float tickDelta = tickCounter.getTickDelta(true);
        JesterMomentGrayscale.render(client, tickDelta);
        PerfumerBlurRenderer.render(client, tickDelta);
        UndercoverThermalRenderer.render(client, tickDelta);
    }
}
