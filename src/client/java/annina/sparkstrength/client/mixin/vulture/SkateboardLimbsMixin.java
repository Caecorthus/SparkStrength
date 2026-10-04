package annina.sparkstrength.client.mixin.vulture;

import annina.sparkstrength.client.role.vulture.SkateboardClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * A rider rolls instead of walking: on the client, a riding player's limb input is zeroed so the legs settle into a
 * standing pose. {@code updateLimbs(F)} is the single sink for both the local player's travel and remote players'
 * tick; every other entity, and every server-side entity, passes through untouched.
 * 骑手是滚动而非行走：在客户端把滑行玩家的肢体输入置零，腿部回到站立姿态。{@code updateLimbs(F)} 是本地玩家移动与
 * 远程玩家 tick 的共同出口；其他实体以及服务端实体均原样通过。
 */
@Mixin(LivingEntity.class)
public abstract class SkateboardLimbsMixin {
    @ModifyVariable(method = "updateLimbs(F)V", at = @At("HEAD"), argsOnly = true)
    private float sparkstrength$standStillOnSkateboard(float posDelta) {
        if ((Object) this instanceof PlayerEntity player && player.getWorld().isClient()
                && SkateboardClient.isRiding(player)) {
            return 0.0F;
        }
        return posDelta;
    }
}
