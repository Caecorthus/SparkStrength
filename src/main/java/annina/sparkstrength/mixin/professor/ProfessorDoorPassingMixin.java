package annina.sparkstrength.mixin.professor;

import annina.sparkstrength.component.professor.ProfessorSerumTargetComponent;
import annina.sparkstrength.util.RaycastShapeScope;
import dev.doctor4t.wathe.block.DoorPartBlock;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.block.BlockState;
import net.minecraft.block.EntityShapeContext;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 穿门试剂：让目标在持续时间内忽略 Wathe 门的方块碰撞。
 *
 * <p>Wathe 门真正挡人的地方是 {@link DoorPartBlock#getCollisionShape}，
 * 因此这里只在碰撞形状查询时对带有效果的存活玩家返回空形状。
 * 门的渲染、开关状态和交互都不改。</p>
 *
 * <p>Movement only: COLLIDER rays reuse the target's shape context, so queries inside {@link RaycastShapeScope}
 * keep the door and the target's sight and aim ({@code canSee}, {@code ProjectileUtil.getCollision}) stop at it.
 * 仅限移动：COLLIDER 射线沿用目标的形状上下文，因此 {@link RaycastShapeScope} 内的查询保留门的形状，
 * 目标的视线与瞄准（canSee、ProjectileUtil.getCollision）仍会被门挡住。</p>
 */
@Mixin(DoorPartBlock.class)
public abstract class ProfessorDoorPassingMixin {
    @Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true)
    private void sparkstrength$passThroughDoor(
            BlockState state,
            BlockView world,
            BlockPos pos,
            ShapeContext context,
            CallbackInfoReturnable<VoxelShape> cir
    ) {
        if (!(context instanceof EntityShapeContext entityShapeContext)) {
            return;
        }

        Entity entity = entityShapeContext.getEntity();
        if (entity instanceof PlayerEntity player
                && GameFunctions.isPlayerPlayingAndAlive(player)
                && ProfessorSerumTargetComponent.KEY.get(player).hasDoorpassing()
                && !RaycastShapeScope.isRaycast()) {
            cir.setReturnValue(VoxelShapes.empty());
        }
    }
}
