package annina.sparkstrength.client.mixin.bomber;

import annina.sparkstrength.client.role.bomber.DronePilotClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.SetCameraEntityS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A vanilla {@code SetCameraEntityS2CPacket} (spectating, NoellesRoles Taotie/Jester...) outranks pilot mode: just
 * before vanilla applies it on the main thread (after {@code forceMainThread}, only when the entity exists), the pilot
 * session ends locally without restoring the camera and the server is told to EXIT. No-op unless piloting.
 * 原版 SetCameraEntityS2CPacket（旁观、NoellesRoles 饕餮/小丑等）优先于驾驶模式：在原版于主线程应用之前（forceMainThread 之后，
 * 且仅当实体存在时），在本地结束驾驶会话但不恢复镜头，并通知服务器 EXIT。未驾驶时不做任何事。
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class DronePilotCameraPacketMixin {
    @Inject(method = "onSetCameraEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/MinecraftClient;setCameraEntity(Lnet/minecraft/entity/Entity;)V"))
    private void sparkstrength$yieldCameraToServer(SetCameraEntityS2CPacket packet, CallbackInfo ci) {
        DronePilotClient.onServerCameraOverride(MinecraftClient.getInstance());
    }
}
