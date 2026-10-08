package annina.sparkstrength.mixin.spiritualist;

import annina.sparkstrength.role.spiritualist.SpiritPossessionService;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerChunkLoadingManager;
import net.minecraft.util.math.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Spiritualist possession at any distance, chunk view: while possessing, the Spiritualist's chunk view is centred on
 * the Wraith instead of the motionless body (same approach as the Bomber drone's {@code DronePilotChunkLoadingManagerMixin},
 * chained on the same call). Falls back to vanilla on the next tick once {@link SpiritPossessionService#targetOf}
 * returns null. Server-side tickets around the body are untouched.
 * 灵界行者无视距离的附身（区块视野）：附身期间，灵界行者的区块视野以冤魂而非静止的肉身为中心（做法与炸弹客无人机的
 * DronePilotChunkLoadingManagerMixin 相同，串联在同一调用上）。targetOf 返回 null 后下一刻自动恢复原版行为。
 * 身体周围的服务端区块票据不受影响。
 *
 * <p>{@code require = 0}: a chunk-system mod that reshapes this method only skips the injector; the Spiritualist then
 * sees only what is around the body. / {@code require = 0}：区块系统模组改写此方法时只跳过注入，此时灵界行者只能看到肉身附近。</p>
 */
@Mixin(ServerChunkLoadingManager.class)
public abstract class SpiritPossessionChunkLoadingManagerMixin {
    @ModifyExpressionValue(
            method = "sendWatchPackets(Lnet/minecraft/server/network/ServerPlayerEntity;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/network/ServerPlayerEntity;getChunkPos()Lnet/minecraft/util/math/ChunkPos;"),
            require = 0
    )
    private ChunkPos sparkstrength$centreViewOnPossessedWraith(
            ChunkPos original, @Local(argsOnly = true) ServerPlayerEntity player
    ) {
        ServerPlayerEntity wraith = SpiritPossessionService.targetOf(player);
        return wraith == null ? original : wraith.getChunkPos();
    }
}
