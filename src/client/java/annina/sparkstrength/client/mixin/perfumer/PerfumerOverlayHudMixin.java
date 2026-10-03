package annina.sparkstrength.client.mixin.perfumer;

import annina.sparkstrength.client.role.perfumer.PerfumerOverlayRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the Perfumer overlays first in the HUD pass so the hotbar, chat and every other HUD layer stay on top.
 * GameRenderer calls {@code InGameHud.render} even with F1, and this HEAD sits outside vanilla's hudHidden gate, so
 * F1 cannot clear the squint.
 * 在 HUD 阶段最先绘制调香师覆盖层，使快捷栏、聊天与其他 HUD 层都在其上方。GameRenderer 在按下 F1 时
 * 仍会调用 {@code InGameHud.render}，而此 HEAD 位于原版 hudHidden 门控之外，因此 F1 无法去掉眯眼效果。
 */
@Mixin(InGameHud.class)
public abstract class PerfumerOverlayHudMixin {
    @Inject(
            method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
            at = @At("HEAD")
    )
    private void sparkstrength$renderPerfumerOverlays(DrawContext context, RenderTickCounter tickCounter,
                                                      CallbackInfo ci) {
        PerfumerOverlayRenderer.render(context, tickCounter.getTickDelta(true));
    }
}
