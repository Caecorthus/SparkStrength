package annina.sparkstrength.client.mixin.vulture;

import annina.sparkstrength.client.role.vulture.SkateboardRenderer;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lifts a riding player's model (and everything drawn in its frame: features, name tag) onto the deck. Adds to the
 * returned offset at every RETURN, so vanilla's sneak drop and other mods' offsets compose; scales with the entity
 * like vanilla's sneak offset. Visual only: hitbox and camera stay where the server puts them.
 * 把滑行玩家的模型（以及在其坐标系内绘制的特征、名牌）抬到板面上。在每个 RETURN 处叠加到返回的偏移上，
 * 因此与原版潜行下沉及其他模组的偏移叠加共存；与原版潜行偏移一样随实体缩放。纯视觉：碰撞箱与镜头保持服务端位置。
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class SkateboardLiftMixin {
    @ModifyReturnValue(
            method = "getPositionOffset(Lnet/minecraft/client/network/AbstractClientPlayerEntity;F)Lnet/minecraft/util/math/Vec3d;",
            at = @At("RETURN")
    )
    private Vec3d sparkstrength$liftSkateboardRider(Vec3d offset, AbstractClientPlayerEntity player, float tickDelta) {
        if (!SkateboardRenderer.standsOnBoard(player)) {
            return offset;
        }
        return offset.add(0.0D, SkateboardRenderer.DECK_TOP_HEIGHT * player.getScale(), 0.0D);
    }
}
