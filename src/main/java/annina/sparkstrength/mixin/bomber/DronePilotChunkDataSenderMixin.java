package annina.sparkstrength.mixin.bomber;

import annina.sparkstrength.entity.DroneEntity;
import annina.sparkstrength.role.bomber.drone.DronePilotService;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.network.ChunkDataSender;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Unlimited drone range, chunk batches: queued chunks are sent nearest-first around the piloted drone (matching
 * {@link DronePilotChunkLoadingManagerMixin}), so the area the pilot looks at arrives first. Falls back to the body
 * when {@link DronePilotService#droneOf} returns null.
 * 无人机无限距离（区块批次）：排队的区块按与所驾驶无人机的距离由近到远发送（与 DronePilotChunkLoadingManagerMixin 一致），
 * 使驾驶者正在看的区域最先到达。droneOf 返回 null 时回退为身体位置。
 *
 * <p>{@code require = 0}: skipped (vanilla nearest-to-body order) if a chunk-system mod reshapes this method.
 * {@code require = 0}：区块系统模组改写此方法时跳过（保持原版按身体由近到远的顺序）。</p>
 */
@Mixin(ChunkDataSender.class)
public abstract class DronePilotChunkDataSenderMixin {
    @ModifyExpressionValue(
            method = "sendChunkBatches(Lnet/minecraft/server/network/ServerPlayerEntity;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/network/ServerPlayerEntity;getChunkPos()Lnet/minecraft/util/math/ChunkPos;"),
            require = 0
    )
    private ChunkPos sparkstrength$batchAroundDrone(
            ChunkPos original, @Local(argsOnly = true) ServerPlayerEntity player
    ) {
        DroneEntity drone = DronePilotService.droneOf(player);
        return drone == null ? original : drone.getChunkPos();
    }
}
