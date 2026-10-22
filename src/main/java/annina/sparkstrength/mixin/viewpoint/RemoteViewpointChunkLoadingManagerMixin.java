package annina.sparkstrength.mixin.viewpoint;

import annina.sparkstrength.viewpoint.RemoteViewpoint;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerChunkLoadingManager;
import net.minecraft.util.math.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Remote viewpoint at any distance, chunk view: while a player pilots a drone or possesses a Wraith, its chunk view is
 * centred on that anchor instead of the motionless body. {@code sendWatchPackets} runs every tick for watching
 * players, so vanilla diffs the old/new view, sends the render-distance centre, streams the anchor's chunks and unloads
 * the rest; when {@link RemoteViewpoint#anchorOf} returns null (session over, camera taken by another mod) the vanilla
 * body centre comes back on the next tick by itself. Server-side tickets around the body are untouched.
 * 远程视点无视距离（区块视野）：玩家驾驶无人机或附身冤魂时，其区块视野以该锚点而非静止的身体为中心。sendWatchPackets 每刻都会为
 * 观察区块的玩家执行，因此原版会比较新旧视野、发送渲染中心、推送锚点周围区块并卸载其余区块；anchorOf 返回 null（会话结束、镜头被
 * 其他模组接管）时，下一刻自动恢复为原版的身体中心。身体周围的服务端区块票据不受影响。
 *
 * <p>{@code require = 0}: a server mod that replaces the chunk system (different method shapes) only skips this
 * injector instead of failing at startup; the viewer then sees chunks/entities around the body only (body-range
 * view), while sessions, flight and the anchor's own chunk ticket keep working.
 * {@code require = 0}：替换区块系统的服务端模组（方法结构不同）只会让此注入被跳过，而不是在启动时崩溃；此时观看者只能看到
 * 身体周围的区块与实体（身体范围视野），会话、飞行与锚点自身的区块票据照常工作。</p>
 */
@Mixin(ServerChunkLoadingManager.class)
public abstract class RemoteViewpointChunkLoadingManagerMixin {
    @ModifyExpressionValue(
            method = "sendWatchPackets(Lnet/minecraft/server/network/ServerPlayerEntity;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/network/ServerPlayerEntity;getChunkPos()Lnet/minecraft/util/math/ChunkPos;"),
            require = 0
    )
    private ChunkPos sparkstrength$centreViewOnRemoteViewpoint(
            ChunkPos original, @Local(argsOnly = true) ServerPlayerEntity player
    ) {
        Entity anchor = RemoteViewpoint.anchorOf(player);
        return anchor == null ? original : anchor.getChunkPos();
    }
}
