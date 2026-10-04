package annina.sparkstrength.mixin.bomber;

import annina.sparkstrength.role.bomber.drone.DroneWeaponHits;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Optional SparkWitch seam: its server-side hitscans and blasts already call one static entry each in
 * {@code SeekerDeviceHits} (nearest-wins against Seeker devices), so Bomber drones join the same contract at those
 * entries' HEAD: a drone nearer than the target absorbs the shot (null target / true / shortened ray / burst point),
 * and blasts break drones in range. Not a public API: {@code @Pseudo}, name-only selectors, {@code @Local} arguments
 * and {@code require = 0} make every hook a silent no-op when SparkWitch is absent (the class never loads) or the
 * method changes (zero matches or an unmatched {@code @Local} drops it). When a drone absorbs a ray, SparkWitch's own
 * device check for that call is skipped (a Seeker device and a drone on one ray is an accepted edge case).
 * 可选的 SparkWitch 接缝：其服务端即时射线与爆炸都已在 SeekerDeviceHits 中各调用一个静态入口（对搜寻者设备的“最近者命中”），
 * 因此炸弹客无人机在这些入口的 HEAD 加入同一契约：比目标更近的无人机吸收这一击（返回 null 目标 / true / 截短射线 / 爆点），
 * 爆炸则击毁范围内的无人机。这不是公开 API：@Pseudo、仅方法名选择器、@Local 参数与 require = 0 使 SparkWitch 缺失（类不加载）
 * 或方法变更（零匹配或 @Local 不匹配）时静默失效。无人机吸收射线时本次调用跳过 SparkWitch 自身的设备判定
 * （同一射线上同时有搜寻者设备与无人机属于可接受的边界情况）。
 */
@Pseudo
@Mixin(targets = "dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDeviceHits", remap = false)
public abstract class DroneSparkWitchHitsMixin {
    /** Hunter double-barrel shotgun: {@code onShotgunFired(PlayerEntity, T, double)}. / 双管猎枪。 */
    @Inject(method = "onShotgunFired", at = @At("HEAD"), cancellable = true, require = 0)
    private static void sparkstrength$shotgunHitsDrone(CallbackInfoReturnable<PlayerEntity> cir,
                                                       @Local(argsOnly = true, index = 0) PlayerEntity shooter,
                                                       @Local(argsOnly = true, index = 1) PlayerEntity target,
                                                       @Local(argsOnly = true) double range) {
        if (DroneWeaponHits.onSparkWitchRay(shooter, target, range)) {
            cir.setReturnValue(null);
        }
    }

    /** Black Raven feather blade: {@code onFeatherBladeFired(ServerPlayerEntity, T, double)}. / 黑鸦羽刃。 */
    @Inject(method = "onFeatherBladeFired", at = @At("HEAD"), cancellable = true, require = 0)
    private static void sparkstrength$featherBladeHitsDrone(CallbackInfoReturnable<PlayerEntity> cir,
                                                            @Local(argsOnly = true, index = 0) ServerPlayerEntity user,
                                                            @Local(argsOnly = true, index = 1) PlayerEntity target,
                                                            @Local(argsOnly = true) double range) {
        if (DroneWeaponHits.onSparkWitchRay(user, target, range)) {
            cir.setReturnValue(null);
        }
    }

    /** Control Expert taser: {@code onTaserFired(ServerPlayerEntity, T, double)}. / 控场专家电击枪。 */
    @Inject(method = "onTaserFired", at = @At("HEAD"), cancellable = true, require = 0)
    private static void sparkstrength$taserHitsDrone(CallbackInfoReturnable<PlayerEntity> cir,
                                                     @Local(argsOnly = true, index = 0) ServerPlayerEntity user,
                                                     @Local(argsOnly = true, index = 1) PlayerEntity target,
                                                     @Local(argsOnly = true) double range) {
        if (DroneWeaponHits.onSparkWitchRay(user, target, range)) {
            cir.setReturnValue(null);
        }
    }

