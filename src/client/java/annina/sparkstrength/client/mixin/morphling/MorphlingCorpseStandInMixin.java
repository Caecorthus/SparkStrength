package annina.sparkstrength.client.mixin.morphling;

import annina.sparkstrength.client.role.morphling.MorphlingCorpseStandIn;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Corpse-mode Morphling stand-in ({@link MorphlingCorpseStandIn}): a whole-method wrap of the dispatcher's render hands
 * every draw of that player (world pass, shadow-map pass, SparkWitch Blind redraws, secondary world views) the Wathe
 * corpse stand-in instead, at the same camera-relative position. Everything inside the method, ETF's state mount
 * included, then sees the corpse: its renderer, its zero position offset (a sneaking player's offset would sink it) and
 * its zero shadow. NoellesRoles' corpse transform and angle mixins on the player renderer no longer run in the world.
 * The outline colour was already chosen for the player by the world renderer, so instinct rules still decide it.
 * 尸体模式变形者替身（见 MorphlingCorpseStandIn）：整方法包裹分发器的 render，让该玩家的每次绘制（世界 pass、阴影贴图 pass、
 * SparkWitch 盲人重绘、次级世界视图）都在相同的相机相对位置改为分发 Wathe 尸体替身。方法内的一切，包括 ETF 的状态挂载，
 * 看到的都是尸体：它的渲染器、为零的位置偏移（潜行玩家的偏移会让它沉入地面）与为零的阴影。NoellesRoles 在玩家渲染器上的
 * 尸体变换与角度 mixin 在世界中不再执行。描边颜色已由世界渲染器按玩家选定，因此仍由本能规则决定。
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class MorphlingCorpseStandInMixin {
    @WrapMethod(method = "render(Lnet/minecraft/entity/Entity;DDDFFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V")
    private void sparkstrength$drawCorpseMorphlingAsCorpse(Entity entity, double x, double y, double z, float yaw,
                                                           float tickDelta, MatrixStack matrices,
                                                           VertexConsumerProvider vertexConsumers, int light,
                                                           Operation<Void> original) {
        Entity standIn = MorphlingCorpseStandIn.standInFor(entity);
        if (standIn == null) {
            original.call(entity, x, y, z, yaw, tickDelta, matrices, vertexConsumers, light);
            return;
        }
        original.call(standIn, x, y, z, standIn.getYaw(), tickDelta, matrices, vertexConsumers, light);
    }
}
