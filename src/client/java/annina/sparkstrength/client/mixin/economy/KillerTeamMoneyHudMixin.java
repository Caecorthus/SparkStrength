package annina.sparkstrength.client.mixin.economy;

import annina.sparkstrength.client.role.economy.KillerTeamEconomyClientHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
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
    // Descriptor-less owner;name selector: renderHud has exactly one odometer render call.
    // 省略描述符的选择器：renderHud 中只有一次滚动数字渲染调用。
    @WrapOperation(
            method = "renderHud",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/doctor4t/wathe/client/gui/StoreRenderer$MoneyNumberRenderer;render"
            )
    )
    private static void sparkstrength$clipPersonalMoneyAboveTeamRow(
            StoreRenderer.MoneyNumberRenderer view,
            TextRenderer renderer,
            DrawContext context,
            int x,
            int y,
            int colour,
            float delta,
            Operation<Void> original,
            @Local(argsOnly = true) ClientPlayerEntity player
    ) {
        KillerTeamEconomyClientHooks.renderPersonalRow(player, context, renderer.fontHeight,
                () -> original.call(view, renderer, context, x, y, colour, delta));
    }

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
