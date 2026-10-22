package annina.sparkstrength.mixin.viewpoint;

import annina.sparkstrength.viewpoint.RemoteViewpoint;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ChunkDataSender;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Remote viewpoint at any distance, chunk batches: queued chunks are sent nearest-first around the piloted drone or
 * the possessed Wraith (matching {@link RemoteViewpointChunkLoadingManagerMixin}), so the area the viewer looks at
 * arrives first. Falls back to the body when {@link RemoteViewpoint#anchorOf} returns null.
 * 远程视点无视距离（区块批次）：排队的区块按与所驾驶无人机或被附身冤魂的距离由近到远发送（与
 * RemoteViewpointChunkLoadingManagerMixin 一致），使观看者正在看的区域最先到达。anchorOf 返回 null 时回退为身体位置。
 *
 * <p>{@code require = 0}: skipped (vanilla nearest-to-body order) if a chunk-system mod reshapes this method.
 * {@code require = 0}：区块系统模组改写此方法时跳过（保持原版按身体由近到远的顺序）。</p>
 */
@Mixin(ChunkDataSender.class)
public abstract class RemoteViewpointChunkDataSenderMixin {
    @ModifyExpressionValue(
            method = "sendChunkBatches(Lnet/minecraft/server/network/ServerPlayerEntity;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/network/ServerPlayerEntity;getChunkPos()Lnet/minecraft/util/math/ChunkPos;"),
            require = 0
    )
    private ChunkPos sparkstrength$batchAroundRemoteViewpoint(
            ChunkPos original, @Local(argsOnly = true) ServerPlayerEntity player
    ) {
        Entity anchor = RemoteViewpoint.anchorOf(player);
        return anchor == null ? original : anchor.getChunkPos();
    }
}
