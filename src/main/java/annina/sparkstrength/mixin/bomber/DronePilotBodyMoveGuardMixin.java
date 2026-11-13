package annina.sparkstrength.mixin.bomber;

import annina.sparkstrength.role.bomber.drone.DronePilotService;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Security guard: a drone pilot's body stays where the server holds it. The client already freezes the body and sends
 * no movement while piloting; a movement packet that would still move it is dropped here ({@link
 * DronePilotService#holdsBody}). Vanilla's other movement handling, rotation-only packets and server teleports are
 * untouched.
 * 安全闸门：无人机驾驶者的身体停留在服务端所持的位置。客户端驾驶时本就冻结身体且不发送移动；仍会移动身体的移动包在此被丢弃
 * （DronePilotService#holdsBody）。原版其余的移动处理、仅含朝向的包与服务端传送均不受影响。
 */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class DronePilotBodyMoveGuardMixin {
    @Shadow
    public ServerPlayerEntity player;

    @Inject(method = "onPlayerMove", at = @At("HEAD"), cancellable = true)
    private void sparkstrength$holdPilotBody(PlayerMoveC2SPacket packet, CallbackInfo ci) {
        // HEAD also runs on the Netty thread before vanilla re-schedules the packet; sessions are read on the server
        // thread only. / HEAD 也会在原版重新调度之前于网络线程执行；会话只在服务端主线程读取。
        if (player.getServerWorld().getServer().isOnThread() && DronePilotService.holdsBody(player, packet)) {
            ci.cancel();
        }
    }
}
