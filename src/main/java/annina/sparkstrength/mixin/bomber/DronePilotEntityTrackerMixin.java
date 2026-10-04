package annina.sparkstrength.mixin.bomber;

import annina.sparkstrength.entity.DroneEntity;
import annina.sparkstrength.role.bomber.drone.DronePilotService;
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
 * Unlimited drone range, entity tracking: the pilot always tracks the drone it pilots (whatever the distance to its
 * body), and other entities are tracked by their distance to that drone, so the pilot sees what is around it. Every
 * branch falls back to vanilla when {@link DronePilotService#droneOf} returns null; the pilot's per-tick
 * {@code updatePosition} re-evaluation (DronePilotService) then restores body-based tracking.
 * 无人机无限距离（实体追踪）：驾驶者始终追踪自己驾驶的无人机（无论与身体相距多远），其他实体按与该无人机的距离判断是否追踪，
 * 使驾驶者能看到无人机周围的事物。droneOf 返回 null 时所有分支都回退为原版；DronePilotService 每刻调用 updatePosition
 * 重新评估，从而恢复以身体为准的追踪。
 *
 * <p>NoellesRoles also shadows {@code entity}/{@code listeners} on this class for a different method; no conflict.
 * NoellesRoles 也在此类上影射 entity/listeners，但作用于其他方法，互不冲突。</p>
 *
 * <p>{@code require = 0} on both injectors: if a chunk-system mod reshapes {@code updateTrackedStatus} they are
 * skipped instead of failing at startup, and tracking stays vanilla (entities within range of the body only).
 * 两个注入都为 {@code require = 0}：区块系统模组改写 updateTrackedStatus 时跳过而不是启动崩溃，追踪保持原版（只追踪身体范围内的实体）。</p>
 */
@Mixin(targets = "net.minecraft.server.world.ServerChunkLoadingManager$EntityTracker")
public abstract class DronePilotEntityTrackerMixin {
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
    private void sparkstrength$alwaysTrackPilotedDrone(ServerPlayerEntity player, CallbackInfo ci) {
        if (!(entity instanceof DroneEntity) || DronePilotService.droneOf(player) != entity) {
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
    private Vec3d sparkstrength$measureFromPilotedDrone(
            Vec3d original, @Local(argsOnly = true) ServerPlayerEntity player
    ) {
        DroneEntity drone = DronePilotService.droneOf(player);
        return drone == null ? original : drone.getPos();
    }
}
