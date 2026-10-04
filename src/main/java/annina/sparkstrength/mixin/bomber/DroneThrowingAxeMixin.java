package annina.sparkstrength.mixin.bomber;

import annina.sparkstrength.role.bomber.drone.DroneHitGeometry;
import annina.sparkstrength.role.bomber.drone.DroneWeaponHits;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.agmas.noellesroles.entity.ThrowingAxeEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * NoellesRoles throwing axe versus Bomber drones (server). Its entity collision is custom (players only, piercing),
 * so a drone is resolved on this tick's segment before the pierce loop and broken (attributed to the thrower); players
 * before the drone are still hit, players behind it are shielded, and the axe stops at the drone. With no drone on the
 * segment the cut stays infinite and NoellesRoles behaves exactly as before. Coexists with SparkWitch's Seeker hooks on
 * the same calls (each wrapper only narrows the hit set).
 * NoellesRoles 飞斧与炸弹客无人机（服务端）。其实体碰撞为自定义（只认玩家、可贯穿），因此在贯穿循环前判定本刻路径上的无人机并将其击毁
 * （归属投掷者）；无人机之前的玩家照常命中，之后的玩家被挡下，飞斧停在无人机处。路径上没有无人机时截断距离保持无穷大，
 * NoellesRoles 行为完全不变。与 SparkWitch 搜寻者在相同调用上的钩子共存（每个包装只会缩小命中集合）。
 */
@Mixin(ThrowingAxeEntity.class)
public abstract class DroneThrowingAxeMixin {
    /** Squared distance from this tick's start to the broken drone; infinite when none. / 本刻起点到被击毁无人机的平方距离。 */
    @Unique
    private double sparkstrength$droneCutSquared = Double.POSITIVE_INFINITY;

    @Inject(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/World;getOtherEntities(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Box;)Ljava/util/List;"))
    private void sparkstrength$sweepDrones(CallbackInfo ci) {
        ThrowingAxeEntity axe = (ThrowingAxeEntity) (Object) this;
        Vec3d from = axe.getPos();
        DroneHitGeometry.DroneHit hit = DroneWeaponHits.onThrowingAxeSweep(axe, axe.getOwner(), from,
                from.add(axe.getVelocity()));
        sparkstrength$droneCutSquared = hit == null ? Double.POSITIVE_INFINITY : hit.distanceSquared();
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/math/Box;raycast(Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Vec3d;)Ljava/util/Optional;"))
    private Optional<Vec3d> sparkstrength$shieldPlayersBehindDrone(Box box, Vec3d from, Vec3d to,
                                                                   Operation<Optional<Vec3d>> original) {
        Optional<Vec3d> hit = original.call(box, from, to);
        double cut = sparkstrength$droneCutSquared;
        return cut == Double.POSITIVE_INFINITY ? hit : hit.filter(point -> from.squaredDistanceTo(point) < cut);
    }

    @Inject(method = "tick", cancellable = true, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/projectile/PersistentProjectileEntity;tick()V"))
    private void sparkstrength$stopAtDrone(CallbackInfo ci) {
        if (sparkstrength$droneCutSquared == Double.POSITIVE_INFINITY) {
            return;
        }
        sparkstrength$droneCutSquared = Double.POSITIVE_INFINITY;
        ((ThrowingAxeEntity) (Object) this).discard();
        ci.cancel();
    }
}
