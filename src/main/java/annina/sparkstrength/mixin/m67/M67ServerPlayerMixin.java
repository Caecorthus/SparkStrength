package annina.sparkstrength.mixin.m67;

import annina.sparkstrength.item.m67.M67UseService;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public abstract class M67ServerPlayerMixin {
    // Programmatic drops bypass the packet handler. / 服务端主动丢弃不会经过数据包处理器。
    @Inject(method = "dropSelectedItem", at = @At("HEAD"))
    private void sparkstrength$cancelM67OnDrop(boolean entireStack, CallbackInfoReturnable<Boolean> cir) {
        M67UseService.cancel((ServerPlayerEntity) (Object) this);
    }
    @Inject(method = "dropSelectedItem", at = @At("RETURN"))
    private void sparkstrength$refreshM67AfterDrop(boolean entireStack, CallbackInfoReturnable<Boolean> cir) {
        M67UseService.tick((ServerPlayerEntity) (Object) this);
    }
}
