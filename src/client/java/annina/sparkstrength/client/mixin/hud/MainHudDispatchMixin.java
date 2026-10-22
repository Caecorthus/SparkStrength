package annina.sparkstrength.client.mixin.hud;

import annina.sparkstrength.client.role.bodyguard.BodyguardHud;
import annina.sparkstrength.client.role.bomber.DronePilotHud;
import annina.sparkstrength.client.role.corruptcop.CorruptCopAbilityHud;
import annina.sparkstrength.client.role.demonhunter.DemonHunterSniffHudRenderer;
import annina.sparkstrength.client.role.detective.DetectiveMagnifierHudRenderer;
import annina.sparkstrength.client.role.partyanimal.PartygoerHud;
import annina.sparkstrength.client.role.spiritualist.SpiritPossessionHud;
import annina.sparkstrength.client.role.taotie.TaotieHeadHudRenderer;
import annina.sparkstrength.client.role.vulture.SkateboardHud;
import annina.sparkstrength.client.role.vulture.VultureSuperCurseHud;
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
 * Every SparkStrength line appended to the main HUD pass, from one TAIL hook (gated by F1 like the rest of the main
 * HUD). Each feature keeps its own checks; the order below is the order their separate TAIL hooks ran in (same-point
 * injectors of one config run in mixin config order), so later lines still draw over earlier ones.
 * 所有追加到主 HUD 渲染流程的 SparkStrength 行，由一个 TAIL 钩子统一调用（与主 HUD 其他部分一样受 F1 控制）。各功能保留
 * 自己的判断；下列顺序即原先各自 TAIL 钩子的执行顺序（同一配置中同一注入点的注入器按 mixin 配置顺序执行），因此后面的行
 * 仍然画在前面的行之上。
 */
@Mixin(InGameHud.class)
public abstract class MainHudDispatchMixin {
    @Shadow
    @Final
    private MinecraftClient client;

    @Inject(method = "renderMainHud", at = @At("TAIL"))
    private void sparkstrength$renderMainHudLines(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        ClientPlayerEntity player = client.player;
        if (player != null) {
            BodyguardHud.render(client.textRenderer, player, context);
        }
        DronePilotHud.renderInstruments(context, tickCounter);
        if (player != null) {
            CorruptCopAbilityHud.render(client.textRenderer, player, context);
        }
        SpiritPossessionHud.render(context, player);
        if (player != null) {
            TaotieHeadHudRenderer.render(context, player);
            PartygoerHud.render(client.textRenderer, player, context);
            DetectiveMagnifierHudRenderer.render(context, player);
            DemonHunterSniffHudRenderer.render(context, player);
            SkateboardHud.render(client.textRenderer, player, context);
            VultureSuperCurseHud.render(client.textRenderer, player, context);
        }
    }
}
