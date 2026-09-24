package annina.sparkstrength.client.mixin.economy;

import annina.sparkstrength.client.role.economy.KillerTeamEconomyClientHooks;
import dev.doctor4t.wathe.client.gui.StoreRenderer;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the team row only after Wathe's own money permission gate has passed.
 * 仅在 Wathe 原有金币权限检查通过后追加团队余额行。
 */
@Mixin(value = StoreRenderer.class, remap = false)
public abstract class KillerTeamMoneyHudMixin {
    @Inject(method = "renderHud", at = @At("TAIL"))
    private static void sparkstrength$renderKillerTeamMoney(
            TextRenderer renderer,
            ClientPlayerEntity player,
            DrawContext context,
            float delta,
            CallbackInfo ci
    ) {
        KillerTeamEconomyClientHooks.render(renderer, player, context, delta);
    }
}
