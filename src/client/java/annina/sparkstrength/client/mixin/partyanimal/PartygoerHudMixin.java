package annina.sparkstrength.client.mixin.partyanimal;

import annina.sparkstrength.client.role.partyanimal.PartygoerHud;
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
 * Adds the Party Animal's Partygoer line to the in-game HUD (render only; the server owns the skill).
 * 将派对狂的派对客状态行加入游戏内 HUD（仅渲染，技能由服务端掌控）。
 */
@Mixin(InGameHud.class)
public abstract class PartygoerHudMixin {
    @Shadow
    @Final
    private MinecraftClient client;

    @Inject(method = "renderMainHud", at = @At("TAIL"))
    private void sparkstrength$renderPartygoerHud(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        ClientPlayerEntity player = client.player;
        if (player != null) {
            PartygoerHud.render(client.textRenderer, player, context);
        }
    }
}
