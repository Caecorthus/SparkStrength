package annina.sparkstrength.client.mixin.bomber;

import annina.sparkstrength.entity.DroneEntity;
import annina.sparkstrength.role.bomber.drone.DroneHitGeometry;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.doctor4t.wathe.client.gui.CrosshairRenderer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;

/**
 * Wathe crosshair hints over Bomber drones (display only; the knife hint already follows {@code DroneKnifeTargetMixin}).
 * Revolver/Derringer: the private players-only {@code getVisibleGunTarget} pick is replaced by a strictly nearer drone,
 * like {@code DroneGunTargetMixin} does for the shot. Bat: the "crosshair target is a player" check also accepts a live
 * drone, since a bat hit breaks it. {@code remap = false} (Wathe class, name-only selectors, the class literal is
 * remapped as bytecode) and {@code require = 0}: a reshaped Wathe renderer only loses these hints.
 * 炸弹客无人机上的 Wathe 准星提示（仅显示；刀提示已随 DroneKnifeTargetMixin 生效）。左轮/德林加：私有、只看玩家的
 * getVisibleGunTarget 结果若有严格更近的无人机则替换，与 DroneGunTargetMixin 对射击的处理一致。球棒：“准星目标是玩家”的判断
 * 也接受存活的无人机，因为球棒击中会将其击毁。remap = false（Wathe 类，仅用方法名选择，类字面量按字节码重映射）且 require = 0：
 * Wathe 渲染器结构变化时只会失去这些提示。
 */
@Mixin(value = CrosshairRenderer.class, remap = false)
public abstract class DroneCrosshairHintMixin {
    @WrapOperation(
            method = "renderCrosshair",
            at = @At(value = "INVOKE", target = "Ldev/doctor4t/wathe/client/gui/CrosshairRenderer;getVisibleGunTarget"),
            require = 0
    )
    private static HitResult sparkstrength$hintDroneForGuns(
            PlayerEntity user, double range, Operation<HitResult> original
    ) {
        return DroneHitGeometry.preferNearerDrone(user, original.call(user, range), range);
    }

    @WrapOperation(
            method = "renderCrosshair",
            constant = @Constant(classValue = PlayerEntity.class),
            require = 0
    )
    private static boolean sparkstrength$hintDroneForBat(Object target, Operation<Boolean> original) {
        return original.call(target) || target instanceof DroneEntity drone && DroneHitGeometry.isLive(drone);
    }
}
