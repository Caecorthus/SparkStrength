package annina.sparkstrength.client.mixin.bomber;

import annina.sparkstrength.client.role.bomber.DronePilotClient;
import annina.sparkstrength.client.role.bomber.DronePilotHud;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drone pilot HUD hooks. {@code render} HEAD draws the camera-feed lens under every HUD layer; {@code renderMainHud}
 * TAIL draws the instruments (gated by F1 like the rest of the HUD; the vanilla hotbar already hides itself because
 * the camera is not a player). {@code renderCrosshair} HEAD hides the vanilla crosshair; Wathe's {@code @WrapMethod}
 * replaces that body with {@code CrosshairRenderer} in a round, which {@link DronePilotCrosshairMixin} covers.
 * 无人机驾驶 HUD 钩子。render 的 HEAD 在所有 HUD 层之下绘制图传镜头层；renderMainHud 的 TAIL 绘制仪表（与其他 HUD 一样受
 * F1 控制；镜头不是玩家，原版快捷栏会自行隐藏）。renderCrosshair 的 HEAD 隐藏原版准心；对局中 Wathe 的 @WrapMethod 会用
 * CrosshairRenderer 替换该方法体，由 DronePilotCrosshairMixin 处理。
 */
@Mixin(InGameHud.class)
public abstract class DronePilotHudMixin {
    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
            at = @At("HEAD"))
    private void sparkstrength$renderDroneLens(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        DronePilotHud.renderLens(context, tickCounter);
    }

    @Inject(method = "renderMainHud", at = @At("TAIL"))
    private void sparkstrength$renderDroneInstruments(DrawContext context, RenderTickCounter tickCounter,
                                                      CallbackInfo ci) {
        DronePilotHud.renderInstruments(context, tickCounter);
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void sparkstrength$hideVanillaCrosshairWhilePiloting(DrawContext context, RenderTickCounter tickCounter,
                                                                 CallbackInfo ci) {
        if (DronePilotClient.isViewingDrone()) {
            ci.cancel();
        }
    }
}
