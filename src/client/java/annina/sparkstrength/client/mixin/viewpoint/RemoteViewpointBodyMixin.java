package annina.sparkstrength.client.mixin.viewpoint;

import annina.sparkstrength.client.viewpoint.RemoteViewpointClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the body still while {@link RemoteViewpointClient#freezesBody} holds it (a drone pilot or a possessing
 * Spiritualist, and afterwards until its chunks are back): its own {@code SELF} moves are cancelled (velocity zeroed),
 * so gravity cannot drop it through the chunk the re-centred view unloaded. A pilot's body sends no movement packets,
 * so the server holds it still anyway; NoellesRoles keeps a projecting body sending them, so a client-side fall would
 * otherwise reach the server and trip its "body moved" forced return. Piston/shulker pushes still apply, as on the
 * server.
 * 在 freezesBody 保持期间（驾驶无人机或附身中的灵界行者，以及之后直到其区块回来）让身体静止：取消其自身的 SELF 移动并清零速度，
 * 使重力无法让它穿过重新居中的视野所卸载的区块。驾驶者的身体不发送移动包，服务器本来就让它保持不动；NoellesRoles 让出窍中的肉身
 * 继续发送移动包，否则客户端的下落会传到服务器并触发“肉身被移动”的强制回归。活塞/潜影贝推动照常生效，与服务器一致。
 */
@Mixin(ClientPlayerEntity.class)
public abstract class RemoteViewpointBodyMixin {
    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void sparkstrength$freezeBodyDuringRemoteView(MovementType type, Vec3d movement, CallbackInfo ci) {
        ClientPlayerEntity body = (ClientPlayerEntity) (Object) this;
        if (type == MovementType.SELF && RemoteViewpointClient.freezesBody(body)) {
            body.setVelocity(Vec3d.ZERO);
            ci.cancel();
        }
    }
}
