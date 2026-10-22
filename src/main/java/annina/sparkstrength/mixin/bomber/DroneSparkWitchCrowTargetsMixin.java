package annina.sparkstrength.mixin.bomber;

import annina.sparkstrength.role.bomber.drone.DroneWeaponHits;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Optional SparkWitch seam, nearest wins between Bomber drones and the Black Raven's Raven Form crow. SparkWitch runs its
 * crow step before its Seeker device step and skips that step when the crow is strictly nearer than every competitor it
 * measures ({@code BlackRavenCrowTargets.deviceSquared}: the Hunter shotgun, the Feather Blade, the shuriken / skull /
 * potion shell tick sweeps and the throwing axe pierce cut), so a drone, which absorbs only inside that device step
 * ({@link DroneSparkWitchHitsMixin}), never got the chance to beat a farther crow. This lowers that competitor distance
 * at RETURN to the nearest live drone on the block-clipped segment (read-only, {@code DroneWeaponHits
 * .nearestDroneSquared}), so the crow loses to a nearer drone and the device step then breaks it as before. Not a
 * public API: {@code @Pseudo}, a name-only selector, {@code @Local} arguments and {@code require = 0} make it a silent
 * no-op when SparkWitch is absent or the method changes. The USEC rifle and the backblast already let a drone win
 * (their crow step shrinks the reach the device step uses), so they are untouched.
 * 可选的 SparkWitch 接缝：炸弹客无人机与黑羽鸦化鸦乌鸦之间的最近者命中。SparkWitch 在搜寻者设备步骤之前执行乌鸦步骤，乌鸦严格近于
 * 其量取的所有竞争对象（{@code BlackRavenCrowTargets.deviceSquared}：猎人猎枪、羽刃、手里剑 / 凋灵之首 / 药炮弹的逐刻扫掠、
 * 飞斧贯穿截断）时跳过设备步骤，因此只在该设备步骤内吸收的无人机（{@link DroneSparkWitchHitsMixin}）从来没有机会胜过更远的乌鸦。
 * 本 mixin 在 RETURN 处把该竞争距离压低到按方块截断的线段上最近的存活无人机（只读，{@code DroneWeaponHits.nearestDroneSquared}），
 * 使乌鸦输给更近的无人机，随后设备步骤照旧将其击毁。这不是公开 API：@Pseudo、仅方法名选择器、@Local 参数与 require = 0 使
 * SparkWitch 缺失或方法变更时静默失效。USEC 步枪与尾焰已能让无人机胜出（其乌鸦步骤缩短了设备步骤使用的距离），因此不受影响。
 */
@Pseudo
@Mixin(targets = "dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.hit.BlackRavenCrowTargets", remap = false)
public abstract class DroneSparkWitchCrowTargetsMixin {
    /** {@code deviceSquared(ServerPlayerEntity, Vec3d, Vec3d)}: squared distance, +inf when none. / 平方距离。 */
    @Inject(method = "deviceSquared", at = @At("RETURN"), cancellable = true, require = 0)
    private static void sparkstrength$dronesCompeteWithCrows(CallbackInfoReturnable<Double> cir,
                                                             @Local(argsOnly = true) ServerPlayerEntity attacker,
                                                             @Local(argsOnly = true, index = 1) Vec3d start,
                                                             @Local(argsOnly = true, index = 2) Vec3d end) {
        double drone = DroneWeaponHits.nearestDroneSquared(attacker, start, end);
        if (drone < cir.getReturnValue()) {
            cir.setReturnValue(drone);
        }
    }
}
