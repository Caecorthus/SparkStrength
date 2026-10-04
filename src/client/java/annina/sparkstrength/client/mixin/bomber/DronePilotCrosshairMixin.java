package annina.sparkstrength.client.mixin.bomber;

import annina.sparkstrength.client.role.bomber.DronePilotClient;
import dev.doctor4t.wathe.client.gui.CrosshairRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides Wathe's in-round crosshair (knife/bat/gun indicators) while the camera looks through a drone; the drone HUD
 * draws its own reticle. A plain HEAD cancel composes with the Professor/Morphling HEAD injections on the same method:
 * those only act for their own held items, and the pilot holds the tablet. {@code remap = false}: Wathe class; the
 * descriptor's Minecraft types are remapped by Loom through the method's parameter list.
 * 镜头通过无人机观看时隐藏 Wathe 对局准心（刀/棒球棍/枪的指示），由无人机 HUD 绘制自己的准星。普通 HEAD 取消可与同一方法上
 * 教授/变形者的 HEAD 注入共存：它们只对各自的手持物品生效，而驾驶者手持平板。remap = false：Wathe 类。
 */
@Mixin(value = CrosshairRenderer.class, remap = false)
public abstract class DronePilotCrosshairMixin {
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private static void sparkstrength$hideWatheCrosshairWhilePiloting(MinecraftClient client, ClientPlayerEntity player,
                                                                      DrawContext context,
                                                                      RenderTickCounter tickCounter,
                                                                      CallbackInfo ci) {
        if (DronePilotClient.isViewingDrone()) {
            ci.cancel();
        }
    }
}
