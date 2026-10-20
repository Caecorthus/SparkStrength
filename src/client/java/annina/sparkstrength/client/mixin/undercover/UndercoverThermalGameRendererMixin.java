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
 * Thermal image: same point as the Cooling Oil blur (after the world, entity outlines and vanilla's post effect, before
 * the HUD); the heat target was filled earlier in the frame. {@code require = 0}: cosmetic, a renderer mod rewriting
 * this method only costs the thermal image.
 * 热成像画面：与风油精模糊同一注入点（世界、实体描边与原版后处理之后，HUD 之前）；热图已在本帧较早时填好。
 * {@code require = 0}：仅为表现层，若渲染模组重写此方法只会失去热成像画面。
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
