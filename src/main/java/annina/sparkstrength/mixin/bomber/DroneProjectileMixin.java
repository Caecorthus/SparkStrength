package annina.sparkstrength.mixin.bomber;

import annina.sparkstrength.entity.DroneEntity;
import annina.sparkstrength.role.bomber.drone.DroneWeaponHits;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ProjectileDeflection;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Projectiles versus Bomber drones (both sides, so client prediction agrees). {@code canHit}: only weapons collide
 * with a drone (damaging projectiles, tagged thrown weapons, Wathe grenades); every other throwable passes through.
 * Subclass overrides AND/OR with this result, so their own rules still apply. {@code hitOrDeflect}: a tagged thrown
 * weapon (e.g. SparkWitch's shuriken, whose own hit logic only knows players) breaks the drone and stops (server).
 * 投射物与炸弹客无人机（两端执行，客户端预测一致）。canHit：只有武器会与无人机碰撞（有伤害的投射物、带标签的投掷武器、Wathe 手雷），
 * 其余投掷物直接穿过；子类覆写会与此结果做与/或运算，其自身规则仍然生效。hitOrDeflect：带标签的投掷武器（如只认玩家的 SparkWitch
 * 手里剑）击毁无人机并停下（服务端）。
 */
@Mixin(ProjectileEntity.class)
public abstract class DroneProjectileMixin {
    @Inject(method = "canHit(Lnet/minecraft/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
    private void sparkstrength$onlyWeaponsHitDrones(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof DroneEntity
                && !DroneWeaponHits.projectileMayHitDrone((ProjectileEntity) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "hitOrDeflect(Lnet/minecraft/util/hit/HitResult;)Lnet/minecraft/entity/ProjectileDeflection;",
            at = @At("HEAD"), cancellable = true)
    private void sparkstrength$taggedProjectileBreaksDrone(HitResult hitResult,
                                                           CallbackInfoReturnable<ProjectileDeflection> cir) {
        if (hitResult instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof DroneEntity drone
                && DroneWeaponHits.onTaggedProjectileHit((ProjectileEntity) (Object) this, drone)) {
            cir.setReturnValue(ProjectileDeflection.NONE);
        }
    }
}
