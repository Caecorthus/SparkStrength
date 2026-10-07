package annina.sparkstrength.mixin.serialkiller;

import annina.sparkstrength.item.SerialPistolItem;
import dev.doctor4t.wathe.util.GunShootPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The serial pistols sit in {@code wathe:guns} only for Wathe's gun pose and pickup treatment; they fire through
 * {@code sparkstrength:serial_pistol_shoot} and never send {@code wathe:gunshoot}. Wathe's receiver reads only the main
 * hand and trusts the client (no role check, 65 blocks, no line of sight, zero cooldown for unlisted items), so any
 * such packet with a serial pistol in the main hand is forged and dropped.
 * 连环手枪加入 {@code wathe:guns} 只为沿用 Wathe 的持枪姿势与拾取处理；它们经 {@code sparkstrength:serial_pistol_shoot}
 * 开火，从不发送 {@code wathe:gunshoot}。Wathe 接收器只读主手且信任客户端（无身份检查、65 格、无视线、未登记物品零冷却），
 * 因此主手持连环手枪时收到的此类数据包必为伪造，直接丢弃。
 */
@Mixin(value = GunShootPayload.Receiver.class, remap = false)
public abstract class SerialPistolGunShootGuardMixin {
    @Inject(
            method = "receive(Ldev/doctor4t/wathe/util/GunShootPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void sparkstrength$dropForgedSerialPistolShot(
            GunShootPayload payload,
            ServerPlayNetworking.Context context,
            CallbackInfo ci
    ) {
        if (SerialPistolItem.isSerialPistol(context.player().getMainHandStack())) {
            ci.cancel();
        }
    }
}
