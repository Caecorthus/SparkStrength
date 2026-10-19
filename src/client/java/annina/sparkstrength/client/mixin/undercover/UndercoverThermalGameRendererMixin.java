package annina.sparkstrength.client.mixin.undercover;

import annina.sparkstrength.client.role.undercover.UndercoverThermalRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Thermal colour grade: same point as the Cooling Oil blur (after the world, entity outlines and vanilla's post effect,
 * before the HUD), so the magenta outlines are already in the frame when the shader turns them into heat.
 * {@code require = 0}: cosmetic, a renderer mod rewriting this method only costs the grade (the outlines remain).
 * 热成像调色：与风油精模糊同一注入点（世界、实体描边与原版后处理之后，HUD 之前），着色器把品红描边变成热源时它们已在画面里。
 * {@code require = 0}：仅为表现层，若渲染模组重写此方法只会失去调色（描边仍在）。
 */
@Mixin(GameRenderer.class)
public abstract class UndercoverThermalGameRendererMixin {
    @Inject(
            method = "render(Lnet/minecraft/client/render/RenderTickCounter;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/Framebuffer;beginWrite(Z)V"),
            require = 0
    )
    private void sparkstrength$renderUndercoverThermal(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        UndercoverThermalRenderer.render(MinecraftClient.getInstance(), tickCounter.getTickDelta(true));
    }
}
