package annina.sparkstrength.mixin.veteran;

import annina.sparkstrength.role.veteran.VeteranKnifeService;
import dev.doctor4t.wathe.util.KnifeStabPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Routes Veteran payloads into the custom server-authoritative knife service.
 * 在 Wathe 服务端刀人包入口将老兵分流至自定义权威服务。
 *
 * <p>Wathe's Veteran component stores only 0/1/2 uses, so the custom path preserves cumulative
 * multi-knife uses and applies the fixed Veteran cooldown. Normal wind-up and sounds are preserved;
 * non-Veterans continue through Wathe/NoellesRoles unchanged.</p>
 * <p>Wathe 的老兵组件只能保存 0/1/2 次，因此自定义路径负责累计多把匕首次数并应用老兵固定冷却。
 * 常规蓄力与音效均保留；非老兵仍完整走 Wathe/NoellesRoles 原有流程。</p>
 */
@Mixin(KnifeStabPayload.Receiver.class)
public abstract class VeteranKnifeStabPayloadMixin {
    @Inject(method = "receive", at = @At("HEAD"), cancellable = true)
    private void sparkstrength$veteranKnifeStab(
            KnifeStabPayload payload,
            ServerPlayNetworking.Context context,
            CallbackInfo ci
    ) {
        if (VeteranKnifeService.handleKnifeStab(payload, context)) {
            ci.cancel();
        }
    }
}
