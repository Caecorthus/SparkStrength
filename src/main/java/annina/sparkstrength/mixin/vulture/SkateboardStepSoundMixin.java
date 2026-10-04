package annina.sparkstrength.mixin.vulture;

import annina.sparkstrength.component.vulture.SkateboardRideComponent;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A skating player rolls instead of stepping: footsteps are silenced on both sides (the server broadcast and the
 * rider's own client) while the synced ride lasts; clients play a rolling sound for riders instead.
 * 滑行中的玩家是滚动而非迈步：在同步的滑行期间，两端（服务端广播与骑手本人客户端）都静音脚步声；客户端改为播放滚轮声。
 */
@Mixin(Entity.class)
public abstract class SkateboardStepSoundMixin {
    @Inject(method = "playStepSounds", at = @At("HEAD"), cancellable = true)
    private void sparkstrength$skipSkateboardStepSounds(BlockPos pos, BlockState state, CallbackInfo ci) {
        if ((Object) this instanceof PlayerEntity player && SkateboardRideComponent.KEY.get(player).isRiding()) {
            ci.cancel();
        }
    }
}
