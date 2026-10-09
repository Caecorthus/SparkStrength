package annina.sparkstrength.mixin.wathe;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.doctor4t.wathe.config.datapack.MapEnhancementsConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Jumping no longer costs stamina (owner 2026-10-09). Wathe reads the map's jump stamina cost in three places: the
 * server charge in {@code LivingEntity#jump}, the tick that disables jumping when stamina cannot pay, and the client
 * jump-key suppression. Zeroing the accessor turns all three off on both sides, so a tired player can still jump.
 * 跳跃不再消耗体力：地图跳跃体力消耗一律读成 0，服务端扣体力、体力不足禁跳和客户端按键屏蔽一并失效。
 */
@Mixin(value = MapEnhancementsConfiguration.JumpConfig.class, remap = false)
public abstract class JumpStaminaCostMixin {
    @ModifyReturnValue(method = "staminaCost", at = @At("RETURN"))
    private float sparkstrength$jumpCostsNoStamina(float mapCost) {
        return 0.0F;
    }
}
