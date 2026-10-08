package annina.sparkstrength.client.mixin.bodyguard;

import annina.sparkstrength.client.role.bodyguard.BodyguardHud;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the Bodyguard's shield / vest status lines to the in-game HUD.
 * 将保镖的盾牌/防弹衣状态行加入游戏内 HUD。
 */
@Mixin(InGameHud.class)
public abstract class BodyguardHudMixin {
    @Shadow
    @Final
    private MinecraftClient client;

    @Inject(method = "renderMainHud", at = @At("TAIL"))
    private void sparkstrength$renderBodyguardHud(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        ClientPlayerEntity player = client.player;
        if (player != null) {
            BodyguardHud.render(client.textRenderer, player, context);
        }
    }
}
