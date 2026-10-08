package annina.sparkstrength.client.mixin.spiritualist;

import annina.sparkstrength.client.role.spiritualist.SpiritPossessionHud;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Appends the Spiritualist possession line to the main HUD pass.
 * 把灵界行者附身行追加到主 HUD 渲染流程。
 */
@Mixin(InGameHud.class)
public abstract class SpiritPossessionHudMixin {
    @Inject(method = "renderMainHud", at = @At("TAIL"))
    private void sparkstrength$renderSpiritPossessionHud(DrawContext context, RenderTickCounter tickCounter,
                                                        CallbackInfo ci) {
        SpiritPossessionHud.render(context, MinecraftClient.getInstance().player);
    }
}