    /** Abyss Listener shriek gun: {@code onShriekGunFired(ServerPlayerEntity, T, double)}. / 聆渊者啸音铳。 */
    @Inject(method = "onShriekGunFired", at = @At("HEAD"), cancellable = true, require = 0)
    private static void sparkstrength$shriekGunHitsDrone(CallbackInfoReturnable<PlayerEntity> cir,
                                                         @Local(argsOnly = true, index = 0) ServerPlayerEntity user,
                                                         @Local(argsOnly = true, index = 1) PlayerEntity target,
                                                         @Local(argsOnly = true) double range) {
        if (DroneWeaponHits.onSparkWitchRay(user, target, range)) {
            cir.setReturnValue(null);
        }
    }

    /**
     * Murderous Witch death ray: {@code onDeathRayFired(ServerPlayerEntity, Vec3d, Vec3d, double)}; the visible distance
     * is cut at the nearest drone before SparkWitch's own device check runs on the shortened ray.
     * 杀意魔女死光：在 SparkWitch 自身设备判定之前，把可见距离截断到最近的无人机处。
     */
    @ModifyVariable(method = "onDeathRayFired", at = @At("HEAD"), argsOnly = true, require = 0)
    private static double sparkstrength$deathRayStopsAtDrone(double visibleDistance,
                                                             @Local(argsOnly = true) ServerPlayerEntity user,
                                                             @Local(argsOnly = true, index = 1) Vec3d start,
                                                             @Local(argsOnly = true, index = 2) Vec3d direction) {
        return DroneWeaponHits.onSparkWitchDeathRay(user, start, direction, visibleDistance);
    }

    /** Potion Gunner shell in flight: {@code onPotionShellSweep(Entity, Entity, Vec3d, Vec3d)}. / 药炮手炮弹飞行。 */
    @Inject(method = "onPotionShellSweep", at = @At("HEAD"), cancellable = true, require = 0)
    private static void sparkstrength$shellBurstsOnDrone(CallbackInfoReturnable<Vec3d> cir,
                                                         @Local(argsOnly = true, index = 0) Entity shell,
                                                         @Local(argsOnly = true, index = 1) Entity thrower,
                                                         @Local(argsOnly = true, index = 2) Vec3d from,
                                                         @Local(argsOnly = true, index = 3) Vec3d to) {
        Vec3d burst = DroneWeaponHits.onSparkWitchShellSweep(shell, thrower, from, to);
        if (burst != null) {
            cir.setReturnValue(burst);
        }
    }

    /** Potion launcher backblast: {@code onPotionBackblast(ServerPlayerEntity, Vec3d, Vec3d, double)}. / 炮筒尾焰。 */
    @Inject(method = "onPotionBackblast", at = @At("HEAD"), cancellable = true, require = 0)
    private static void sparkstrength$backblastHitsDrone(CallbackInfoReturnable<Boolean> cir,
                                                         @Local(argsOnly = true) ServerPlayerEntity gunner,
                                                         @Local(argsOnly = true, index = 1) Vec3d start,
                                                         @Local(argsOnly = true, index = 2) Vec3d direction,
                                                         @Local(argsOnly = true) double reach) {
        if (DroneWeaponHits.onSparkWitchBackblast(gunner, start, direction, reach)) {
            cir.setReturnValue(true);
        }
    }

    /**
     * Area blasts: {@code onBlast(ServerWorld, Vec3d, double, ServerPlayerEntity, SeekerBreakSource)}. SparkWitch also
     * reports Wathe grenades (at their HEAD, before the kills) and M67s here; those are ignored while
     * {@code DroneCombatService.runOwnBlast} runs, since their own hooks break drones after the kills.
     * 范围爆炸。SparkWitch 也会在此上报 Wathe 手雷（在其 HEAD、击杀之前）与 M67；runOwnBlast 执行期间忽略这些上报，
     * 因为它们自身的钩子会在击杀之后击毁无人机。
     */
    @Inject(method = "onBlast", at = @At("HEAD"), require = 0)
    private static void sparkstrength$blastBreaksDrones(CallbackInfo ci,
                                                        @Local(argsOnly = true) ServerWorld world,
                                                        @Local(argsOnly = true) Vec3d center,
                                                        @Local(argsOnly = true) double radius,
                                                        @Local(argsOnly = true) ServerPlayerEntity owner) {
        DroneWeaponHits.onSparkWitchBlast(world, center, radius, owner);
    }
}
