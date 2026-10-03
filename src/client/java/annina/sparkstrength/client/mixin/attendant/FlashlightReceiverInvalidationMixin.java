package annina.sparkstrength.client.mixin.attendant;

import annina.sparkstrength.client.role.attendant.flashlight.FlashlightRenderer;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Flashlight receiver meshes follow client block changes. {@code ClientWorld#updateListeners} is the single point where
 * every re-rendering block change (set, delta and single-block packets, local prediction) reaches the world renderer,
 * so HEAD here sees exactly what vanilla and Sodium re-mesh. Observe-only: same-state calls (block entity refreshes)
 * are ignored and nothing is cancelled.
 * 手电筒受光网格跟随客户端方块变化。{@code ClientWorld#updateListeners} 是所有需要重绘的方块变化（单方块、批量、
 * 本地预测）通知世界渲染器的唯一入口，因此在 HEAD 注入看到的正是原版与 Sodium 会重新网格化的变化。只观察：
 * 状态未变的调用（方块实体刷新）被忽略，且不取消任何逻辑。
 */
@Mixin(ClientWorld.class)
public abstract class FlashlightReceiverInvalidationMixin {
    @Inject(method = "updateListeners", at = @At("HEAD"))
    private void sparkstrength$invalidateFlashlightReceivers(BlockPos pos, BlockState oldState, BlockState newState,
                                                              int flags, CallbackInfo ci) {
        if (oldState != newState) {
            FlashlightRenderer.onBlockChanged(pos);
        }
    }
}
