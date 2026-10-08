package annina.sparkstrength.client.mixin.taotie;

import annina.sparkstrength.client.role.taotie.TaotieHeadHudRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Appends the Taotie head line to the main HUD pass.
 * 把饕餮头颅技能行追加到主 HUD 渲染流程。
 */
@Mixin(InGameHud.class)
public abstract class TaotieHeadHudMixin {
    @Inject(method = "renderMainHud", at = @At("TAIL"))
    private void sparkstrength$renderTaotieHeadHud(
            DrawContext context,
            RenderTickCounter tickCounter,
            CallbackInfo ci
    ) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null) {
            TaotieHeadHudRenderer.render(context, player);
        }
    }
}
