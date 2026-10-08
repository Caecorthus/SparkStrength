package annina.sparkstrength.mixin.spiritualist;

import annina.sparkstrength.role.spiritualist.SpiritPossessionService;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.network.ChunkDataSender;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Spiritualist possession at any distance, chunk batches: queued chunks are sent nearest-first around the possessed
 * Wraith (matching {@link SpiritPossessionChunkLoadingManagerMixin}). Falls back to the body when
 * {@link SpiritPossessionService#targetOf} returns null.
 * 灵界行者无视距离的附身（区块批次）：排队的区块按与被附身冤魂的距离由近到远发送（与 SpiritPossessionChunkLoadingManagerMixin
 * 一致）。targetOf 返回 null 时回退为肉身位置。
 *
 * <p>{@code require = 0}: skipped (vanilla nearest-to-body order) if a chunk-system mod reshapes this method.
 * {@code require = 0}：区块系统模组改写此方法时跳过（保持原版按身体由近到远的顺序）。</p>
 */
@Mixin(ChunkDataSender.class)
public abstract class SpiritPossessionChunkDataSenderMixin {
    @ModifyExpressionValue(
            method = "sendChunkBatches(Lnet/minecraft/server/network/ServerPlayerEntity;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/network/ServerPlayerEntity;getChunkPos()Lnet/minecraft/util/math/ChunkPos;"),
            require = 0
    )
    private ChunkPos sparkstrength$batchAroundPossessedWraith(
            ChunkPos original, @Local(argsOnly = true) ServerPlayerEntity player
    ) {
        ServerPlayerEntity wraith = SpiritPossessionService.targetOf(player);
        return wraith == null ? original : wraith.getChunkPos();
    }
}
