package annina.sparkstrength.client.mixin.viewpoint;

import annina.sparkstrength.client.viewpoint.RemoteViewpointClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.SetCameraEntityS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A vanilla {@code SetCameraEntityS2CPacket} (spectating, NoellesRoles Taotie/Jester...) outranks drone piloting and
 * Wraith possession: just before vanilla applies it on the main thread (after {@code forceMainThread}, only when the
 * entity exists), each running remote view ends locally without restoring the camera and the server is told to stop.
 * No-op unless a remote view is on.
 * 原版 SetCameraEntityS2CPacket（旁观、NoellesRoles 饕餮/小丑等）优先于无人机驾驶与附身冤魂：在原版于主线程应用之前
 * （forceMainThread 之后，且仅当实体存在时），正在进行的远程画面都在本地结束但不恢复镜头，并通知服务器停止。没有远程画面时不做任何事。
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class RemoteViewpointCameraPacketMixin {
    @Inject(method = "onSetCameraEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/MinecraftClient;setCameraEntity(Lnet/minecraft/entity/Entity;)V"))
    private void sparkstrength$yieldRemoteViewToServer(SetCameraEntityS2CPacket packet, CallbackInfo ci) {
        RemoteViewpointClient.onServerCameraOverride(MinecraftClient.getInstance());
    }
}
