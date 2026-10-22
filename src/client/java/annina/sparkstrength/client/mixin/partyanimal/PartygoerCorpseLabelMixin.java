package annina.sparkstrength.client.mixin.partyanimal;

import annina.sparkstrength.client.role.partyanimal.PartygoerCorpseClient;
import dev.doctor4t.wathe.client.gui.RoleNameRenderer;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the Partygoer corpse label to Wathe's body hover HUD, which skips any corpse whose owner has no role. TAIL is
 * the final return only, so Wathe's early return in darkness also hides this label. A Wathe HUD class, not a
 * Sodium-overwritten renderer method.
 * 为 Wathe 的尸体注视 HUD 补上派对客尸体标签（Wathe 会跳过主人没有身份的尸体）。TAIL 只对应最后一次 return，因此 Wathe 在黑暗中
 * 提前返回时这个标签同样隐藏。目标是 Wathe 的 HUD 类，不是被 Sodium 覆盖的渲染方法。
 */
@Mixin(value = RoleNameRenderer.class, remap = false)
public abstract class PartygoerCorpseLabelMixin {
    @Inject(method = "renderHud", at = @At("TAIL"))
    private static void sparkstrength$drawPartygoerCorpseLabel(
            TextRenderer renderer,
            ClientPlayerEntity player,
            DrawContext context,
            RenderTickCounter tickCounter,
            CallbackInfo ci
    ) {
        PartygoerCorpseClient.renderLabel(renderer, player, context, tickCounter);
    }
}
