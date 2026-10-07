package annina.sparkstrength.client.mixin.spiritualist;

import annina.sparkstrength.client.role.spiritualist.SpiritPossessionClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.SetCameraEntityS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A vanilla {@code SetCameraEntityS2CPacket} (spectating, a NoellesRoles Taotie swallow...) outranks the possession:
 * just before vanilla applies it on the main thread, the possession ends locally without restoring the camera and the
 * server is told to stop. No-op unless possessing.
 * 原版 SetCameraEntityS2CPacket（旁观、NoellesRoles 饕餮吞噬等）优先于附身：在原版于主线程应用之前，在本地结束附身但不恢复镜头，
 * 并通知服务器停止。未附身时不做任何事。
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class SpiritPossessionCameraPacketMixin {
    @Inject(method = "onSetCameraEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/MinecraftClient;setCameraEntity(Lnet/minecraft/entity/Entity;)V"))
    private void sparkstrength$yieldPossessionToServer(SetCameraEntityS2CPacket packet, CallbackInfo ci) {
        SpiritPossessionClient.onServerCameraOverride(MinecraftClient.getInstance());
    }
}
