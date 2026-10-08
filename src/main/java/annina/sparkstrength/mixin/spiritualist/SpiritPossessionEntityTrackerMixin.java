package annina.sparkstrength.mixin.spiritualist;

import annina.sparkstrength.role.spiritualist.SpiritPossessionService;
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
 * Spiritualist possession at any distance, entity tracking: the Spiritualist always tracks the possessed Wraith
 * (whatever the distance to its body), and other entities are tracked by their distance to that Wraith, so the view
 * shows what is around it. Every branch falls back to vanilla when {@link SpiritPossessionService#targetOf} returns
 * null; the per-tick {@code updatePosition} re-evaluation then restores body-based tracking. Chains with the Bomber
 * drone's {@code DronePilotEntityTrackerMixin}: a player is never both a pilot and a possessing Spiritualist.
 * 灵界行者无视距离的附身（实体追踪）：灵界行者始终追踪被附身的冤魂（无论与肉身相距多远），其他实体按与该冤魂的距离判断是否追踪，
 * 使画面能看到冤魂周围的事物。targetOf 返回 null 时所有分支都回退为原版；每刻的 updatePosition 重新评估随后恢复以肉身为准的追踪。
 * 与炸弹客无人机的 DronePilotEntityTrackerMixin 串联：同一玩家不会既驾驶无人机又在附身。
 *
 * <p>{@code require = 0} on both injectors: skipped if a chunk-system mod reshapes {@code updateTrackedStatus}, and
 * tracking stays vanilla. / 两个注入都为 {@code require = 0}：区块系统模组改写 updateTrackedStatus 时跳过，追踪保持原版。</p>
 */
@Mixin(targets = "net.minecraft.server.world.ServerChunkLoadingManager$EntityTracker")
public abstract class SpiritPossessionEntityTrackerMixin {
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
    private void sparkstrength$alwaysTrackPossessedWraith(ServerPlayerEntity player, CallbackInfo ci) {
        if (!(entity instanceof ServerPlayerEntity) || entity == player
                || SpiritPossessionService.targetOf(player) != entity) {
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
    private Vec3d sparkstrength$measureFromPossessedWraith(
            Vec3d original, @Local(argsOnly = true) ServerPlayerEntity player
    ) {
        ServerPlayerEntity wraith = SpiritPossessionService.targetOf(player);
        return wraith == null ? original : wraith.getPos();
    }
}
