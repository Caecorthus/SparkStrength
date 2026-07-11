package annina.sparkstrength.mixin.corruptcop;

import annina.sparkstrength.role.corruptcop.CorruptCopAbilityService;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.packet.AbilityC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reuses NoellesRoles' shared ability packet without adding a SparkStrength payload id.
 * 复用 NoellesRoles 的通用技能包，不新增 SparkStrength 数据包 id。
 */
@Mixin(value = Noellesroles.class, remap = false)
public abstract class CorruptCopAbilityPacketMixin {
    // The bundled 1.7.6 jar uses lambda 36; the alternate local 1.7.6 build uses lambda 5.
    // 当前打包的 1.7.6 jar 使用 lambda 36；本地另一份 1.7.6 构建使用 lambda 5。
    @Inject(
            method = {"lambda$registerPackets$36", "lambda$registerPackets$5"},
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void sparkstrength$toggleCorruptCopAbility(
            AbilityC2SPacket payload,
            ServerPlayNetworking.Context context,
            CallbackInfo ci
    ) {
        if (CorruptCopAbilityService.toggle(context.player())) {
            ci.cancel();
        }
    }
}
