package annina.sparkstrength.client.mixin.spiritualist;

import annina.sparkstrength.client.role.spiritualist.SpiritPossessionClient;
import net.minecraft.client.MinecraftClient;
import org.agmas.noellesroles.client.spiritualist.SpiritCameraHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A possession from the body starts a NoellesRoles projection, whose {@code enable()} points the camera at a fresh
 * spirit at the body; this hands the camera straight back to the Wraith in the same tick, so the spirit view never
 * flashes. NoellesRoles never re-asserts the spirit camera afterwards. No-op unless possessing.
 * 从肉身发动的附身会开启 NoellesRoles 出窍，其 enable() 会把镜头交给肉身处新建的灵魂；这里在同一刻立即把镜头交回冤魂，
 * 灵魂画面不会闪现。NoellesRoles 之后不会再强制改回灵魂镜头。未附身时不做任何事。
 */
@Mixin(value = SpiritCameraHandler.class, remap = false)
public abstract class SpiritPossessionSpiritCameraMixin {
    @Inject(method = "enable", at = @At("TAIL"))
    private static void sparkstrength$keepPossessionCamera(CallbackInfo ci) {
        SpiritPossessionClient.onSpiritCameraEnabled(MinecraftClient.getInstance());
    }
}
