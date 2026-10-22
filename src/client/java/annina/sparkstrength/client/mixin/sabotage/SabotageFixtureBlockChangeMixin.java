package annina.sparkstrength.client.mixin.sabotage;

import annina.sparkstrength.client.sabotage.SabotageFixtureIndex;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the client's sabotage fixture index current. {@code World#setBlockState} ends every successful change with
 * {@code onBlockChanged}, whatever the flags (packets, chunk deltas, local prediction), and ClientWorld does not override
 * it. Observe-only; the integrated server's worlds are skipped.
 * 让客户端的破坏装置索引保持最新。{@code World#setBlockState} 的每次成功修改（数据包、区块批量更新、本地预测，不论标志位）
 * 都以 {@code onBlockChanged} 结束，ClientWorld 也没有覆写它。只观察；内置服务端的世界被跳过。
 */
@Mixin(World.class)
public abstract class SabotageFixtureBlockChangeMixin {
    @Inject(method = "onBlockChanged", at = @At("HEAD"))
    private void sparkstrength$indexSabotageFixture(BlockPos pos, BlockState oldBlock, BlockState newBlock,
                                                    CallbackInfo ci) {
        if ((Object) this instanceof ClientWorld world) {
            SabotageFixtureIndex.onBlockChanged(world, pos, oldBlock, newBlock);
        }
    }
}
