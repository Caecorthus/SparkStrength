package annina.sparkstrength.client.mixin.bomber;

import annina.sparkstrength.client.role.bomber.DronePilotClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the pilot's body still: right after {@code Input.tick} in {@code tickMovement} (before sneak/sprint/jump and
 * travel read it) the movement input is zeroed. Works for any {@code Input} implementation; the body stays a non-camera
 * entity, so the server keeps driving tracking. No-op unless the local player pilots a drone.
 * 让驾驶者的本体保持静止：在 tickMovement 中紧接 Input.tick（潜行、疾跑、跳跃与移动读取之前）清零移动输入。
 * 适用于任意 Input 实现；本体仍不是镜头实体，追踪继续由服务器驱动。仅在本地玩家驾驶无人机时生效。
 *
 * <p>The body's own {@code SELF} moves are also cancelled (velocity zeroed) while {@link DronePilotClient#freezesBody}
 * holds it, so gravity cannot drop it through its unloaded chunk. Piston/shulker pushes still apply, as on the server.
 * 在 freezesBody 保持期间同时取消本体自身的 SELF 移动（并清零速度），使重力无法让它穿过已卸载的区块下落。活塞/潜影贝推动照常生效，与服务器一致。</p>
 */
@Mixin(ClientPlayerEntity.class)
public abstract class DronePilotBodyMixin {
    @Inject(method = "tickMovement", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/input/Input;tick(ZF)V", shift = At.Shift.AFTER))
    private void sparkstrength$holdBodyStillWhilePiloting(CallbackInfo ci) {
        DronePilotClient.holdBodyStill((ClientPlayerEntity) (Object) this);
    }

    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void sparkstrength$freezeBodyWhilePiloting(MovementType type, Vec3d movement, CallbackInfo ci) {
        ClientPlayerEntity body = (ClientPlayerEntity) (Object) this;
        if (type == MovementType.SELF && DronePilotClient.freezesBody(body)) {
            body.setVelocity(Vec3d.ZERO);
            ci.cancel();
        }
    }
}
