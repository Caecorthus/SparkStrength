package annina.sparkstrength.mixin.attendant;

import annina.sparkstrength.role.attendant.DoorLogService;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.doctor4t.wathe.block.SmallDoorBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Observes Wathe's room-door use for the Attendant door monitor without changing its result: the wrapper snapshots
 * the clicked door before and after the whole onUse (DoorInteraction listeners included) and hands both to
 * {@link DoorLogService}. Server only; the client keeps Wathe's prediction untouched. {@code TrainDoorBlock}
 * overrides onUse without calling super, so train doors never reach this wrapper.
 * 为乘务员房门监控观察 Wathe 房门交互，不改变其结果：在整个 onUse（含 DoorInteraction 监听器）前后对被点击的门取快照，
 * 交给 DoorLogService。仅服务端；客户端预测保持原样。TrainDoorBlock 重写 onUse 且不调用 super，列车门不会进入此包装。
 */
@Mixin(SmallDoorBlock.class)
public abstract class SmallDoorBlockDoorLogMixin {
    @WrapMethod(method = "onUse", require = 1, allow = 1)
    private ActionResult sparkstrength$logRoomDoorUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                                                      BlockHitResult hit, Operation<ActionResult> original) {
        if (world.isClient()) {
            return original.call(state, world, pos, player, hit);
        }
        DoorLogService.Interaction interaction = DoorLogService.beginUse(state, world, pos, player);
        boolean completed = false;
        try {
            ActionResult result = original.call(state, world, pos, player, hit);
            completed = true;
            return result;
        } finally {
            // Always unwind the per-thread interaction; only a normal return is logged.
            // 无论如何都要回退线程内的交互记录；只有正常返回才记录。
            DoorLogService.endUse(interaction, completed);
        }
    }
}
