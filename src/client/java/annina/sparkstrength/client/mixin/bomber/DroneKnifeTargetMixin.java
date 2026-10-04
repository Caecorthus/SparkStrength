package annina.sparkstrength.client.mixin.bomber;

import annina.sparkstrength.role.bomber.drone.DroneHitGeometry;
import annina.sparkstrength.role.bomber.drone.DroneWeaponRules;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.doctor4t.wathe.item.KnifeItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Client knife target: Wathe's {@code KnifeItem.getKnifeTarget} only sees players, so a Bomber drone strictly nearer on
 * the look ray replaces the result. Every caller shares it: the charged release, the instant stabs (NoellesRoles
 * Scavenger, SparkStrength Veteran/Coroner disguise) and Wathe's crosshair knife hint; the server
 * ({@code DroneKnifeStabPayloadMixin}) validates and breaks. Client only: server-side calls are returned untouched.
 * Wathe member with a unique name, so the selector is name-only with {@code remap = false}.
 * 客户端刀目标：Wathe 的 KnifeItem.getKnifeTarget 只看玩家，因此视线上严格更近的炸弹客无人机会替换结果。所有调用方共用：
 * 蓄力释放、瞬刺（NoellesRoles 拾荒者、SparkStrength 老兵/验尸官伪装）以及 Wathe 准星刀提示；由服务端
 * DroneKnifeStabPayloadMixin 校验并击毁。仅客户端：服务端调用原样返回。Wathe 成员且方法名唯一，选择器仅用方法名并 remap = false。
 */
@Mixin(KnifeItem.class)
public abstract class DroneKnifeTargetMixin {
    @ModifyReturnValue(method = "getKnifeTarget", at = @At("RETURN"), remap = false)
    private static HitResult sparkstrength$preferNearerDrone(HitResult original, PlayerEntity user) {
        if (user == null || !user.getWorld().isClient()) {
            return original;
        }
        return DroneHitGeometry.preferNearerDrone(user, original, DroneWeaponRules.KNIFE_RANGE);
    }
}
