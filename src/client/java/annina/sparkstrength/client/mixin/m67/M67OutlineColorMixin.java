package annina.sparkstrength.client.mixin.m67;

import annina.sparkstrength.client.item.M67Client;
import annina.sparkstrength.entity.M67GrenadeEntity;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Client config only: vanilla's outline framebuffer needs no team or global glow mutation.
// 仅在客户端配置注册：原版描边缓冲无需修改队伍或全局发光状态。
@Mixin(Entity.class)
public abstract class M67OutlineColorMixin {
    @Inject(method = "getTeamColorValue()I", at = @At("HEAD"), cancellable = true)
    private void sparkstrength$m67OutlineColor(CallbackInfoReturnable<Integer> cir) {
        if ((Object) this instanceof M67GrenadeEntity entity) {
            int color = M67Client.outlineColor(entity);
            if (color != -1) {
                cir.setReturnValue(color);
            }
        }
    }
}
