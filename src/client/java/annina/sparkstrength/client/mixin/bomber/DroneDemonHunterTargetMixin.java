package annina.sparkstrength.client.mixin.bomber;

import annina.sparkstrength.role.bomber.drone.DroneHitGeometry;
import annina.sparkstrength.role.bomber.drone.DroneWeaponRules;
import annina.sparkstrength.role.partyanimal.PartygoerHitGeometry;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import org.agmas.noellesroles.demonhunter.DemonHunterPistolItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Client Demon Hunter pistol target (NoellesRoles): its players-only {@code getGunTarget} is replaced by a strictly
 * nearer Bomber drone, so the pistol sends the drone id and {@code DroneDemonHunterShootMixin} validates and breaks it.
 * Only NoellesRoles' client helper calls it; server-side calls are returned untouched.
 * 客户端猎魔枪目标（NoellesRoles）：其只看玩家的 getGunTarget 结果会被严格更近的炸弹客无人机替换，猎魔枪随即发送无人机 id，
 * 由 DroneDemonHunterShootMixin 校验并击毁。只有 NoellesRoles 客户端辅助类调用它；服务端调用原样返回。
 *
 * <p>A Partygoer strictly nearer than that result replaces it in turn; {@code DroneDemonHunterShootMixin} validates and
 * kills it. / 严格比该结果更近的派对客再次替换它；由 DroneDemonHunterShootMixin 校验并击杀。</p>
 */
@Mixin(DemonHunterPistolItem.class)
public abstract class DroneDemonHunterTargetMixin {
    @ModifyReturnValue(method = "getGunTarget", at = @At("RETURN"), remap = false)
    private static HitResult sparkstrength$preferNearerDrone(HitResult original, PlayerEntity user) {
        if (user == null || !user.getWorld().isClient()) {
            return original;
        }
        return PartygoerHitGeometry.preferNearer(user,
                DroneHitGeometry.preferNearerDrone(user, original, DroneWeaponRules.DEMON_HUNTER_PISTOL_RANGE),
                DroneWeaponRules.DEMON_HUNTER_PISTOL_RANGE);
    }
}
