package annina.sparkstrength.client.mixin.spiritualist;

import annina.sparkstrength.client.role.spiritualist.SpiritPossessionClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the Spiritualist's body still while {@link SpiritPossessionClient#freezesBody} holds it: its own {@code SELF}
 * moves are cancelled (velocity zeroed), so gravity cannot drop it through the chunk the re-centred view unloaded.
 * NoellesRoles keeps the projecting body sending movement packets, so a client-side fall would otherwise reach the
 * server and trip its "body moved" forced return. Piston/shulker pushes still apply, as on the server.
 * 在 freezesBody 保持期间让灵界行者的肉身静止：取消其自身的 SELF 移动并清零速度，使重力无法让它穿过重新居中的视野所卸载的区块。
 * NoellesRoles 让出窍中的肉身继续发送移动包，否则客户端的下落会传到服务器并触发“肉身被移动”的强制回归。活塞/潜影贝推动照常生效。
 */
@Mixin(ClientPlayerEntity.class)
public abstract class SpiritPossessionBodyMixin {
    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void sparkstrength$freezeBodyWhilePossessing(MovementType type, Vec3d movement, CallbackInfo ci) {
        ClientPlayerEntity body = (ClientPlayerEntity) (Object) this;
        if (type == MovementType.SELF && SpiritPossessionClient.freezesBody(body)) {
            body.setVelocity(Vec3d.ZERO);
            ci.cancel();
        }
    }
}
