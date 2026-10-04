package annina.sparkstrength.client.mixin.bomber;

import annina.sparkstrength.role.bomber.drone.DroneHitGeometry;
import annina.sparkstrength.role.bomber.drone.DroneWeaponRules;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.doctor4t.wathe.item.DerringerItem;
import dev.doctor4t.wathe.item.RevolverItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Client gun target: wraps the {@code getGunTarget} call inside Revolver/Derringer {@code use} (vanilla override,
 * remapped) rather than the static method itself, because SparkTraits Marksman replaces {@code getGunTarget} at HEAD
 * with its own players-only raycast. The original is called first (keeping Marksman's range), then a Bomber drone
 * strictly nearer than its hit replaces it, so Wathe sends the drone id and {@code DroneGunShootPayloadMixin} validates
 * and breaks it; the player behind is not shot. Chains with SparkWitch's Seeker wrapper on the same call. The
 * {@code @At} targets keep the default remap so their Minecraft descriptor types are remapped in production.
 * 客户端枪械目标：包装左轮/德林加 use（原版覆写，需重映射）中对 getGunTarget 的调用，而不是该静态方法本身，因为 SparkTraits
 * 神射手会在 HEAD 用只看玩家的射线替换 getGunTarget。先调用原方法（保留神射手射程），若有严格更近的炸弹客无人机则替换结果，
 * Wathe 随即发送无人机 id，由 DroneGunShootPayloadMixin 校验并击毁；身后的玩家不会中枪。与 SparkWitch 搜寻者在同一调用上的包装链式共存。
 * @At 目标保持默认 remap，使其中的 Minecraft 描述符类型在生产环境被重映射。
 */
@Mixin({RevolverItem.class, DerringerItem.class})
public abstract class DroneGunTargetMixin {
    @WrapOperation(
            method = "use(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/Hand;)Lnet/minecraft/util/TypedActionResult;",
            at = {
                    @At(value = "INVOKE",
                            target = "Ldev/doctor4t/wathe/item/RevolverItem;getGunTarget(Lnet/minecraft/entity/player/PlayerEntity;)Lnet/minecraft/util/hit/HitResult;"),
                    @At(value = "INVOKE",
                            target = "Ldev/doctor4t/wathe/item/DerringerItem;getGunTarget(Lnet/minecraft/entity/player/PlayerEntity;)Lnet/minecraft/util/hit/HitResult;")
            })
    private HitResult sparkstrength$preferNearerDrone(PlayerEntity user, Operation<HitResult> original) {
        HitResult result = original.call(user);
        double range = (Object) this instanceof DerringerItem
                ? DroneWeaponRules.DERRINGER_RANGE : DroneWeaponRules.REVOLVER_RANGE;
        return DroneHitGeometry.preferNearerDrone(user, result, range);
    }
}
