package annina.sparkstrength.mixin.viewpoint;

import annina.sparkstrength.viewpoint.RemoteViewpoint;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.network.PlayerAssociatedNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/**
 * Remote viewpoint at any distance, entity tracking: the viewer always tracks its anchor, the drone it pilots or the
 * Wraith it possesses (whatever the distance to its body), and other entities are tracked by their distance to
 * {@link RemoteViewpoint#anchorOf}, so the viewer sees what is around it. Every branch falls back to vanilla when the
 * viewer has no anchor; the per-tick {@code updatePosition} re-evaluation (RemoteViewSessions) then restores
 * body-based tracking.
 * 远程视点无视距离（实体追踪）：观看者始终追踪自己的锚点，即所驾驶的无人机或所附身的冤魂（无论与身体相距多远），其他实体按与
 * anchorOf 的距离判断是否追踪，使观看者能看到锚点周围的事物。观看者没有锚点时所有分支都回退为原版；RemoteViewSessions 每刻调用
 * updatePosition 重新评估，从而恢复以身体为准的追踪。
 *
 * <p>NoellesRoles also shadows {@code entity}/{@code listeners} on this class for a different method; no conflict.
 * NoellesRoles 也在此类上影射 entity/listeners，但作用于其他方法，互不冲突。</p>
 *
 * <p>{@code require = 0} on both injectors: if a chunk-system mod reshapes {@code updateTrackedStatus} they are
 * skipped instead of failing at startup, and tracking stays vanilla (entities within range of the body only).
 * 两个注入都为 {@code require = 0}：区块系统模组改写 updateTrackedStatus 时跳过而不是启动崩溃，追踪保持原版（只追踪身体范围内的实体）。</p>
 */
@Mixin(targets = "net.minecraft.server.world.ServerChunkLoadingManager$EntityTracker")
public abstract class RemoteViewpointEntityTrackerMixin {
    @Shadow
    @Final
    Entity entity;

    @Shadow
    @Final
    EntityTrackerEntry entry;

    @Shadow
    @Final
    private Set<PlayerAssociatedNetworkHandler> listeners;

    @Inject(
            method = "updateTrackedStatus(Lnet/minecraft/server/network/ServerPlayerEntity;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void sparkstrength$alwaysTrackRemoteViewpoint(ServerPlayerEntity player, CallbackInfo ci) {
        if (!RemoteViewpoint.isAnchor(player, entity)) {
            return;
        }
        if (listeners.add(player.networkHandler)) {
            entry.startTracking(player);
        }
        ci.cancel();
    }

    @ModifyExpressionValue(
            method = "updateTrackedStatus(Lnet/minecraft/server/network/ServerPlayerEntity;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/network/ServerPlayerEntity;getPos()Lnet/minecraft/util/math/Vec3d;"),
            require = 0
    )
    private Vec3d sparkstrength$measureFromRemoteViewpoint(
            Vec3d original, @Local(argsOnly = true) ServerPlayerEntity player
    ) {
        Entity anchor = RemoteViewpoint.anchorOf(player);
        return anchor == null ? original : anchor.getPos();
    }
}
